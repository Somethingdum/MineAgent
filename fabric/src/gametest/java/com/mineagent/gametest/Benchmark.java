package com.mineagent.gametest;

import com.google.gson.GsonBuilder;
import com.mineagent.engine.MineAgentEngine;
import com.mineagent.engine.entity.CompanionEntity;
import com.mineagent.engine.loop.AgentLoop;
import com.mineagent.engine.task.TaskContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The benchmark (SMH plan 70, its owner: "It's job in 3 days is to have a hut, a furnace, a crafting table, some food,
 * some fuel. And at least stone tools. Minimum requirements."): on a real world (a dedicated server, its seed in
 * server.properties), a fresh companion with nothing is told "go survive on your own" and the game sprints through
 * {@code -Dmineagent.benchmark} ticks (72,000 for three days). Its owner is a stand-in player, out of the way in a sealed
 * box over the spawn. No model: what it does is its own code. Every game minute a line of how it is doing; at the end,
 * the minimum checked (a hut built, a furnace and a crafting table put down or carried, food enough for a night (its
 * owner: "It just needs ENOUGH food to survive until morning": food carried, or a hunger bar that lasts a night as it
 * is, and the report says which), 4 coal or charcoal, a stone pickaxe, axe, sword and
 * shovel or better; a bed is reported, not needed: "A bed if possible") and how the run went (deaths, watchdog, what it holds), written to
 * {@code -Dmineagent.benchmark.report}, and the server stopped. Only when the property is set; never in the jar.
 */
public final class Benchmark implements ModInitializer {
    private static long total = -1, startedAt = -1, lastLine = -1;
    /** The day the minimum was first met, or -1. */
    private static double firstMet = -1;
    private static ServerPlayer owner;
    private static CompanionEntity companion;
    private static final Map<String, Object> days = new LinkedHashMap<>();
    /** Per tick, from the start: the server's own tick time (vanilla's measure) and the companion's (MineAgentEngine). */
    private static long[] serverNanos = new long[0], companionNanos = new long[0];
    private static int samples;
    /** Stalls (SMH plan 71, §7.13): where and what it carried when it last changed, and for how long since. */
    private static BlockPos stillFrom;
    private static String stillCarrying = "";
    private static long lastChange;
    private static int stillDaytimeMinutes;
    private static int stalls;
    private static double firstStallDay = -1;
    private static String stallSnapshot;
    private static boolean endedStalled;

    @Override
    public void onInitialize() {
        String ticks = System.getProperty("mineagent.benchmark");
        if (ticks == null || ticks.isBlank()) return;
        total = Long.parseLong(ticks.trim());
        // The switches this run turns on over the config's (-PbenchmarkSwitches, "altoTasks,altoLife"): the same worlds
        // run both ways, so a switch is kept on only where it helped.
        for (String sw : System.getProperty("mineagent.benchmark.switches", "").split(",")) {
            switch (sw.trim()) {
                case "altoTasks" -> com.mineagent.engine.settings.Switches.altoTasksOverride = Boolean.TRUE;
                case "altoLife" -> com.mineagent.engine.settings.Switches.altoLifeOverride = Boolean.TRUE;
                case "altoSurvival" -> com.mineagent.engine.settings.Switches.altoSurvivalOverride = Boolean.TRUE;
                case "oneAgent" -> com.mineagent.engine.settings.Switches.oneAgentOverride = Boolean.TRUE;
                case "" -> { }
                default -> System.out.println("[BENCH] no such switch to turn on: " + sw.trim());
            }
        }
        System.out.println("[BENCH] switches on over the config: " + System.getProperty("mineagent.benchmark.switches", "none"));
        ServerLifecycleEvents.SERVER_STARTED.register(Benchmark::start);
        ServerTickEvents.END_SERVER_TICK.register(Benchmark::tick);
    }

