package com.mineagent.engine.task;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.task.CompanionTask;
import com.mineagent.api.task.TaskRecord;
import com.mineagent.api.task.TaskState;
import com.mineagent.engine.gear.XpSeeking;
import com.mineagent.engine.pathing.execute.PlayerNav;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Levels earned by fighting (SMH plan 70, its owner: "once its doing well in terms of materials, armor and weapons. It
 * should start seeking beef with mobs. Of any kind."): the mob most worth it near ({@link XpSeeking#pick}: a monster
 * first, then a wild animal, two of its kind always left) gone after. A monster's fight is the fighting chain's (it wants
 * the experience, so the weighing takes the fight: combat/FightWeighing), and one that hasn't noticed it is struck to start
 * it; an animal is struck down by this task. The experience and the drops are gathered after each. With nothing about,
 * it walks round its home (or where it started), out past the torchlight, and looks again. It goes until it has the
 * levels it wants or, on its own time, until dawn; hurt to half, out of food or with its weapon nearly worn through, it
 * stops; and says what it got.
 */
public class XpHuntTask extends CompanionTask<XpHuntTask.XpRecord> {
    public static final class XpRecord extends TaskRecord {
        /** The level to get to. */
        public final int target;
        /** On its own time: only while it is night. */
        public final boolean tonight;

        public XpRecord(String toolCallId, int target, boolean tonight) {
            super(toolCallId);
            this.target = target;
            this.tonight = tonight;
        }
    }

    private enum Phase { LOOK, GO, ROAM, GATHER, DONE }

    /** How far it looks for a mob, and how far round its home it walks to find them. */
    static final double LOOK = 32.0, ROAM_NEAR = 20.0, ROAM_FAR = 40.0;
    /** Animals this near its home are its or its owner's: never killed for levels. */
    static final double HOME_GROUNDS = 24.0;

    private PlayerNav nav;
    private Phase phase = Phase.LOOK;
    private Entity prey;
    private BlockPos roamTo, gatherAt, centre;
    private int ticks, total, startLevel = -1, kills, roams;
    private final Set<UUID> givenUp = new HashSet<>();
    private final java.util.Random random = new java.util.Random();
    private String outcome = "";

    public XpHuntTask(AgentPlayer player, XpRecord record) {
        super(player, record);
    }

    @Override
    protected void onStart() {
        ServerPlayer sp = TaskContext.serverPlayer(player);
        if (nav == null) nav = new PlayerNav(player, TaskContext.navCaches(player));
        if (startLevel < 0) {
            startLevel = sp.experienceLevel;
            var loop = TaskContext.agentLoop(player);
            BlockPos home = loop != null ? loop.homeIn(sp.serverLevel().dimension().location().toString()) : null;
            centre = home != null ? home : sp.blockPosition();
            record.extendDeadlineTo(Math.max(record.deadline(), sp.level().getGameTime() + 14_000L));
        }
        com.mineagent.engine.survival.MobDefenseChain.equipWeapon(sp);
    }

    @Override
    protected void onResume() {
        // Back from a fight the chain took: whatever it was after may be dead now, or moved.
        onStart();
        if (nav != null) nav.cancel();
        if (phase == Phase.ROAM) phase = Phase.LOOK;
    }

