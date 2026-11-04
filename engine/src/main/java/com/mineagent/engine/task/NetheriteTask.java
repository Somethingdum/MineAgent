package com.mineagent.engine.task;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.task.CompanionTask;
import com.mineagent.api.task.TaskRecord;
import com.mineagent.api.task.TaskState;
import com.mineagent.engine.pathing.execute.PlayerNav;
import com.mineagent.engine.world.PlaceKeeper;
import com.mineagent.engine.world.RouteMemory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/**
 * A trip to the Nether for what netherite takes (SMH plan 70, its owner: "It should eventually get to netherite."): a
 * smithing template from a bastion's chests (the only place one is found; one is then copied with seven diamonds and a
 * netherrack, so one is enough) and ancient debris, strip-mined where the game puts most of it (task/MiningTask's height
 * for it, about Y 15). There through a portal it has been through, or one it builds from its obsidian (task/PortalTask);
 * back the same way. The scrap, the ingots (four scrap and four gold each), the copies of the template and the upgrades
 * are done at home as any other job. It goes in diamond armour with a diamond pickaxe or better, fed, and turns back
 * hurt, out of food or with its pack full; it says what it brought back.
 */
public class NetheriteTask extends CompanionTask<NetheriteTask.NetheriteRecord> {
    public static final class NetheriteRecord extends TaskRecord {
        /** Bring back a template (it has none). */
        public final boolean template;
        /** Ancient debris to bring back. */
        public final int debris;

        public NetheriteRecord(String toolCallId, boolean template, int debris) {
            super(toolCallId);
            this.template = template;
            this.debris = Math.max(0, debris);
        }
    }

    private enum Phase { TO_PORTAL, BUILD_PORTAL, ENTER, BASTION, DEBRIS, BACK, LEAVE, DONE }

    /** A trip's longest: two game days, then home with what it has. */
    static final long LONGEST = 48_000;

    private PlayerNav nav;
    private Phase phase = Phase.TO_PORTAL;
    private CompanionTask<?> sub;
    private BlockPos portal;
    private RouteMemory.Point arrived;
    private int ticks, templatesBefore, debrisBefore, built;
    private long startedAt = -1;
    private String outcome = "";

    public NetheriteTask(AgentPlayer player, NetheriteRecord record) {
        super(player, record);
    }