    private static void start(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes("OfflinePlayer:Owner".getBytes()), "Owner"), false);
        owner = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, owner, cookie);
        owner.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        owner.teleportTo(level, spawn.getX() + 0.5, y, spawn.getZ() + 0.5, 0.0f, 0.0f);
        companion = MineAgentEngine.spawnCompanion(owner, "Hoarder", "openai-compatible", "benchmark", "benchmark-hoarder",
                "http://127.0.0.1:9", 0.2, null, false);
        if (companion == null) {
            System.out.println("[BENCH] the companion did not spawn");
            server.halt(false);
            return;
        }
        // Its own from the start (never following its owner up into the box), and told so while they stand by it.
        MineAgentEngine.setCompanionMode(companion.companionId(), MineAgentEngine.CompanionMode.FREE);
        MineAgentEngine.onPlayerChat(owner, "go survive on your own");
        // Its owner out of the way: a sealed box of barrier high over the spawn, the chunks round the spawn kept loaded.
        int by = Math.min(level.getMaxBuildHeight() - 6, Math.max(y + 60, 200));
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 0; dy <= 3; dy++) {
            boolean inside = dx == 0 && dz == 0 && (dy == 1 || dy == 2);
            level.setBlockAndUpdate(spawn.offset(dx, by - spawn.getY() + dy, dz), inside ? Blocks.AIR.defaultBlockState() : Blocks.BARRIER.defaultBlockState());
        }
        owner.teleportTo(level, spawn.getX() + 0.5, by + 1, spawn.getZ() + 0.5, 0.0f, 0.0f);
        owner.getAbilities().invulnerable = true;
        owner.onUpdateAbilities();
        startedAt = level.getGameTime();
        int capacity = (int) Math.min(total + 100, 2_000_000);
        serverNanos = new long[capacity];
        companionNanos = new long[capacity];
        // A forced stall, to check the stall marking and its snapshot (-Dmineagent.benchmark.boxIn=true): the companion
        // shut in barrier where it stands, so it can neither move nor get anything.
        if (Boolean.getBoolean("mineagent.benchmark.boxIn") && companion.serverPlayer() != null) {
            BlockPos c = companion.serverPlayer().blockPosition();
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = -1; dy <= 2; dy++) {
                boolean inside = dx == 0 && dz == 0 && (dy == 0 || dy == 1);
                level.setBlockAndUpdate(c.offset(dx, dy, dz), inside ? Blocks.AIR.defaultBlockState() : Blocks.BARRIER.defaultBlockState());
            }
            System.out.println("[BENCH] boxed in at " + c.toShortString() + " (a forced stall)");
        }
        watchForAHang(server);
        server.tickRateManager().requestGameToSprint((int) Math.min(Integer.MAX_VALUE, total));
        System.out.println("[BENCH] started at the spawn " + spawn.toShortString() + " on seed " + level.getSeed() + ", " + total + " ticks");
    }

    /**
     * The run's own watchdog (its server's is off, max-tick-time=-1, as a sprint's long ticks would trip it): two minutes
     * with no server tick and every thread's stack goes to the log ("[BENCH] hung:"), and the run ends, so a freeze is
     * found from its log instead of sitting till the job's time is up (two AltoClef worlds froze on Nov 5).
     */
    private static void watchForAHang(MinecraftServer server) {
        Thread watch = new Thread(() -> {
            int last = -1;
            long since = System.nanoTime();
            while (true) {
                try {
                    Thread.sleep(10_000);
                } catch (InterruptedException e) {
                    return;
                }
                int now = server.getTickCount();
                if (now != last) {
                    last = now;
                    since = System.nanoTime();
                    continue;
                }
                if (System.nanoTime() - since < 120_000_000_000L) continue;
                System.out.println("[BENCH] hung: no server tick for two minutes, at server tick " + now + "; its threads:");
                for (var info : java.lang.management.ManagementFactory.getThreadMXBean().dumpAllThreads(true, true)) {
                    System.out.println("[BENCH] hung: \"" + info.getThreadName() + "\" " + info.getThreadState()
                            + (info.getLockName() == null ? "" : " waiting on " + info.getLockName())
                            + (info.getLockOwnerName() == null ? "" : " held by \"" + info.getLockOwnerName() + "\""));
                    // In full for the server's thread, Baritone's (the old port's and Automatone's path workers), any
                    // blocked on a lock and any holding one (the job log keeps 400 lines).
                    boolean full = info.getThreadName().equals("Server thread") || info.getThreadName().startsWith("MineAgent Baritone")
                            || info.getThreadName().startsWith("Automatone Worker")
                            || info.getThreadState() == Thread.State.BLOCKED || info.getLockOwnerName() != null
                            || info.getLockedMonitors().length > 0;
                    var frames = info.getStackTrace();
                    for (int i = 0; i < Math.min(full ? 40 : 2, frames.length); i++) System.out.println("[BENCH] hung:     at " + frames[i]);
                    for (var held : info.getLockedMonitors()) System.out.println("[BENCH] hung:     holds " + held);
                }
                System.out.flush();
                Runtime.getRuntime().halt(3);
            }
        }, "MineAgent benchmark watchdog");
        watch.setDaemon(true);
        watch.start();
    }

    private static void tick(MinecraftServer server) {
        if (startedAt < 0 || companion == null) return;
        ServerLevel level = server.overworld();
        long elapsed = level.getGameTime() - startedAt;
        if (samples < serverNanos.length) {
            long[] times = server.getTickTimesNanos();
            serverNanos[samples] = times[server.getTickCount() % times.length];
            companionNanos[samples] = com.mineagent.engine.MineAgentEngine.lastTickNanos;
            samples++;
        }
        if (elapsed - lastLine >= 1_200) {
            lastLine = elapsed;
            var s = state(server);
            String day = String.format("%.2f", elapsed / 24_000.0);
            Runtime rt = Runtime.getRuntime();
            System.out.println("[BENCH] day " + day + ": " + s.get("summary") + " | memory "
                    + (rt.totalMemory() - rt.freeMemory()) / 1_048_576 + " of " + rt.maxMemory() / 1_048_576 + " MB");
            if (elapsed % 24_000 < 1_200) days.put("day " + (elapsed / 24_000), s);
            if (firstMet < 0 && Boolean.TRUE.equals(s.get("minimum"))) firstMet = elapsed / 24_000.0;
            stuckReport(server, day);
            deathReport(day);
            System.out.println("[BENCH] day " + day + " tick cost: " + tickCost());
            stallCheck(server, elapsed, day, s);
            if (endedStalled) {
                finish(server, elapsed);
                return;
            }
        }
        if (elapsed >= total) finish(server, elapsed);
    }

    private static BlockPos lastAt;
    private static int stillLines;
    private static int deathsSeen;

    /** Each death since the last line: what the game said killed it, and where (the eleventh run died eleven times). */
    private static void deathReport(String day) {
        AgentLoop loop = TaskContext.agentLoop(companion);
        if (loop == null) return;
        var deaths = new java.util.ArrayList<String>();
        for (var e : loop.journal().search("", "death", 2_000)) if (!e.text().startsWith("came back")) deaths.add(e.text());
        if (deaths.size() <= deathsSeen) return;
        java.util.Collections.reverse(deaths); // the journal gives the newest first
        for (int i = deathsSeen; i < deaths.size(); i++) System.out.println("[BENCH] day " + day + " died: " + deaths.get(i));
        deathsSeen = deaths.size();
    }

    /**
     * Where it has stood still for three game minutes and more (the third run sat on one block from day 1.1, every hunt
     * "getting nowhere"): what is round it, row by row from two over its head to under its feet (a letter a block, '.'
     * air, '~' water), the blocks it carries to climb or bridge with, and what has its body. Then every five minutes
     * while it stays.
     */
    private static void stuckReport(MinecraftServer server, String day) {
        var sp = companion.serverPlayer();
        if (sp == null) return;
        BlockPos at = sp.blockPosition();
        if (!at.equals(lastAt)) {
            lastAt = at.immutable();
            stillLines = 0;
            return;
        }
        if (++stillLines < 3 || (stillLines - 3) % 5 != 0) return;
        ServerLevel level = sp.serverLevel();
        StringBuilder b = new StringBuilder("[BENCH] day " + day + " still at " + at.toShortString() + " for " + stillLines + " minutes; round it (x across, z down):");
        for (int dy = 2; dy >= -1; dy--) {
            b.append(" | y").append(dy >= 0 ? "+" : "").append(dy).append(':');
            for (int dz = -2; dz <= 2; dz++) {
                b.append(dz == -2 ? "" : "/");
                for (int dx = -2; dx <= 2; dx++) {
                    var st = level.getBlockState(at.offset(dx, dy, dz));
                    String path = BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath();
                    char c = st.isAir() ? '.' : !st.getFluidState().isEmpty() ? '~' : Character.toUpperCase(path.charAt(0));
                    b.append(dx == 0 && dz == 0 && (dy == 0 || dy == 1) ? '@' : c);
                }
            }
        }
        b.append(" | sky ").append(level.canSeeSky(at.above())).append(", surface y ")
                .append(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()));
        b.append(" | blocks it carries: ").append(com.mineagent.engine.act.Placement.supportBlockCount(sp));
        // Each block hemming it in, and why it may not break it (its own walls kept it in a whole day once).
        var placed = com.mineagent.engine.world.PlacedBlocks.of(level);
        StringBuilder walls = new StringBuilder();
        for (BlockPos p : new BlockPos[] {at.north(), at.south(), at.east(), at.west(), at.above().north(), at.above().south(),
                at.above().east(), at.above().west(), at.above(2), at.below()}) {
            var st = level.getBlockState(p);
            if (st.isAir() || !st.getFluidState().isEmpty()) continue;
            var why = com.mineagent.engine.task.BlockDigger.assessBreak(sp, p);
            walls.append(walls.length() == 0 ? "" : "; ").append(p.subtract(at).toShortString()).append(' ')
                    .append(BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath())
                    .append(placed.isPlaced(p.getX(), p.getY(), p.getZ()) ? " placed" : "")
                    .append(why.allowed() ? "" : " (" + why.status() + ": " + why.detail() + ")");
        }
        if (walls.length() > 0) b.append(" | round it: ").append(walls);
        b.append(" | AFK quiet here ").append(com.mineagent.engine.world.AfkFarms.protects(level.dimension().location().toString(),
                at.getX(), at.getY(), at.getZ()));
        b.append(" | body: ").append(MineAgentEngine.getCompanion(companion.companionId()).map(c -> c.auction.inControl()).orElse("gone"));
        System.out.println(b);
    }

    /** How it stands now: the minimum and the rest. */
    private static Map<String, Object> state(MinecraftServer server) {
        Map<String, Object> out = new LinkedHashMap<>();
        var sp = companion.serverPlayer();
        AgentLoop loop = TaskContext.agentLoop(companion);
        if (sp == null || loop == null) {
            out.put("summary", "no body");
            return out;
        }
        ServerLevel level = sp.serverLevel();
        String dim = level.dimension().location().toString();
        BlockPos home = loop.homeIn("minecraft:overworld");
        ServerLevel over = server.overworld();
        BlockPos centre = home != null ? home : sp.blockPosition();
        // What it holds: its pack and its own chests (read as they are).
        Map<String, Integer> has = new LinkedHashMap<>();
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) add(has, inv.getItem(i));
        for (String k : loop.ownChests()) {
            String[] f = k.split("\\|");
            if (f.length != 4 || !f[0].equals("minecraft:overworld")) continue;
            var p = new BlockPos(Integer.parseInt(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3]));
            if (over.isLoaded(p) && over.getBlockEntity(p) instanceof net.minecraft.world.Container c) {
                for (int i = 0; i < c.getContainerSize(); i++) add(has, c.getItem(i));
            }
        }
        // Food: its owner, "It just needs ENOUGH food to survive until morning": what it carries worth three shanks or
        // more (a cooked steak, two breads, three berries' worth twice), or a hunger bar that lasts the night as it is
        // (14 of 20: a night closed in costs next to nothing). Which of the two is said.
        int food = 0, foodPoints = 0;
        for (var e : has.entrySet()) {
            var item = BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(e.getKey()));
            var st = new ItemStack(item);
            var props = st.get(net.minecraft.core.component.DataComponents.FOOD);
            if (props != null && !st.is(Items.ROTTEN_FLESH) && !st.is(Items.SPIDER_EYE) && !st.is(Items.POISONOUS_POTATO)) {
                food += e.getValue();
                foodPoints += props.nutrition() * e.getValue();
            }
        }
        int hunger = sp.getFoodData().getFoodLevel();
        boolean fedByPack = foodPoints >= 6;
        boolean fed = fedByPack || hunger >= 14;
        boolean bed = false;
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).getItem() instanceof net.minecraft.world.item.BedItem) bed = true;
        if (!bed) for (var p : BlockPos.betweenClosed(centre.offset(-8, -4, -8), centre.offset(8, 4, 8))) {
            if (over.isLoaded(p) && over.getBlockState(p).getBlock() instanceof net.minecraft.world.level.block.BedBlock) {
                bed = true;
                break;
            }
        }
        int fuel = has.getOrDefault("minecraft:coal", 0) + has.getOrDefault("minecraft:charcoal", 0) + 9 * has.getOrDefault("minecraft:coal_block", 0);
        Map<String, Integer> tools = new LinkedHashMap<>();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var st = inv.getItem(i);
            if (st.isEmpty()) continue;
            String id = BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
            String kind = com.mineagent.engine.gear.GodGear.kindOf(id);
            if (kind == null) continue;
            tools.merge(kind, com.mineagent.engine.gear.GodGear.tier(id), Math::max);
        }
        boolean stoneTools = true;
        for (String k : new String[] {"pickaxe", "axe", "sword", "shovel"}) if (tools.getOrDefault(k, 0) < 2) stoneTools = false;
        boolean furnace = has.containsKey("minecraft:furnace") || placedNear(over, centre, Blocks.FURNACE)
                || placedNear(over, centre, Blocks.BLAST_FURNACE) || placedNear(over, centre, Blocks.SMOKER);
        boolean table = has.containsKey("minecraft:crafting_table") || placedNear(over, centre, Blocks.CRAFTING_TABLE);
        boolean hut = home != null && loop.hutNear("minecraft:overworld", home) || !loop.journal().search("built the hut", null, 3).isEmpty();
        // Which kind of home (a hut of what, or dug into a hillside or down a stairway), from the place it kept.
        String hutKind = "";
        for (var p : loop.placeKeeper().memory().places()) {
            if ("build".equals(p.kind()) && p.name() != null && p.name().startsWith("hut")) hutKind = p.name().split("\\|", 3).length > 1
                    ? p.name().split("\\|", 3)[0].replace("hut_", "") + " " + p.name().split("\\|", 3)[1] : p.name();
        }
        int deaths = 0, comeBack = 0;
        for (var e : loop.journal().search("", "death", 2_000)) {
            if (e.text().startsWith("came back")) comeBack++;
            else deaths++;
        }
        boolean minimum = hut && furnace && table && fed && fuel >= 4 && stoneTools;
        out.put("minimum", minimum);
        out.put("hut", hut);
        out.put("furnace", furnace);
        out.put("craftingTable", table);
        out.put("food", food);
        out.put("foodPoints", foodPoints);
        out.put("hunger", hunger);
        out.put("fed", fed);
        out.put("fedByPack", fedByPack);
        out.put("bed", bed);
        out.put("fuel", fuel);
        out.put("tools", tools);
        out.put("stoneTools", stoneTools);
        out.put("deaths", deaths);
        out.put("cameBack", comeBack);
        out.put("watchdog", loop.journal().search("", "watchdog", 100).size());
        out.put("home", home == null ? null : home.toShortString());
        out.put("at", dim + " " + sp.blockPosition().toShortString());
        out.put("health", sp.getHealth());
        out.put("doing", loop.doing());
        out.put("hutKind", hutKind);
        out.put("summary", (minimum ? "MINIMUM MET" : "not yet") + " | hut " + hut + (hutKind.isEmpty() ? "" : " (" + hutKind + ")") + ", furnace " + furnace + ", table " + table + ", food " + food
                + " (" + foodPoints + " points, hunger " + hunger + (fedByPack ? ", fed: food carried" : fed ? ", fed: on its hunger bar" : ", not fed") + "), bed " + bed + ", fuel " + fuel + ", tools " + tools + " | " + dim + " " + sp.blockPosition().toShortString() + ", health "
                + String.format("%.0f", sp.getHealth()) + ", deaths " + deaths + ", doing: " + loop.doing()
                // The one agent's own state when it has the body: its chain, Baritone's goal and its task tree, root to leaf.
                + (com.mineagent.engine.agent.Agents.existing(companion.companionId()) != null ? " | agent: "
                        + com.mineagent.engine.agent.Agents.describe(companion.companionId()) : ""));
        return out;
    }

    private static void add(Map<String, Integer> has, ItemStack st) {
        if (st == null || st.isEmpty()) return;
        has.merge(BuiltInRegistries.ITEM.getKey(st.getItem()).toString(), st.getCount(), Integer::sum);
    }

    /** That block put down within 48 of its home (or of it). */
    private static boolean placedNear(ServerLevel level, BlockPos centre, net.minecraft.world.level.block.Block block) {
        for (BlockPos p : BlockPos.betweenClosed(centre.offset(-48, -12, -48), centre.offset(48, 12, 48))) {
            if (level.isLoaded(p) && level.getBlockState(p).is(block)) return true;
        }
        return false;
    }

    /** Mean and 99th percentile, in ms, of the companion's own tick and the server's, over the run so far. */
    private static Map<String, Object> tickCostMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ticks", samples);
        m.put("companionMeanMs", meanMs(companionNanos));
        m.put("companionP99Ms", p99Ms(companionNanos));
        m.put("serverMeanMs", meanMs(serverNanos));
        m.put("serverP99Ms", p99Ms(serverNanos));
        return m;
    }

    private static String tickCost() {
        return String.format("companion mean %.2f ms, p99 %.2f ms; server mean %.2f ms, p99 %.2f ms",
                meanMs(companionNanos), p99Ms(companionNanos), meanMs(serverNanos), p99Ms(serverNanos));
    }

    private static double meanMs(long[] a) {
        if (samples == 0) return 0;
        long sum = 0;
        for (int i = 0; i < samples; i++) sum += a[i];
        return sum / (double) samples / 1_000_000.0;
    }

    private static double p99Ms(long[] a) {
        if (samples == 0) return 0;
        long[] copy = java.util.Arrays.copyOf(a, samples);
        java.util.Arrays.sort(copy);
        return copy[Math.min(samples - 1, (int) Math.ceil(samples * 0.99) - 1)] / 1_000_000.0;
    }

    /**
     * A stall (SMH plan 71, §7.13): while the minimum is not met (resting once it is, as its owner allows, is not a
     * stall: a run on Nov 5 met it at day 0.35, rested in its hut, and was wrongly ended at day 1.5), in daylight after
     * the mobs have burned (time of day 1,000 to 12,000; waiting out the night inside is the owner's own rule), five
     * game minutes running with it never more than two blocks from where it was and nothing gained or lost. The first
     * one leaves a snapshot a scene can rebuild. No change at all, day or night, for a whole game day while the minimum
     * is not met ends the run, failed.
     */
    private static void stallCheck(MinecraftServer server, long elapsed, String day, Map<String, Object> s) {
        var sp = companion.serverPlayer();
        if (sp == null || !sp.isAlive() || Boolean.TRUE.equals(s.get("minimum"))) {
            stillFrom = null;
            stillDaytimeMinutes = 0;
            lastChange = elapsed;
            return;
        }
        BlockPos at = sp.blockPosition();
        StringBuilder carrying = new StringBuilder();
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var st = inv.getItem(i);
            if (!st.isEmpty()) carrying.append(BuiltInRegistries.ITEM.getKey(st.getItem())).append('x').append(st.getCount()).append(',');
        }
        boolean changed = stillFrom == null || at.distSqr(stillFrom) > 4 || !carrying.toString().equals(stillCarrying);
        if (changed) {
            stillFrom = at.immutable();
            stillCarrying = carrying.toString();
            stillDaytimeMinutes = 0;
            lastChange = elapsed;
            return;
        }
        long timeOfDay = sp.serverLevel().getDayTime() % 24_000L;
        if (timeOfDay >= 1_000 && timeOfDay < 12_000) stillDaytimeMinutes++;
        else stillDaytimeMinutes = 0;
        if (stillDaytimeMinutes == 5) {
            stalls++;
            if (firstStallDay < 0) firstStallDay = elapsed / 24_000.0;
            System.out.println("[BENCH] day " + day + " stalled at " + at.toShortString() + ": five daytime minutes without moving"
                    + " two blocks or gaining or losing anything | " + s.get("summary"));
            if (stallSnapshot == null) stallSnapshot = snapshot(sp, elapsed, day, s);
        }
        if (elapsed - lastChange >= 24_000) {
            System.out.println("[BENCH] day " + day + " no change for a whole day: the run ends here, failed");
            endedStalled = true;
        }
    }

    /**
     * The blocks within 32 of it (a structure file), what it carries, its health, hunger, the time of day, its home and
     * its description, under stalls/<seed>-<tick>/ in the run's directory, for the "a stall snapshot" scene
     * ({@code ./gradlew :fabric:runGametest -Pscenes=stallsnapshot -Pstall=<that directory>}). A synthetic world only.
     */
    private static String snapshot(ServerPlayer sp, long elapsed, String day, Map<String, Object> s) {
        try {
            ServerLevel level = sp.serverLevel();
            BlockPos at = sp.blockPosition();
            int y0 = Math.max(level.getMinBuildHeight(), at.getY() - 32);
            int y1 = Math.min(level.getMaxBuildHeight() - 1, at.getY() + 32);
            BlockPos corner = new BlockPos(at.getX() - 32, y0, at.getZ() - 32);
            var template = new net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate();
            template.fillFromWorld(level, corner, new net.minecraft.core.Vec3i(65, y1 - y0 + 1, 65), true, Blocks.STRUCTURE_VOID);
            Path dir = Path.of("stalls", level.getSeed() + "-" + elapsed).toAbsolutePath();
            Files.createDirectories(dir);
            net.minecraft.nbt.NbtIo.writeCompressed(template.save(new net.minecraft.nbt.CompoundTag()), dir.resolve("structure.nbt"));
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("day", day);
            info.put("tick", elapsed);
            info.put("seed", level.getSeed());
            BlockPos rel = at.subtract(corner);
            info.put("at", new int[] {rel.getX(), rel.getY(), rel.getZ()});
            info.put("yaw", sp.getYRot());
            info.put("timeOfDay", level.getDayTime() % 24_000L);
            info.put("health", sp.getHealth());
            info.put("hunger", sp.getFoodData().getFoodLevel());
            info.put("switches", System.getProperty("mineagent.benchmark.switches", ""));
            AgentLoop loop = TaskContext.agentLoop(companion);
            BlockPos home = loop == null ? null : loop.homeIn("minecraft:overworld");
            info.put("home", home == null ? null : new int[] {home.getX() - corner.getX(), home.getY() - corner.getY(), home.getZ() - corner.getZ()});
            java.util.List<Map<String, Object>> items = new java.util.ArrayList<>();
            var inv = sp.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                var st = inv.getItem(i);
                if (st.isEmpty()) continue;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("slot", i);
                item.put("snbt", st.save(level.registryAccess()).toString());
                items.add(item);
            }
            info.put("items", items);
            info.put("summary", s.get("summary"));
            Files.writeString(dir.resolve("stall.json"), new GsonBuilder().setPrettyPrinting().create().toJson(info));
            System.out.println("[BENCH] stall snapshot saved: " + dir);
            return dir.toString();
        } catch (Exception e) {
            System.out.println("[BENCH] couldn't save the stall snapshot: " + e);
            return null;
        }
    }

    private static void finish(MinecraftServer server, long elapsed) {
        startedAt = -1;
        var s = state(server);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("ticks", elapsed);
        report.put("days", elapsed / 24_000.0);
        report.put("seed", server.overworld().getSeed());
        report.put("passed", s.get("minimum"));
        report.put("heldAtEnd", s.get("minimum"));
        report.put("minimumFirstMetDay", firstMet);
        report.put("endedEarlyStalled", endedStalled);
        report.put("stalls", stalls);
        report.put("firstStallDay", firstStallDay);
        report.put("stallSnapshot", stallSnapshot);
        report.put("tickCost", tickCostMap());
        report.put("switchesOn", System.getProperty("mineagent.benchmark.switches", ""));
        report.put("end", s);
        report.put("byDay", days);
        String json = new GsonBuilder().setPrettyPrinting().create().toJson(report);
        System.out.println("[BENCH] done after " + elapsed + " ticks: " + s.get("summary") + " | held at the end ("
                + String.format("day %.2f", elapsed / 24_000.0) + "): " + s.get("minimum") + " | minimum first met on day "
                + (firstMet < 0 ? "never" : String.format("%.2f", firstMet)) + " | stalls " + stalls
                + (endedStalled ? " (ended early: no change for a whole day)" : "")
                + (stallSnapshot == null ? "" : ", first snapshot " + stallSnapshot) + " | tick cost: " + tickCost()
                + " | switches on: " + System.getProperty("mineagent.benchmark.switches", "none"));
        System.out.println("[BENCH] report\n" + json);
        String path = System.getProperty("mineagent.benchmark.report");
        if (path != null) {
            try {
                Files.createDirectories(Path.of(path).getParent());
                Files.writeString(Path.of(path), json);
            } catch (Exception e) {
                System.out.println("[BENCH] couldn't write the report: " + e.getMessage());
            }
        }
        server.halt(false);
    }
}