    @Override
    protected TaskState onTick() {
        ServerPlayer sp = TaskContext.serverPlayer(player);
        ServerLevel level = sp.serverLevel();
        var loop = TaskContext.agentLoop(player);
        try {
            ticks++;
            total++;
            if (loop != null) loop.noteXpHunt(); // a reason to take each fight (combat/FightWeighing)
            if (prey != null && !prey.isAlive()) {
                if (prey instanceof net.minecraft.world.entity.LivingEntity le && le.getHealth() <= 0) kills++;
                gatherAt = prey.blockPosition().immutable();
                prey = null;
                phase = Phase.GATHER;
                ticks = 0;
            }
            if (sp.experienceLevel >= record.target && phase != Phase.GATHER) return end(true, null);
            if (record.tonight && !XpSeeking.night(level.getDayTime()) && phase != Phase.GATHER) return end(true, "it's getting light");
            int food = foodCarried(sp);
            if (!XpSeeking.goOn(sp.getHealth(), sp.getMaxHealth(), food)) {
                return end(true, food < 2 ? "I'm nearly out of food" : "I'm hurt, so I'm stopping");
            }
            if (worn(sp.getMainHandItem())) return end(true, "my weapon is nearly worn through");
            if (total > 12_000) return end(true, null);
            switch (phase) {
                case GATHER -> {
                    // Its orbs fly to it from 8 blocks; its drops are walked to.
                    if (ticks > 100) {
                        phase = Phase.LOOK;
                        return TaskState.RUNNING;
                    }
                    Entity near = null;
                    for (Entity e : level.getEntities((Entity) null, new AABB(gatherAt).inflate(6, 3, 6),
                            e -> e.isAlive() && (e instanceof ExperienceOrb || e instanceof ItemEntity item
                                    && com.mineagent.engine.survival.Manners.mayTake(item, sp)))) {
                        if (near == null || e.distanceToSqr(sp) < near.distanceToSqr(sp)) near = e;
                    }
                    if (near == null) {
                        if (ticks > 20) phase = Phase.LOOK; // a second for the orbs to come
                        return TaskState.RUNNING;
                    }
                    if (ticks % 20 == 1 || !nav.isNavigating()) nav.navigateNear(near.getBlockX(), near.getBlockY(), near.getBlockZ(), 0);
                    nav.tick();
                    return TaskState.RUNNING;
                }
                case LOOK -> {
                    prey = choose(level, sp, loop);
                    ticks = 0;
                    if (prey != null) {
                        phase = Phase.GO;
                        nav.cancel();
                        return TaskState.RUNNING;
                    }
                    // Nothing about: a walk round its home, a dozen at most without a kill.
                    if (roams >= 12 && kills == 0) return end(true, "there's nothing about to fight here now");
                    roamTo = roamPoint(level);
                    phase = Phase.ROAM;
                    roams++;
                    if (roamTo != null) nav.navigateNear(roamTo.getX(), roamTo.getY(), roamTo.getZ(), 3);
                    return TaskState.RUNNING;
                }
                case ROAM -> {
                    if (ticks % 20 == 0) {
                        Entity seen = choose(level, sp, loop);
                        if (seen != null) {
                            prey = seen;
                            phase = Phase.GO;
                            ticks = 0;
                            nav.cancel();
                            return TaskState.RUNNING;
                        }
                    }
                    if (roamTo == null) {
                        if (ticks > 40) phase = Phase.LOOK; // nowhere dry to walk to: a look round again in two seconds
                        return TaskState.RUNNING;
                    }
                    if (ticks > 400 || sp.blockPosition().distSqr(roamTo) <= 16 || !nav.isNavigating() && ticks > 20) {
                        phase = Phase.LOOK;
                        return TaskState.RUNNING;
                    }
                    nav.tick();
                    return TaskState.RUNNING;
                }
                case GO -> {
                    if (prey == null || prey.isRemoved() || ticks > 600) {
                        if (prey != null) givenUp.add(prey.getUUID());
                        prey = null;
                        phase = Phase.LOOK;
                        return TaskState.RUNNING;
                    }
                    boolean reach = com.mineagent.engine.act.Interaction.inEntityReach(sp, prey) && sp.hasLineOfSight(prey);
                    if (!reach) {
                        if (ticks % 20 == 1 || !nav.isNavigating()) nav.navigateNear(prey.getBlockX(), prey.getBlockY(), prey.getBlockZ(), 1);
                        nav.tick();
                        return TaskState.RUNNING;
                    }
                    // In reach: struck when the swing is charged (a monster that turns on it is the chain's fight from here).
                    nav.cancel();
                    TaskContext.inputDriver(player).clear();
                    com.mineagent.engine.survival.MobDefenseChain.equipWeapon(sp);
                    var to = prey.position().add(0, prey.getBbHeight() * 0.5, 0).subtract(sp.getEyePosition());
                    sp.setYRot((float) Math.toDegrees(Math.atan2(-to.x, to.z)));
                    sp.setXRot((float) -Math.toDegrees(Math.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z))));
                    sp.setYHeadRot(sp.getYRot());
                    if (sp.getAttackStrengthScale(0.5f) >= 0.9f) com.mineagent.engine.act.Interaction.attackEntity(sp, prey);
                    return TaskState.RUNNING;
                }
                default -> {
                    return end(true, null);
                }
            }
        } catch (RuntimeException e) {
            return end(false, "something went wrong: " + e.getMessage());
        }
    }

    /** The mob to go for now ({@link XpSeeking#pick}), or null. */
    private Entity choose(ServerLevel level, ServerPlayer sp, com.mineagent.engine.loop.AgentLoop loop) {
        String dim = level.dimension().location().toString();
        BlockPos home = loop != null ? loop.homeIn(dim) : null;
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, sp.getBoundingBox().inflate(LOOK, 12, LOOK), Mob::isAlive);
        List<XpSeeking.Prey> prey = new ArrayList<>();
        List<Mob> of = new ArrayList<>();
        for (Mob m : mobs) {
            String type = BuiltInRegistries.ENTITY_TYPE.getKey(m.getType()).toString();
            boolean enemy = m instanceof Enemy;
            XpSeeking.Kind kind = XpSeeking.kind(type, enemy);
            if (kind == XpSeeking.Kind.NEVER) continue;
            boolean kept = m.hasCustomName() || m.isLeashed() || givenUp.contains(m.getUUID())
                    || loop != null && loop.spared(m.getUUID())
                    || com.mineagent.engine.world.AfkFarms.protects(dim, m.getBlockX(), m.getBlockY(), m.getBlockZ());
            int herd = 0;
            if (kind == XpSeeking.Kind.ANIMAL) {
                kept = kept || !com.mineagent.engine.planning.production.GameKnowledge.huntable(m) || m instanceof TamableAnimal
                        || home != null && m.blockPosition().distSqr(home) <= HOME_GROUNDS * HOME_GROUNDS || penned(level, m.blockPosition());
                for (Mob o : mobs) {
                    if (o.getType() == m.getType() && com.mineagent.engine.planning.production.GameKnowledge.huntable(o)) herd++;
                }
            }
            boolean adult = !(m instanceof AgeableMob a && a.isBaby());
            prey.add(new XpSeeking.Prey(type, enemy, m.distanceTo(sp), sp.hasLineOfSight(m), m.isInWater(), adult, kept, herd));
            of.add(m);
        }
        int i = XpSeeking.pick(prey);
        return i < 0 ? null : of.get(i);
    }

    /** A fence, gate or wall within 4 blocks of it, about its level: someone's pen. */
    private static boolean penned(ServerLevel level, BlockPos at) {
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-4, -1, -4), at.offset(4, 1, 4))) {
            var st = level.getBlockState(p);
            if (st.is(BlockTags.FENCES) || st.is(BlockTags.FENCE_GATES) || st.is(BlockTags.WALLS)) return true;
        }
        return false;
    }

    /** A spot 20 to 40 blocks round its home (or where it started), on dry ground; null if none is found. */
    private BlockPos roamPoint(ServerLevel level) {
        for (int i = 0; i < 8; i++) {
            double a = random.nextDouble() * Math.PI * 2, r = ROAM_NEAR + random.nextDouble() * (ROAM_FAR - ROAM_NEAR);
            int x = centre.getX() + (int) Math.round(Math.cos(a) * r), z = centre.getZ() + (int) Math.round(Math.sin(a) * r);
            if (!level.isLoaded(new BlockPos(x, centre.getY(), z))) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos p = new BlockPos(x, y, z);
            if (!level.getFluidState(p.below()).isEmpty() || Math.abs(y - centre.getY()) > 24) continue;
            return p;
        }
        return null;
    }

    private static int foodCarried(ServerPlayer sp) {
        int n = 0;
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            var food = st.get(net.minecraft.core.component.DataComponents.FOOD);
            if (food == null || st.is(net.minecraft.world.item.Items.ROTTEN_FLESH) || st.is(net.minecraft.world.item.Items.SPIDER_EYE)
                    || st.is(net.minecraft.world.item.Items.POISONOUS_POTATO)) continue;
            n += st.getCount();
        }
        return n;
    }

    /** A weapon with a tenth or less of its wear left. */
    private static boolean worn(ItemStack st) {
        return st.isDamageableItem() && st.getMaxDamage() > 0 && (st.getMaxDamage() - st.getDamageValue()) <= st.getMaxDamage() * 0.1;
    }

    private TaskState end(boolean ok, String why) {
        if (nav != null) nav.cancel();
        TaskContext.inputDriver(player).clear();
        phase = Phase.DONE;
        ServerPlayer sp = TaskContext.serverPlayer(player);
        int now = sp.experienceLevel, got = Math.max(0, now - Math.max(0, startLevel));
        outcome = (got > 0 ? "Got " + got + (got == 1 ? " level" : " levels") : "No levels")
                + (kills > 0 ? " from " + kills + (kills == 1 ? " kill" : " kills") : "") + "; I'm at " + now
                + (now >= record.target ? ", what I was after" : "") + (why == null ? "" : " (" + why + ")");
        var loop = TaskContext.agentLoop(player);
        if (loop != null) {
            loop.say(outcome + ".");
            loop.journal().record("xp", outcome, sp.level().getGameTime());
        }
        return ok ? TaskState.SUCCESS : TaskState.FAILED;
    }

    @Override
    protected String successMessage() {
        return "{\"success\":true,\"note\":\"" + outcome.replace("\"", "'") + "\"}";
    }

    @Override
    protected String failureMessage() {
        return "{\"success\":false,\"note\":\"" + outcome.replace("\"", "'") + "\"}";
    }

    @Override
    protected void onInterrupt() {
        if (nav != null) nav.cancel();
        TaskContext.inputDriver(player).clear();
    }
}
