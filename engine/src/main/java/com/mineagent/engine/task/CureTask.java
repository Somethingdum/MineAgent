package com.mineagent.engine.task;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.task.CompanionTask;
import com.mineagent.api.task.TaskRecord;
import com.mineagent.api.task.TaskState;
import com.mineagent.engine.act.Interaction;
import com.mineagent.engine.pathing.execute.PlayerNav;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;

import java.util.UUID;

/**
 * A zombie villager cured (SMH plan 70: trading mastery; a villager cured by a player sells to them for far less), as
 * the game has it: a splash potion of weakness on it, then a golden apple fed to it while it is weak, and it shakes and
 * turns back over three to five minutes. It still bites while it turns and burns in the sun, so it starts only in shade
 * or at night with the time to finish, the companion's fighting leaves it be, and it waits on a pillar two blocks high
 * out of its reach (as by an iron golem), then comes down. The villager it becomes is kept.
 */
public class CureTask extends CompanionTask<CureTask.CureRecord> {
    public static final class CureRecord extends TaskRecord {
        public CureRecord(String toolCallId) {
            super(toolCallId);
        }
    }

    /** A cure takes 3,600 to 6,000 ticks; the night lasts from 13,000 to 23,000. */
    static final long LONGEST = 6_000, DUSK = 13_000, DAWN = 23_000;

    private enum Phase { FIND, THROW, FEED, PILLAR, WAIT, DOWN, DONE }

    private PlayerNav nav;
    private Phase phase = Phase.FIND;
    private ZombieVillager target;
    private BlockPos lastAt, pillarBase;
    private int ticks, pillared, thrown;
    private String outcome = "";

    public CureTask(AgentPlayer player, CureRecord record) {
        super(player, record);
    }

    @Override
    protected void onStart() {
        if (nav == null) nav = new PlayerNav(player, TaskContext.navCaches(player));
        record.extendDeadlineTo(Math.max(record.deadline(), TaskContext.serverPlayer(player).level().getGameTime() + 12_000L));
    }

