package com.mineagent.engine.survival;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.entity.InputDriver;
import com.mineagent.api.task.TaskChain;
import com.mineagent.engine.MineAgentEngine;
import com.mineagent.engine.act.Interaction;
import com.mineagent.engine.combat.PlayerFightBrain;
import com.mineagent.engine.combat.PlayerFightBrain.Plan;
import com.mineagent.engine.combat.PlayerFightBrain.Step;
import com.mineagent.engine.combat.PlayerFightBrain.Weapon;
import com.mineagent.engine.entity.CompanionEntity;
import com.mineagent.engine.learning.Scorecard;
import com.mineagent.engine.loop.AgentLoop;
import com.mineagent.engine.pathing.execute.PlayerNav;
import com.mineagent.engine.task.TaskContext;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.UUID;

/**
 * Fighting players (SMH plan 70, MA10.6): the game side of {@link PlayerFightBrain}. Whom: a player its owner (or a
 * trusted player) named (the target list, {@code /mineagent target} or "kill &lt;name&gt;"), or one who hit it or its
 * owner first; never its owner, a trusted player, a companion of the same owner, nor anyone the server won't let be
 * hurt. How: through a player's inputs, by the game's rules (full-charge hits, crit jumps, strafing, the shield, an axe
 * against a raised shield, the bow at range, healing when low). It never steps into lava or fire, or off a drop it
 * couldn't climb back from. Each fight is rated per opponent, as mob fights are. The switch is
 * {@code behaviour.fightPlayers}.
 */
public final class PlayerFightChain implements TaskChain {
    private static final float PRIORITY = 5.6f;
    private static final float PRIORITY_BACKING_OFF = 5.8f;
    /** Looked at around it, blocks. */
    private static final double SCAN = 24.0;
    /** Someone who hit it or its owner within this many ticks started it. */
    private static final int STARTED_WITHIN = 200;
    private static final int REPATH = 20;

    private final AgentPlayer companion;
    private PlayerNav nav;
    private net.minecraft.world.level.Level navLevel;
    private Player seen;
    private long seenTick = -1;
    private UUID foe;
    private String foeName = "";
    private boolean fighting, backingOff;
    private int chargedTicks, repath;
    private boolean launched;

    public PlayerFightChain(AgentPlayer companion) {
        this.companion = companion;
    }

    @Override
    public String name() {
        return "player_fight";
    }

    @Override
    public float getPriority(AgentPlayer companion) {
        try {
            Player p = look();
            if (p == null) {
                if (fighting) endFight(Scorecard.Outcome.DONE);
                return Float.NEGATIVE_INFINITY;
            }
            return backingOff ? PRIORITY_BACKING_OFF : PRIORITY;
        } catch (Exception e) {
            System.err.println("[MineAgent] PlayerFight getPriority error: " + e.getMessage());
            return Float.NEGATIVE_INFINITY;
        }
    }

