package com.mineagent.engine.task;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.task.CompanionTask;
import com.mineagent.api.task.TaskRecord;
import com.mineagent.api.task.TaskState;
import com.mineagent.engine.act.BowShot;
import com.mineagent.engine.act.Interaction;
import com.mineagent.engine.pathing.execute.PlayerNav;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * The dragon, only when its owner asks in so many words (SMH plan 70: "I don't want it to beat the ender dragon without
 * me ... both should be an option, if requested explicitly"). As players fight it: the end crystals on the pillars shot
 * first with the bow (the ones in iron cages left: arrows don't go through bars), then the dragon itself, arrows while
 * it flies, the sword on its head while it perches on the portal; never in its breath. "Shoot the end crystals" does the
 * first part alone, anywhere. It doesn't jump into the portal after: that's its owner's to do.
 */
public class DragonTask extends CompanionTask<DragonTask.DragonRecord> {
    public static final class DragonRecord extends TaskRecord {
        /** The crystals only ("shoot the end crystals"), not the dragon. */
        public final boolean crystalsOnly;

        public DragonRecord(String toolCallId, boolean crystalsOnly) {
            super(toolCallId);
            this.crystalsOnly = crystalsOnly;
        }
    }

    /** How far it looks for crystals and the dragon; how far it shoots. */
    static final double SEEK = 192.0, SHOT = 60.0;

    private enum Phase { CHECK, CRYSTALS, DRAGON, DONE }

    private PlayerNav nav;
    private Phase phase = Phase.CHECK;
    private EndCrystal crystal;
    private int ticks, shot, caged, crystalTicks;
    private String outcome = "";

    public DragonTask(AgentPlayer player, DragonRecord record) {
        super(player, record);
    }

    @Override
    protected void onStart() {
        if (nav == null) nav = new PlayerNav(player, TaskContext.navCaches(player));
        record.extendDeadlineTo(Math.max(record.deadline(), TaskContext.serverPlayer(player).level().getGameTime() + 36_000L));
    }

    @Override
    protected TaskState onTick() {
        ServerPlayer sp = TaskContext.serverPlayer(player);
        ServerLevel level = sp.serverLevel();
        try {
            ticks++;
            if (awayFromBreath(sp, level)) return TaskState.RUNNING;
            switch (phase) {
                case CHECK -> {
                    if (!record.crystalsOnly && level.dimension() != Level.END) {
                        return end(false, "I'm not in the End: take me through its portal first (\"find the stronghold\" finds it)");
                    }
                    if (sp.getInventory().countItem(Items.BOW) == 0 && sp.getMainHandItem().getItem() != Items.BOW) return end(false, "I have no bow");
                    if (sp.getInventory().countItem(Items.ARROW) == 0 && sp.getInventory().countItem(Items.SPECTRAL_ARROW) == 0) return end(false, "I have no arrows");
                    var loop = TaskContext.agentLoop(player);
                    if (loop != null) for (EnderDragon d : dragons(level, sp)) loop.spare(d.getUUID()); // fought here, not fled
                    phase = Phase.CRYSTALS;
                    ticks = 0;
                    return TaskState.RUNNING;
                }
                case CRYSTALS -> {
                    if (crystal == null || !crystal.isAlive()) {
                        if (crystal != null && !crystal.isAlive()) shot++;
                        crystal = nextCrystal(level, sp);
                        crystalTicks = 0;
                        if (crystal == null) {
                            if (record.crystalsOnly) return end(shot > 0, shot > 0 ? null : "there are no end crystals I can shoot from here");
                            phase = Phase.DRAGON;
                            ticks = 0;
                            say("The crystals I can reach are down (" + shot + (caged > 0 ? "; " + caged + " in cages left" : "") + "). Now the dragon.");
                            return TaskState.RUNNING;
                        }
                    }
                    // Out of range or out of sight: nearer, by the pillar (it walks, it doesn't climb).
                    double d = sp.distanceTo(crystal);
                    if (crystalTicks % 100 == 0) {
                        System.out.println("[MineAgent] crystal at " + crystal.blockPosition().toShortString() + String.format(java.util.Locale.ROOT, ": %.1f away", d)
                                + ", in sight " + sp.hasLineOfSight(crystal) + ", in hand " + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(sp.getMainHandItem().getItem())
                                + ", drawing " + sp.isUsingItem() + " " + sp.getTicksUsingItem() + ", " + crystalTicks + " ticks on it");
                    }
                    if (d > SHOT || !sp.hasLineOfSight(crystal)) {
                        if (++crystalTicks > 1_200) { // no way to see it: the next
                            caged++;
                            crystalsGivenUp.add(crystal.getUUID());
                            crystal = null;
                            return TaskState.RUNNING;
                        }
                        if (crystalTicks % 40 == 1) nav.navigateNear(crystal.getBlockX(), sp.getBlockY(), crystal.getBlockZ(), 24);
                        nav.tick();
                        return TaskState.RUNNING;
                    }
                    nav.cancel();
                    TaskContext.inputDriver(player).clear();
                    bowInHand(sp);
                    var shotState = BowShot.tick(sp, crystal);
                    if (crystalTicks % 100 == 0) System.out.println("[MineAgent] crystal shot: " + shotState);
                    if (sidestep(sp, shotState, crystal)) return TaskState.RUNNING;
                    if (++crystalTicks > 1_600) { // shot and shot and still there: the next
                        caged++;
                        crystalsGivenUp.add(crystal.getUUID());
                        crystal = null;
                    }
                    return TaskState.RUNNING;
                }
                case DRAGON -> {
                    var dragons = dragons(level, sp);
                    if (dragons.isEmpty()) return end(true, null);
                    EnderDragon dragon = dragons.get(0);
                    var loop = TaskContext.agentLoop(player);
                    if (loop != null) loop.spare(dragon.getUUID());
                    if (dragon.isDeadOrDying()) return end(true, null);
                    boolean perched = dragon.getPhaseManager().getCurrentPhase().isSitting();
                    Entity head = head(dragon);
                    if (perched && head != null && sp.distanceTo(head) < 24) {
                        // On the portal: the sword on its head, every blow at full charge.
                        if (!Interaction.inEntityReach(sp, head)) {
                            if (ticks % 10 == 1) nav.navigateNear(head.getBlockX(), (int) Math.floor(sp.getY()), head.getBlockZ(), 2);
                            nav.tick();
                            return TaskState.RUNNING;
                        }
                        nav.cancel();
                        com.mineagent.engine.survival.MobDefenseChain.equipWeapon(sp);
                        sp.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, head.position());
                        if (sp.getAttackStrengthScale(0.5f) >= 0.95f) Interaction.attackEntity(sp, head);
                        return TaskState.RUNNING;
                    }
                    // Flying: arrows when it is near and in sight, else wait by the portal for it to come down.
                    if (sp.distanceTo(dragon) <= SHOT && sp.hasLineOfSight(dragon)) {
                        nav.cancel();
                        TaskContext.inputDriver(player).clear();
                        bowInHand(sp);
                        sidestep(sp, BowShot.tick(sp, dragon), dragon);
                        return TaskState.RUNNING;
                    }
                    BlockPos portal = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.ZERO);
                    if (sp.blockPosition().distSqr(portal) > 12 * 12) {
                        if (ticks % 40 == 1) nav.navigateNear(portal.getX() + 6, portal.getY(), portal.getZ() + 6, 4);
                        nav.tick();
                    }
                    if (ticks > 30_000) return end(false, "the fight went on too long");
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

    /** Shots held back in a row (someone in the line of fire), and a step aside when it is three. */
    private int heldBack;
    private BlockPos aside;

    /**
     * Held back three times running (its owner, or another player, in the way): three blocks to the side, across the line
     * of fire, for a clear one. True while it steps.
     */
    private boolean sidestep(ServerPlayer sp, BowShot.State st, Entity target) {
        if (aside != null) {
            if (sp.blockPosition().distSqr(aside) <= 2 || ++asideTicks > 100) {
                aside = null;
                asideTicks = 0;
                return false;
            }
            nav.tick();
            return true;
        }
        if (st != BowShot.State.HELD_BACK) {
            if (st == BowShot.State.LOOSED) heldBack = 0;
            return false;
        }
        if (++heldBack < 3) return false;
        heldBack = 0;
        var to = target.position().subtract(sp.position());
        double len = Math.max(0.01, Math.sqrt(to.x * to.x + to.z * to.z));
        int side = (sidesTried++ % 2 == 0) ? 1 : -1;
        aside = BlockPos.containing(sp.getX() - to.z / len * 3 * side, sp.getY(), sp.getZ() + to.x / len * 3 * side);
        nav.navigateNear(aside.getX(), aside.getY(), aside.getZ(), 1);
        return true;
    }

    private int asideTicks, sidesTried;

    /** The bow into its main hand (the shot draws only a bow held there). */
    private void bowInHand(ServerPlayer sp) {
        if (!(sp.getMainHandItem().getItem() instanceof net.minecraft.world.item.BowItem)) TaskContext.selectInventoryItem(player, "minecraft:bow");
    }

    /** Its breath on the ground near: a step out of it first. True while it moves. */
    private boolean awayFromBreath(ServerPlayer sp, ServerLevel level) {
        for (AreaEffectCloud cloud : level.getEntitiesOfClass(AreaEffectCloud.class, sp.getBoundingBox().inflate(6))) {
            if (cloud.getParticle().getType() != net.minecraft.core.particles.ParticleTypes.DRAGON_BREATH) continue;
            double r = cloud.getRadius() + 1.5;
            if (cloud.position().distanceToSqr(sp.position()) > r * r) continue;
            var away = sp.position().subtract(cloud.position()).normalize().scale(r + 2);
            nav.navigateNear((int) Math.floor(sp.getX() + away.x), sp.getBlockY(), (int) Math.floor(sp.getZ() + away.z), 1);
            nav.tick();
            return true;
        }
        return false;
    }

    private static java.util.List<EnderDragon> dragons(ServerLevel level, ServerPlayer sp) {
        return level.getEntitiesOfClass(EnderDragon.class, sp.getBoundingBox().inflate(SEEK), EnderDragon::isAlive);
    }

    private static Entity head(EnderDragon dragon) {
        for (EnderDragonPart part : dragon.getSubEntities()) if ("head".equals(part.name)) return part;
        return null;
    }

    /** The nearest crystal it hasn't given up on, not in an iron cage (bars round it: arrows stop there). */
    private EndCrystal nextCrystal(ServerLevel level, ServerPlayer sp) {
        EndCrystal best = null;
        double bestD = Double.MAX_VALUE;
        for (EndCrystal c : level.getEntitiesOfClass(EndCrystal.class, sp.getBoundingBox().inflate(SEEK), EndCrystal::isAlive)) {
            if (inCage(level, c)) continue;
            if (crystalsGivenUp.contains(c.getUUID())) continue;
            double d = c.distanceToSqr(sp);
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }

    private final java.util.Set<java.util.UUID> crystalsGivenUp = new java.util.HashSet<>();

    private static boolean inCage(ServerLevel level, EndCrystal c) {
        BlockPos at = c.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-2, -1, -2), at.offset(2, 3, 2))) {
            if (level.getBlockState(p).is(Blocks.IRON_BARS)) return true;
        }
        return false;
    }

    private void say(String s) {
        var loop = TaskContext.agentLoop(player);
        if (loop != null) loop.say(s);
    }

    private TaskState end(boolean ok, String why) {
        if (nav != null) nav.cancel();
        TaskContext.inputDriver(player).clear();
        ServerPlayer sp = TaskContext.serverPlayer(player);
        if (sp.isUsingItem()) sp.stopUsingItem();
        phase = Phase.DONE;
        if (record.crystalsOnly) {
            outcome = shot > 0 ? "Shot " + shot + (shot == 1 ? " end crystal" : " end crystals") + (caged > 0 ? "; " + caged + " I couldn't hit" : "")
                    : (why == null ? "no crystals down" : why);
        } else {
            outcome = ok ? "The dragon is dead. The portal home is open; jumping in is yours." : (why == null ? "it didn't work" : why);
        }
        say(ok || record.crystalsOnly && shot > 0 ? outcome + "." : "I couldn't finish the dragon: " + outcome + ".");
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
        ServerPlayer sp = TaskContext.serverPlayer(player);
        if (sp.isUsingItem()) sp.stopUsingItem();
    }
}