    @Override
    protected TaskState onTick() {
        ServerPlayer sp = TaskContext.serverPlayer(player);
        ServerLevel level = sp.serverLevel();
        var loop = TaskContext.agentLoop(player);
        try {
            ticks++;
            if (target != null && phase.ordinal() < Phase.WAIT.ordinal() && !target.isAlive()) return end(false, "it died before it turned");
            switch (phase) {
                case FIND -> {
                    target = nearest(level, sp);
                    if (target == null) return end(false, "there's no zombie villager near me");
                    if (splash(sp) < 0) return end(false, "I have no splash potion of weakness");
                    if (sp.getInventory().countItem(Items.GOLDEN_APPLE) == 0) return end(false, "I have no golden apple");
                    if (!safeFromSun(level, target)) return end(false, "it would burn in the sun before it turned: in shade, or at night");
                    if (loop != null) loop.spare(target.getUUID());
                    phase = Phase.THROW;
                    ticks = 0;
                    return TaskState.RUNNING;
                }
                case THROW -> {
                    if (target.hasEffect(MobEffects.WEAKNESS)) {
                        phase = Phase.FEED;
                        ticks = 0;
                        return TaskState.RUNNING;
                    }
                    double d = sp.distanceTo(target);
                    if (d > 3.5) {
                        if (ticks > 600) return end(false, "I couldn't get near it");
                        if (ticks % 20 == 1) nav.navigateNear(target.getBlockX(), target.getBlockY(), target.getBlockZ(), 2);
                        nav.tick();
                        return TaskState.RUNNING;
                    }
                    nav.cancel();
                    TaskContext.inputDriver(player).clear();
                    if (ticks % 15 != 0) return TaskState.RUNNING; // a throw, and a moment to see where it landed
                    int slot = splash(sp);
                    if (slot < 0 || ++thrown > 3) return end(false, thrown > 3 ? "my potions missed it" : "I have no splash potion of weakness left");
                    select(sp, slot);
                    // At its feet: the throw arcs up a little from where it looks, so it lands on it from here.
                    sp.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.position());
                    Interaction.useItem(sp, InteractionHand.MAIN_HAND);
                    TaskContext.syncInventory(sp);
                    return TaskState.RUNNING;
                }
                case FEED -> {
                    if (target.isConverting()) {
                        if (loop != null) loop.say("The zombie villager is turning back. I'll wait up out of its reach.");
                        phase = Phase.PILLAR;
                        ticks = 0;
                        return TaskState.RUNNING;
                    }
                    if (!target.hasEffect(MobEffects.WEAKNESS)) {
                        phase = Phase.THROW; // the weakness wore off before the apple: another potion
                        ticks = 0;
                        return TaskState.RUNNING;
                    }
                    if (!Interaction.inEntityReach(sp, target)) {
                        if (ticks % 10 == 1) nav.navigateNear(target.getBlockX(), target.getBlockY(), target.getBlockZ(), 1);
                        nav.tick();
                        if (ticks > 400) return end(false, "I couldn't get to it with the apple");
                        return TaskState.RUNNING;
                    }
                    nav.cancel();
                    if (!TaskContext.selectInventoryItem(player, "minecraft:golden_apple")) return end(false, "I have no golden apple left");
                    Interaction.interactEntity(sp, target, InteractionHand.MAIN_HAND);
                    TaskContext.syncInventory(sp);
                    return TaskState.RUNNING;
                }
                case PILLAR -> {
                    // Two blocks under its feet, beside it: a zombie's blows don't reach that high.
                    if (pillared >= 2 || ticks > 200) {
                        phase = Phase.WAIT;
                        ticks = 0;
                        return TaskState.RUNNING;
                    }
                    var input = TaskContext.inputDriver(player);
                    input.setForward(0.0f);
                    input.setStrafe(0.0f);
                    sp.setXRot(90.0f);
                    if (sp.onGround()) {
                        pillarBase = sp.blockPosition();
                        input.setJumping(true);
                    } else if (pillarBase != null && sp.getY() >= pillarBase.getY() + 1.0 && level.getBlockState(pillarBase).canBeReplaced()
                            && com.mineagent.engine.act.Placement.placeAnySupportBlock(sp, pillarBase)) {
                        pillared++;
                        pillarBase = null;
                    }
                    return TaskState.RUNNING;
                }
                case WAIT -> {
                    TaskContext.inputDriver(player).clear();
                    if (target.isAlive()) {
                        lastAt = target.blockPosition();
                        if (ticks > LONGEST + 1_200) return end(false, "it never turned");
                        return TaskState.RUNNING;
                    }
                    // Gone: turned (a villager where it stood) or killed.
                    Villager cured = null;
                    if (lastAt != null) {
                        for (var v : level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(lastAt).inflate(2), Villager::isAlive)) {
                            cured = v;
                            break;
                        }
                    }
                    if (cured == null) return end(false, "it died before it turned");
                    if (loop != null) {
                        loop.keepVillager(cured.getUUID());
                        loop.journal().record("mastery", "cured a zombie villager", level.getGameTime(), level.dimension().location().toString(),
                                cured.getBlockX(), cured.getBlockY(), cured.getBlockZ());
                    }
                    outcome = "cured";
                    phase = Phase.DOWN;
                    ticks = 0;
                    return TaskState.RUNNING;
                }
                case DOWN -> {
                    // Off the pillar, each block dug as it stands on it.
                    BlockPos under = sp.blockPosition().below();
                    if (pillared <= 0 || ticks > 400) return end(true, null);
                    if (!sp.onGround()) return TaskState.RUNNING;
                    if (level.getBlockState(under).isAir()) return TaskState.RUNNING;
                    sp.setXRot(90.0f);
                    if (ticks % 30 == 1 && !BlockDigger.breaking(sp, under)) BlockDigger.startBreaking(sp, under);
                    if (level.getBlockState(under).isAir()) pillared--;
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

    /** Shade over it, or night with the longest cure's time still to run before dawn. */
    static boolean safeFromSun(ServerLevel level, ZombieVillager z) {
        if (!level.canSeeSky(z.blockPosition().above())) return true;
        long t = level.getDayTime() % 24_000L;
        return t >= DUSK && t + LONGEST < DAWN;
    }

    private static ZombieVillager nearest(ServerLevel level, ServerPlayer sp) {
        ZombieVillager best = null;
        double bestD = Double.MAX_VALUE;
        for (var z : level.getEntitiesOfClass(ZombieVillager.class, sp.getBoundingBox().inflate(32, 8, 32), z -> z.isAlive() && !z.isConverting())) {
            double d = z.distanceToSqr(sp);
            if (d < bestD && sp.hasLineOfSight(z)) {
                bestD = d;
                best = z;
            }
        }
        return best;
    }

    /** The pack slot of a splash potion of weakness, or -1. */
    public static int splash(ServerPlayer sp) {
        var inv = sp.getInventory();
        for (int i = 0; i < Math.min(36, inv.getContainerSize()); i++) {
            ItemStack st = inv.getItem(i);
            if (!st.is(Items.SPLASH_POTION)) continue;
            var c = st.get(DataComponents.POTION_CONTENTS);
            if (c != null && (c.is(Potions.WEAKNESS) || c.is(Potions.LONG_WEAKNESS))) return i;
        }
        return -1;
    }

    private void select(ServerPlayer sp, int slot) {
        ItemStack st = sp.getInventory().getItem(slot);
        if (slot < 9) {
            sp.getInventory().selected = slot;
            return;
        }
        // Into the hand from the pack, as a swap.
        int hand = sp.getInventory().selected;
        ItemStack held = sp.getInventory().getItem(hand);
        sp.getInventory().setItem(hand, st);
        sp.getInventory().setItem(slot, held);
    }

    private TaskState end(boolean ok, String why) {
        if (nav != null) nav.cancel();
        TaskContext.inputDriver(player).clear();
        var loop = TaskContext.agentLoop(player);
        if (loop != null && target != null) loop.unspare(target.getUUID());
        phase = Phase.DONE;
        outcome = ok ? "Cured a zombie villager: it trades with me for far less now, and I keep it" : (why == null ? "it didn't work" : why);
        if (loop != null) loop.say(ok ? outcome + "." : "I couldn't cure it: " + outcome + ".");
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
        var loop = TaskContext.agentLoop(player);
        if (loop != null && target != null) loop.unspare(target.getUUID());
    }
}