    @Override
    public void tick(AgentPlayer companion) {
        ServerPlayer sp = sp();
        InputDriver input = TaskContext.inputDriver(companion);
        try {
            Player p = seen != null && seenTick == sp.level().getGameTime() ? seen : look();
            if (p == null) {
                stop(sp, input);
                return;
            }
            if (!fighting || !p.getUUID().equals(foe)) beginFight(p, sp);
            double charge = sp.getAttackStrengthScale(0.5f);
            chargedTicks = charge >= PlayerFightBrain.FULL ? chargedTicks + 1 : 0;
            boolean falling = !sp.onGround() && sp.getDeltaMovement().y < 0 && sp.fallDistance > 0;
            var inv = sp.getInventory();
            boolean axe = false, bow = false, apple = false, food = false, shield = false, totem = false, mace = false, wind = false;
            int arrows = 0;
            var foods = com.mineagent.engine.planning.production.GameKnowledge.foods().keySet();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack st = inv.getItem(i);
                if (st.isEmpty()) continue;
                if (st.getItem() instanceof AxeItem) axe = true;
                if (st.getItem() instanceof BowItem) bow = true;
                if (st.is(Items.ARROW) || st.is(Items.SPECTRAL_ARROW) || st.is(Items.TIPPED_ARROW)) arrows += st.getCount();
                if (st.is(Items.GOLDEN_APPLE) || st.is(Items.ENCHANTED_GOLDEN_APPLE)) apple = true;
                if (foods.contains(BuiltInRegistries.ITEM.getKey(st.getItem()).toString())) food = true;
                if (st.getItem() instanceof ShieldItem) shield = true;
                if (st.is(Items.TOTEM_OF_UNDYING)) totem = true;
                if (st.is(Items.MACE)) mace = true;
                if (st.is(Items.WIND_CHARGE)) wind = true;
            }
            var me = new PlayerFightBrain.Me(sp.getHealth(), charge, sp.onGround(), falling, sp.isSprinting(), chargedTicks,
                    axe, bow, arrows, apple, food, sp.getFoodData().getFoodLevel(), shield, totem,
                    sp.getOffhandItem().is(Items.TOTEM_OF_UNDYING), mace, wind, backingOff);
            boolean shieldUp = p.isUsingItem() && p.getUseItem().getItem() instanceof ShieldItem;
            double distance = sp.getEyePosition().distanceTo(p.getBoundingBox().getCenter());
            var them = new PlayerFightBrain.Foe(distance, shieldUp, sp.hasLineOfSight(p), p.swinging, friendBeside(p, sp));
            Plan plan = PlayerFightBrain.decide(them, me, sp.level().getGameTime());
            if (plan.step() == Step.BACK_OFF || plan.step() == Step.HEAL) backingOff = true;
            else if (sp.getHealth() >= PlayerFightBrain.BACK_AT) backingOff = false;
            apply(plan, p, sp, input);
        } catch (Exception e) {
            System.err.println("[MineAgent] PlayerFight tick error: " + e.getMessage());
            stop(sp, input);
        }
    }

    @Override
    public void onInterrupt(AgentPlayer companion) {
        try {
            stop(sp(), TaskContext.inputDriver(companion));
        } catch (Exception ignored) {
            // tearing down
        }
    }

    // ── Whom ─────────────────────────────────────────────────────────────

    /** The player to fight now, or null: the one it is fighting if still in it, else the nearest it may fight. */
    private Player look() {
        ServerPlayer sp = sp();
        if (sp == null || !sp.isAlive() || !com.mineagent.engine.settings.Switches.fightPlayers()) return null;
        AgentLoop loop = TaskContext.agentLoop(companion);
        ServerPlayer owner = ((CompanionEntity) companion).serverPlayerOwner();
        UUID ownerId = companion.ownerUuid();
        Player best = null;
        double bestD = Double.MAX_VALUE;
        for (Player p : sp.serverLevel().players()) {
            if (p == sp || !p.isAlive() || p.isSpectator() || p.isCreative()) continue;
            double d = p.distanceTo(sp);
            if (d > SCAN) continue;
            UUID id = p.getUUID();
            boolean isOwner = id.equals(ownerId);
            boolean trusted = loop != null && loop.getAgentSettings().trusted().contains(id.toString().toLowerCase(Locale.ROOT));
            boolean sameOwner = ownerId != null && MineAgentEngine.isCompanionOwnedBy(id, ownerId);
            boolean named = loop != null && loop.getAgentSettings().isAllowedTarget(id);
            boolean started = startedIt(p, sp, owner);
            if (!PlayerFightBrain.mayFight(isOwner, trusted, sameOwner, named, started, sp.canHarmPlayer(p))) continue;
            // Someone named is fought once it sees them (or they're close); someone who started it, at once.
            if (!started && d > 8.0 && !sp.hasLineOfSight(p)) continue;
            if (id.equals(foe)) d -= 4.0; // the one it is fighting, unless another is much nearer
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        seen = best;
        seenTick = sp.level().getGameTime();
        return best;
    }

    /** Someone it must not hurt (anyone but the one it fights, or a pet) within a sweep's reach of them. */
    private boolean friendBeside(Player p, ServerPlayer sp) {
        var box = p.getBoundingBox().inflate(1.5, 0.5, 1.5);
        for (var e : sp.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box, x -> x != p && x != sp && x.isAlive())) {
            if (e instanceof Player) return true;
            if (e instanceof net.minecraft.world.entity.OwnableEntity pet && pet.getOwnerUUID() != null) return true;
            if (e instanceof net.minecraft.world.entity.npc.AbstractVillager) return true;
        }
        return false;
    }

    /** They hit it, or its owner, in the last ten seconds. */
    private static boolean startedIt(Player p, ServerPlayer sp, ServerPlayer owner) {
        if (sp.getLastHurtByMob() == p && sp.tickCount - sp.getLastHurtByMobTimestamp() < STARTED_WITHIN) return true;
        return owner != null && owner.getLastHurtByMob() == p && owner.tickCount - owner.getLastHurtByMobTimestamp() < STARTED_WITHIN;
    }

    // ── How ──────────────────────────────────────────────────────────────

    private void apply(Plan plan, Player p, ServerPlayer sp, InputDriver input) {
        hold(plan.weapon(), sp);
        Vec3 eyes = p.getEyePosition();
        switch (plan.step()) {
            case TOTEM -> {
                TaskContext.selectInventoryItemForHand(companion, "minecraft:totem_of_undying", InteractionHand.OFF_HAND);
                face(sp, eyes);
            }
            case SHOOT -> shoot(p, sp, input);
            case APPROACH -> {
                shieldDown(sp);
                if (sp.hasLineOfSight(p) && p.distanceTo(sp) < 16.0) {
                    cancelNav();
                    face(sp, eyes);
                    move(sp, input, 1.0f, 0.0f, plan.sprint());
                } else {
                    PlayerNav n = nav(sp);
                    if (++repath >= REPATH || !n.isNavigating()) {
                        n.navigateNear(p.getBlockX(), p.getBlockY(), p.getBlockZ(), 2);
                        repath = 0;
                    }
                    n.tick();
                }
            }
            case STRIKE -> {
                cancelNav();
                shieldDown(sp);
                face(sp, eyes);
                input.setJumping(false);
                input.setSprinting(false);
                input.setForward(0.0f);
                input.setStrafe(0.0f);
                Interaction.attackEntity(sp, p);
                chargedTicks = 0;
                launched = false;
            }
            case CRIT_JUMP -> {
                cancelNav();
                shieldDown(sp);
                face(sp, eyes);
                input.setSprinting(false);
                input.setForward(0.0f);
                input.setStrafe(0.0f);
                input.setJumping(true);
            }
            case STRAFE -> {
                cancelNav();
                shieldDown(sp);
                face(sp, eyes);
                input.setJumping(false);
                move(sp, input, p.distanceTo(sp) > 2.5 ? 0.3f : -0.2f, plan.strafe(), false);
            }
            case SHIELD -> {
                cancelNav();
                face(sp, eyes);
                move(sp, input, 0.0f, 0.0f, false);
                if (!(sp.getOffhandItem().getItem() instanceof ShieldItem)
                        && !TaskContext.selectInventoryItemForHand(companion, "minecraft:shield", InteractionHand.OFF_HAND)) return;
                if (!(sp.isUsingItem() && sp.getUseItem().getItem() instanceof ShieldItem)) {
                    if (sp.isUsingItem()) sp.stopUsingItem();
                    Interaction.useItem(sp, InteractionHand.OFF_HAND);
                }
            }
            case BACK_OFF -> {
                shieldDown(sp);
                Vec3 away = sp.position().subtract(p.position());
                if (away.horizontalDistanceSqr() < 1e-6) away = new Vec3(1, 0, 0);
                face(sp, sp.getEyePosition().add(away.x, 0, away.z));
                if (!move(sp, input, 1.0f, 0.0f, true)) {
                    // Straight away is unsafe or blocked: a path away instead.
                    PlayerNav n = nav(sp);
                    if (++repath >= REPATH || !n.isNavigating()) {
                        n.runAway(p.getBlockX(), p.getBlockY(), p.getBlockZ(), 16.0);
                        repath = 0;
                    }
                    n.tick();
                }
            }
            case HEAL -> {
                cancelNav();
                move(sp, input, 0.0f, 0.0f, false);
                eat(sp);
            }
            case LAUNCH -> {
                cancelNav();
                if (launched || !TaskContext.selectInventoryItemForHand(companion, "minecraft:wind_charge", InteractionHand.MAIN_HAND)) return;
                move(sp, input, 0.0f, 0.0f, false);
                sp.setXRot(90.0f); // at its feet: the burst throws it up
                Interaction.useItem(sp, InteractionHand.MAIN_HAND);
                launched = true;
            }
        }
    }

    /** What goes in its hand for this step. */
    private void hold(Weapon w, ServerPlayer sp) {
        switch (w) {
            case MELEE -> MobDefenseChain.equipWeapon(sp);
            case AXE -> best(sp, s -> s.getItem() instanceof AxeItem);
            case BOW -> best(sp, s -> s.getItem() instanceof BowItem);
            case MACE -> best(sp, s -> s.is(Items.MACE));
            case NONE -> { }
        }
    }

    /** The best-tier item that passes {@code which} into the hand (a real hotbar swap). */
    private void best(ServerPlayer sp, java.util.function.Predicate<ItemStack> which) {
        var inv = sp.getInventory();
        if (which.test(inv.getSelected())) return;
        int best = -1, tier = -1;
        for (int i = 0; i < Math.min(36, inv.getContainerSize()); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty() || !which.test(st)) continue;
            int t = MobDefenseChain.weaponTier(st);
            if (t > tier) {
                tier = t;
                best = i;
            }
        }
        if (best < 0) return;
        if (best < 9) {
            inv.selected = best;
        } else {
            ItemStack displaced = inv.getItem(inv.selected);
            inv.setItem(inv.selected, inv.getItem(best));
            inv.setItem(best, displaced);
        }
        TaskContext.syncInventory(sp);
    }

    /** Draw, lead them, loose: never past someone else (manners), never at a player it lost sight of. */
    private void shoot(Player p, ServerPlayer sp, InputDriver input) {
        cancelNav();
        shieldDown(sp);
        move(sp, input, 0.0f, 0.0f, false);
        com.mineagent.engine.act.BowShot.tick(sp, p);
    }

    /** A golden apple first, else the best food it carries; held until eaten. */
    private void eat(ServerPlayer sp) {
        if (sp.isUsingItem() && sp.getUseItem().get(net.minecraft.core.component.DataComponents.FOOD) != null) return;
        if (sp.isUsingItem()) sp.stopUsingItem();
        String pick = null;
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize() && pick == null; i++) {
            if (inv.getItem(i).is(Items.ENCHANTED_GOLDEN_APPLE)) pick = "minecraft:enchanted_golden_apple";
        }
        for (int i = 0; i < inv.getContainerSize() && pick == null; i++) {
            if (inv.getItem(i).is(Items.GOLDEN_APPLE)) pick = "minecraft:golden_apple";
        }
        if (pick == null) {
            for (String id : com.mineagent.engine.planning.production.GameKnowledge.foods().keySet()) {
                boolean has = false;
                for (int i = 0; i < inv.getContainerSize() && !has; i++) {
                    has = BuiltInRegistries.ITEM.getKey(inv.getItem(i).getItem()).toString().equals(id);
                }
                if (has) {
                    pick = id;
                    break;
                }
            }
        }
        if (pick == null || sp.getFoodData().getFoodLevel() >= 20 && !pick.contains("golden_apple")) return;
        if (TaskContext.selectInventoryItemForHand(companion, pick, InteractionHand.MAIN_HAND)) Interaction.useItem(sp, InteractionHand.MAIN_HAND);
    }

    /**
     * Moves by a player's inputs, unless the step would take it into lava or fire, or off a drop of more than three
     * blocks: then it stands still. False when it didn't move.
     */
    private boolean move(ServerPlayer sp, InputDriver input, float forward, float strafe, boolean sprint) {
        double yaw = Math.toRadians(sp.getYRot());
        double dx = strafe * Math.cos(yaw) - forward * Math.sin(yaw);
        double dz = forward * Math.cos(yaw) + strafe * Math.sin(yaw);
        boolean moving = Math.abs(dx) + Math.abs(dz) > 1e-3;
        if (moving && !safeStep(sp, dx, dz)) {
            input.setForward(0.0f);
            input.setStrafe(0.0f);
            input.setSprinting(false);
            return false;
        }
        input.setForward(forward);
        input.setStrafe(strafe);
        input.setSprinting(sprint && forward > 0);
        input.setJumping(moving && sp.horizontalCollision && sp.onGround());
        return moving;
    }

    /** The cell a step in direction (dx, dz) takes it to: no lava or fire there or under it, and ground within 3 below. */
    static boolean safeStep(ServerPlayer sp, double dx, double dz) {
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-6) return true;
        var level = sp.serverLevel();
        BlockPos next = BlockPos.containing(sp.getX() + dx / len * 0.9, sp.getY(), sp.getZ() + dz / len * 0.9);
        for (int up = 0; up <= 1; up++) {
            var st = level.getBlockState(next.above(up));
            if (st.is(Blocks.LAVA) || st.is(BlockTags.FIRE) || st.is(Blocks.MAGMA_BLOCK) || st.is(BlockTags.CAMPFIRES)
                    || st.is(Blocks.CACTUS) || st.is(Blocks.SWEET_BERRY_BUSH) || st.is(Blocks.POWDER_SNOW)) return false;
        }
        for (int down = 1; down <= 4; down++) {
            var st = level.getBlockState(next.below(down));
            if (st.is(Blocks.LAVA) || st.is(BlockTags.FIRE) || st.is(Blocks.MAGMA_BLOCK)) return false;
            if (!st.getCollisionShape(level, next.below(down)).isEmpty() || !st.getFluidState().isEmpty()) return true;
        }
        return false; // a drop of more than three blocks
    }

    // ── The fight ────────────────────────────────────────────────────────

    private void beginFight(Player p, ServerPlayer sp) {
        AgentLoop loop = TaskContext.agentLoop(companion);
        if (fighting && loop != null) loop.fightEnded(Scorecard.Outcome.DONE);
        fighting = true;
        backingOff = false;
        launched = false;
        foe = p.getUUID();
        foeName = p.getName().getString();
        if (GearUp.run(sp)) SurvivalBuiltin.bodyLog(companion).report("put on its best gear");
        if (loop != null) {
            loop.fightBegan("player:" + foeName.toLowerCase(Locale.ROOT), "duel", sp);
            loop.say("Fighting " + foeName + ".");
        }
    }

    private void endFight(Scorecard.Outcome outcome) {
        AgentLoop loop = TaskContext.agentLoop(companion);
        if (loop != null) loop.fightEnded(backingOff ? Scorecard.Outcome.RETREATED : outcome);
        fighting = false;
        backingOff = false;
        foe = null;
        foeName = "";
        try {
            stop(sp(), TaskContext.inputDriver(companion));
        } catch (Exception ignored) {
            // tearing down
        }
    }

    private void stop(ServerPlayer sp, InputDriver input) {
        cancelNav();
        input.clear();
        if (sp != null) shieldDown(sp);
    }

    private static void shieldDown(ServerPlayer sp) {
        if (sp.isUsingItem() && sp.getUseItem().getItem() instanceof ShieldItem) sp.stopUsingItem();
    }

    private void cancelNav() {
        if (nav != null && nav.isNavigating()) nav.cancel();
    }

    private PlayerNav nav(ServerPlayer sp) {
        if (nav == null || navLevel != sp.level()) {
            nav = new PlayerNav(companion, TaskContext.navCaches(companion));
            navLevel = sp.level();
        }
        return nav;
    }

    private ServerPlayer sp() {
        return ((CompanionEntity) companion).serverPlayer();
    }

    private static void face(ServerPlayer player, Vec3 at) {
        Vec3 dir = at.subtract(player.getEyePosition());
        double flat = dir.horizontalDistance();
        player.setYRot((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)));
        player.setXRot((float) -Math.toDegrees(Math.atan2(dir.y, flat)));
        player.setYHeadRot(player.getYRot());
    }
}
