package com.mineagent.engine.survival;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.entity.InputDriver;
import com.mineagent.api.task.TaskChain;
import com.mineagent.engine.MineAgentEngine;
import com.mineagent.engine.act.Interaction;
import com.mineagent.engine.combat.ArrowPredictor;
import com.mineagent.engine.combat.CombatBrain;
import com.mineagent.engine.combat.CombatBrain.Action;
import com.mineagent.engine.combat.CombatBrain.Assessment;
import com.mineagent.engine.combat.CombatBrain.Context;
import com.mineagent.engine.combat.CombatBrain.Duty;
import com.mineagent.engine.combat.CombatBrain.Hostile;
import com.mineagent.engine.combat.CombatBrain.Move;
import com.mineagent.engine.combat.CombatBrain.Posture;
import com.mineagent.engine.combat.CombatBrain.Self;
import com.mineagent.engine.combat.MobPolicy;
import com.mineagent.engine.combat.Tactics;
import com.mineagent.engine.entity.CompanionEntity;
import com.mineagent.engine.learning.Scorecard;
import com.mineagent.engine.loop.AgentLoop;
import com.mineagent.engine.pathing.execute.PlayerNav;
import com.mineagent.engine.task.TaskContext;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Fighting, fleeing and keeping a post: the game side of {@link CombatBrain} (SMH plan 69, block 3; the Andy
 * Omega plan §12; altoclef's MobDefenseChain, MIT, see THIRD_PARTY_NOTICES.md). Each tick it reads what the
 * game shows (the hostiles, incoming arrows, a skeleton drawing, the companion's gear), asks the brain, and does
 * what the brain says through the same inputs a player has. What it fights depends on its duty
 * ({@link AgentLoop#combatSetup}): a guard clears its perimeter and never leaves it to chase; at work it answers
 * only what comes at it close by, then goes back to the work; a mob it cannot reach is let go.
 *
 * <p>Every fight is measured (health lost, time, a death) and rated by stance against that mob, so the stance it
 * picks next time is the one the game has rated best ({@link AgentLoop#tacticsFor}). The model may advise the
 * fight every two seconds; the fight never waits for it, and the mob table's "never" rules stay whatever it says.
 *
 * <p>Priorities: fleeing 5.5 and up; a fight with something attacking it, its owner or its ward 5.5 and up (it
 * pauses the body task, which resumes after); clearing a guarded perimeter 5.0; walking back to a guard post 1.5;
 * holding the post 0.5 (so it does not wander off). A body task the owner gave it outranks the last two.
 */
public final class MobDefenseChain implements TaskChain {

    private static final float PRIORITY_FLEE = 5.5f;
    private static final float PRIORITY_ATTACKED = 5.5f;
    private static final float PRIORITY_RETURN = 1.5f;
    private static final float PRIORITY_HOLD_POST = 0.5f;
    /** Looked at around the companion, blocks. */
    private static final double SCAN_RADIUS = 20.0;
    /** A guard is back at its post within this many blocks. */
    private static final double AT_POST = 3.0;
    /** A ward (a player) is kept within this many blocks when nothing is happening. */
    private static final double NEAR_WARD = 5.0;
    /** A target it has made no headway on for this long is let go (it cannot reach it). */
    private static final int NO_HEADWAY_TICKS = 200;
    private static final int LET_GO_TICKS = 600;
    private static final int REPATH_TICKS = 20;
    /** A skeleton this far or nearer, drawing this long, gets the shield. */
    private static final double DRAW_RANGE = 16.0;
    private static final int DRAW_TICKS = 15;

    private final SurvivalConfig config;
    private final CompanionBodyLog bodyLog;
    private final AgentPlayer companion;
    private PlayerNav nav;
    private net.minecraft.world.level.Level navLevel;

    /** What getPriority saw this tick, reused by tick(). */
    private Snapshot seen;
    /** How fast its health is falling (MA8.2): fast enough to die within two seconds means leave now. */
    private final com.mineagent.engine.combat.HealthTrend trend = new com.mineagent.engine.combat.HealthTrend();
    private long trendTick = -1;
    /** Fleeing and getting nowhere, the threat at arm's length: cornered. Blocks placed under it pillaring up. */
    private int stuckFleeing;
    private int pillared;
    private net.minecraft.core.BlockPos pillarBase;

    // The fight under way (kept across interruptions by other chains; ended when nothing is left).
    private boolean fighting;
    private boolean fled;
    private Vec3 fightAnchor;
    private Tactics tactics;
    private String fightMob = "";
    private int fightTicks;
    private UUID trackedTarget;
    private double bestDistance;
    private long lastHeadway;
    private final Map<UUID, Long> letGo = new HashMap<>();
    private int repath;
    private Vec3 navGoal;
    /** The last day it geared up at dusk (MA3.6). */
    private long gearedDay = -1;

    public MobDefenseChain(AgentPlayer companion, SurvivalConfig config) {
        this.companion = companion;
        this.config = config;
        this.bodyLog = SurvivalBuiltin.bodyLog(companion);
    }

    @Override
    public String name() {
        return "mob_defense";
    }

    /** What one tick saw. {@code mobs} lines up with {@code hostiles}. */
    private record Snapshot(long tick, AgentLoop.CombatSetup setup, Vec3 anchor, Context context,
                            List<Hostile> hostiles, List<Mob> mobs, Self self, Assessment assessment,
                            boolean arrowIncoming, Vec3 arrowFrom, boolean skeletonDrawing) {}

    @Override
    public float getPriority(AgentPlayer companion) {
        try {
            Snapshot s = look();
            if (s == null) return Float.NEGATIVE_INFINITY;
            Posture posture = s.assessment().posture();
            if (posture == Posture.FLEEING || posture == Posture.OVERWHELMED) {
                return Math.max(PRIORITY_FLEE, SurvivalDecisions.mobDefensePriority(
                        (float) s.self().health(), (float) s.self().maxHealth(), s.hostiles().size()));
            }
            if (posture == Posture.ENGAGED) {
                Hostile top = s.hostiles().get(s.assessment().primary());
                float urgency = SurvivalDecisions.mobDefensePriority((float) s.self().health(),
                        (float) s.self().maxHealth(), s.hostiles().size());
                boolean pressing = top.targetsSelf() || top.targetsOwner() || top.targetsWard() || top.distance() <= 4.0;
                return pressing ? Math.max(PRIORITY_ATTACKED, urgency) : urgency;
            }
            // Nothing left to fight: the fight is over (ended here, so a body task holding the body cannot keep it open).
            if (fighting) endFight(TaskContext.agentLoop(companion), fled ? Scorecard.Outcome.RETREATED : Scorecard.Outcome.DONE);
            gearUpAtDusk();
            if (s.setup().duty() == Duty.GUARD && s.anchor() != null) {
                return s.context().selfFromAnchor() > nearEnough(s) ? PRIORITY_RETURN : PRIORITY_HOLD_POST;
            }
            return Float.NEGATIVE_INFINITY;
        } catch (Exception e) {
            System.err.println("[MineAgent] MobDefense getPriority error: " + e.getMessage());
            return Float.NEGATIVE_INFINITY;
        }
    }

    @Override
    public void tick(AgentPlayer companion) {
        try {
            ServerPlayer sp = serverPlayer();
            InputDriver input = TaskContext.inputDriver(companion);
            Snapshot s = seen != null && seen.tick() == sp.level().getGameTime() ? seen : look();
            if (s == null) {
                stop(input, sp);
                return;
            }
            AgentLoop loop = TaskContext.agentLoop(companion);
            Assessment a = s.assessment();
            Posture posture = a.posture();

            if (posture == Posture.SAFE || posture == Posture.ALERT) {
                if (fighting) endFight(loop, fled ? Scorecard.Outcome.RETREATED : Scorecard.Outcome.DONE);
                if (shootAtRange(s, sp, input, false)) return;
                if (s.setup().duty() == Duty.GUARD && s.anchor() != null) keepPost(s, sp, input);
                else stop(input, sp);
                return;
            }

            Hostile target = a.primary() >= 0 ? s.hostiles().get(a.primary()) : null;
            Mob targetMob = a.primary() >= 0 ? s.mobs().get(a.primary()) : null;
            if (!fighting && target != null) beginFight(loop, s, target, sp);
            fightTicks++;
            if (posture == Posture.FLEEING || posture == Posture.OVERWHELMED) {
                if (!fled) bodyLog.report(a.reason() + ", getting away from " + target.type());
                fled = true;
            }
            if (loop != null && fightTicks % 40 == 1) {
                // Mid-fight, only the model's word on whom to strike first applies: the fight stays rated under the
                // way it began with, and the rest of the advice is tried in a later fight (combat/TacticBook).
                List<String> order = loop.advisedTargetOrder();
                if (tactics != null && !order.isEmpty() && !order.equals(tactics.targetOrder())) {
                    tactics = new Tactics(tactics.stance(), order, tactics.retreatHealth(), tactics.useShield(), tactics.crits());
                }
                loop.adviseFight(situation(s, a));
            }
            if (targetMob != null) watchHeadway(targetMob, target, sp);
            if (tactics == null) tactics = Tactics.of("balanced", config.healthFlee());

            double charge = sp.getAttackStrengthScale(0.5f);
            boolean onGround = sp.onGround();
            boolean falling = !onGround && sp.getDeltaMovement().y < 0 && sp.fallDistance > 0;
            Action act = CombatBrain.decide(a, target, s.self(), charge, onGround, falling, sp.isInWater(),
                    s.arrowIncoming(), s.skeletonDrawing(), tactics, sp.level().getGameTime(), s.context());
            // Fighting back switched off: it gets away instead, unless the owner set it to guard.
            if (!reflexEnabled("fight_back", companion, config.fightBack()) && s.setup().duty() != Duty.GUARD
                    && act.move() != Move.RETURN && act.move() != Move.HOLD) {
                act = new Action(Move.FLEE, 0, !act.shield(), false, act.shield(), false);
            }
            if (act.move() != Move.FLEE && shootAtRange(s, sp, input, true)) return;
            if (sp.isUsingItem() && sp.getUseItem().getItem() instanceof BowItem) sp.stopUsingItem();
            // Its business only because the bow reaches it: shot from here or not at all, never walked to.
            if (target != null && act.move() != Move.FLEE && !CombatBrain.relevant(target, s.context())) {
                if (nav != null && nav.isNavigating()) nav.cancel();
                navGoal = null;
                input.clear();
                if (targetMob != null) face(sp, targetMob.getEyePosition());
                return;
            }
            apply(act, s, target, targetMob, sp, input);
            // For the game tests: what it saw and did, every 2 seconds of a fight (the property is unset in play).
            if (Boolean.getBoolean("mineagent.traceFights") && sp.level().getGameTime() % 40 == 0 && targetMob != null) {
                System.out.println("[FIGHT " + companion.companionName() + "] " + posture + " (" + a.reason() + "), "
                        + String.format("%.1f", target.distance()) + " away, charge " + String.format("%.2f", charge)
                        + ", " + act.move() + (act.attack() ? " strike" : "") + (act.jump() ? " jump" : "")
                        + (act.shield() ? " shield" : "") + ", in reach " + Interaction.inEntityReach(sp, targetMob)
                        + ", sight " + sp.hasLineOfSight(targetMob) + ", using " + sp.isUsingItem()
                        + ", on ground " + onGround + ", stance " + tactics.stance()
                        + "; server tick " + sp.server.getTickCount() + ", game time " + sp.level().getGameTime()
                        + ", day time " + sp.level().getDayTime() + "; its ticks " + sp.tickCount + ", the mob's "
                        + targetMob.tickCount + ", mob's spot entity-ticking "
                        + sp.serverLevel().isPositionEntityTicking(targetMob.blockPosition()) + ", runs normally "
                        + sp.level().tickRateManager().runsNormally() + ", sprinting "
                        + sp.server.tickRateManager().isSprinting());
            }
        } catch (Exception e) {
            System.err.println("[MineAgent] MobDefense tick error: " + e.getMessage());
            stop(TaskContext.inputDriver(companion), serverPlayer());
        }
    }

    @Override
    public void onInterrupt(AgentPlayer companion) {
        // Another chain or the body task takes over; a fight under way stays open and is ended (and rated) when
        // nothing is left to fight.
        try {
            stop(TaskContext.inputDriver(companion), serverPlayer());
        } catch (Exception ignored) {
            // The entity may already be tearing down.
        }
    }

    // ── Seeing ──────────────────────────────────────────────────────────

    private Snapshot look() {
        ServerPlayer sp = serverPlayer();
        if (sp == null || !sp.isAlive()) return null;
        ServerPlayer owner = ((CompanionEntity) companion).serverPlayerOwner();
        AgentLoop loop = TaskContext.agentLoop(companion);
        long now = sp.level().getGameTime();
        AgentLoop.CombatSetup setup = loop != null ? loop.combatSetup(sp, owner)
                : new AgentLoop.CombatSetup(Duty.IDLE, null, owner != null ? owner.getUUID() : null, 10.0);
        // Work and idle: the anchor is where the companion stood when the fight began; it goes back there after.
        Vec3 anchor = setup.anchor() != null ? setup.anchor() : fighting && fightAnchor != null ? fightAnchor : sp.position();
        double selfFromAnchor = horizontal(sp.position(), anchor);
        Context context = new Context(setup.duty(), setup.perimeter(), selfFromAnchor);

        AABB box = scanBox(sp, anchor, setup);
        boolean avoidCreepers = reflexEnabled("avoid_creeper", companion, true);
        UUID ownerUuid = companion.ownerUuid();
        List<Hostile> hostiles = new ArrayList<>();
        List<Mob> mobs = new ArrayList<>();
        boolean drawing = false;
        letGo.values().removeIf(until -> until < now);
        // Whom it protects besides itself (MA3.5): the owner and trusted players within 24 blocks.
        List<Player> charges = new ArrayList<>();
        if (owner != null && owner.level() == sp.level()) charges.add(owner);
        List<String> trusted = loop != null ? loop.getAgentSettings().trusted() : List.of();
        if (!trusted.isEmpty()) {
            for (Player p : sp.level().players()) {
                if (p != sp && p != owner && p.isAlive() && p.distanceTo(sp) <= 24.0
                        && trusted.contains(p.getUUID().toString().toLowerCase(Locale.ROOT))) charges.add(p);
            }
        }
        for (Mob mob : sp.level().getEntitiesOfClass(Mob.class, box, Mob::isAlive)) {
            boolean enemy = mob instanceof Enemy;
            LivingEntity aim = mob.getTarget();
            boolean targetsSelf = aim == sp;
            boolean targetsOwner = (aim != null && (charges.contains(aim)
                    || (aim instanceof Player p && ownerUuid != null && MineAgentEngine.isCompanionOwnedBy(p.getUUID(), ownerUuid))))
                    || hurtOneOf(mob, charges);
            boolean targetsWard = aim != null && setup.ward() != null && aim.getUUID().equals(setup.ward());
            // On guard, its owner's pets and the villagers inside the perimeter are kept as its owner is (MA10.1).
            if (!targetsWard && aim != null && setup.duty() == Duty.GUARD && com.mineagent.engine.settings.Switches.guardPerimeter()
                    && (aim instanceof net.minecraft.world.entity.OwnableEntity pet && ownerUuid != null && ownerUuid.equals(pet.getOwnerUUID())
                    || aim instanceof net.minecraft.world.entity.npc.AbstractVillager && horizontal(aim.position(), anchor) <= setup.perimeter())) {
                targetsWard = true;
            }
            if (!enemy && !(mob instanceof NeutralMob && (targetsSelf || targetsOwner || targetsWard))) continue;
            if (mob instanceof Creeper && !avoidCreepers && !targetsSelf) continue;
            double distance = mob.distanceTo(sp);
            // A phantom circling overhead is out of reach: watched, and fought as it dives in (or shot, with a bow).
            if (mob instanceof net.minecraft.world.entity.monster.Phantom && distance > 5.0 && !carriesBow(sp)) continue;
            if (letGo.containsKey(mob.getUUID()) && distance > 4.0) continue;
            String type = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath();
            boolean bow = mob.isUsingItem() && (mob.getUseItem().getItem() instanceof BowItem
                    || mob.getUseItem().getItem() instanceof CrossbowItem);
            boolean sight = sp.hasLineOfSight(mob);
            if (bow && sight && distance <= DRAW_RANGE && mob.getTicksUsingItem() >= DRAW_TICKS) drawing = true;
            Vec3 toMob = mob.position().subtract(sp.position());
            Vec3 toSelf = toMob.lengthSqr() < 1e-6 ? Vec3.ZERO : toMob.normalize().scale(-1);
            double closing = mob.getDeltaMovement().dot(toSelf);
            double bearing = Math.toDegrees(Math.atan2(toMob.z, toMob.x));
            boolean fusing = mob instanceof Creeper c && c.getSwellDir() > 0;
            boolean angry = aim != null;
            hostiles.add(new Hostile(type, distance, closing, mob.getHealth(), targetsSelf, targetsOwner, fusing, bow,
                    sight, bearing, angry, horizontal(mob.position(), anchor), targetsWard));
            mobs.add(mob);
        }

        boolean arrow = false;
        Vec3 arrowFrom = null;
        Vec3 me = sp.position().add(0, sp.getBbHeight() * 0.5, 0);
        for (AbstractArrow a : sp.level().getEntitiesOfClass(AbstractArrow.class, sp.getBoundingBox().inflate(12.0))) {
            Entity shooter = a.getOwner();
            if (shooter == sp || shooter == owner) continue;
            Vec3 v = a.getDeltaMovement();
            if (v.lengthSqr() < 0.01) continue; // stuck in the ground
            Vec3 at = a.position();
            if (ArrowPredictor.incoming(at.x, at.y, at.z, v.x, v.y, v.z, ArrowPredictor.ARROW_GRAVITY, me.x, me.y, me.z)) {
                arrow = true;
                arrowFrom = at;
                break;
            }
        }

        Self self = selfOf(sp);
        Tactics t = tactics != null ? tactics : Tactics.of("balanced", config.healthFlee());
        Assessment assessment = CombatBrain.assess(hostiles, self, t, context);
        if (now != trendTick) {
            trendTick = now;
            trend.sample(now, sp.getHealth());
        }
        if (!hostiles.isEmpty() && trend.lethalSoon() && assessment.posture() != Posture.FLEEING) {
            // Leaving before the hit that kills: whatever the fight looks like, at this rate it is over in two seconds.
            assessment = new Assessment(assessment.total(), assessment.strength(), Posture.FLEEING, assessment.primary(),
                    assessment.ownerAtRisk(), "health falling too fast: leaving before the hit that kills");
        }
        Snapshot s = new Snapshot(now, setup, anchor, context, hostiles, mobs, self, assessment, arrow, arrowFrom, drawing);
        seen = s;
        return s;
    }

    /** It hit one of them in the last 5 s (it may have turned to someone else since). */
    private static boolean hurtOneOf(Mob mob, List<Player> charges) {
        for (Player p : charges) {
            if (p.getLastHurtByMob() == mob && p.tickCount - p.getLastHurtByMobTimestamp() < 100) return true;
        }
        return false;
    }

    private static AABB scanBox(ServerPlayer sp, Vec3 anchor, AgentLoop.CombatSetup setup) {
        // With a bow and arrows it looks out to the bow's reach (combat/RangedChoice).
        AABB around = sp.getBoundingBox().inflate(carriesBow(sp) ? com.mineagent.engine.combat.RangedChoice.MAX : SCAN_RADIUS);
        if (setup.duty() != Duty.GUARD && setup.duty() != Duty.ESCORT) return around;
        double r = setup.perimeter() + 8.0;
        return new AABB(Math.min(around.minX, anchor.x - r), Math.min(around.minY, anchor.y - 8), Math.min(around.minZ, anchor.z - r),
                Math.max(around.maxX, anchor.x + r), Math.max(around.maxY, anchor.y + 8), Math.max(around.maxZ, anchor.z + r));
    }

    private static boolean carriesBow(ServerPlayer sp) {
        var inv = sp.getInventory();
        boolean bow = false, arrows = false;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var st = inv.getItem(i);
            if (st.getItem() instanceof BowItem) bow = true;
            if (st.getItem() instanceof net.minecraft.world.item.ArrowItem) arrows = true;
        }
        return bow && arrows;
    }

    private Self selfOf(ServerPlayer sp) {
        var inv = sp.getInventory();
        boolean shield = sp.getOffhandItem().getItem() instanceof ShieldItem;
        boolean bow = false;
        int arrows = 0;
        int tier = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof ShieldItem) shield = true;
            if (stack.getItem() instanceof BowItem) bow = true;
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            if (id.endsWith("arrow")) arrows += stack.getCount();
            tier = Math.max(tier, weaponTier(stack));
        }
        boolean hurtByEffect = sp.hasEffect(MobEffects.WITHER) || sp.hasEffect(MobEffects.POISON);
        return new Self(sp.getHealth(), sp.getMaxHealth(), sp.getArmorValue(), shield, tier, bow, arrows,
                sp.getFoodData().getFoodLevel(), hurtByEffect);
    }

    /** 0 not a weapon, 1 wood or gold .. 5 netherite; a sword or an axe counts in full, any other tool one less. */
    public static int weaponTier(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof TieredItem)) return 0;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        int tier = id.startsWith("netherite_") ? 5 : id.startsWith("diamond_") ? 4 : id.startsWith("iron_") ? 3
                : id.startsWith("stone_") ? 2 : 1;
        boolean weapon = id.endsWith("_sword") || id.endsWith("_axe");
        return weapon ? tier : Math.max(1, tier - 1);
    }

    // ── Acting ──────────────────────────────────────────────────────────

    private void apply(Action act, Snapshot s, Hostile target, Mob targetMob, ServerPlayer sp, InputDriver input) {
        Move move = act.move();
        if (move == Move.FLEE) {
            flee(s, targetMob, sp, input, act);
            return;
        }
        if (move == Move.RETURN) {
            shield(act.shield(), sp);
            walkTo(s.anchor(), sp, input);
            return;
        }
        if (nav != null && nav.isNavigating()) nav.cancel();
        navGoal = null;
        if (s.arrowIncoming() && act.shield() && s.arrowFrom() != null) face(sp, s.arrowFrom());
        else if (targetMob != null) face(sp, targetMob.getEyePosition());
        input.setForward(switch (move) {
            case APPROACH -> 1.0f;
            case BACK_OFF -> -1.0f;
            default -> 0.0f;
        });
        input.setStrafe((float) act.strafe());
        input.setSprinting(act.sprint() && move == Move.APPROACH);
        boolean blocked = sp.horizontalCollision && sp.onGround() && move != Move.HOLD;
        input.setJumping(act.jump() || blocked);
        if (act.attack() && targetMob != null) {
            shield(false, sp);
            equipWeapon(sp);
            if (Interaction.attackEntity(sp, targetMob)) lastHeadway = sp.level().getGameTime();
        } else {
            shield(act.shield(), sp);
        }
    }

    /** Away from the danger: straight away when it is close (a fusing creeper cannot wait for a path), else a path. */
    private void flee(Snapshot s, Mob from, ServerPlayer sp, InputDriver input, Action act) {
        if (!eatingApple(sp, from)) shield(act.shield(), sp);
        if (from == null) {
            stop(input, sp);
            return;
        }
        double distance = from.distanceTo(sp);
        // Cornered (MA8.2): fleeing for two seconds with the threat still at arm's length and nowhere it got to: up a
        // pillar of the blocks it carries, 8 high at most; most mobs can't follow.
        boolean going = sp.getDeltaMovement().horizontalDistanceSqr() > 0.002;
        stuckFleeing = distance < 3.0 && !going ? stuckFleeing + 1 : Math.max(0, stuckFleeing - 2);
        if ((stuckFleeing > 40 || pillared > 0 && pillared < 8 && distance < 4.0)
                && com.mineagent.engine.act.Placement.hasSupportBlock(sp)) {
            if (nav != null && nav.isNavigating()) nav.cancel();
            navGoal = null;
            input.setForward(0.0f);
            input.setStrafe(0.0f);
            input.setSprinting(false);
            sp.setXRot(90.0f); // straight down
            if (sp.onGround()) pillarBase = sp.blockPosition();
            input.setJumping(pillared < 8);
            // At the top of the jump, clear of the cell it stood in: a block into that cell, under its feet.
            if (!sp.onGround() && pillarBase != null && sp.getY() >= pillarBase.getY() + 1.0
                    && sp.level().getBlockState(pillarBase).isAir()
                    && com.mineagent.engine.act.Placement.placeAnySupportBlock(sp, pillarBase)) {
                pillared++;
                pillarBase = null;
                if (pillared == 1) SurvivalBuiltin.bodyLog(companion).report("cornered: pillaring up");
            }
            return;
        }
        if (distance > 8.0) pillared = 0;
        if (distance < 8.0) {
            if (nav != null && nav.isNavigating()) nav.cancel();
            navGoal = null;
            Vec3 away = sp.position().subtract(from.position());
            if (away.horizontalDistanceSqr() < 1e-6) away = new Vec3(1, 0, 0);
            face(sp, sp.getEyePosition().add(away.x, 0, away.z));
            input.setForward(1.0f);
            input.setStrafe(0.0f);
            input.setSprinting(!act.shield());
            input.setJumping(sp.horizontalCollision && sp.onGround());
            return;
        }
        PlayerNav n = nav();
        if (navGoal == null || ++repath >= REPATH_TICKS || !n.isNavigating()) {
            double keep = Math.max(16.0, MobPolicy.of(BuiltInRegistries.ENTITY_TYPE.getKey(from.getType()).getPath()).keepDistance() + 4.0);
            n.runAway((int) Math.floor(from.getX()), (int) Math.floor(from.getY()), (int) Math.floor(from.getZ()), keep);
            navGoal = from.position();
            repath = 0;
        }
        n.tick();
    }

    /**
     * Low and getting away with room to do it: a golden apple eaten on the run (regeneration and absorption; an enchanted
     * one, kept for the worst, adds resistance and fire resistance). True while it eats one.
     */
    private boolean eatingApple(ServerPlayer sp, Mob from) {
        if (sp.isUsingItem() && FoodPolicy.isGoldenApple(sp.getUseItem().getItem())) return true;
        if (sp.getHealth() > 8.0f || sp.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION)
                || sp.hasEffect(net.minecraft.world.effect.MobEffects.ABSORPTION)) return false;
        if (from != null && from.distanceTo(sp) < 4.0) return false;
        var inv = sp.getInventory();
        boolean plain = inv.countItem(net.minecraft.world.item.Items.GOLDEN_APPLE) > 0;
        boolean enchanted = inv.countItem(net.minecraft.world.item.Items.ENCHANTED_GOLDEN_APPLE) > 0;
        String pick = enchanted && (sp.getHealth() <= 5.0f || !plain) ? "minecraft:enchanted_golden_apple"
                : plain ? "minecraft:golden_apple" : null;
        if (pick == null) return false;
        if (sp.isUsingItem()) sp.stopUsingItem();
        if (!TaskContext.selectInventoryItemForHand(companion, pick, InteractionHand.MAIN_HAND)) return false;
        Interaction.useItem(sp, InteractionHand.MAIN_HAND);
        if (sp.isUsingItem()) SurvivalBuiltin.bodyLog(companion).report("low and getting away: eating "
                + (pick.contains("enchanted") ? "an enchanted golden apple" : "a golden apple"));
        return sp.isUsingItem();
    }

    /** Back to the post (or the ward, or the work spot), by a path. */
    private void walkTo(Vec3 goal, ServerPlayer sp, InputDriver input) {
        if (goal == null) {
            stop(input, sp);
            return;
        }
        PlayerNav n = nav();
        boolean moved = navGoal == null || navGoal.distanceToSqr(goal) > 4.0;
        if (moved || ++repath >= REPATH_TICKS && !n.isNavigating()) {
            n.navigateNear((int) Math.floor(goal.x), (int) Math.floor(goal.y), (int) Math.floor(goal.z), 2);
            navGoal = goal;
            repath = 0;
        }
        n.tick();
    }

    /** Nothing to fight: walk back to the post if away, else stand at it watching the perimeter. */
    private void keepPost(Snapshot s, ServerPlayer sp, InputDriver input) {
        shield(false, sp);
        if (s.context().selfFromAnchor() > nearEnough(s)) {
            walkTo(s.anchor(), sp, input);
            return;
        }
        if (nav != null && nav.isNavigating()) nav.cancel();
        navGoal = null;
        input.clear();
        // Face the nearest hostile it can see (a guard watches what is coming), else stand as it is.
        Mob nearest = null;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < s.mobs().size(); i++) {
            Hostile h = s.hostiles().get(i);
            if (h.lineOfSight() && h.distance() < best) {
                best = h.distance();
                nearest = s.mobs().get(i);
            }
        }
        if (nearest != null) face(sp, nearest.getEyePosition());
    }

    /**
     * The bow at range (MA10.1, and SMH plan 70's "clutch with a bow"): whom {@code combat/RangedChoice} picks (a creeper
     * coming in, an archer, anything coming for someone, and on guard anything near the post), shot from where it stands,
     * drawn to full and aimed as the arrow flies ({@code act/BowShot}). Against an archer it side-steps while it draws.
     * Out of the fight ({@code engaged} false) only on guard or at what is already coming for someone. True while it
     * draws or looses.
     */
    private boolean shootAtRange(Snapshot s, ServerPlayer sp, InputDriver input, boolean engaged) {
        boolean drawing = sp.isUsingItem() && sp.getUseItem().getItem() instanceof BowItem;
        boolean guard = s.setup().duty() == Duty.GUARD && com.mineagent.engine.settings.Switches.guardPerimeter();
        int i = !com.mineagent.engine.settings.Switches.bowAtRange() && !guard ? -1
                : com.mineagent.engine.combat.RangedChoice.pick(s.hostiles(), s.self(), sp.isInWater(), guard);
        if (i >= 0 && !com.mineagent.engine.settings.Switches.bowAtRange() && !"creeper".equals(s.hostiles().get(i).type())) i = -1;
        if (i >= 0 && !engaged && !guard && !s.hostiles().get(i).targetsSelf() && !s.hostiles().get(i).targetsOwner()) i = -1;
        if (i < 0) {
            if (drawing) sp.stopUsingItem();
            return false;
        }
        Mob best = s.mobs().get(i);
        Hostile h = s.hostiles().get(i);
        if (!(sp.getMainHandItem().getItem() instanceof BowItem)
                && !TaskContext.selectInventoryItemForHand(companion, "minecraft:bow", InteractionHand.MAIN_HAND)) return false;
        if (nav != null && nav.isNavigating()) nav.cancel();
        navGoal = null;
        input.clear();
        shield(false, sp);
        // An archer drawing on it: a side-step every second and a half while it draws (its aim takes its own motion in).
        boolean archerOnIt = false;
        for (Hostile o : s.hostiles()) if (o.drawingBow() && o.lineOfSight() && o.targetsSelf()) archerOnIt = true;
        if (archerOnIt) input.setStrafe((sp.level().getGameTime() / 30) % 2 == 0 ? 1.0f : -1.0f);
        var state = com.mineagent.engine.act.BowShot.tick(sp, best);
        if (state == com.mineagent.engine.act.BowShot.State.LOOSED) {
            bodyLog.report("an arrow at a " + h.type().replace('_', ' ') + " " + Math.round(h.distance()) + " blocks out");
        }
        return state != com.mineagent.engine.act.BowShot.State.NO_BOW && state != com.mineagent.engine.act.BowShot.State.HELD_BACK;
    }

    private void shield(boolean up, ServerPlayer sp) {
        boolean raised = sp.isUsingItem() && sp.getUseItem().getItem() instanceof ShieldItem;
        if (!up) {
            if (raised) sp.stopUsingItem();
            return;
        }
        if (raised) return;
        if (!(sp.getOffhandItem().getItem() instanceof ShieldItem)) {
            // A totem in the off hand stays there: it saves a life, the shield only saves hits.
            if (sp.getOffhandItem().is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING)) return;
            if (!TaskContext.selectInventoryItemForHand(companion, "minecraft:shield", InteractionHand.OFF_HAND)) return;
        }
        if (sp.isUsingItem()) sp.stopUsingItem();
        Interaction.useItem(sp, InteractionHand.OFF_HAND);
    }

    private void stop(InputDriver input, ServerPlayer sp) {
        try {
            if (nav != null && nav.isNavigating()) nav.cancel();
            navGoal = null;
            input.clear();
            if (sp != null) shield(false, sp);
        } catch (Exception ignored) {
            // tearing down
        }
    }

    /** Once each dusk (game time 12 500 to 13 500), before the night's mobs: the best armour and the shield on. */
    private void gearUpAtDusk() {
        ServerPlayer sp = serverPlayer();
        long day = sp.level().getDayTime();
        long time = Math.floorMod(day, 24_000L);
        long today = Math.floorDiv(day, 24_000L);
        if (time < 12_500L || time >= 13_500L || today == gearedDay) return;
        gearedDay = today;
        if (GearUp.run(sp)) bodyLog.report("dusk: put on its best gear");
    }

    // ── The fight's record ──────────────────────────────────────────────

    private void beginFight(AgentLoop loop, Snapshot s, Hostile target, ServerPlayer sp) {
        fighting = true;
        fled = false;
        fightTicks = 0;
        fightAnchor = s.anchor();
        fightMob = target.type();
        var choice = loop != null ? loop.choiceFor(fightMob) : null;
        tactics = choice != null ? choice.tactics() : Tactics.of("balanced", config.healthFlee());
        String way = choice != null ? choice.name() : "balanced";
        trackedTarget = null;
        if (GearUp.run(sp)) bodyLog.report("put on its best gear");
        // Getting away from what is never fought (the warden) is not a fight to rate a stance by.
        if (loop != null && !MobPolicy.of(fightMob).mustFlee()) loop.fightBegan(fightMob, way, sp);
        bodyLog.report((s.setup().duty() == Duty.GUARD ? "on guard: " : "") + "fighting " + fightMob + " (" + way + ")");
    }

    private void endFight(AgentLoop loop, Scorecard.Outcome outcome) {
        if (loop != null) loop.fightEnded(outcome);
        bodyLog.report(outcome == Scorecard.Outcome.DONE ? "fight over" : "got away");
        fighting = false;
        fled = false;
        fightAnchor = null;
        tactics = null;
        trackedTarget = null;
        letGo.clear();
    }

    /** No headway on a target for 10 s (it is on a ledge, across water, behind a wall): let it go for 30 s. */
    private void watchHeadway(Mob mob, Hostile target, ServerPlayer sp) {
        long now = sp.level().getGameTime();
        if (!mob.getUUID().equals(trackedTarget)) {
            trackedTarget = mob.getUUID();
            bestDistance = target.distance();
            lastHeadway = now;
            return;
        }
        if (target.distance() < bestDistance - 1.0) {
            bestDistance = target.distance();
            lastHeadway = now;
        }
        if (now - lastHeadway > NO_HEADWAY_TICKS && target.distance() > CombatBrain.REACH + 1.0) {
            letGo.put(mob.getUUID(), now + LET_GO_TICKS);
            trackedTarget = null;
            bodyLog.report("can't reach the " + target.type() + ", leaving it");
        }
    }

    /** The fight as the model reads it: no player's words, only what the game shows and what it measured. */
    private String situation(Snapshot s, Assessment a) {
        JsonObject o = new JsonObject();
        o.addProperty("duty", s.setup().duty().name().toLowerCase(Locale.ROOT));
        o.addProperty("perimeter", Math.round(s.setup().perimeter()));
        o.addProperty("posture", a.posture().name().toLowerCase(Locale.ROOT));
        o.addProperty("why", a.reason());
        JsonObject me = new JsonObject();
        me.addProperty("health", Math.round(s.self().health()));
        me.addProperty("max_health", Math.round(s.self().maxHealth()));
        me.addProperty("armor", s.self().armor());
        me.addProperty("weapon_tier", s.self().weaponTier());
        me.addProperty("shield", s.self().hasShield());
        me.addProperty("food", s.self().food());
        o.add("self", me);
        JsonArray list = new JsonArray();
        for (int i = 0; i < s.hostiles().size() && list.size() < 6; i++) {
            Hostile h = s.hostiles().get(i);
            if (!CombatBrain.relevant(h, s.context())) continue;
            JsonObject m = new JsonObject();
            m.addProperty("type", h.type());
            m.addProperty("distance", Math.round(h.distance() * 10) / 10.0);
            m.addProperty("health", Math.round(h.health()));
            m.addProperty("targets", h.targetsSelf() ? "you" : h.targetsOwner() ? "owner" : h.targetsWard() ? "ward" : "none");
            if (h.fusing()) m.addProperty("fusing", true);
            if (h.drawingBow()) m.addProperty("drawing_bow", true);
            list.add(m);
        }
        o.add("hostiles", list);
        AgentLoop loop = TaskContext.agentLoop(companion);
        if (loop != null) {
            JsonObject records = new JsonObject();
            for (String way : loop.tacticNames(fightMob)) {
                records.addProperty(way, loop.scorecard().describe(com.mineagent.engine.combat.TacticBook.key(fightMob, way)));
            }
            o.add("record_against_" + fightMob, records);
        }
        o.addProperty("stance_now", tactics == null ? "balanced" : tactics.stance());
        return o.toString();
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private ServerPlayer serverPlayer() {
        return ((CompanionEntity) companion).serverPlayer();
    }

    private PlayerNav nav() {
        // A path finder knows one world's blocks: a new one after a change of dimension (as FollowChain does).
        var level = serverPlayer().level();
        if (nav == null || nav.player() == null || navLevel != level) {
            if (nav != null) nav.cancel();
            nav = new PlayerNav(companion, TaskContext.navCaches(companion));
            navLevel = level;
            navGoal = null;
        }
        return nav;
    }

    /** Close enough to the post to stand at it; a guarded player is given more room. */
    private static double nearEnough(Snapshot s) {
        return s.setup().duty() == Duty.GUARD && s.setup().ward() != null ? NEAR_WARD : AT_POST;
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        if (a == null || b == null) return 0.0;
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static void face(ServerPlayer player, Vec3 at) {
        Vec3 dir = at.subtract(player.getEyePosition());
        double flat = dir.horizontalDistance();
        player.setYRot((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)));
        player.setXRot((float) -Math.toDegrees(Math.atan2(dir.y, flat)));
        player.setYHeadRot(player.getYRot());
    }

    /** The best sword or axe (else any tool) into the hand: a real hotbar swap. */
    public static void equipWeapon(ServerPlayer player) {
        var inv = player.getInventory();
        int best = -1;
        int bestTier = 0;
        for (int i = 0; i < Math.min(36, inv.getContainerSize()); i++) {
            int tier = weaponTier(inv.getItem(i));
            if (tier > bestTier) {
                bestTier = tier;
                best = i;
            }
        }
        if (best < 0 || best == inv.selected) return;
        if (best < 9) {
            inv.selected = best;
        } else {
            int selected = inv.selected;
            ItemStack displaced = inv.getItem(selected);
            inv.setItem(selected, inv.getItem(best));
            inv.setItem(best, displaced);
        }
        TaskContext.syncInventory(player);
    }

    private static boolean reflexEnabled(String id, AgentPlayer companion, boolean fallback) {
        return com.mineagent.api.task.reflex.ReflexRegistry.get(id)
                .map(reflex -> reflex.isEnabled(companion)).orElse(fallback);
    }
}