    @Override
    protected void onStart() {
        ServerPlayer sp = TaskContext.serverPlayer(player);
        if (nav == null) nav = new PlayerNav(player, TaskContext.navCaches(player));
        if (startedAt < 0) {
            startedAt = sp.level().getGameTime();
            templatesBefore = sp.getInventory().countItem(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
            debrisBefore = sp.getInventory().countItem(Items.ANCIENT_DEBRIS);
            record.extendDeadlineTo(Math.max(record.deadline(), startedAt + LONGEST + 12_000L));
        }
        if (sub != null) sub.resume();
    }

    /** Ready to go: diamond armour or better all over and a diamond pickaxe or better, fed. Null if so, else why not. */
    public static String notReady(ServerPlayer sp) {
        for (var slot : new net.minecraft.world.entity.EquipmentSlot[] {net.minecraft.world.entity.EquipmentSlot.HEAD,
                net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET}) {
            var st = sp.getItemBySlot(slot);
            if (st.isEmpty() || com.mineagent.engine.gear.GodGear.tier(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).toString()) < 4) {
                return "I want diamond armour all over first";
            }
        }
        if (sp.getInventory().countItem(Items.DIAMOND_PICKAXE) == 0 && sp.getInventory().countItem(Items.NETHERITE_PICKAXE) == 0) {
            return "ancient debris takes a diamond pickaxe";
        }
        int food = 0;
        for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
            var st = sp.getInventory().getItem(i);
            if (st.get(net.minecraft.core.component.DataComponents.FOOD) != null && !st.is(Items.ROTTEN_FLESH)) food += st.getCount();
        }
        return food < 16 ? "I want 16 food for the trip" : null;
    }

    @Override
    protected TaskState onTick() {
        ServerPlayer sp = TaskContext.serverPlayer(player);
        ServerLevel level = sp.serverLevel();
        var loop = TaskContext.agentLoop(player);
        try {
            if (sub != null) {
                TaskState st = sub.tick();
                if (st == TaskState.RUNNING) return TaskState.RUNNING;
                sub = null;
                ticks = 0;
                switch (phase) {
                    case BUILD_PORTAL -> {
                        if (st != TaskState.SUCCESS) return end(false, "I couldn't build a portal to the Nether");
                        phase = Phase.TO_PORTAL;
                    }
                    case BASTION -> phase = record.debris > 0 ? Phase.DEBRIS : Phase.BACK;
                    case DEBRIS -> phase = Phase.BACK;
                    default -> { }
                }
                return TaskState.RUNNING;
            }
            ticks++;
            long now = level.getGameTime();
            boolean nether = level.dimension() == Level.NETHER;
            // Turning back: hurt, hungry, the trip too long, or its pack full.
            if (nether && phase != Phase.BACK && phase != Phase.LEAVE
                    && (sp.getHealth() < sp.getMaxHealth() * 0.4 || food(sp) < 4 || now - startedAt > LONGEST || sp.getInventory().getFreeSlot() < 0)) {
                say("Heading back from the Nether.");
                phase = Phase.BACK;
                ticks = 0;
            }
            switch (phase) {
                case TO_PORTAL -> {
                    if (nether) {
                        enteredNether(sp, loop);
                        return TaskState.RUNNING;
                    }
                    if (portal == null) portal = portalNear(sp, loop);
                    if (portal == null) {
                        if (built++ > 0) return end(false, "the portal I built doesn't light");
                        say("No portal to the Nether near: building one from my obsidian.");
                        sub = new PortalTask(player, new PortalTask.PortalRecord(record.toolCallId() + ":portal"));
                        sub.start();
                        phase = Phase.BUILD_PORTAL;
                        return TaskState.RUNNING;
                    }
                    phase = Phase.ENTER;
                    ticks = 0;
                    return TaskState.RUNNING;
                }
                case ENTER -> {
                    // Into the portal and stood in it till the game takes it through (four seconds).
                    if (nether) {
                        enteredNether(sp, loop);
                        return TaskState.RUNNING;
                    }
                    if (ticks > 1_200) return end(false, "I couldn't get into the portal");
                    if (!PlaceKeeper.standingInPortal(sp)) {
                        if (ticks % 20 == 1 || !nav.isNavigating()) nav.navigateTo(portal.getX(), portal.getY(), portal.getZ());
                        nav.tick();
                    } else {
                        nav.cancel();
                        TaskContext.inputDriver(player).clear();
                    }
                    return TaskState.RUNNING;
                }
                case BASTION -> {
                    say("Off to the nearest bastion for a smithing template.");
                    sub = new LootTask(player, new LootTask.LootRecord(record.toolCallId() + ":bastion", true, List.of("minecraft:bastion_remnant")));
                    sub.start();
                    return TaskState.RUNNING;
                }
                case DEBRIS -> {
                    if (sp.getInventory().countItem(Items.ANCIENT_DEBRIS) - debrisBefore >= record.debris) {
                        phase = Phase.BACK;
                        return TaskState.RUNNING;
                    }
                    say("Strip-mining for ancient debris.");
                    sub = new MiningTask(player, new MiningTask.MineRecord(record.toolCallId() + ":debris", "ancient debris", record.debris, false));
                    sub.start();
                    return TaskState.RUNNING;
                }
                case BACK -> {
                    if (!nether) return end(true, null);
                    if (arrived == null) return end(false, "I don't know the way back to the portal");
                    BlockPos back = new BlockPos(arrived.x(), arrived.y(), arrived.z());
                    if (PlaceKeeper.standingInPortal(sp) || sp.blockPosition().distSqr(back) <= 4) {
                        phase = Phase.LEAVE;
                        ticks = 0;
                        return TaskState.RUNNING;
                    }
                    if (ticks > 24_000) return end(false, "I couldn't find my way back to the portal");
                    if (ticks % 40 == 1 || !nav.isNavigating()) nav.navigateNear(back.getX(), back.getY(), back.getZ(), 1);
                    nav.tick();
                    return TaskState.RUNNING;
                }
                case LEAVE -> {
                    if (!nether) return end(true, null);
                    if (ticks > 1_200) return end(false, "I couldn't get back through the portal");
                    if (!PlaceKeeper.standingInPortal(sp)) {
                        BlockPos p = portalBlockNear(level, sp.blockPosition(), 4);
                        if (p == null) return end(false, "the portal I came through is gone");
                        if (ticks % 20 == 1 || !nav.isNavigating()) nav.navigateTo(p.getX(), p.getY(), p.getZ());
                        nav.tick();
                    } else {
                        nav.cancel();
                        TaskContext.inputDriver(player).clear();
                    }
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

    /** Through to the Nether: where it came out remembered (the way back), then the template or the debris. */
    private void enteredNether(ServerPlayer sp, com.mineagent.engine.loop.AgentLoop loop) {
        nav.cancel();
        TaskContext.inputDriver(player).clear();
        if (arrived == null) arrived = PlaceKeeper.point(sp);
        say("In the Nether.");
        phase = record.template ? Phase.BASTION : record.debris > 0 ? Phase.DEBRIS : Phase.BACK;
        ticks = 0;
    }

    /** A portal to the Nether: one it has been through from here, else a lit one within 64 blocks. */
    private BlockPos portalNear(ServerPlayer sp, com.mineagent.engine.loop.AgentLoop loop) {
        if (loop != null) {
            var link = loop.placeKeeper().memory().portalTo("minecraft:the_nether", PlaceKeeper.point(sp));
            if (link.isPresent()) {
                var at = PlaceKeeper.portalBlockNear(sp, link.get().from());
                if (at != null) return new BlockPos(at.x(), at.y(), at.z());
            }
        }
        return portalBlockNear(sp.serverLevel(), sp.blockPosition(), 64);
    }

    /** The nearest nether portal block within {@code r}, the lowest of its column; null if none. */
    private static BlockPos portalBlockNear(ServerLevel level, BlockPos at, int r) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-r, -Math.min(r, 16), -r), at.offset(r, Math.min(r, 16), r))) {
            if (!level.getBlockState(p).is(Blocks.NETHER_PORTAL) || level.getBlockState(p.below()).is(Blocks.NETHER_PORTAL)) continue;
            double d = p.distSqr(at);
            if (d < bestD) {
                bestD = d;
                best = p.immutable();
            }
        }
        return best;
    }

    private static int food(ServerPlayer sp) {
        int n = 0;
        for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
            var st = sp.getInventory().getItem(i);
            if (st.get(net.minecraft.core.component.DataComponents.FOOD) != null && !st.is(Items.ROTTEN_FLESH)) n += st.getCount();
        }
        return n;
    }

    private void say(String s) {
        var loop = TaskContext.agentLoop(player);
        if (loop != null) loop.say(s);
    }

    private TaskState end(boolean ok, String why) {
        if (sub != null) sub.interrupt();
        sub = null;
        if (nav != null) nav.cancel();
        TaskContext.inputDriver(player).clear();
        phase = Phase.DONE;
        ServerPlayer sp = TaskContext.serverPlayer(player);
        int templates = sp.getInventory().countItem(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE) - templatesBefore;
        int debris = sp.getInventory().countItem(Items.ANCIENT_DEBRIS) - debrisBefore;
        String got = (templates > 0 ? templates + (templates == 1 ? " smithing template" : " smithing templates") : "")
                + (templates > 0 && debris > 0 ? " and " : "") + (debris > 0 ? debris + " ancient debris" : "");
        outcome = (got.isEmpty() ? "Back from the Nether with nothing for netherite" : "Back from the Nether with " + got)
                + (why == null ? "" : " (" + why + ")");
        var loop = TaskContext.agentLoop(player);
        if (loop != null) {
            loop.say(outcome + ".");
            loop.journal().record("gear", outcome, sp.level().getGameTime());
        }
        return ok && (templates > 0 || debris > 0) ? TaskState.SUCCESS : TaskState.FAILED;
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
        if (sub != null) sub.interrupt();
        if (nav != null) nav.cancel();
        TaskContext.inputDriver(player).clear();
    }
}
