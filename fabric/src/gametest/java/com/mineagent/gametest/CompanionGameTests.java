package com.mineagent.gametest;

import com.mineagent.engine.MineAgentEngine;
import com.mineagent.engine.chat.CommandParser;
import com.mineagent.engine.entity.CompanionEntity;
import com.mineagent.engine.learning.DeathNote;
import com.mineagent.engine.loop.AgentLoop;
import com.mineagent.engine.planning.GoalBook;
import com.mineagent.engine.task.TaskContext;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The companion in a running game (SMH plan 69, MA4.4), on a headless server in CI: what the unit tests cannot show.
 * No model is needed for these scenes: the companion points at a closed local port, so every model call fails at
 * once and only the game-side code acts (combat, guard duty, deaths, the goal book). Husks stand in for zombies:
 * they do not burn in daylight, which would kill them for the companion. Each scene runs alone (its own batch) and
 * first removes any companion an earlier scene left behind.
 */
public class CompanionGameTests implements FabricGameTest {
    private static final String NO_MODEL = "http://127.0.0.1:9";
    /** A dedicated server's default. */
    private static final int SIMULATION_DISTANCE = 10;

    static {
        System.setProperty("mineagent.traceFights", "true"); // fights print what the companion saw and did
    }

    private record Scene(ServerPlayer owner, CompanionEntity companion, ServerPlayer body, AgentLoop loop) {}

    /** A stone floor, an owner (a mock player) at (3, 1, 3), and a companion beside them, with no model. */
    private static Scene scene(GameTestHelper helper, String name) {
        return scene(helper, name, false, null);
    }

    /**
     * With {@code installed}, the companion uses the mod's own settings as a fresh install has them (SMH on this PC,
     * where FakeSmh stands in); {@code owner}: an owner to reuse (null: a new mock player).
     */
    private static Scene scene(GameTestHelper helper, String name, boolean installed, ServerPlayer reuseOwner) {
        // The test server keeps entities ticking only in the chunk a player stands in; a real server does so for 5 to
        // 32 chunks around each player (10 by default). With none, a mob a step across a chunk border froze in place
        // mid-fight. Give the scenes a real server's simulation distance.
        var players = helper.getLevel().getServer().getPlayerList();
        if (players.getSimulationDistance() < SIMULATION_DISTANCE) players.setSimulationDistance(SIMULATION_DISTANCE);
        // And a real server's view distance (10 by default): how far round a player the world is loaded, so a long
        // trip's next leg is on loaded ground as it would be in a real game.
        if (players.getViewDistance() < SIMULATION_DISTANCE) players.setViewDistance(SIMULATION_DISTANCE);
        for (var s : List.copyOf(MineAgentEngine.allCompanions())) {
            MineAgentEngine.despawnCompanion(s.companion.companionId(), true);
        }
        // Scenes build past their own test area, and the areas sit close together: clear what an earlier scene left
        // above this floor (walls, roofs, gravel, lava, and blocks it marked as a player's) before laying it.
        var placed = com.mineagent.engine.world.PlacedBlocks.of(helper.getLevel());
        for (int x = -16; x <= 24; x++) {
            for (int z = -16; z <= 24; z++) {
                for (int y = 12; y >= 1; y--) helper.setBlock(x, y, z, Blocks.AIR);
                helper.setBlock(x, 0, z, Blocks.STONE);
                // Under the floor too: the swimming scene digs a lake down to the bedrock at -3.
                helper.setBlock(x, -1, z, Blocks.DIRT);
                helper.setBlock(x, -2, z, Blocks.DIRT);
                for (int y = 0; y <= 12; y++) placed.clear(helper.absolutePos(new BlockPos(x, y, z)));
            }
        }
        // And what earlier scenes left lying here or walking about (dropped items, cows, zombies): a scene starts clean.
        var area = new net.minecraft.world.phys.AABB(helper.absoluteVec(new Vec3(-16, -3, -16)), helper.absoluteVec(new Vec3(25, 13, 25)));
        for (var e : helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Entity.class, area,
                x -> x instanceof net.minecraft.world.entity.item.ItemEntity || x instanceof net.minecraft.world.entity.Mob
                        || x instanceof net.minecraft.world.entity.ExperienceOrb)) {
            e.discard();
        }
        ServerPlayer owner = reuseOwner != null ? reuseOwner : helper.makeMockServerPlayerInLevel();
        BlockPos at = helper.absolutePos(new BlockPos(3, 1, 3));
        owner.teleportTo(helper.getLevel(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0f, 0.0f);
        var llm = MineAgentEngine.getConfig().llm();
        CompanionEntity companion = installed
                ? MineAgentEngine.spawnCompanion(owner, name, llm.provider(), llm.apiKey(), llm.model(),
                        llm.baseUrl().isEmpty() ? null : llm.baseUrl(), llm.temperature(), null, false)
                : MineAgentEngine.spawnCompanion(owner, name, "openai-compatible", "test",
                        "gametest-" + name.toLowerCase(), NO_MODEL, 0.2, null, false);
        if (companion == null) throw new IllegalStateException("the companion did not spawn");
        AgentLoop loop = TaskContext.agentLoop(companion);
        if (loop == null) throw new IllegalStateException("the companion has no loop");
        return new Scene(owner, companion, companion.serverPlayer(), loop);
    }

    private static void armed(ServerPlayer body) {
        body.getInventory().setItem(0, new ItemStack(Items.IRON_SWORD));
        body.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        body.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        body.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
    }

    /** A setting changed as the owner would change it, by /mineagent setconfig; the scene puts it back after. */
    private static void setting(GameTestHelper helper, String key, String value) {
        var server = helper.getLevel().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "mineagent setconfig " + key + " " + value);
    }

    private static void tidy(Scene s) {
        MineAgentEngine.despawnCompanion(s.companion().companionId(), true);
        s.owner().getServer().getPlayerList().remove(s.owner());
    }

    /**
     * Where a block is in the scene's own coordinates. 1.21.1's {@code GameTestHelper.relativePos} turns x and z
     * around (it gives (-10, 1, -10) for a block set at (10, 1, 10)); our scenes are not rotated, so this is the
     * offset from the scene's origin.
     */
    private static BlockPos rel(GameTestHelper helper, BlockPos absolute) {
        return absolute.subtract(helper.absolutePos(BlockPos.ZERO));
    }

    /** Every 40 ticks, where the companion is and what has its body, into CI's log. */
    private static void trace(GameTestHelper helper, Scene s, String what) {
        if (helper.getTick() % 40 != 0) return;
        var state = MineAgentEngine.getCompanion(s.companion().companionId());
        String control = state.map(c -> c.auction.inControl()).orElse("gone");
        System.out.println("[SCENE " + what + "] tick " + helper.getTick() + ": at "
                + rel(helper, s.body().blockPosition()).toShortString() + ", " + control
                + (s.body().isSleeping() ? ", asleep" : ""));
    }

    /** A scene's failure, also printed, so the reason is in CI's log (the report file is not). */
    private static void fail(GameTestHelper helper, String why) {
        System.out.println("[GAMETEST FAILED] " + why);
        helper.fail(why);
    }

    private static double flat(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Scene 6: a husk comes at it; it fights back and kills it. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_fight", timeoutTicks = 2400)
    public void aHuskThatAttacksItIsKilled(GameTestHelper helper) {
        Scene s = scene(helper, "Fighter");
        armed(s.body());
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(6, 1, 3));
        husk.setTarget(s.body());
        var server = helper.getLevel().getServer();
        int serverStart = server.getTickCount();
        long gameStart = helper.getLevel().getGameTime();
        helper.onEachTick(() -> {
            if (!s.body().isAlive()) {
                tidy(s);
                fail(helper, "the companion died to one husk with an iron sword and a shield");
            } else if (!husk.isAlive()) {
                boolean byIt = husk.getLastHurtByMob() == s.body();
                tidy(s);
                if (byIt) helper.succeed();
                else fail(helper, "the husk died, but not by the companion");
            } else if (helper.getTick() >= 2300) {
                String how = String.format("the husk still lives after 2300 ticks: husk health %.0f, %.1f blocks away, companion health %.0f"
                        + " (%d server ticks and %d of game time went by; day time %d; simulation distance %d; husk's spot"
                        + " entity-ticking %b)",
                        husk.getHealth(), husk.distanceTo(s.body()), s.body().getHealth(),
                        server.getTickCount() - serverStart, helper.getLevel().getGameTime() - gameStart,
                        helper.getLevel().getDayTime(), server.getPlayerList().getSimulationDistance(),
                        helper.getLevel().isPositionEntityTicking(husk.blockPosition()));
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /**
     * Plan 70, MA5.8: it hits from a player's reach (3 blocks, eyes to hitbox), not from 4.5 blocks feet to feet as it
     * used to. A still pig 4 blocks away is out of reach; 2 blocks away it is in reach. (A pig, so its fighting mind
     * leaves it be; the companion is put back in place before each try, so nothing it does in between counts.)
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_reach", timeoutTicks = 100)
    public void itHitsOnlyFromAPlayersReach(GameTestHelper helper) {
        Scene s = scene(helper, "Reach");
        BlockPos me = helper.absolutePos(new BlockPos(3, 1, 8));
        Runnable stand = () -> s.body().teleportTo(helper.getLevel(), me.getX() + 0.5, me.getY(), me.getZ() + 0.5, -90.0f, 0.0f);
        var pig = helper.spawn(EntityType.PIG, new BlockPos(7, 1, 8));
        pig.setNoAi(true);
        helper.runAfterDelay(5, () -> {
            stand.run();
            boolean far = com.mineagent.engine.act.Interaction.attackEntity(s.body(), pig);
            BlockPos near = helper.absolutePos(new BlockPos(5, 1, 8));
            pig.teleportTo(near.getX() + 0.5, near.getY(), near.getZ() + 0.5);
            stand.run();
            boolean close = com.mineagent.engine.act.Interaction.attackEntity(s.body(), pig);
            String where = String.format("companion at %s, pig at %s; reach %.2f, in reach %b, in sight %b, alive %b",
                    s.body().position(), pig.position(), s.body().entityInteractionRange(),
                    com.mineagent.engine.act.Interaction.inEntityReach(s.body(), pig), s.body().hasLineOfSight(pig),
                    pig.isAlive());
            tidy(s);
            if (far) fail(helper, "it hit a pig 4 blocks away (" + where + ")");
            else if (!close) fail(helper, "it could not hit a pig 2 blocks away (" + where + ")");
            else helper.succeed();
        });
    }

    /**
     * Plan 70, MA5.1: a block's break cost is its mining time with the best tool carried plus the hole; nothing built is
     * broken to get somewhere, nor anything that would let lava in or drop a tall stack of gravel.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_breaks", timeoutTicks = 100)
    public void breakCostsAreTheGamesAndSafe(GameTestHelper helper) {
        Scene s = scene(helper, "Digger");
        helper.setBlock(8, 1, 8, Blocks.STONE);
        helper.setBlock(10, 1, 8, Blocks.OAK_PLANKS);
        helper.setBlock(18, 1, 8, Blocks.STONE);
        helper.setBlock(19, 1, 8, Blocks.LAVA); // far from the planks, so nothing catches
        helper.setBlock(8, 1, 14, Blocks.STONE);
        for (int y = 2; y <= 4; y++) helper.setBlock(8, y, 14, Blocks.GRAVEL);
        helper.setBlock(11, 1, 14, Blocks.STONE);
        for (int y = 2; y <= 11; y++) helper.setBlock(11, y, 14, Blocks.GRAVEL);
        helper.runAfterDelay(2, () -> {
            var caches = TaskContext.navCaches(s.companion());
            java.util.function.Function<int[], Double> cost = xyz -> {
                var ctx = new com.mineagent.engine.pathing.moves.CalculationContext(caches.level(), caches.worldView(),
                        caches.loadedChunks()::isChunkLoaded, true, false);
                ctx.withBreakTimer(com.mineagent.engine.pathing.moves.ToolTimer.of(s.body()));
                BlockPos at = helper.absolutePos(new BlockPos(xyz[0], xyz[1], xyz[2]));
                return com.mineagent.engine.pathing.moves.MovementHelper.costOfBreaking(ctx, at.getX(), at.getY(), at.getZ());
            };
            double byHand = cost.apply(new int[] {8, 1, 8});
            s.body().getInventory().setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
            double withPick = cost.apply(new int[] {8, 1, 8});
            double planks = cost.apply(new int[] {10, 1, 8});
            double byLava = cost.apply(new int[] {18, 1, 8});
            double underGravel = cost.apply(new int[] {8, 1, 14});
            double underTall = cost.apply(new int[] {11, 1, 14});
            String all = String.format("hand %.1f, pickaxe %.1f, planks %.1f, by lava %.1f, under 3 gravel %.1f, under 10 gravel %.1f",
                    byHand, withPick, planks, byLava, underGravel, underTall);
            tidy(s);
            if (!(Double.isFinite(byHand) && Double.isFinite(withPick) && withPick < byHand)) fail(helper, "stone: " + all);
            else if (Double.isFinite(planks)) fail(helper, "planks would be broken to get somewhere: " + all);
            else if (Double.isFinite(byLava)) fail(helper, "a block beside lava would be broken: " + all);
            else if (!(Double.isFinite(underGravel) && underGravel > withPick)) fail(helper, "gravel above: " + all);
            else if (Double.isFinite(underTall)) fail(helper, "a block under ten gravel would be broken: " + all);
            else helper.succeed();
        });
    }

    /**
     * A house of planks from (6, 1, 6) to (14, 4, 14) with a roof, its door at (10, 1, 14) facing south, the companion
     * inside at (10, 1, 10). Returns the goal just past the back wall, at (10, 1, 3).
     */
    private static BlockPos house(GameTestHelper helper, Scene s, net.minecraft.world.level.block.Block door) {
        for (int x = 6; x <= 14; x++) {
            for (int z = 6; z <= 14; z++) {
                boolean edge = x == 6 || x == 14 || z == 6 || z == 14;
                for (int y = 1; y <= 3; y++) if (edge) helper.setBlock(x, y, z, Blocks.OAK_PLANKS);
                helper.setBlock(x, 4, z, Blocks.OAK_PLANKS);
            }
        }
        var lower = door.defaultBlockState()
                .setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.SOUTH)
                .setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                        net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER);
        helper.setBlock(new BlockPos(10, 1, 14), lower);
        helper.setBlock(new BlockPos(10, 2, 14), lower.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        BlockPos inside = helper.absolutePos(new BlockPos(10, 1, 10));
        s.body().teleportTo(helper.getLevel(), inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5, 0.0f, 0.0f);
        System.out.println("[SCENE house] put inside at " + rel(helper, s.body().blockPosition()).toShortString()
                + "; wall at (10, 1, 6) is " + helper.getBlockState(new BlockPos(10, 1, 6)).getBlock());
        return helper.absolutePos(new BlockPos(10, 1, 3));
    }

    /** A stone button on the wall beside the door, at (9, 2, 13) inside or (9, 2, 15) outside. */
    private static void button(GameTestHelper helper, boolean inside) {
        helper.setBlock(new BlockPos(9, 2, inside ? 13 : 15), Blocks.STONE_BUTTON.defaultBlockState()
                .setValue(net.minecraft.world.level.block.ButtonBlock.FACE,
                        net.minecraft.world.level.block.state.properties.AttachFace.WALL)
                .setValue(net.minecraft.world.level.block.ButtonBlock.FACING,
                        inside ? net.minecraft.core.Direction.NORTH : net.minecraft.core.Direction.SOUTH));
    }

    private static void sendTo(GameTestHelper helper, Scene s, BlockPos goal, String id) {
        helper.runAfterDelay(5, () -> {
            var args = new com.google.gson.JsonObject();
            args.addProperty("goal_mode", "xzy");
            args.addProperty("x", goal.getX());
            args.addProperty("y", goal.getY());
            args.addProperty("z", goal.getZ());
            new com.mineagent.tools.movement.MoveToTool().onServerCall(id, args, s.companion(), reply -> {});
        });
    }

    private static int brokenWall(GameTestHelper helper) {
        int broken = 0;
        for (int x = 6; x <= 14; x++) {
            for (int z = 6; z <= 14; z++) {
                boolean edge = x == 6 || x == 14 || z == 6 || z == 14;
                if (!edge || (x == 10 && z == 14)) continue;
                for (int y = 1; y <= 3; y++) if (!helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.OAK_PLANKS)) broken++;
            }
        }
        return broken;
    }

    /** Whether its feet are in the doorway or the step just outside it. */
    private static boolean atDoor(GameTestHelper helper, Scene s) {
        BlockPos rel = rel(helper, s.body().blockPosition());
        return rel.getX() == 10 && (rel.getZ() == 14 || rel.getZ() == 15);
    }

    private static boolean doorOpen(GameTestHelper helper) {
        var state = helper.getBlockState(new BlockPos(10, 1, 14));
        return state.hasProperty(net.minecraft.world.level.block.DoorBlock.OPEN)
                && state.getValue(net.minecraft.world.level.block.DoorBlock.OPEN);
    }

    /**
     * Plan 70, MA5.1 and MA5.3: shut in a house of planks with its door on the far side, sent just past the back wall,
     * it goes out through the door and around, and shuts the door behind it. It used to dig two blocks through the wall.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_house", timeoutTicks = 1600)
    public void itUsesTheDoorNotTheWall(GameTestHelper helper) {
        Scene s = scene(helper, "Housemate");
        BlockPos goal = house(helper, s, Blocks.OAK_DOOR);
        sendTo(helper, s, goal, "house-1");
        boolean[] door = {false};
        helper.onEachTick(() -> {
            trace(helper, s, "house");
            if (atDoor(helper, s)) door[0] = true;
            int broken = brokenWall(helper);
            double off = flat(s.body().position(), Vec3.atBottomCenterOf(goal));
            if (broken > 0) {
                tidy(s);
                fail(helper, broken + " planks of the house were broken");
            } else if (off <= 1.5 && !doorOpen(helper)) {
                tidy(s);
                if (door[0]) helper.succeed();
                else fail(helper, "it got there without going through the door");
            } else if (helper.getTick() >= 1500) {
                String how = String.format("after 1500 ticks: %.1f blocks off, at %s, door %s", off,
                        s.body().blockPosition().toShortString(), doorOpen(helper) ? "left open" : "shut");
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /** Plan 70, MA5.3: an iron door with a button inside: it presses the button and goes out, the wall left whole. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_iron_door", timeoutTicks = 1600)
    public void itPressesTheButtonOfAnIronDoor(GameTestHelper helper) {
        Scene s = scene(helper, "Doorman");
        BlockPos goal = house(helper, s, Blocks.IRON_DOOR);
        button(helper, true);
        button(helper, false);
        sendTo(helper, s, goal, "iron-1");
        boolean[] door = {false};
        helper.onEachTick(() -> {
            if (atDoor(helper, s)) door[0] = true;
            int broken = brokenWall(helper);
            double off = flat(s.body().position(), Vec3.atBottomCenterOf(goal));
            if (broken > 0 || !helper.getBlockState(new BlockPos(10, 1, 14)).is(Blocks.IRON_DOOR)) {
                tidy(s);
                fail(helper, "the house or its iron door was broken");
            } else if (off <= 1.5) {
                tidy(s);
                if (door[0]) helper.succeed();
                else fail(helper, "it got there without going through the iron door");
            } else if (helper.getTick() >= 1500) {
                String how = String.format("after 1500 ticks: %.1f blocks off, at %s", off, s.body().blockPosition().toShortString());
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /**
     * Plan 70, MA5.3: an iron door with nothing to open it is a wall: it stays inside and breaks nothing. The floor is
     * one its owner laid (as a house's floor is), so digging under the wall is out too; natural ground under a wall
     * may be dug, as a player would.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_iron_wall", timeoutTicks = 700)
    public void anIronDoorWithNoButtonIsAWall(GameTestHelper helper) {
        Scene s = scene(helper, "Shutin");
        BlockPos goal = house(helper, s, Blocks.IRON_DOOR);
        var placed = com.mineagent.engine.world.PlacedBlocks.of(helper.getLevel());
        for (int x = 6; x <= 14; x++) {
            for (int z = 6; z <= 14; z++) placed.mark(helper.absolutePos(new BlockPos(x, 0, z)));
        }
        int[] floor = {0};
        sendTo(helper, s, goal, "iron-2");
        helper.onEachTick(() -> {
            trace(helper, s, "iron wall");
            floor[0] = 0;
            for (int x = 7; x <= 13; x++) {
                for (int z = 7; z <= 13; z++) if (!helper.getBlockState(new BlockPos(x, 0, z)).is(Blocks.STONE)) floor[0]++;
            }
            if (brokenWall(helper) > 0 || !helper.getBlockState(new BlockPos(10, 1, 14)).is(Blocks.IRON_DOOR)) {
                tidy(s);
                fail(helper, "it broke out of the house");
            } else if (floor[0] > 0) {
                tidy(s);
                fail(helper, "it dug " + floor[0] + " blocks of the floor its owner laid");
            } else if (helper.getTick() >= 600) {
                BlockPos at = s.body().blockPosition();
                BlockPos rel = rel(helper, at);
                boolean inside = rel.getX() > 6 && rel.getX() < 14 && rel.getZ() > 6 && rel.getZ() < 14;
                tidy(s);
                if (inside) helper.succeed();
                else fail(helper, "it got out of a house it could not open: at " + rel.toShortString());
            }
        });
    }

    /** A bed from (x, 1, z) to (x + 1, 1, z), head to the east. */
    private static void bed(GameTestHelper helper, int x, int z) {
        var foot = Blocks.RED_BED.defaultBlockState()
                .setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.EAST)
                .setValue(net.minecraft.world.level.block.BedBlock.PART,
                        net.minecraft.world.level.block.state.properties.BedPart.FOOT);
        helper.setBlock(new BlockPos(x, 1, z), foot);
        helper.setBlock(new BlockPos(x + 1, 1, z), foot.setValue(net.minecraft.world.level.block.BedBlock.PART,
                net.minecraft.world.level.block.state.properties.BedPart.HEAD));
    }

    /** Only the owner and its companion in the world: any player left awake keeps the night from passing. */
    private static void onlyUs(GameTestHelper helper, Scene s) {
        for (var p : List.copyOf(helper.getLevel().players())) {
            if (p != s.owner() && p != s.body() && !MineAgentEngine.isCompanionPlayer(p.getUUID())) {
                p.getServer().getPlayerList().remove(p);
            }
        }
    }

    /**
     * Plan 70, MA5.5: the night passes only when everyone in the Overworld sleeps, the companion too. The owner lies
     * down at night; the companion goes to the free bed near by and sleeps; the game passes the night.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_sleep", timeoutTicks = 1400)
    public void theNightPassesWhenItSleepsToo(GameTestHelper helper) {
        Scene s = scene(helper, "Sleeper");
        onlyUs(helper, s);
        bed(helper, 8, 3);
        bed(helper, 8, 7);
        var level = helper.getLevel();
        long dayStart = level.getDayTime() / 24000L * 24000L;
        level.setDayTime(dayStart + 14000L);
        BlockPos byBed = helper.absolutePos(new BlockPos(7, 1, 3));
        s.owner().teleportTo(level, byBed.getX() + 0.5, byBed.getY(), byBed.getZ() + 0.5, 0.0f, 0.0f);
        boolean[] sawItAsleep = {false};
        helper.runAfterDelay(5, () -> {
            var tried = s.owner().startSleepInBed(helper.absolutePos(new BlockPos(8, 1, 3)));
            if (tried.left().isPresent()) {
                tidy(s);
                fail(helper, "the owner could not lie down: " + tried.left().get());
            }
        });
        helper.onEachTick(() -> {
            // A test's mock player has no client connection to tick it, so its time in bed never counts: tick it as
            // the connection would (the companion is ticked through its own).
            s.owner().doTick();
            trace(helper, s, "sleep");
            if (s.body().isSleeping()) sawItAsleep[0] = true;
            if (level.getDayTime() >= dayStart + 24000L) {
                tidy(s);
                if (sawItAsleep[0]) helper.succeed();
                else fail(helper, "the night passed without the companion asleep");
            } else if (helper.getTick() >= 1300) {
                String how = String.format("still night after 1300 ticks: owner asleep %b, companion asleep %b, companion at %s",
                        s.owner().isSleeping(), s.body().isSleeping(), rel(helper, s.body().blockPosition()).toShortString());
                level.setDayTime(dayStart + 25000L);
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /** Plan 70, MA5.5: with no bed for the companion, the owner asleep alone does not pass the night. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_sleepless", timeoutTicks = 600)
    public void withoutItAsleepTheNightWaits(GameTestHelper helper) {
        Scene s = scene(helper, "Sleepless");
        onlyUs(helper, s);
        bed(helper, 8, 3);
        var level = helper.getLevel();
        long dayStart = level.getDayTime() / 24000L * 24000L;
        level.setDayTime(dayStart + 14000L);
        BlockPos byBed = helper.absolutePos(new BlockPos(7, 1, 3));
        s.owner().teleportTo(level, byBed.getX() + 0.5, byBed.getY(), byBed.getZ() + 0.5, 0.0f, 0.0f);
        helper.runAfterDelay(5, () -> s.owner().startSleepInBed(helper.absolutePos(new BlockPos(8, 1, 3))));
        helper.onEachTick(() -> {
            s.owner().doTick(); // as in the scene above: the mock player's time in bed counts only when it is ticked
            if (level.getDayTime() >= dayStart + 24000L) {
                tidy(s);
                fail(helper, "the night passed with the companion awake");
            } else if (helper.getTick() >= 500) {
                boolean ownerAsleep = s.owner().isSleeping() && s.owner().isSleepingLongEnough();
                level.setDayTime(dayStart + 25000L);
                tidy(s);
                if (ownerAsleep) helper.succeed();
                else fail(helper, "the owner never slept long enough to pass a night, so this shows nothing");
            }
        });
    }

    /**
     * Plan 70, MA5.4: the owner says "afk" out in the open: its duty becomes guarding them, as on "guard me". "Back"
     * ends it, as their next input would. (Their idle timer counts too; three minutes is too long for a scene.)
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_afk", timeoutTicks = 200)
    public void anAfkOwnerInTheOpenIsGuarded(GameTestHelper helper) {
        Scene s = scene(helper, "Watcher");
        // Guarding an AFK owner is optional (off by default): the owner turns it on.
        setting(helper, "behaviour.afkGuardMe", "on");
        if (!com.mineagent.engine.settings.Switches.afkGuard()) {
            tidy(s);
            fail(helper, "/mineagent setconfig behaviour.afkGuardMe on did not take");
            return;
        }
        Runnable done = () -> {
            setting(helper, "behaviour.afkGuardMe", "off");
            tidy(s);
        };
        helper.runAfterDelay(5, () -> MineAgentEngine.onPlayerChat(s.owner(), "afk"));
        boolean[] guarded = {false};
        helper.onEachTick(() -> {
            var setup = s.loop().combatSetup(s.body(), s.owner());
            boolean guarding = setup.duty() == com.mineagent.engine.combat.CombatBrain.Duty.GUARD
                    && s.owner().getUUID().equals(setup.ward());
            if (!guarded[0] && guarding && s.loop().ownerAfk()) {
                guarded[0] = true;
                MineAgentEngine.onPlayerChat(s.owner(), "back");
            } else if (guarded[0] && !s.loop().ownerAfk()) {
                boolean stoodDown = s.loop().combatSetup(s.body(), s.owner()).duty()
                        != com.mineagent.engine.combat.CombatBrain.Duty.GUARD;
                done.run();
                if (stoodDown) helper.succeed();
                else fail(helper, "the owner is back, but it still guards them");
            } else if (helper.getTick() >= 150) {
                String how = "after 150 ticks: afk " + s.loop().ownerAfk() + ", duty " + setup.duty();
                done.run();
                fail(helper, guarded[0] ? "it never saw the owner come back: " + how : "it never guarded the AFK owner: " + how);
            }
        });
    }

    /**
     * Plan 70, MA5.2: a stone block the owner places is remembered and never broken to get somewhere; stone the world
     * has is still the land; breaking the owner's block strikes it off.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_placed", timeoutTicks = 100)
    public void blocksAPlayerPlacedAreLeftStanding(GameTestHelper helper) {
        Scene s = scene(helper, "Mason");
        var level = helper.getLevel();
        s.owner().setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 4));
        BlockPos floor = helper.absolutePos(new BlockPos(6, 0, 6));
        var hit = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0),
                net.minecraft.core.Direction.UP, floor, false);
        s.owner().gameMode.useItemOn(s.owner(), level, s.owner().getMainHandItem(),
                net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        helper.setBlock(9, 1, 6, Blocks.STONE);
        BlockPos theirs = helper.absolutePos(new BlockPos(6, 1, 6));
        BlockPos nature = helper.absolutePos(new BlockPos(9, 1, 6));
        helper.runAfterDelay(2, () -> {
            var record = com.mineagent.engine.world.PlacedBlocks.of(level);
            var caches = TaskContext.navCaches(s.companion());
            var ctx = new com.mineagent.engine.pathing.moves.CalculationContext(caches.level(), caches.worldView(),
                    caches.loadedChunks()::isChunkLoaded, true, false);
            boolean placedIt = level.getBlockState(theirs).is(Blocks.STONE);
            boolean remembered = record.isPlaced(theirs.getX(), theirs.getY(), theirs.getZ());
            boolean natureRemembered = record.isPlaced(nature.getX(), nature.getY(), nature.getZ());
            double theirsCost = com.mineagent.engine.pathing.moves.MovementHelper.costOfBreaking(ctx, theirs.getX(), theirs.getY(), theirs.getZ());
            double natureCost = com.mineagent.engine.pathing.moves.MovementHelper.costOfBreaking(ctx, nature.getX(), nature.getY(), nature.getZ());
            level.destroyBlock(theirs, false);
            boolean forgotten = !record.isPlaced(theirs.getX(), theirs.getY(), theirs.getZ());
            String all = String.format("placed %b, remembered %b, nature remembered %b, costs %.1f and %.1f, forgotten after breaking %b",
                    placedIt, remembered, natureRemembered, theirsCost, natureCost, forgotten);
            tidy(s);
            if (placedIt && remembered && !natureRemembered && Double.isInfinite(theirsCost) && Double.isFinite(natureCost) && forgotten) {
                helper.succeed();
            } else {
                fail(helper, all);
            }
        });
    }

    /**
     * Plan 70, MA5.9: the owner leaves and comes back. The companion comes back once, where it was, with what it had:
     * its items, its hunger, an effect on it, its bed's spawn point, and its guard post.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_return", timeoutTicks = 200)
    public void aLeaveAndReturnKeepsWhatItHad(GameTestHelper helper) {
        Scene s = scene(helper, "Keeper2");
        var body = s.body();
        body.getInventory().setItem(3, new ItemStack(Items.IRON_INGOT, 17));
        body.getFoodData().setFoodLevel(13);
        body.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 6000, 1));
        BlockPos spawn = helper.absolutePos(new BlockPos(12, 1, 12));
        body.setRespawnPosition(helper.getLevel().dimension(), spawn, 0.0f, true, false);
        BlockPos post = helper.absolutePos(new BlockPos(5, 1, 5));
        s.loop().setGuard(Vec3.atBottomCenterOf(post), null, 9.0);
        helper.runAfterDelay(5, () -> {
            MineAgentEngine.onPlayerLeave(s.owner());
            boolean gone = MineAgentEngine.getCompanionsByOwner(s.owner().getUUID()).isEmpty();
            MineAgentEngine.onPlayerJoin(s.owner());
            MineAgentEngine.onPlayerJoin(s.owner()); // a second join in the same moment must not make a second one
            var back = MineAgentEngine.getCompanionsByOwner(s.owner().getUUID());
            if (back.size() != 1) {
                for (var c : back) MineAgentEngine.despawnCompanion(c.companion.companionId(), true);
                s.owner().getServer().getPlayerList().remove(s.owner());
                fail(helper, "after leaving and coming back there are " + back.size() + " companions (gone at first: " + gone + ")");
                return;
            }
            var again = back.get(0).companion.serverPlayer();
            var loop = TaskContext.agentLoop(back.get(0).companion);
            int iron = again.getInventory().countItem(Items.IRON_INGOT);
            int food = again.getFoodData().getFoodLevel();
            boolean fast = again.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED);
            boolean spawnKept = spawn.equals(again.getRespawnPosition());
            boolean guarding = loop != null && loop.guarding() && loop.guardRadiusBlocks() == 9;
            String all = String.format("gone at first %b, iron %d, food %d, speed %b, spawn kept %b, guarding %b",
                    gone, iron, food, fast, spawnKept, guarding);
            MineAgentEngine.despawnCompanion(back.get(0).companion.companionId(), true);
            s.owner().getServer().getPlayerList().remove(s.owner());
            if (gone && iron == 17 && food == 13 && fast && spawnKept && guarding) helper.succeed();
            else fail(helper, all);
        });
    }

    /** On guard, a husk 14 blocks away, outside a 6-block perimeter and not moving: it stays at its post. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_guard_far", timeoutTicks = 200)
    public void onGuardItDoesNotRunOffAfterAFarMob(GameTestHelper helper) {
        Scene s = scene(helper, "Sentry");
        armed(s.body());
        if (!s.loop().ownerCommand(new CommandParser.Command(CommandParser.Kind.GUARD_HERE, null, 6))) {
            tidy(s);
            fail(helper, "guard here was refused");
            return;
        }
        Vec3 post = s.owner().position();
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(17, 1, 3));
        husk.setNoAi(true);
        husk.setTarget(s.body());
        helper.onEachTick(() -> {
            double away = flat(s.body().position(), post);
            if (away > 3.5) {
                tidy(s);
                fail(helper, "it left its post for a mob outside the perimeter: " + String.format("%.1f", away) + " blocks out");
            } else if (helper.getTick() >= 120) {
                tidy(s);
                helper.succeed();
            }
        });
    }

    /** On guard, a husk inside the perimeter is fought and killed, and it never leaves the perimeter to do it. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_guard_near", timeoutTicks = 2400)
    public void onGuardItClearsItsPerimeterWithoutLeavingIt(GameTestHelper helper) {
        Scene s = scene(helper, "Warden");
        armed(s.body());
        if (!s.loop().ownerCommand(new CommandParser.Command(CommandParser.Kind.GUARD_HERE, null, 8))) {
            tidy(s);
            fail(helper, "guard here was refused");
            return;
        }
        Vec3 post = s.owner().position();
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(8, 1, 3));
        husk.setTarget(s.body());
        helper.onEachTick(() -> {
            trace(helper, s, "guard");
            if (helper.getTick() % 40 == 0) {
                System.out.println("[SCENE guard] husk at " + rel(helper, husk.blockPosition()).toShortString() + ", health "
                        + Math.round(husk.getHealth()) + ", " + String.format("%.1f", husk.distanceTo(s.body()))
                        + " away; companion blocking " + s.body().isBlocking() + ", in reach "
                        + com.mineagent.engine.act.Interaction.inEntityReach(s.body(), husk) + ", husk's target "
                        + (husk.getTarget() == null ? "none" : husk.getTarget().getName().getString()));
            }
            double away = flat(s.body().position(), post);
            if (away > 8 + 3.5) {
                tidy(s);
                fail(helper, "it went " + String.format("%.1f", away) + " blocks from its post, past its 8-block perimeter");
            } else if (!husk.isAlive()) {
                tidy(s);
                helper.succeed();
            } else if (helper.getTick() >= 2300) {
                String how = String.format("the husk still lives after 2300 ticks: husk health %.0f, %.1f blocks from the companion, "
                        + "the companion %.1f from its post, health %.0f", husk.getHealth(), husk.distanceTo(s.body()), away, s.body().getHealth());
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /** Scene 7: it dies carrying things; a goal to go back for them is put ahead of everything else. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_death", timeoutTicks = 200)
    public void aDeathLeavesAGoalToRecoverItsThings(GameTestHelper helper) {
        Scene s = scene(helper, "Unlucky");
        s.loop().addGoal("build a shelter", "owner");
        s.body().getInventory().setItem(3, new ItemStack(Items.DIRT, 16));
        s.body().kill();
        helper.onEachTick(() -> {
            var active = s.loop().goalBook().active();
            if (active.isPresent() && active.get().text().startsWith(DeathNote.RECOVER_PREFIX)) {
                boolean queued = s.loop().goalBook().list().stream()
                        .anyMatch(g -> g.text().equals("build a shelter") && g.status() == GoalBook.Status.PAUSED);
                tidy(s);
                if (queued) helper.succeed();
                else fail(helper, "the goal it was on was not kept behind the recovery goal");
            } else if (helper.getTick() >= 150) {
                tidy(s);
                fail(helper, "no recovery goal after the death; active: " + active.map(GoalBook.Goal::text).orElse("none"));
            }
        });
    }

    private static int dirt(ServerPlayer body) {
        int n = 0;
        var inv = body.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(Items.DIRT)) n += inv.getItem(i).getCount();
        return n;
    }

    /** A second player beside the companion, who says something to it through the game's chat path. */
    private static ServerPlayer neighbour(GameTestHelper helper, Scene s) {
        ServerPlayer p = helper.makeMockServerPlayerInLevel();
        Vec3 at = s.body().position();
        p.teleportTo(helper.getLevel(), at.x + 2, at.y, at.z + 1, 0.0f, 0.0f);
        return p;
    }

    /** Scene 2: a stranger gives an order (one the owner's words would run at once), and nothing happens. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_stranger", timeoutTicks = 200)
    public void aStrangersOrderDoesNothing(GameTestHelper helper) {
        Scene s = scene(helper, "Loyal");
        s.body().getInventory().setItem(3, new ItemStack(Items.DIRT, 16));
        ServerPlayer stranger = neighbour(helper, s);
        MineAgentEngine.onPlayerChat(stranger, "Loyal, drop 16 dirt");
        MineAgentEngine.onPlayerChat(stranger, "Loyal, drop all your stuff");
        helper.onEachTick(() -> {
            if (dirt(s.body()) != 16) {
                stranger.getServer().getPlayerList().remove(stranger);
                tidy(s);
                fail(helper, "a stranger's order moved its things: " + dirt(s.body()) + " dirt left of 16");
            } else if (helper.getTick() >= 100) {
                stranger.getServer().getPlayerList().remove(stranger);
                tidy(s);
                helper.succeed();
            }
        });
    }

    /** Scene 3: a trusted player gives one, and it runs (the owner's plain commands, without a model). */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_trusted", timeoutTicks = 300)
    public void aTrustedPlayersOrderRuns(GameTestHelper helper) {
        Scene s = scene(helper, "Helper");
        s.body().getInventory().setItem(3, new ItemStack(Items.DIRT, 16));
        ServerPlayer friend = neighbour(helper, s);
        s.loop().setAgentSettings(s.loop().getAgentSettings().withTrusted(friend.getUUID()));
        MineAgentEngine.onPlayerChat(friend, "Helper, drop 4 dirt");
        helper.onEachTick(() -> {
            int left = dirt(s.body());
            if (left == 12) {
                friend.getServer().getPlayerList().remove(friend);
                tidy(s);
                helper.succeed();
            } else if (left < 12 || helper.getTick() >= 250) {
                friend.getServer().getPlayerList().remove(friend);
                tidy(s);
                fail(helper, "a trusted player's \"drop 4 dirt\" left " + left + " of 16");
            }
        });
    }

    /** Real time, for scenes that wait on a model call (the test server runs ticks as fast as it can). */
    private static long now() {
        return System.currentTimeMillis();
    }

    /**
     * A fresh install (the mod's own settings) reaches SMH on this PC with no key; a new long goal's call carries the
     * goal, what the game counts as short, and the situation brief (MA3.1, MA4.2).
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_installed", timeoutTicks = 1_000_000)
    public void aFreshInstallReachesSmhAndAGoalCarriesItsBrief(GameTestHelper helper) {
        String fake = FakeSmh.start();
        if (fake != null) {
            fail(helper, fake);
            return;
        }
        var llm = MineAgentEngine.getConfig().llm();
        if (!"http://127.0.0.1:47821/apps".equals(llm.baseUrl()) || !"smh/auto".equals(llm.model()) || !llm.apiKey().isEmpty()) {
            fail(helper, "a fresh install is not set for SMH on this PC: " + llm.provider() + " " + llm.baseUrl() + " " + llm.model());
            return;
        }
        Scene s = scene(helper, "Rookie", true, null);
        if (!"MineAgent".equals(MineAgentEngine.freeCompanionName(s.owner().getUUID(), null, "smh/auto"))) {
            tidy(s);
            fail(helper, "an SMH companion's default name is not MineAgent");
            return;
        }
        s.loop().addGoal("get 3 diamonds", "owner");
        long deadline = now() + 30_000L;
        helper.onEachTick(() -> {
            var call = FakeSmh.CALLS.stream().filter(c -> c.text().contains("Long goal") && c.text().contains("get 3 diamonds")).findFirst();
            if (call.isPresent()) {
                var c = call.get();
                String missing = c.authorization() != null ? "it sent a key"
                        : !c.path().equals("/apps/v1/chat/completions") ? "it called " + c.path()
                        : !c.model().startsWith("smh/") ? "its model was " + c.model()
                        : !c.text().contains("[SITUATION]") ? "no situation brief"
                        : !c.text().contains("Still short (counted): 3 diamond") ? "no counted shortfall"
                        : !c.hasTools() ? "the goal's call had no tools to act with" : null;
                tidy(s);
                if (missing == null) helper.succeed();
                else fail(helper, "the goal reached SMH, but " + missing);
            } else if (now() > deadline) {
                tidy(s);
                fail(helper, "no call with the goal reached SMH on this PC in 30 s (" + FakeSmh.CALLS.size() + " calls)");
            }
        });
    }

    /** Scene 1: a stranger talks and gets a reply, through a call that cannot act; their words never reach one that can. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_talk", timeoutTicks = 1_000_000)
    public void aStrangerTalksAndGetsAReplyButNoTask(GameTestHelper helper) {
        String fake = FakeSmh.start();
        if (fake != null) {
            fail(helper, fake);
            return;
        }
        Scene s = scene(helper, "Chatty", true, null);
        ServerPlayer stranger = neighbour(helper, s);
        MineAgentEngine.onPlayerChat(stranger, "Chatty, lovely weather for mining today");
        long[] heardAt = {0L};
        long deadline = now() + 30_000L;
        helper.onEachTick(() -> {
            var talk = FakeSmh.CALLS.stream().filter(c -> c.text().contains("lovely weather")).toList();
            boolean acted = talk.stream().anyMatch(FakeSmh.Call::hasTools);
            if (acted) {
                stranger.getServer().getPlayerList().remove(stranger);
                tidy(s);
                fail(helper, "a stranger's words reached a call that can act");
                return;
            }
            boolean fenced = talk.stream().anyMatch(c -> c.text().contains("UNTRUSTED PLAYER MESSAGE"));
            if (fenced && heardAt[0] == 0L) heardAt[0] = now();
            // Heard, fenced, with no tools; two seconds more to see that nothing else carries their words on.
            if (heardAt[0] != 0L && now() - heardAt[0] > 2_000L) {
                stranger.getServer().getPlayerList().remove(stranger);
                tidy(s);
                helper.succeed();
            } else if (now() > deadline) {
                stranger.getServer().getPlayerList().remove(stranger);
                tidy(s);
                fail(helper, "the stranger got no reply call in 30 s");
            }
        });
    }

    /** Scene 8: a skill it learned is still there in a new world (the permanent library outlives the world's memory). */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_library", timeoutTicks = 200)
    public void aSkillIsCarriedIntoANewWorld(GameTestHelper helper) {
        Scene s = scene(helper, "Keeper");
        String steps = "[{\"tool\":\"craft\",\"args\":{\"item\":\"minecraft:crafting_table\",\"count\":1}}]";
        s.loop().skillLibrary().registerSequence("make_a_crafting_table", "make a crafting table", "make a crafting table", steps, true);
        if (s.loop().skillLibrary().get("make_a_crafting_table").isEmpty()) {
            tidy(s);
            fail(helper, "the skill was not taken");
            return;
        }
        s.loop().saveMemoryBlocking();
        java.nio.file.Path world = s.loop().memoryFolder();
        java.nio.file.Path library = s.loop().libraryFolder();
        ServerPlayer owner = s.owner();
        MineAgentEngine.despawnCompanion(s.companion().companionId(), false);
        // A new world: this one's memory of the companion is gone; the library is not the world's.
        try {
            if (world != null && java.nio.file.Files.exists(world)) {
                try (var walk = java.nio.file.Files.walk(world)) {
                    walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
                }
            }
        } catch (java.io.IOException e) {
            fail(helper, "could not clear the world's memory: " + e.getMessage());
            return;
        }
        if (library == null || !java.nio.file.Files.exists(library.resolve("skills.json"))) {
            fail(helper, "the skill was not written to the permanent library (" + library + ")");
            return;
        }
        Scene again = scene(helper, "Keeper", false, owner);
        boolean kept = again.loop().skillLibrary().get("make_a_crafting_table").isPresent();
        tidy(again);
        if (kept) helper.succeed();
        else fail(helper, "the skill did not come back in the new world");
    }

    /** "get 3 diamonds" becomes a goal whose amount is counted, the item checked against the game's registry. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_goal", timeoutTicks = 100)
    public void aGoalsAmountsAreReadAgainstTheGame(GameTestHelper helper) {
        Scene s = scene(helper, "Planner");
        s.loop().addGoal("get 3 diamonds and 2 unobtainium", "owner");
        var active = s.loop().goalBook().active();
        tidy(s);
        if (active.isEmpty()) {
            fail(helper, "the goal was not added");
        } else if (!active.get().needs().equals(List.of(new GoalBook.Need("diamond", 3)))) {
            fail(helper, "needs read as " + active.get().needs() + ", expected 3 diamond only");
        } else {
            helper.succeed();
        }
    }

    /** A corridor two blocks high through solid stone: in at (0, 8), east to x 6, north to z 2, east to x 12, south to z 12. */
    private static boolean corridor(int x, int z) {
        return (z == 8 && x >= 0 && x <= 6) || (x == 6 && z >= 2 && z <= 8) || (z == 2 && x >= 6 && x <= 12)
                || (x == 12 && z >= 2 && z <= 12);
    }

    /**
     * Plan 70, MA5.6: it walks into a winding cave, and "lead me out" takes it back out the way it came, by its trail,
     * breaking nothing.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_cave", timeoutTicks = 1600)
    public void itLeadsTheWayOutOfACaveByItsTrail(GameTestHelper helper) {
        Scene s = scene(helper, "Caver");
        var level = helper.getLevel();
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        BlockPos away = helper.absolutePos(new BlockPos(-12, 1, -12));
        s.owner().teleportTo(level, away.getX() + 0.5, away.getY(), away.getZ() + 0.5, 0.0f, 0.0f);
        BlockPos outside = helper.absolutePos(new BlockPos(-3, 1, 8));
        s.body().teleportTo(level, outside.getX() + 0.5, outside.getY(), outside.getZ() + 0.5, 0.0f, 0.0f);
        for (int x = 0; x <= 14; x++) {
            for (int z = 0; z <= 14; z++) {
                for (int y = 1; y <= 5; y++) helper.setBlock(x, y, z, y <= 2 && corridor(x, z) ? Blocks.AIR : Blocks.STONE);
            }
        }
        BlockPos end = helper.absolutePos(new BlockPos(12, 1, 12));
        sendTo(helper, s, end, "cave-in");
        int[] phase = {0};
        helper.onEachTick(() -> {
            trace(helper, s, "cave");
            int broken = 0;
            for (int x = 0; x <= 14; x++) {
                for (int z = 0; z <= 14; z++) {
                    for (int y = 1; y <= 5; y++) {
                        if (!(y <= 2 && corridor(x, z)) && !helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.STONE)) broken++;
                    }
                }
            }
            BlockPos at = rel(helper, s.body().blockPosition());
            boolean open = !com.mineagent.engine.world.PlaceKeeper.underground(level, s.body().blockPosition());
            if (broken > 0) {
                tidy(s);
                fail(helper, "it broke " + broken + " blocks of the cave");
            } else if (phase[0] == 0 && helper.getTick() > 10 && flat(s.body().position(), Vec3.atBottomCenterOf(end)) <= 1.5) {
                phase[0] = 1;
                MineAgentEngine.onPlayerChat(s.owner(), "lead me out");
            } else if (phase[0] == 1 && open && at.getX() < 0) {
                tidy(s);
                helper.succeed();
            } else if (helper.getTick() >= 1500) {
                String how = (phase[0] == 0 ? "it never got to the end of the cave" : "it never got back out")
                        + ": at " + at.toShortString() + ", " + (open ? "in the open" : "underground")
                        + ", trail of " + s.loop().placeKeeper().memory().wayBack().size() + " points";
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /**
     * Plan 70, MA5.6: the owner says "this is home"; sent far off, "go home" brings it back there, and home is in the
     * memory it saves.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_home", timeoutTicks = 1400)
    public void goHomeTakesItBackToTheHomeItWasShown(GameTestHelper helper) {
        Scene s = scene(helper, "Homebody");
        var level = helper.getLevel();
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        BlockPos home = helper.absolutePos(new BlockPos(-12, 1, -12));
        s.owner().teleportTo(level, home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0.0f, 0.0f);
        helper.runAfterDelay(2, () -> MineAgentEngine.onPlayerChat(s.owner(), "this is home"));
        BlockPos far = helper.absolutePos(new BlockPos(20, 1, 20));
        sendTo(helper, s, far, "home-out");
        int[] phase = {0};
        helper.onEachTick(() -> {
            trace(helper, s, "home");
            var memory = s.loop().placeKeeper().memory();
            var known = memory.find("home", null);
            if (phase[0] == 0 && helper.getTick() > 10 && flat(s.body().position(), Vec3.atBottomCenterOf(far)) <= 1.5) {
                phase[0] = 1;
                if (known.isEmpty() || known.get().at().x() != home.getX() || known.get().at().z() != home.getZ()) {
                    String what = known.map(p -> p.at().toString()).orElse("no home");
                    tidy(s);
                    fail(helper, "\"this is home\" did not keep where the owner stood: " + what);
                    return;
                }
                MineAgentEngine.onPlayerChat(s.owner(), "go home");
            } else if (phase[0] == 1 && flat(s.body().position(), Vec3.atBottomCenterOf(home)) <= 2.5) {
                s.loop().saveMemoryBlocking();
                var folder = s.loop().memoryFolder();
                String saved = "";
                if (folder != null) {
                    saved = com.mineagent.engine.memory.store.StoreFile.read(folder.resolve("store/routes/places.gz"))
                            .contents().map(com.mineagent.engine.memory.store.StoreFile.Contents::body).orElse("");
                }
                tidy(s);
                if (saved.contains("\"home\"")) helper.succeed();
                else fail(helper, "home was not in the saved memory store (folder " + folder + ")");
            } else if (helper.getTick() >= 1300) {
                String how = "after 1300 ticks, phase " + phase[0] + ": at " + rel(helper, s.body().blockPosition()).toShortString()
                        + ", home " + known.map(p -> p.at().toString()).orElse("not known");
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /** A straight walk of 36 blocks across the arena, the owner out of the way; whether it sprinted on the way. */
    private static void longWalk(GameTestHelper helper, String name, int food, boolean wantSprint) {
        Scene s = scene(helper, name);
        var level = helper.getLevel();
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        BlockPos aside = helper.absolutePos(new BlockPos(3, 1, -12));
        s.owner().teleportTo(level, aside.getX() + 0.5, aside.getY(), aside.getZ() + 0.5, 0.0f, 0.0f);
        BlockPos start = helper.absolutePos(new BlockPos(-14, 1, 4));
        s.body().teleportTo(level, start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 0.0f, 0.0f);
        s.body().getFoodData().setFoodLevel(food);
        BlockPos goal = helper.absolutePos(new BlockPos(22, 1, 4));
        sendTo(helper, s, goal, "walk-" + name);
        int[] sprintTicks = {0};
        helper.onEachTick(() -> {
            if (s.body().isSprinting()) sprintTicks[0]++;
            double off = flat(s.body().position(), Vec3.atBottomCenterOf(goal));
            if (off <= 1.5) {
                tidy(s);
                boolean sprinted = sprintTicks[0] > 0;
                if (sprinted == wantSprint) helper.succeed();
                else fail(helper, (wantSprint ? "it walked the whole way" : "it sprinted with " + food + " food")
                        + " (" + sprintTicks[0] + " ticks sprinting, " + helper.getTick() + " ticks in all)");
            } else if (helper.getTick() >= 500) {
                String how = String.format("after 500 ticks it is %.1f blocks short, at %s", off,
                        rel(helper, s.body().blockPosition()).toShortString());
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /** Plan 70, MA5.10: on a long straight walk it sprints, as a player would. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_sprint", timeoutTicks = 600)
    public void itSprintsOnALongStraightWalk(GameTestHelper helper) {
        longWalk(helper, "Runner", 20, true);
    }

    /** Plan 70, MA5.10: with 6 food a player can't sprint, and neither does it. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_hungry_walk", timeoutTicks = 600)
    public void hungryItWalks(GameTestHelper helper) {
        longWalk(helper, "Hungry", 6, false);
    }

    /**
     * A tower of planks from (4, 1, 4) to (6, 9, 6), the companion on top at (5, 10, 5), the owner out of the way. Planks
     * are someone's building, so it never digs down through them. Returns the goal on the ground east of it.
     */
    private static BlockPos tower(GameTestHelper helper, Scene s) {
        var level = helper.getLevel();
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        BlockPos aside = helper.absolutePos(new BlockPos(-12, 1, -12));
        s.owner().teleportTo(level, aside.getX() + 0.5, aside.getY(), aside.getZ() + 0.5, 0.0f, 0.0f);
        for (int x = 4; x <= 6; x++) {
            for (int z = 4; z <= 6; z++) {
                for (int y = 1; y <= 9; y++) helper.setBlock(x, y, z, Blocks.OAK_PLANKS);
            }
        }
        BlockPos top = helper.absolutePos(new BlockPos(5, 10, 5));
        s.body().teleportTo(level, top.getX() + 0.5, top.getY(), top.getZ() + 0.5, 0.0f, 0.0f);
        return helper.absolutePos(new BlockPos(12, 1, 5));
    }

    private static int towerBroken(GameTestHelper helper) {
        int broken = 0;
        for (int x = 4; x <= 6; x++) {
            for (int z = 4; z <= 6; z++) {
                for (int y = 1; y <= 9; y++) if (!helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.OAK_PLANKS)) broken++;
            }
        }
        return broken;
    }

    /**
     * Plan 70, MA5.10: on a tower of planks with no way down but a drop of 10 blocks into one block of water, it takes
     * the drop (water of any depth breaks a fall) and lands unhurt.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_water_drop", timeoutTicks = 700)
    public void itDropsIntoWaterFromHigh(GameTestHelper helper) {
        Scene s = scene(helper, "Diver");
        BlockPos goal = tower(helper, s);
        helper.setBlock(7, -1, 5, Blocks.STONE);
        helper.setBlock(7, 0, 5, Blocks.WATER);
        sendTo(helper, s, goal, "dive-1");
        helper.onEachTick(() -> {
            trace(helper, s, "water drop");
            double off = flat(s.body().position(), Vec3.atBottomCenterOf(goal));
            if (towerBroken(helper) > 0) {
                tidy(s);
                fail(helper, "it broke the tower");
            } else if (off <= 1.5) {
                float health = s.body().getHealth();
                tidy(s);
                if (health >= 20.0f) helper.succeed();
                else fail(helper, "it got down hurt: health " + health);
            } else if (helper.getTick() >= 600) {
                String how = String.format("after 600 ticks: %.1f blocks off, at %s, health %.0f", off,
                        rel(helper, s.body().blockPosition()).toShortString(), s.body().getHealth());
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /**
     * Plan 70, MA5.10: with a water bucket, from the same tower onto plain ground: it steps off, the water goes down
     * under it before it lands, and comes back up into the bucket.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_bucket_descent", timeoutTicks = 700)
    public void itComesDownWithAWaterBucket(GameTestHelper helper) {
        Scene s = scene(helper, "Clutch");
        BlockPos goal = tower(helper, s);
        s.body().getInventory().setItem(1, new ItemStack(Items.WATER_BUCKET));
        sendTo(helper, s, goal, "bucket-1");
        helper.onEachTick(() -> {
            trace(helper, s, "bucket");
            double off = flat(s.body().position(), Vec3.atBottomCenterOf(goal));
            if (towerBroken(helper) > 0) {
                tidy(s);
                fail(helper, "it broke the tower");
            } else if (off <= 1.5) {
                float health = s.body().getHealth();
                boolean bucket = s.body().getInventory().contains(new ItemStack(Items.WATER_BUCKET));
                int water = 0;
                for (int x = 3; x <= 8; x++) {
                    for (int z = 3; z <= 7; z++) {
                        if (helper.getBlockState(new BlockPos(x, 1, z)).getFluidState().is(net.minecraft.tags.FluidTags.WATER)) water++;
                    }
                }
                tidy(s);
                if (health >= 20.0f && bucket && water == 0) helper.succeed();
                else fail(helper, "down, but health " + health + ", water bucket back " + bucket + ", water left " + water);
            } else if (helper.getTick() >= 600) {
                String how = String.format("after 600 ticks: %.1f blocks off, at %s, health %.0f", off,
                        rel(helper, s.body().blockPosition()).toShortString(), s.body().getHealth());
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /**
     * Plan 70, MA5.10: a lake three deep with an island in it, and nothing to build a bridge with: it swims there, its
     * head above the water.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_swim", timeoutTicks = 900)
    public void itSwimsToAnIsland(GameTestHelper helper) {
        Scene s = scene(helper, "Swimmer");
        var level = helper.getLevel();
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        BlockPos aside = helper.absolutePos(new BlockPos(-14, 1, -14));
        s.owner().teleportTo(level, aside.getX() + 0.5, aside.getY(), aside.getZ() + 0.5, 0.0f, 0.0f);
        for (int x = -6; x <= 8; x++) {
            for (int z = -6; z <= 8; z++) {
                boolean island = x >= 0 && x <= 2 && z >= 0 && z <= 2;
                for (int y = -2; y <= 0; y++) helper.setBlock(x, y, z, island ? Blocks.STONE : Blocks.WATER);
            }
        }
        BlockPos start = helper.absolutePos(new BlockPos(-10, 1, 1));
        s.body().teleportTo(level, start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 0.0f, 0.0f);
        BlockPos goal = helper.absolutePos(new BlockPos(1, 1, 1));
        sendTo(helper, s, goal, "swim-1");
        int[] lowestAir = {Integer.MAX_VALUE};
        helper.onEachTick(() -> {
            trace(helper, s, "swim");
            lowestAir[0] = Math.min(lowestAir[0], s.body().getAirSupply());
            double off = flat(s.body().position(), Vec3.atBottomCenterOf(goal));
            if (off <= 1.0 && s.body().onGround()) {
                float health = s.body().getHealth();
                tidy(s);
                if (health >= 20.0f) helper.succeed();
                else fail(helper, "it got there hurt: health " + health + ", lowest air " + lowestAir[0]);
            } else if (helper.getTick() >= 800) {
                String how = String.format("after 800 ticks: %.1f blocks off, at %s, in water %b, lowest air %d", off,
                        rel(helper, s.body().blockPosition()).toShortString(), s.body().isInWater(), lowestAir[0]);
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /**
     * Plan 70, MA5.10: a husk (standing still, its AI off) on the straight line to the goal: the path goes round it,
     * out of its reach, and it is left alone.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_mob_round", timeoutTicks = 700)
    public void itGoesRoundAMobInItsWay(GameTestHelper helper) {
        Scene s = scene(helper, "Wary");
        var level = helper.getLevel();
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        BlockPos aside = helper.absolutePos(new BlockPos(3, 1, -14));
        s.owner().teleportTo(level, aside.getX() + 0.5, aside.getY(), aside.getZ() + 0.5, 0.0f, 0.0f);
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(4, 1, 4));
        husk.setNoAi(true);
        BlockPos start = helper.absolutePos(new BlockPos(-12, 1, 4));
        s.body().teleportTo(level, start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 0.0f, 0.0f);
        BlockPos goal = helper.absolutePos(new BlockPos(20, 1, 4));
        sendTo(helper, s, goal, "round-1");
        double[] closest = {Double.MAX_VALUE};
        helper.onEachTick(() -> {
            trace(helper, s, "mob round");
            if (helper.getTick() > 5) closest[0] = Math.min(closest[0], flat(s.body().position(), husk.position()));
            double off = flat(s.body().position(), Vec3.atBottomCenterOf(goal));
            if (off <= 1.5) {
                boolean alive = husk.isAlive();
                tidy(s);
                husk.discard();
                if (closest[0] >= 4.0 && alive) helper.succeed();
                else fail(helper, String.format("it came within %.1f blocks of the husk (alive %b)", closest[0], alive));
            } else if (helper.getTick() >= 600) {
                String how = String.format("after 600 ticks: %.1f blocks off, at %s, closest %.1f", off,
                        rel(helper, s.body().blockPosition()).toShortString(), closest[0]);
                tidy(s);
                husk.discard();
                fail(helper, how);
            }
        });
    }

    /**
     * Plan 70, MA5.7: diamond ore in plain view is remembered, and noted in the journal as a find; iron ore sealed in
     * stone is not (only what it can see); the diamond mined out is struck off. What it saw is in the store on disk.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_seen", timeoutTicks = 400)
    public void itRemembersWhatItCanSeeAndNothingThroughStone(GameTestHelper helper) {
        Scene s = scene(helper, "Looker");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        helper.setBlock(7, 1, 3, Blocks.DIAMOND_ORE);
        for (int x = 9; x <= 11; x++) for (int y = 1; y <= 3; y++) for (int z = 6; z <= 8; z++) helper.setBlock(x, y, z, Blocks.STONE);
        helper.setBlock(10, 2, 7, Blocks.IRON_ORE);
        BlockPos diamond = helper.absolutePos(new BlockPos(7, 1, 3));
        boolean[] mined = {false};
        helper.onEachTick(() -> {
            var seen = s.loop().placeKeeper().sightings();
            var body = s.body();
            String dim = body.serverLevel().dimension().location().toString();
            var at = body.blockPosition();
            boolean sawDiamond = !seen.nearest("diamond", dim, at.getX(), at.getY(), at.getZ(), 1, 64).isEmpty();
            boolean sawIron = !seen.nearest("iron", dim, at.getX(), at.getY(), at.getZ(), 1, 64).isEmpty();
            if (sawIron) {
                tidy(s);
                fail(helper, "it remembered iron ore sealed inside stone");
            } else if (sawDiamond && !mined[0]) {
                mined[0] = true;
                helper.getLevel().destroyBlock(diamond, false);
            } else if (mined[0] && !sawDiamond) {
                boolean noted = s.loop().journal().recent(50).stream()
                        .anyMatch(e -> "find".equals(e.kind()) && e.text().contains("diamond"));
                s.loop().saveMemoryBlocking();
                var folder = s.loop().memoryFolder();
                boolean onDisk = folder != null && java.nio.file.Files.exists(folder.resolve("store/sightings/index.gz"));
                tidy(s);
                if (noted && onDisk) helper.succeed();
                else fail(helper, "struck off, but journal find " + noted + ", store on disk " + onDisk);
            } else if (helper.getTick() >= 350) {
                tidy(s);
                fail(helper, mined[0] ? "the mined-out diamond was still remembered" : "it never noted the diamond in plain view");
            }
        });
    }

    /**
     * Plan 70, MA5.7 and MA5.9: a chest it looked in holds iron; "where did you put the iron?" is answered from its
     * chest memory, and "what did we do today?" from its journal, both without a model.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_recall", timeoutTicks = 200)
    public void itAnswersFromItsMemory(GameTestHelper helper) {
        Scene s = scene(helper, "Recaller");
        BlockPos chest = helper.absolutePos(new BlockPos(6, 1, 6));
        var dim = s.body().serverLevel().dimension().location().toString();
        helper.runAfterDelay(2, () -> {
            var pos = new com.mineagent.engine.world.WorldAssetIndex.Position(dim, chest.getX(), chest.getY(), chest.getZ());
            s.loop().worldAssetIndex().observeContainer("minecraft:chest@" + chest.toShortString(), pos,
                    List.of(new com.mineagent.engine.world.WorldAssetIndex.ItemObservation(0, "minecraft:iron_ingot", 32, 0, 0,
                            java.util.Set.of(), 1.0)), true, helper.getLevel().getGameTime());
            MineAgentEngine.onPlayerChat(s.owner(), "remember this as the forge");
            MineAgentEngine.onPlayerChat(s.owner(), "where did you put the iron?");
            MineAgentEngine.onPlayerChat(s.owner(), "what did we do today?");
        });
        helper.onEachTick(() -> {
            var said = s.loop().journal().recent(50).stream().filter(e -> "said".equals(e.kind())).map(e -> e.text()).toList();
            boolean iron = said.stream().anyMatch(t -> t.contains("32 iron ingot in the chest"));
            boolean today = said.stream().anyMatch(t -> t.startsWith("Today:") && t.contains("place named"));
            if (iron && today) {
                tidy(s);
                helper.succeed();
            } else if (helper.getTick() >= 150) {
                tidy(s);
                fail(helper, "said: " + said);
            }
        });
    }

    /** A bell on the ground and two villagers standing by it (their AI off), at (x, 1, z). */
    private static void bellAndVillagers(GameTestHelper helper, int x, int z) {
        helper.setBlock(x, 1, z, Blocks.BELL);
        for (int i = 0; i < 2; i++) {
            var v = helper.spawn(EntityType.VILLAGER, new BlockPos(x + 2 + i, 1, z + 1));
            v.setNoAi(true);
        }
    }

    /** Plan 70, MA5.6: a bell with villagers about it is a village, whatever built it, and is remembered. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_bell_village", timeoutTicks = 400)
    public void aBellWithVillagersIsAVillage(GameTestHelper helper) {
        Scene s = scene(helper, "Townie");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        bellAndVillagers(helper, 8, 6);
        helper.onEachTick(() -> {
            var village = s.loop().placeKeeper().memory().find("village", null);
            if (village.isPresent()) {
                tidy(s);
                helper.succeed();
            } else if (helper.getTick() >= 350) {
                tidy(s);
                fail(helper, "no village from a bell and two villagers in plain view");
            }
        });
    }

    /**
     * Plan 70, MA5.6: "find a village" with none known: it walks to ground it has never seen and finds the one there.
     * Everything round it but the way south is marked as seen, so the nearest new ground is that way.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_find_village", timeoutTicks = 1400)
    public void itGoesLookingForAVillageOnNewGround(GameTestHelper helper) {
        Scene s = scene(helper, "Scout");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        var body = s.body();
        String dim = body.serverLevel().dimension().location().toString();
        int cx = body.getBlockX() >> 4, cz = body.getBlockZ() >> 4;
        var chunks = s.loop().placeKeeper().chunks();
        for (int x = cx - 7; x <= cx + 7; x++) {
            for (int z = cz - 7; z <= cz + 7; z++) {
                if (x == cx && z > cz) continue;
                chunks.put(dim, x, z, new com.mineagent.engine.world.ChunkSummaries.Summary(
                        com.mineagent.engine.world.ChunkSummaries.Kind.LAND, body.getBlockY(), false));
            }
        }
        // On the way it goes: it heads for the middle of each new chunk south, so 3 blocks to the side of that line.
        BlockPos rel0 = rel(helper, body.blockPosition());
        int lineX = rel(helper, new BlockPos((cx << 4) + 8, body.getBlockY(), body.getBlockZ())).getX();
        bellAndVillagers(helper, lineX + 3, rel0.getZ() + 76);
        helper.runAfterDelay(5, () -> MineAgentEngine.onPlayerChat(s.owner(), "find a village"));
        helper.onEachTick(() -> {
            trace(helper, s, "find village");
            var village = s.loop().placeKeeper().memory().find("village", null);
            if (village.isPresent()) {
                tidy(s);
                helper.succeed();
            } else if (helper.getTick() >= 1300) {
                String how = "after 1300 ticks, at " + rel(helper, s.body().blockPosition()).toShortString();
                tidy(s);
                fail(helper, "it never found the village: " + how);
            }
        });
    }

    /**
     * Plan 70, MA5.6: it walks into a lit nether portal, goes through, and the portal is remembered at both ends; then
     * "go home" from the Nether takes it back through the same portal and home.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_portal", timeoutTicks = 2400)
    public void throughAPortalAndBackHome(GameTestHelper helper) {
        Scene s = scene(helper, "Traveller");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        var level = helper.getLevel();
        // An obsidian frame four wide and five high at x 8..11, z 6, then its inside lit. The whole frame goes first:
        // a portal block checks its frame whenever a neighbour changes, and one lit inside half a frame goes out (the
        // first run of this scene built them together, and the portal was gone before it got there).
        for (int x = 8; x <= 11; x++) {
            for (int y = 1; y <= 5; y++) {
                if (x == 8 || x == 11 || y == 1 || y == 5) helper.setBlock(new BlockPos(x, y, 6), Blocks.OBSIDIAN);
            }
        }
        var lit = Blocks.NETHER_PORTAL.defaultBlockState().setValue(net.minecraft.world.level.block.NetherPortalBlock.AXIS,
                net.minecraft.core.Direction.Axis.X);
        for (int x = 9; x <= 10; x++) {
            for (int y = 2; y <= 4; y++) {
                // As lighting a portal places them: told to clients, no shape updates among themselves.
                level.setBlock(helper.absolutePos(new BlockPos(x, y, 6)), lit, 2 | 16);
            }
        }
        if (!level.getBlockState(helper.absolutePos(new BlockPos(9, 2, 6))).is(Blocks.NETHER_PORTAL)) {
            tidy(s);
            fail(helper, "the scene's portal did not stay lit");
            return;
        }
        BlockPos home = helper.absolutePos(new BlockPos(3, 1, 1));
        s.owner().teleportTo(level, home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0.0f, 0.0f);
        helper.runAfterDelay(2, () -> MineAgentEngine.onPlayerChat(s.owner(), "this is home"));
        sendTo(helper, s, helper.absolutePos(new BlockPos(9, 2, 6)), "portal-in");
        int[] phase = {0};
        helper.onEachTick(() -> {
            var body = s.body();
            String dim = body.serverLevel().dimension().location().toString();
            var memory = s.loop().placeKeeper().memory();
            if (helper.getTick() % 40 == 0) {
                System.out.println("[SCENE portal] tick " + helper.getTick() + ": phase " + phase[0] + ", " + dim + " at "
                        + body.blockPosition().toShortString() + ", in a portal "
                        + com.mineagent.engine.world.PlaceKeeper.standingInPortal(body) + ", crossing '"
                        + s.loop().crossingStage() + "'");
            }
            if (phase[0] == 0 && dim.equals("minecraft:the_nether")) {
                boolean linked = memory.portalTo("minecraft:overworld",
                        com.mineagent.engine.world.PlaceKeeper.point(body)).isPresent();
                if (!linked) {
                    tidy(s);
                    fail(helper, "through to the Nether, but the portal was not remembered");
                    return;
                }
                phase[0] = 1;
                helper.runAfterDelay(100, () -> {
                    // A client confirms the teleport; the companion has none, and the mark is cleared for it.
                    if (s.body().isChangingDimension()) {
                        tidy(s);
                        fail(helper, "after the portal it was still marked as changing dimension (it would take no damage)");
                        return;
                    }
                    MineAgentEngine.onPlayerChat(s.owner(), "go home");
                });
            } else if (phase[0] == 1 && dim.equals("minecraft:overworld")
                    && flat(body.position(), Vec3.atBottomCenterOf(home)) <= 2.5) {
                tidy(s);
                helper.succeed();
            } else if (helper.getTick() >= 2300) {
                String how = "phase " + phase[0] + ", in " + dim + " at " + body.blockPosition().toShortString();
                tidy(s);
                fail(helper, "after 2300 ticks: " + how);
            }
        });
    }
    /** How many of {@code item} a chest at {@code pos} holds. */
    private static int inChest(GameTestHelper helper, BlockPos pos, net.minecraft.world.item.Item item) {
        var be = helper.getLevel().getBlockEntity(pos);
        if (!(be instanceof net.minecraft.world.Container c)) return -1;
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
        return n;
    }

    /**
     * Plan 70, MA5.4: iron meant for an AFK owner goes in the chest by them, not on the ground at their feet (dropped
     * items vanish in five minutes); a question it has waits; when they're back, one line says where the iron went and
     * asks the question.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_afk_chest", timeoutTicks = 500)
    public void itemsForAnAfkOwnerGoInAChestAndQuestionsWait(GameTestHelper helper) {
        Scene s = scene(helper, "Keeper");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        helper.setBlock(7, 1, 3, Blocks.CHEST);
        BlockPos chest = helper.absolutePos(new BlockPos(7, 1, 3));
        s.body().getInventory().setItem(1, new ItemStack(Items.IRON_INGOT, 5));
        helper.runAfterDelay(5, () -> MineAgentEngine.onPlayerChat(s.owner(), "afk"));
        int[] phase = {0};
        String[] reply = {""};
        helper.onEachTick(() -> {
            var level = helper.getLevel();
            var onGround = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    s.owner().getBoundingBox().inflate(16.0), e -> e.getItem().is(Items.IRON_INGOT));
            if (!onGround.isEmpty()) {
                tidy(s);
                fail(helper, "the iron was dropped on the ground by the AFK owner");
                return;
            }
            if (phase[0] == 0 && s.loop().ownerAfk()) {
                phase[0] = 1;
                var args = new com.google.gson.JsonObject();
                args.addProperty("item_id", "minecraft:iron_ingot");
                args.addProperty("count", 5);
                new com.mineagent.tools.inventory.DropItemsTool().onServerCall("drop-afk", args, s.companion(), r -> reply[0] = r);
            } else if (phase[0] == 1 && inChest(helper, chest, Items.IRON_INGOT) == 5) {
                phase[0] = 2;
                s.loop().say("Shall I smelt the iron?");
                boolean held = s.loop().journal().recent(20).stream()
                        .anyMatch(e -> "held".equals(e.kind()) && e.text().contains("smelt"));
                if (!held) {
                    tidy(s);
                    fail(helper, "its question was asked while the owner was AFK");
                    return;
                }
                MineAgentEngine.onPlayerChat(s.owner(), "back");
            } else if (phase[0] == 2 && !s.loop().ownerAfk()) {
                var welcome = s.loop().journal().recent(20).stream()
                        .filter(e -> "said".equals(e.kind()) && e.text().startsWith("Welcome back")).findFirst();
                if (welcome.isEmpty()) return; // said on the next tick
                String line = welcome.get().text();
                tidy(s);
                if (line.contains("iron") && line.contains("chest") && line.contains("Shall I smelt the iron?")) helper.succeed();
                else fail(helper, "the welcome-back line: " + line);
            } else if (helper.getTick() >= 450) {
                String how = "phase " + phase[0] + ", the chest holds " + inChest(helper, chest, Items.IRON_INGOT)
                        + " iron, it carries " + s.body().getInventory().countItem(Items.IRON_INGOT) + ", the tool said " + reply[0];
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /**
     * Plan 70, MA5.4: AFK at a farm's hoppers, the farm is left alone: nothing broken within 16 blocks of the owner and
     * it keeps within 8; back at the keys, the block can be broken again.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_afk_farm", timeoutTicks = 400)
    public void anAfkFarmIsLeftAlone(GameTestHelper helper) {
        Scene s = scene(helper, "Farmhand");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        helper.setBlock(2, 0, 2, Blocks.HOPPER);
        helper.setBlock(4, 0, 2, Blocks.HOPPER);
        helper.setBlock(5, 1, 5, Blocks.DIRT);
        BlockPos dirt = helper.absolutePos(new BlockPos(5, 1, 5));
        helper.runAfterDelay(5, () -> MineAgentEngine.onPlayerChat(s.owner(), "afk"));
        int[] phase = {0};
        helper.onEachTick(() -> {
            var check = com.mineagent.engine.task.BlockDigger.assessBreak(s.body(), dirt);
            boolean farmSaysNo = check.detail() != null && check.detail().contains("AFK");
            if (phase[0] == 0 && s.loop().ownerAtFarm()) {
                if (!farmSaysNo) {
                    tidy(s);
                    fail(helper, "AFK at the farm, but breaking a block by them was allowed: " + check.status());
                    return;
                }
                phase[0] = 1;
                MineAgentEngine.onPlayerChat(s.owner(), "back");
            } else if (phase[0] == 1 && !s.loop().ownerAfk()) {
                tidy(s);
                if (!farmSaysNo) helper.succeed();
                else fail(helper, "the owner is back, but the farm is still off-limits");
            } else if (helper.getTick() >= 350) {
                String how = "phase " + phase[0] + ", afk " + s.loop().ownerAfk() + ", at a farm " + s.loop().ownerAtFarm();
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /** Plan 70, MA5.8: a block within a player's reach is broken; one six blocks away is out of reach. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_block_reach", timeoutTicks = 300)
    public void itBreaksOnlyWhatAPlayerCouldReach(GameTestHelper helper) {
        Scene s = scene(helper, "Reacher");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        BlockPos at = s.body().blockPosition();
        BlockPos near = at.offset(2, 0, 0), far = at.offset(-7, 0, 0);
        helper.getLevel().setBlockAndUpdate(near, Blocks.DIRT.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(far, Blocks.DIRT.defaultBlockState());
        int[] started = {-100};
        helper.onEachTick(() -> {
            var level = helper.getLevel();
            var farCheck = com.mineagent.engine.task.BlockDigger.assessBreak(s.body(), far);
            if (farCheck.allowed() || com.mineagent.engine.task.BlockDigger.startBreaking(s.body(), far) || level.getBlockState(far).isAir()) {
                tidy(s);
                fail(helper, "a block 7 blocks away was in reach: " + farCheck.status());
                return;
            }
            if (!level.getBlockState(near).isAir()) {
                // A break once started goes on by itself, as a player holding the button (again only if it stopped).
                if (helper.getTick() - started[0] >= 60) {
                    started[0] = (int) helper.getTick();
                    com.mineagent.engine.task.BlockDigger.startBreaking(s.body(), near);
                }
            } else {
                tidy(s);
                if (farCheck.status() == com.mineagent.engine.task.BlockDigger.BreakStatus.OUT_OF_REACH) helper.succeed();
                else fail(helper, "the far block was refused, but not for reach: " + farCheck.status());
                return;
            }
            if (helper.getTick() >= 250) {
                tidy(s);
                fail(helper, "the block 2 blocks away was never broken: " + com.mineagent.engine.task.BlockDigger.assessBreak(s.body(), near).status());
            }
        });
    }

    /**
     * Plan 70, MA5.9: while the owner is away, lava fills the spot it left from; when they come back it comes back
     * beside it, on safe ground, unhurt, and remembers home and what it did.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_return_lava", timeoutTicks = 200)
    public void comingBackToLavaItStandsBesideIt(GameTestHelper helper) {
        Scene s = scene(helper, "Keeper3");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        helper.runAfterDelay(2, () -> MineAgentEngine.onPlayerChat(s.owner(), "this is home"));
        helper.runAfterDelay(10, () -> {
            BlockPos left = s.body().blockPosition();
            float health = s.body().getHealth();
            MineAgentEngine.onPlayerLeave(s.owner());
            var level = helper.getLevel();
            level.setBlockAndUpdate(left, Blocks.LAVA.defaultBlockState());
            level.setBlockAndUpdate(left.above(), Blocks.LAVA.defaultBlockState());
            MineAgentEngine.onPlayerJoin(s.owner());
            var back = MineAgentEngine.getCompanionsByOwner(s.owner().getUUID());
            if (back.size() != 1) {
                for (var c : back) MineAgentEngine.despawnCompanion(c.companion.companionId(), true);
                s.owner().getServer().getPlayerList().remove(s.owner());
                fail(helper, "after leaving and coming back there are " + back.size() + " companions");
                return;
            }
            var again = back.get(0).companion.serverPlayer();
            var loop = TaskContext.agentLoop(back.get(0).companion);
            BlockPos now = again.blockPosition();
            boolean inLava = !level.getBlockState(now).getFluidState().isEmpty()
                    || !level.getBlockState(now.above()).getFluidState().isEmpty();
            boolean beside = now.distSqr(left) <= 5 * 5;
            boolean onGround = com.mineagent.engine.pathing.util.BlockHelper.canStandOn(level.getBlockState(now.below()));
            boolean home = loop != null && loop.placeKeeper().memory().find("home", null).isPresent();
            boolean journalKept = loop != null && loop.journal().recent(50).stream().anyMatch(e -> e.text().contains("ome"));
            String all = String.format("in lava %b, beside %b (%s from %s), on ground %b, health %.1f of %.1f, home %b, journal kept %b",
                    inLava, beside, now.toShortString(), left.toShortString(), onGround, again.getHealth(), health, home, journalKept);
            MineAgentEngine.despawnCompanion(back.get(0).companion.companionId(), true);
            s.owner().getServer().getPlayerList().remove(s.owner());
            level.setBlockAndUpdate(left, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(left.above(), Blocks.AIR.defaultBlockState());
            if (!inLava && beside && onGround && again.getHealth() >= health && home && journalKept) helper.succeed();
            else fail(helper, all);
        });
    }

    /**
     * Plan 70, MA5.10: a 256-block trip south over ground it has never seen, planned by chunks first: it gets there,
     * and the chunks it passed are in its explored map.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_long_trip", timeoutTicks = 2400)
    public void aLongTripGetsThereAndMapsTheWay(GameTestHelper helper) {
        Scene s = scene(helper, "Voyager");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        // Out of the test grid first: the other scenes' areas, walled in barrier blocks, lie along the way from here.
        // 4,000 blocks off, the flat world is open ground.
        var level = helper.getLevel();
        BlockPos far = helper.absolutePos(new BlockPos(3, 0, 3)).offset(4_000, 0, 4_000);
        level.getChunk(far.getX() >> 4, far.getZ() >> 4); // made now, so its ground is known
        int groundY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, far.getX(), far.getZ());
        s.owner().teleportTo(level, far.getX() + 0.5, groundY, far.getZ() + 0.5, 0.0f, 0.0f);
        s.body().teleportTo(level, far.getX() + 1.5, groundY, far.getZ() + 0.5, 0.0f, 0.0f);
        BlockPos start = new BlockPos(far.getX() + 1, groundY, far.getZ());
        BlockPos goal = start.offset(0, 0, 256);
        // The route's ground made and kept loaded for the scene: the scene is the trip, not how fast a CI machine
        // makes new chunks (it waits for them as a player would, up to a minute in a trip; MoveToTask).
        java.util.List<long[]> forced = new java.util.ArrayList<>();
        for (int cz = start.getZ() >> 4; cz <= (goal.getZ() >> 4) + 1; cz++) {
            for (int cx = (start.getX() >> 4) - 1; cx <= (start.getX() >> 4) + 1; cx++) {
                level.setChunkForced(cx, cz, true);
                forced.add(new long[] {cx, cz});
            }
        }
        Runnable unforce = () -> forced.forEach(c -> level.setChunkForced((int) c[0], (int) c[1], false));
        helper.runAfterDelay(5, () -> {
            var args = new com.google.gson.JsonObject();
            args.addProperty("goal_mode", "xz");
            args.addProperty("x", goal.getX());
            args.addProperty("z", goal.getZ());
            new com.mineagent.tools.movement.MoveToTool().onServerCall("long-trip", args, s.companion(), r -> {});
        });
        helper.onEachTick(() -> {
            trace(helper, s, "long trip");
            var body = s.body();
            double left = flat(body.position(), Vec3.atBottomCenterOf(goal));
            if (left <= 3.0) {
                var chunks = s.loop().placeKeeper().chunks();
                String dim = body.serverLevel().dimension().location().toString();
                int cx = start.getX() >> 4;
                int mapped = 0;
                for (int z = start.getZ() >> 4; z <= goal.getZ() >> 4; z++) if (chunks.seen(dim, cx, z)) mapped++;
                int along = (goal.getZ() >> 4) - (start.getZ() >> 4) + 1;
                tidy(s);
                unforce.run();
                if (mapped >= along - 1) helper.succeed();
                else fail(helper, "there, but only " + mapped + " of the " + along + " chunks along the way are mapped");
            } else if (helper.getTick() >= 2300) {
                String how = String.format("%.0f blocks short after 2300 ticks, %d blocks along", left, body.getBlockZ() - start.getZ());
                tidy(s);
                unforce.run();
                fail(helper, how);
            }
        });
    }
    /**
     * Plan 70, MA5.4: a distance to keep within while they're AFK is optional; set to 32, sent 60 blocks off, it turns
     * back at the edge and keeps within reach of them.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_afk_leash", timeoutTicks = 700)
    public void withADistanceSetItKeepsWithinItWhileTheyAreAfk(GameTestHelper helper) {
        Scene s = scene(helper, "Tethered");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        setting(helper, "behaviour.afkStayWithin", "32");
        if (com.mineagent.engine.settings.Switches.afkStayWithin() != 32) {
            tidy(s);
            fail(helper, "/mineagent setconfig behaviour.afkStayWithin 32 did not take");
            return;
        }
        helper.runAfterDelay(5, () -> MineAgentEngine.onPlayerChat(s.owner(), "afk"));
        boolean[] sent = {false};
        double[] furthest = {0};
        helper.onEachTick(() -> {
            trace(helper, s, "afk leash");
            double away = s.body().distanceTo(s.owner());
            furthest[0] = Math.max(furthest[0], away);
            if (!sent[0] && s.loop().ownerAfk()) {
                sent[0] = true;
                BlockPos far = s.owner().blockPosition().offset(60, 0, 0);
                var args = new com.google.gson.JsonObject();
                args.addProperty("goal_mode", "xz");
                args.addProperty("x", far.getX());
                args.addProperty("z", far.getZ());
                new com.mineagent.tools.movement.MoveToTool().onServerCall("afk-far", args, s.companion(), r -> {});
            }
            if (furthest[0] > 38.0) {
                setting(helper, "behaviour.afkStayWithin", "0");
                tidy(s);
                fail(helper, String.format("it went %.0f blocks from its AFK owner", furthest[0]));
            } else if (sent[0] && helper.getTick() >= 600) {
                setting(helper, "behaviour.afkStayWithin", "0");
                tidy(s);
                if (furthest[0] >= 24.0 && away <= 32.0) helper.succeed();
                else fail(helper, String.format("furthest %.0f, now %.0f blocks from them", furthest[0], away));
            }
        });
    }

    /**
     * Plan 70, MA5.4, by default: AFK only means they're away ("AFK just means I'm away, likely stay busy"). Its owner
     * AFK out in the open, it is sent 60 blocks off on a job and goes all the way, and it does not turn to guarding.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_afk_busy", timeoutTicks = 700)
    public void afkOnlyMeansAwayItStaysBusy(GameTestHelper helper) {
        Scene s = scene(helper, "Busy");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        helper.runAfterDelay(5, () -> MineAgentEngine.onPlayerChat(s.owner(), "afk"));
        boolean[] sent = {false};
        BlockPos[] goal = {null};
        helper.onEachTick(() -> {
            trace(helper, s, "afk busy");
            if (s.loop().ownerAfk() && s.loop().combatSetup(s.body(), s.owner()).duty()
                    == com.mineagent.engine.combat.CombatBrain.Duty.GUARD) {
                tidy(s);
                fail(helper, "the owner went AFK and it took up guarding them instead of its job");
                return;
            }
            if (!sent[0] && s.loop().ownerAfk()) {
                sent[0] = true;
                goal[0] = s.owner().blockPosition().offset(60, 0, 0);
                var args = new com.google.gson.JsonObject();
                args.addProperty("goal_mode", "xz");
                args.addProperty("x", goal[0].getX());
                args.addProperty("z", goal[0].getZ());
                new com.mineagent.tools.movement.MoveToTool().onServerCall("afk-busy", args, s.companion(), r -> {});
            }
            if (goal[0] != null && flat(s.body().position(), Vec3.atBottomCenterOf(goal[0])) <= 2.0) {
                boolean stillAfk = s.loop().ownerAfk();
                tidy(s);
                if (stillAfk) helper.succeed();
                else fail(helper, "there, but the owner no longer counted as AFK");
            } else if (helper.getTick() >= 650) {
                String how = String.format("%.0f blocks from them after 650 ticks", s.body().distanceTo(s.owner()));
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /** What it last said to its owner, from its journal (empty if nothing yet). */
    private static String lastSaid(Scene s, String startingWith) {
        var said = s.loop().journal().recent(40).stream()
                .filter(e -> "said".equals(e.kind()) && e.text().startsWith(startingWith)).toList();
        return said.isEmpty() ? "" : said.get(said.size() - 1).text();
    }

    /** Plan 70, MA5.4, the owner's words: "Going AFK, make sure I dont die": kept safe under a roof too, until they're back. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_afk_keep_safe", timeoutTicks = 300)
    public void goingAfkMakeSureIDontDie(GameTestHelper helper) {
        Scene s = scene(helper, "Minder");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        for (int x = 1; x <= 5; x++) for (int z = 1; z <= 5; z++) helper.setBlock(x, 3, z, Blocks.STONE); // not out in the open
        helper.runAfterDelay(5, () -> MineAgentEngine.onPlayerChat(s.owner(), "Going AFK, make sure I dont die"));
        boolean[] guarded = {false};
        helper.onEachTick(() -> {
            var setup = s.loop().combatSetup(s.body(), s.owner());
            boolean guarding = setup.duty() == com.mineagent.engine.combat.CombatBrain.Duty.GUARD
                    && s.owner().getUUID().equals(setup.ward());
            if (!guarded[0] && guarding && s.loop().ownerAfk()) {
                guarded[0] = true;
                String said = lastSaid(s, "Got it");
                if (!said.contains("keep you safe")) {
                    tidy(s);
                    fail(helper, "it guarded them but said: " + said);
                    return;
                }
                MineAgentEngine.onPlayerChat(s.owner(), "back");
            } else if (guarded[0] && !s.loop().ownerAfk()) {
                boolean stoodDown = !guarding && !s.loop().afkOrder().any();
                tidy(s);
                if (stoodDown) helper.succeed();
                else fail(helper, "back, but it still keeps them safe: duty " + setup.duty());
            } else if (helper.getTick() >= 250) {
                String how = "afk " + s.loop().ownerAfk() + ", duty " + setup.duty() + ", order " + s.loop().afkOrder();
                tidy(s);
                fail(helper, "it never kept the AFK owner safe: " + how);
            }
        });
    }

    /**
     * Plan 70, MA5.4, the owner's words: "Going AFK, block yourself in, don't die". With dirt in its pack it walls
     * itself in, sides and roof, and waits; when they're back it takes the walls down.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_afk_shelter", timeoutTicks = 900)
    public void goingAfkBlockYourselfInDontDie(GameTestHelper helper) {
        Scene s = scene(helper, "Hermit");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        s.body().getInventory().setItem(2, new ItemStack(Items.DIRT, 16));
        // The owner a few steps off, so nobody stands where its walls go.
        BlockPos away = helper.absolutePos(new BlockPos(3, 1, 10));
        s.owner().teleportTo(helper.getLevel(), away.getX() + 0.5, away.getY(), away.getZ() + 0.5, 0.0f, 0.0f);
        helper.runAfterDelay(5, () -> MineAgentEngine.onPlayerChat(s.owner(), "Going AFK, block yourself in, don't die"));
        int[] phase = {0};
        BlockPos[] in = {null};
        float health = s.body().getHealth();
        helper.onEachTick(() -> {
            trace(helper, s, "afk shelter");
            var level = helper.getLevel();
            BlockPos at = s.body().blockPosition();
            java.util.List<BlockPos> walls = new java.util.ArrayList<>();
            for (var d : new net.minecraft.core.Direction[] {net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.EAST,
                    net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.WEST}) {
                walls.add(at.relative(d));
                walls.add(at.above().relative(d));
            }
            walls.add(at.above(2));
            long closed = walls.stream().filter(p -> !com.mineagent.engine.pathing.util.BlockHelper.isPassable(level.getBlockState(p))).count();
            if (phase[0] == 0 && closed == walls.size()) {
                phase[0] = 1;
                in[0] = at;
                helper.runAfterDelay(60, () -> MineAgentEngine.onPlayerChat(s.owner(), "back"));
            } else if (phase[0] == 1 && !at.equals(in[0]) && helper.getTick() > 0 && s.loop().ownerAfk()) {
                tidy(s);
                fail(helper, "walled in, it left its shelter before the owner was back");
            } else if (phase[0] == 1 && !s.loop().ownerAfk() && !s.loop().afkOrder().any()) {
                phase[0] = 2;
            } else if (phase[0] == 2 && closed <= 3) {
                boolean unhurt = s.body().getHealth() >= health;
                tidy(s);
                if (unhurt) helper.succeed();
                else fail(helper, "out again, but hurt: " + s.body().getHealth());
            } else if (helper.getTick() >= 850) {
                String how = "phase " + phase[0] + ", " + closed + " of " + walls.size() + " walls up, dirt left "
                        + s.body().getInventory().countItem(Items.DIRT) + ", said: " + lastSaid(s, "");
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /**
     * Plan 70, MA5.4 and MA7.1, the owner's words: "Going AFK, fill up a chest with food while I'm out." It says so,
     * and does it itself, no model: the chest by them, food it can make here (cows in the field, hunted and cooked),
     * never the last two cows, and when there is no more to be had it says how far it got.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_afk_job", timeoutTicks = 3600)
    public void goingAfkFillUpAChestWithFood(GameTestHelper helper) {
        Scene s = scene(helper, "Larder");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        BlockPos chest = almostFullChest(helper, 0);
        var cows = field(helper, 6);
        var inv = s.body().getInventory();
        inv.setItem(0, new ItemStack(Items.STONE_SWORD));
        inv.setItem(1, new ItemStack(Items.FURNACE, 4));
        inv.setItem(2, new ItemStack(Items.COAL, 4));
        helper.runAfterDelay(5, () -> MineAgentEngine.onPlayerChat(s.owner(), "Going AFK, fill up a chest with food while I'm out."));
        helper.onEachTick(() -> {
            trace(helper, s, "afk food");
            String said = lastSaid(s, "Got it");
            String done = lastSaid(s, "Done");
            int food = inChest(helper, chest, Items.COOKED_BEEF);
            long alive = cows.stream().filter(net.minecraft.world.entity.Entity::isAlive).count();
            if (!done.isEmpty()) {
                boolean told = said.contains("fill up a chest with food, then find something else to do");
                String how = "said " + said + "; done: " + done + "; cooked beef in the chest " + food + "; cows alive " + alive
                        + "; afk " + s.loop().ownerAfk();
                tidy(s);
                // 4 cows it may take give 4 to 12 beef; the last two are left.
                if (told && food >= 4 && alive == 2 && done.contains("cooked beef") && done.contains("cows")) helper.succeed();
                else fail(helper, how);
            } else if (helper.getTick() >= 3500) {
                String how = "said " + said + "; last: " + lastSaid(s, "") + "; cooked beef in the chest " + food + "; cows alive "
                        + alive + "; carries beef " + inv.countItem(Items.BEEF) + ", cooked " + inv.countItem(Items.COOKED_BEEF);
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /** A field fenced round (0 to 14 each way) with {@code cows} grown cows in it. */
    private static java.util.List<net.minecraft.world.entity.animal.Cow> field(GameTestHelper helper, int cows) {
        for (int i = -1; i <= 15; i++) {
            helper.setBlock(i, 1, -1, Blocks.OAK_FENCE);
            helper.setBlock(i, 1, 15, Blocks.OAK_FENCE);
            helper.setBlock(-1, 1, i, Blocks.OAK_FENCE);
            helper.setBlock(15, 1, i, Blocks.OAK_FENCE);
        }
        java.util.List<net.minecraft.world.entity.animal.Cow> out = new java.util.ArrayList<>();
        for (int i = 0; i < cows; i++) out.add(helper.spawn(EntityType.COW, new BlockPos(9 + (i % 3) * 2, 1, 8 + (i / 3) * 3)));
        return out;
    }

    /**
     * Plan 70, MA7.1: "put 4 cooked beef in this chest", with cows in the field: it hunts them (a stone sword, the
     * swing fully charged), picks up the beef, cooks it in the furnace it carries, and puts 4 in; it never takes the
     * last two.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_hunt", timeoutTicks = 2400)
    public void putFourCookedBeefInThisChest(GameTestHelper helper) {
        Scene s = scene(helper, "Hunter");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        BlockPos chest = almostFullChest(helper, 0);
        var cows = field(helper, 6);
        var inv = s.body().getInventory();
        inv.setItem(0, new ItemStack(Items.STONE_SWORD));
        inv.setItem(1, new ItemStack(Items.FURNACE, 1));
        inv.setItem(2, new ItemStack(Items.COAL, 1));
        helper.runAfterDelay(5, () -> MineAgentEngine.onPlayerChat(s.owner(), "put 4 cooked beef in this chest"));
        helper.onEachTick(() -> {
            trace(helper, s, "hunt");
            int food = inChest(helper, chest, Items.COOKED_BEEF);
            String done = lastSaid(s, "Done");
            long alive = cows.stream().filter(net.minecraft.world.entity.Entity::isAlive).count();
            if (food >= 4 && !done.isEmpty()) {
                String plan = lastSaid(s, "On it");
                tidy(s);
                if (food == 4 && alive >= 2 && plan.contains("hunt") && plan.contains("cows") && done.contains("hunted")) helper.succeed();
                else fail(helper, "4 in, but cows alive " + alive + ", plan: " + plan + ", done: " + done);
            } else if (helper.getTick() >= 2300) {
                String how = "the chest holds " + food + " cooked beef; cows alive " + alive + "; carries beef " + inv.countItem(Items.BEEF)
                        + "; said: " + lastSaid(s, "");
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /**
     * Plan 70, MA7.3, the catalogue: "make me a bed", with three white sheep in the field, shears and planks: any bed
     * means the cheapest here, a white one; the wool is shorn, every sheep left alive; the bed is crafted at a table.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_bed", timeoutTicks = 1600)
    public void makeMeABedFromTheSheep(GameTestHelper helper) {
        Scene s = scene(helper, "Shepherd");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        field(helper, 0);
        java.util.List<net.minecraft.world.entity.animal.Sheep> sheep = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            var sh = helper.spawn(EntityType.SHEEP, new BlockPos(9 + i * 2, 1, 9));
            sh.setColor(net.minecraft.world.item.DyeColor.WHITE);
            sheep.add(sh);
        }
        var inv = s.body().getInventory();
        inv.setItem(0, new ItemStack(Items.SHEARS));
        inv.setItem(1, new ItemStack(Items.OAK_PLANKS, 3));
        inv.setItem(2, new ItemStack(Items.CRAFTING_TABLE));
        helper.runAfterDelay(5, () -> MineAgentEngine.onPlayerChat(s.owner(), "make me a bed"));
        helper.onEachTick(() -> {
            trace(helper, s, "bed");
            String done = lastSaid(s, "Done");
            long alive = sheep.stream().filter(net.minecraft.world.entity.Entity::isAlive).count();
            if (!done.isEmpty()) {
                String plan = lastSaid(s, "On it");
                int beds = inv.countItem(Items.WHITE_BED);
                tidy(s);
                if (beds == 1 && alive == 3 && plan.contains("shear") && done.contains("sheared")) helper.succeed();
                else fail(helper, "white beds " + beds + ", sheep alive " + alive + "; plan: " + plan + "; done: " + done);
            } else if (helper.getTick() >= 1500) {
                String how = "white beds " + inv.countItem(Items.WHITE_BED) + ", wool " + inv.countItem(Items.WHITE_WOOL) + ", sheep alive "
                        + alive + "; said: " + lastSaid(s, "");
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /** Solid rock east of the companion (x 4 to 22, y 1 to 4, z -12 to 12), and the companion facing into it at (3, 1, 0). */
    private static void rock(GameTestHelper helper, Scene s) {
        for (int x = 4; x <= 22; x++) for (int y = 1; y <= 4; y++) for (int z = -12; z <= 12; z++) helper.setBlock(x, y, z, Blocks.STONE);
        BlockPos at = helper.absolutePos(new BlockPos(3, 1, 0));
        s.body().teleportTo(helper.getLevel(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, -90.0f, 0.0f); // east
        s.body().setYHeadRot(-90.0f);
    }

    private static boolean is(GameTestHelper helper, int x, int y, int z, net.minecraft.world.level.block.Block b) {
        return helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(x, y, z))).is(b);
    }

    /**
     * Plan 70, MA7.8: "mine 4 iron here": a branch mine at its own height, two high, branches every third block; it
     * takes every iron ore its tunnels open, the vein behind one too, and none it never saw (one sealed in the rock
     * above). Then it comes back and says what it got.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_branch_mine", timeoutTicks = 3000)
    public void aBranchMineTakesEveryOreItOpens(GameTestHelper helper) {
        Scene s = scene(helper, "Miner");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        rock(helper, s);
        int[][] seen = {{5, 1, 1}, {5, 1, 2}, {7, 2, -4}, {6, 3, -7}};
        for (int[] o : seen) helper.setBlock(o[0], o[1], o[2], Blocks.IRON_ORE);
        helper.setBlock(8, 4, 5, Blocks.IRON_ORE); // never opened by a tunnel
        s.body().getInventory().setItem(0, new ItemStack(Items.STONE_PICKAXE));
        helper.runAfterDelay(5, () -> MineAgentEngine.onPlayerChat(s.owner(), "mine 4 iron here"));
        helper.onEachTick(() -> {
            trace(helper, s, "branch mine");
            String done = lastSaid(s, "Mined");
            if (!done.isEmpty()) {
                int raw = s.body().getInventory().countItem(Items.RAW_IRON);
                boolean allTaken = true;
                for (int[] o : seen) allTaken &= !is(helper, o[0], o[1], o[2], Blocks.IRON_ORE);
                boolean unseenLeft = is(helper, 8, 4, 5, Blocks.IRON_ORE);
                String plan = lastSaid(s, "On it: ");
                tidy(s);
                if (raw == 4 && allTaken && unseenLeft && done.startsWith("Mined 4 raw iron") && plan.contains("branch")) helper.succeed();
                else fail(helper, "raw iron " + raw + ", all opened taken " + allTaken + ", unseen left " + unseenLeft + "; plan: " + plan + "; done: " + done);
            } else if (helper.getTick() >= 2900) {
                String how = "raw iron " + s.body().getInventory().countItem(Items.RAW_IRON) + " at " + rel(helper, s.body().blockPosition()).toShortString()
                        + "; said: " + lastSaid(s, "");
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /**
     * Plan 70, MA7.8 (and MA8.4): lava behind the next block of its tunnel: the block is left standing, the lava never
     * reaches the tunnel, and the tunnel turns; it finds its iron the other way, unhurt.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_mine_lava", timeoutTicks = 2400)
    public void lavaBehindABlockIsLeftSealed(GameTestHelper helper) {
        Scene s = scene(helper, "Careful");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        rock(helper, s);
        helper.setBlock(7, 1, 0, Blocks.LAVA);
        helper.setBlock(4, 1, -2, Blocks.IRON_ORE);
        s.body().getInventory().setItem(0, new ItemStack(Items.STONE_PICKAXE));
        helper.runAfterDelay(5, () -> MineAgentEngine.onPlayerChat(s.owner(), "mine 1 iron here"));
        helper.onEachTick(() -> {
            trace(helper, s, "mine lava");
            boolean lavaIn = !helper.getLevel().getFluidState(helper.absolutePos(new BlockPos(6, 1, 0))).isEmpty()
                    || !helper.getLevel().getFluidState(helper.absolutePos(new BlockPos(5, 1, 0))).isEmpty();
            String done = lastSaid(s, "Mined");
            if (lavaIn || s.body().isOnFire() || s.body().getHealth() < s.body().getMaxHealth()) {
                String how = "lava in the tunnel " + lavaIn + ", on fire " + s.body().isOnFire() + ", health " + s.body().getHealth();
                tidy(s);
                fail(helper, how);
            } else if (!done.isEmpty()) {
                boolean wall = is(helper, 6, 1, 0, Blocks.STONE);
                boolean lava = is(helper, 7, 1, 0, Blocks.LAVA);
                int raw = s.body().getInventory().countItem(Items.RAW_IRON);
                tidy(s);
                if (wall && lava && raw == 1) helper.succeed();
                else fail(helper, "wall left " + wall + ", lava where it was " + lava + ", raw iron " + raw + "; done: " + done);
            } else if (helper.getTick() >= 2300) {
                String how = "at " + rel(helper, s.body().blockPosition()).toShortString() + "; said: " + lastSaid(s, "");
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /**
     * Plan 70, MA7.8: its pack full part way, it goes home by its tunnel, puts the haul in the chest there and comes
     * back to the face; then it finishes the order and puts that away too.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_mine_full", timeoutTicks = 3600)
    public void aFullPackGoesHomeAndComesBack(GameTestHelper helper) {
        Scene s = scene(helper, "Hauler");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        rock(helper, s);
        helper.setBlock(2, 1, 3, Blocks.CHEST);
        BlockPos chest = helper.absolutePos(new BlockPos(2, 1, 3));
        for (int[] o : new int[][] {{5, 1, 1}, {5, 2, -1}, {7, 1, 1}}) helper.setBlock(o[0], o[1], o[2], Blocks.IRON_ORE);
        var inv = s.body().getInventory();
        inv.setItem(0, new ItemStack(Items.STONE_PICKAXE));
        // 25 stacks of string for the chest (27 slots: room for the iron after), planks it keeps for tools, one slot free.
        for (int i = 1; i <= 25; i++) inv.setItem(i, new ItemStack(Items.STRING, 64));
        for (int i = 26; i < 35; i++) inv.setItem(i, new ItemStack(Items.OAK_PLANKS, 64));
        helper.runAfterDelay(5, () -> MineAgentEngine.onPlayerChat(s.owner(), "this is home"));
        helper.runAfterDelay(10, () -> MineAgentEngine.onPlayerChat(s.owner(), "mine 3 iron here"));
        helper.onEachTick(() -> {
            trace(helper, s, "mine full");
            String done = lastSaid(s, "Mined");
            if (!done.isEmpty()) {
                int string = inChest(helper, chest, Items.STRING), raw = inChest(helper, chest, Items.RAW_IRON);
                boolean went = !lastSaid(s, "My pack is full").isEmpty(), back = !lastSaid(s, "Back to the tunnel").isEmpty();
                tidy(s);
                if (string == 25 * 64 && raw == 3 && went && back && done.startsWith("Mined 3 raw iron")) helper.succeed();
                else fail(helper, "string in the chest " + string + ", raw iron " + raw + ", went home " + went + ", came back " + back + "; done: " + done);
            } else if (helper.getTick() >= 3500) {
                String how = "at " + rel(helper, s.body().blockPosition()).toShortString() + ", string in the chest "
                        + inChest(helper, chest, Items.STRING) + "; said: " + lastSaid(s, "");
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /**
     * Plan 70, MA7.4: ready before it goes. Sent mining with no food, it gets food first, from the cows it can see,
     * cooked in the furnaces it carries, and says what it has before it breaks a block; what it can't make here
     * (torches, with no wood for sticks) it goes without and says so.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_ready", timeoutTicks = 3200)
    public void withNoFoodItGetsFoodBeforeGoingDown(GameTestHelper helper) {
        Scene s = scene(helper, "Prepared");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        field(helper, 6);
        var inv = s.body().getInventory();
        inv.setItem(0, new ItemStack(Items.STONE_PICKAXE));
        inv.setItem(1, new ItemStack(Items.STONE_SWORD));
        inv.setItem(2, new ItemStack(Items.FURNACE, 4));
        inv.setItem(3, new ItemStack(Items.COAL, 2));
        helper.runAfterDelay(5, () -> MineAgentEngine.onPlayerChat(s.owner(), "mine 1 iron here"));
        helper.onEachTick(() -> {
            trace(helper, s, "ready");
            String ready = lastSaid(s, "Ready");
            if (!ready.isEmpty()) {
                int food = inv.countItem(Items.COOKED_BEEF);
                boolean first = !lastSaid(s, "Food for the trip first").isEmpty();
                boolean mined = !lastSaid(s, "Mined").isEmpty();
                tidy(s);
                // It may eat some of what it cooked on the way (hunting makes it hungry); torches it can't make here.
                if (food >= 4 && first && !mined && ready.contains("torches")) helper.succeed();
                else fail(helper, "cooked beef " + food + ", said food first " + first + ", mined already " + mined + "; ready: " + ready);
            } else if (helper.getTick() >= 3100) {
                String how = "cooked beef " + inv.countItem(Items.COOKED_BEEF) + ", beef " + inv.countItem(Items.BEEF) + "; said: " + lastSaid(s, "");
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /**
     * Plan 70, MA7.6, gathering well: "make me a crafting table" with no oak anywhere, a birch tree in sight and two
     * birch planks lying on the ground: the planks on the ground first (closest source first), the rest from the birch
     * (any log will do, and birch is the one here), never a search for oak.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_gather", timeoutTicks = 1600)
    public void noOakHereItUsesTheBirchAndThePlanksOnTheGround(GameTestHelper helper) {
        Scene s = scene(helper, "Gatherer");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        for (int y = 1; y <= 4; y++) helper.setBlock(9, y, 8, Blocks.BIRCH_LOG);
        BlockPos drop = helper.absolutePos(new BlockPos(6, 1, 5));
        var planks = new net.minecraft.world.entity.item.ItemEntity(helper.getLevel(), drop.getX() + 0.5, drop.getY() + 0.1, drop.getZ() + 0.5,
                new ItemStack(Items.BIRCH_PLANKS, 2));
        planks.setDeltaMovement(0, 0, 0);
        helper.getLevel().addFreshEntity(planks);
        var inv = s.body().getInventory();
        helper.runAfterDelay(5, () -> MineAgentEngine.onPlayerChat(s.owner(), "make me a crafting table"));
        helper.onEachTick(() -> {
            trace(helper, s, "gather");
            String done = lastSaid(s, "Done");
            if (!done.isEmpty()) {
                String plan = lastSaid(s, "On it");
                boolean table = inv.countItem(Items.CRAFTING_TABLE) >= 1;
                boolean pickedUp = !planks.isAlive();
                boolean birch = plan.contains("birch log") && !plan.contains("oak");
                tidy(s);
                if (table && pickedUp && birch) helper.succeed();
                else fail(helper, "table " + table + ", planks picked up " + pickedUp + "; plan: " + plan + "; done: " + done);
            } else if (helper.getTick() >= 1500) {
                String how = "table " + inv.countItem(Items.CRAFTING_TABLE) + ", birch planks " + inv.countItem(Items.BIRCH_PLANKS)
                        + ", logs " + inv.countItem(Items.BIRCH_LOG) + "; said: " + lastSaid(s, "");
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /**
     * Plan 70, MA6.2, MA6.3, MA6.5: "make me 4 oak planks, then make me 8 sticks, then make me a crafting table": three
     * goals in that order, each run by code; "what's next" answered from its goals; and when its owner leaves and comes
     * back between two of them, it carries on with the next.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_then", timeoutTicks = 1600)
    public void ordersInARowSurviveALeaveAndReturn(GameTestHelper helper) {
        Scene s = scene(helper, "Steady");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        s.body().getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 3));
        helper.runAfterDelay(5, () -> MineAgentEngine.onPlayerChat(s.owner(),
                "make me 4 oak planks, then make me 8 sticks, then make me a crafting table"));
        boolean[] asked = {false}, rejoined = {false};
        String[] next = {""};
        helper.onEachTick(() -> {
            trace(helper, s, "then");
            var companions = MineAgentEngine.getCompanionsByOwner(s.owner().getUUID());
            if (companions.isEmpty()) return;
            var c = companions.get(0).companion;
            var loop = TaskContext.agentLoop(c);
            if (loop == null) return;
            String got = said(loop, "Got it");
            if (!asked[0] && !got.isEmpty()) {
                asked[0] = true;
                MineAgentEngine.onPlayerChat(s.owner(), "what's next");
            }
            if (next[0].isEmpty()) next[0] = said(loop, "After this");
            if (!rejoined[0] && !said(loop, "Next: make me 8 sticks").isEmpty()) {
                rejoined[0] = true;
                MineAgentEngine.onPlayerLeave(s.owner());
                MineAgentEngine.onPlayerJoin(s.owner());
                return;
            }
            var inv = c.serverPlayer().getInventory();
            // All three made, and the last goal marked done (the heartbeat that does it runs once a second).
            if (rejoined[0] && inv.countItem(Items.STICK) >= 8 && inv.countItem(Items.CRAFTING_TABLE) >= 1
                    && (loop.goalBook().active().isEmpty() || helper.getTick() >= 1400)) {
                boolean allDone = loop.goalBook().active().isEmpty();
                String how = "got it: " + got + "; next: " + next[0] + "; all done " + allDone;
                MineAgentEngine.despawnCompanion(c.companionId(), true);
                s.owner().getServer().getPlayerList().remove(s.owner());
                if (got.contains("make me 4 oak planks, then make me 8 sticks, then make me a crafting table")
                        && next[0].startsWith("After this: make me 8 sticks, then make me a crafting table") && allDone) helper.succeed();
                else fail(helper, how);
            } else if (helper.getTick() >= 1500) {
                String how = "rejoined " + rejoined[0] + ", sticks " + inv.countItem(Items.STICK) + ", tables " + inv.countItem(Items.CRAFTING_TABLE)
                        + ", planks " + inv.countItem(Items.OAK_PLANKS) + ", goal " + loop.goalBook().active().map(g -> g.text()).orElse("none")
                        + "; said: " + said(loop, "");
                MineAgentEngine.despawnCompanion(c.companionId(), true);
                s.owner().getServer().getPlayerList().remove(s.owner());
                fail(helper, how);
            }
        });
    }

    private static String said(com.mineagent.engine.loop.AgentLoop loop, String startingWith) {
        var said = loop.journal().recent(60).stream()
                .filter(e -> "said".equals(e.kind()) && e.text().startsWith(startingWith)).toList();
        return said.isEmpty() ? "" : said.get(said.size() - 1).text();
    }

    /**
     * Plan 70, MA6.1: an order interrupted by a fight carries on after it. Mid-smelt a zombie comes at it; it fights
     * (its sword) and then fills the chest as if nothing happened.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_fight_resume", timeoutTicks = 2600)
    public void anOrderCarriesOnAfterAFight(GameTestHelper helper) {
        Scene s = scene(helper, "Steadfast");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        BlockPos chest = almostFullChest(helper, 48);
        var inv = s.body().getInventory();
        inv.setItem(0, new ItemStack(Items.STONE_SWORD));
        inv.setItem(1, new ItemStack(Items.COBBLESTONE, 16));
        inv.setItem(2, new ItemStack(Items.COAL, 2));
        inv.setItem(3, new ItemStack(Items.FURNACE, 4));
        helper.runAfterDelay(5, () -> MineAgentEngine.onPlayerChat(s.owner(), "fill this chest with stone bricks"));
        net.minecraft.world.entity.monster.Zombie[] zombie = {null};
        helper.runAfterDelay(160, () -> {
            BlockPos at = s.body().blockPosition().offset(4, 0, 0);
            var z = EntityType.ZOMBIE.create(helper.getLevel());
            z.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            z.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET)); // no burning in the sun
            z.setTarget(s.body());
            helper.getLevel().addFreshEntity(z);
            zombie[0] = z;
        });
        helper.onEachTick(() -> {
            trace(helper, s, "fight resume");
            int bricks = inChest(helper, chest, Items.STONE_BRICKS);
            String done = lastSaid(s, "Your chest is full");
            if (bricks == 64 && !done.isEmpty()) {
                boolean fought = zombie[0] != null && !zombie[0].isAlive();
                tidy(s);
                if (fought) helper.succeed();
                else fail(helper, "filled, but the zombie " + (zombie[0] == null ? "never came" : "is still alive"));
            } else if (helper.getTick() >= 2500) {
                String how = "bricks " + bricks + ", zombie " + (zombie[0] == null ? "none" : zombie[0].isAlive() ? "alive" : "dead")
                        + ", health " + s.body().getHealth() + "; said: " + lastSaid(s, "");
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /**
     * Plan 70, MA6.4, the recovery ladder: an order for a chest that isn't there fails; its owner is here, so it asks
     * (it doesn't guess, and doesn't ask the model about a chest only they know); the chest put down and "carry on"
     * said, it does it.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_ladder", timeoutTicks = 800)
    public void aFailedOrderAsksThenCarriesOn(GameTestHelper helper) {
        Scene s = scene(helper, "Asker");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        s.body().getInventory().setItem(1, new ItemStack(Items.COBBLESTONE, 16));
        BlockPos chest = helper.absolutePos(new BlockPos(7, 1, 3));
        helper.runAfterDelay(5, () -> MineAgentEngine.onPlayerChat(s.owner(),
                "put 16 cobblestone in the chest at " + chest.getX() + ", " + chest.getY() + ", " + chest.getZ()));
        boolean[] placed = {false};
        helper.onEachTick(() -> {
            trace(helper, s, "ladder");
            String asked = lastSaid(s, "I couldn't put 16 cobblestone");
            if (!placed[0] && !asked.isEmpty()) {
                placed[0] = true;
                helper.setBlock(7, 1, 3, Blocks.CHEST);
                MineAgentEngine.onPlayerChat(s.owner(), "carry on");
                return;
            }
            int in = placed[0] ? inChest(helper, chest, Items.COBBLESTONE) : 0;
            if (in == 16) {
                tidy(s);
                if (asked.contains("there is no chest")) helper.succeed();
                else fail(helper, "filled, but it asked: " + asked);
            } else if (helper.getTick() >= 750) {
                String how = "asked: " + asked + "; in the chest " + in + "; said: " + lastSaid(s, "");
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /** Plan 70, MA8.1: stepped into lava, it gets out onto the nearest ground, alive. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_lava", timeoutTicks = 200)
    public void steppedIntoLavaItGetsOut(GameTestHelper helper) {
        Scene s = scene(helper, "Hotfoot");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        for (int x = 6; x <= 8; x++) for (int z = 6; z <= 8; z++) helper.setBlock(x, 0, z, Blocks.LAVA);
        BlockPos in = helper.absolutePos(new BlockPos(7, 0, 7));
        s.body().teleportTo(helper.getLevel(), in.getX() + 0.5, in.getY(), in.getZ() + 0.5, 0.0f, 0.0f);
        int[] outFor = {0};
        helper.onEachTick(() -> {
            trace(helper, s, "lava");
            var body = s.body();
            if (body.isDeadOrDying()) {
                tidy(s);
                fail(helper, "it died in the lava");
                return;
            }
            if (helper.getTick() > 5 && !body.isInLava()) outFor[0]++;
            else outFor[0] = 0;
            if (outFor[0] >= 20) {
                float health = body.getHealth();
                tidy(s);
                helper.succeed();
                System.out.println("[SCENE lava] out of the lava with " + health + " health");
            } else if (helper.getTick() >= 180) {
                String how = "still in lava " + body.isInLava() + ", at " + rel(helper, body.blockPosition()).toShortString() + ", health " + body.getHealth();
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /** Plan 70, MA8.1: set on fire with a water bucket, it pours water on itself, and takes it back up after. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_fire", timeoutTicks = 200)
    public void setOnFireItPutsItselfOut(GameTestHelper helper) {
        Scene s = scene(helper, "Doused");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        var inv = s.body().getInventory();
        inv.setItem(5, new ItemStack(Items.WATER_BUCKET));
        helper.runAfterDelay(5, () -> s.body().setRemainingFireTicks(300));
        int[] outAt = {-1};
        helper.onEachTick(() -> {
            trace(helper, s, "fire");
            var body = s.body();
            if (helper.getTick() > 6 && outAt[0] < 0 && !body.isOnFire()) outAt[0] = (int) helper.getTick();
            boolean puddle = false;
            BlockPos at = body.blockPosition();
            for (BlockPos p : BlockPos.betweenClosed(at.offset(-2, -1, -2), at.offset(2, 1, 2))) {
                if (helper.getLevel().getFluidState(p).is(net.minecraft.tags.FluidTags.WATER)) puddle = true;
            }
            if (outAt[0] > 0 && !puddle && inv.countItem(Items.WATER_BUCKET) == 1) {
                int took = outAt[0] - 5;
                tidy(s);
                if (took <= 40) helper.succeed();
                else fail(helper, "out only after " + took + " ticks");
            } else if (helper.getTick() >= 180) {
                String how = "on fire " + body.isOnFire() + ", out at " + outAt[0] + ", water near " + puddle + ", water buckets "
                        + inv.countItem(Items.WATER_BUCKET) + ", buckets " + inv.countItem(Items.BUCKET);
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /**
     * Plan 70, MA8.2: three zombies at once, and it has no weapon: it doesn't stand and trade hits; it retreats (or,
     * cornered, pillars up out of reach), and is alive after.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_retreat", timeoutTicks = 400)
    public void threeZombiesItRetreats(GameTestHelper helper) {
        Scene s = scene(helper, "Wary");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        s.body().getInventory().setItem(8, new ItemStack(Items.DIRT, 16));
        BlockPos me = helper.absolutePos(new BlockPos(4, 1, 4));
        s.body().teleportTo(helper.getLevel(), me.getX() + 0.5, me.getY(), me.getZ() + 0.5, 0.0f, 0.0f);
        java.util.List<net.minecraft.world.entity.monster.Zombie> zombies = new java.util.ArrayList<>();
        helper.runAfterDelay(5, () -> {
            for (int i = 0; i < 3; i++) {
                var z = EntityType.ZOMBIE.create(helper.getLevel());
                BlockPos at = me.offset(4, 0, i - 1);
                z.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                z.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
                z.setTarget(s.body());
                helper.getLevel().addFreshEntity(z);
                zombies.add(z);
            }
        });
        helper.onEachTick(() -> {
            trace(helper, s, "retreat");
            var body = s.body();
            if (body.isDeadOrDying()) {
                for (var z : zombies) z.discard();
                tidy(s);
                fail(helper, "it died");
                return;
            }
            if (helper.getTick() < 160) return;
            double nearest = Double.MAX_VALUE;
            for (var z : zombies) if (z.isAlive()) nearest = Math.min(nearest, z.distanceTo(body));
            double up = body.getY() - me.getY();
            boolean away = nearest >= 6.0 || up >= 2.0;
            String how = String.format("nearest zombie %.1f, %.1f up, health %.1f", nearest, up, body.getHealth());
            for (var z : zombies) z.discard();
            tidy(s);
            if (away) helper.succeed();
            else fail(helper, "it stayed among them: " + how);
        });
    }

    /** Plan 70, MA8.3: night, its owner away, a bed in its pack: it puts the bed down and sleeps. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_night_bed", timeoutTicks = 400)
    public void nightWithABedItSleeps(GameTestHelper helper) {
        Scene s = scene(helper, "Sleeper");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        s.body().getInventory().setItem(4, new ItemStack(Items.WHITE_BED));
        helper.runAfterDelay(5, () -> MineAgentEngine.onPlayerChat(s.owner(), "going afk"));
        helper.runAfterDelay(10, () -> helper.getLevel().setDayTime(13_000));
        helper.onEachTick(() -> {
            trace(helper, s, "night bed");
            if (s.body().isSleeping()) {
                helper.getLevel().setDayTime(1_000);
                tidy(s);
                helper.succeed();
            } else if (helper.getTick() >= 380) {
                String how = "not asleep; bed carried " + s.body().getInventory().countItem(Items.WHITE_BED) + ", owner afk " + s.loop().ownerAfk()
                        + ", day time " + helper.getLevel().getDayTime() + "; said: " + lastSaid(s, "");
                helper.getLevel().setDayTime(1_000);
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /** Plan 70, MA8.3: night, its owner away, no bed: walled in until morning, then out again. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_night_none", timeoutTicks = 900)
    public void nightWithNoBedItShelters(GameTestHelper helper) {
        Scene s = scene(helper, "Huddled");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        s.body().getInventory().setItem(4, new ItemStack(Items.DIRT, 16));
        helper.runAfterDelay(5, () -> MineAgentEngine.onPlayerChat(s.owner(), "going afk"));
        helper.runAfterDelay(10, () -> helper.getLevel().setDayTime(13_000));
        int[] shelteredAt = {-1};
        helper.onEachTick(() -> {
            trace(helper, s, "night none");
            var body = s.body();
            BlockPos at = body.blockPosition();
            // Walled in and roofed over (as far as its blocks go: it says if any gap is left).
            boolean walled = !lastSaid(s, "Walled in").isEmpty() && !helper.getLevel().canSeeSky(at.above());
            if (shelteredAt[0] < 0 && walled) {
                shelteredAt[0] = (int) helper.getTick();
                helper.getLevel().setDayTime(1_000); // morning
            }
            if (shelteredAt[0] > 0 && helper.getLevel().canSeeSky(at.above())) {
                tidy(s);
                helper.succeed();
            } else if (helper.getTick() >= 880) {
                String how = "sheltered at " + shelteredAt[0] + ", walled now " + walled + ", sky " + helper.getLevel().canSeeSky(at.above())
                        + "; said: " + lastSaid(s, "");
                helper.getLevel().setDayTime(1_000);
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /**
     * Plan 70, MA8.5, food that renews: "make me 2 bread" by a field of ripe wheat: it harvests what it needs, plants a
     * seed back in every cell it took, and bakes the bread at the table it carries.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_farm", timeoutTicks = 1600)
    public void aWheatFieldHarvestedAndReplanted(GameTestHelper helper) {
        Scene s = scene(helper, "Farmer");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        var ripe = ((net.minecraft.world.level.block.CropBlock) Blocks.WHEAT).getStateForAge(7);
        for (int x = 8; x <= 10; x++) for (int z = 8; z <= 10; z++) {
            helper.setBlock(x, 0, z, Blocks.FARMLAND);
            helper.setBlock(x, 1, z, ripe);
        }
        helper.setBlock(9, 0, 11, Blocks.WATER);
        s.body().getInventory().setItem(3, new ItemStack(Items.CRAFTING_TABLE));
        helper.runAfterDelay(5, () -> MineAgentEngine.onPlayerChat(s.owner(), "make me 2 bread"));
        helper.onEachTick(() -> {
            trace(helper, s, "farm");
            String done = lastSaid(s, "Done");
            if (!done.isEmpty()) {
                int bread = s.body().getInventory().countItem(Items.BREAD);
                int planted = 0, young = 0;
                for (int x = 8; x <= 10; x++) for (int z = 8; z <= 10; z++) {
                    var st = helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(x, 1, z)));
                    if (st.is(Blocks.WHEAT)) {
                        planted++;
                        if (!((net.minecraft.world.level.block.CropBlock) Blocks.WHEAT).isMaxAge(st)) young++;
                    }
                }
                String plan = lastSaid(s, "On it");
                tidy(s);
                // 6 wheat for 2 bread: at least 6 cells harvested, every cell still growing wheat.
                if (bread == 2 && planted == 9 && young >= 6 && plan.contains("harvest")) helper.succeed();
                else fail(helper, "bread " + bread + ", cells with wheat " + planted + ", replanted " + young + "; plan: " + plan + "; done: " + done);
            } else if (helper.getTick() >= 1500) {
                String how = "bread " + s.body().getInventory().countItem(Items.BREAD) + ", wheat " + s.body().getInventory().countItem(Items.WHEAT)
                        + "; said: " + lastSaid(s, "");
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /** Plan 70, MA8.5: "give me 4 bread": it comes to its owner and hands 4 over, keeping the rest. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_give", timeoutTicks = 400)
    public void giveMeFourBread(GameTestHelper helper) {
        Scene s = scene(helper, "Giver");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        s.body().getInventory().setItem(2, new ItemStack(Items.BREAD, 8));
        BlockPos far = helper.absolutePos(new BlockPos(12, 1, 12));
        s.body().teleportTo(helper.getLevel(), far.getX() + 0.5, far.getY(), far.getZ() + 0.5, 0.0f, 0.0f);
        helper.runAfterDelay(5, () -> MineAgentEngine.onPlayerChat(s.owner(), "give me 4 bread"));
        helper.onEachTick(() -> {
            trace(helper, s, "give");
            String here = lastSaid(s, "Here");
            if (!here.isEmpty()) {
                int kept = s.body().getInventory().countItem(Items.BREAD);
                int handed = s.owner().getInventory().countItem(Items.BREAD);
                for (var e : helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                        s.owner().getBoundingBox().inflate(4.0), e -> e.getItem().is(Items.BREAD))) handed += e.getItem().getCount();
                double near = s.body().distanceTo(s.owner());
                tidy(s);
                if (kept == 4 && handed == 4 && near <= 4.0) helper.succeed();
                else fail(helper, String.format("kept %d, handed %d, %.1f from its owner; said: %s", kept, handed, near, here));
            } else if (helper.getTick() >= 380) {
                String how = "said: " + lastSaid(s, "") + "; bread " + s.body().getInventory().countItem(Items.BREAD);
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /**
     * Plan 70, MA8.8, living on its own with no model (the scenes' SMH is not there): its owner away, logs and wool in a
     * chest it knows. By day it makes a bed from them, by code; at night it puts the bed down and sleeps in it.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_own", timeoutTicks = 1800)
    public void onItsOwnItMakesABedBeforeNight(GameTestHelper helper) {
        Scene s = scene(helper, "Settler");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        helper.setBlock(6, 1, 3, Blocks.CHEST);
        BlockPos chest = helper.absolutePos(new BlockPos(6, 1, 3));
        var c = (net.minecraft.world.Container) helper.getLevel().getBlockEntity(chest);
        c.setItem(0, new ItemStack(Items.WHITE_WOOL, 3));
        c.setItem(1, new ItemStack(Items.OAK_LOG, 3));
        c.setChanged();
        String dim = helper.getLevel().dimension().location().toString();
        s.loop().noteChest(dim, chest.getX(), chest.getY(), chest.getZ(),
                java.util.Map.of("minecraft:white_wool", 3, "minecraft:oak_log", 3), helper.getLevel().getGameTime());
        helper.getLevel().setDayTime(1_000);
        helper.runAfterDelay(5, () -> MineAgentEngine.onPlayerChat(s.owner(), "going afk"));
        boolean[] night = {false};
        helper.onEachTick(() -> {
            trace(helper, s, "own");
            var inv = s.body().getInventory();
            if (!night[0] && inv.countItem(Items.WHITE_BED) >= 1) {
                night[0] = true;
                helper.getLevel().setDayTime(13_000);
            }
            if (night[0] && s.body().isSleeping()) {
                helper.getLevel().setDayTime(1_000);
                tidy(s);
                helper.succeed();
            } else if (helper.getTick() >= 1750) {
                String how = "bed made " + night[0] + ", asleep " + s.body().isSleeping() + ", wool " + inv.countItem(Items.WHITE_WOOL)
                        + ", logs " + inv.countItem(Items.OAK_LOG) + ", planks " + inv.countItem(Items.OAK_PLANKS) + "; said: " + lastSaid(s, "");
                helper.getLevel().setDayTime(1_000);
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /**
     * Plan 70, MA9.2: "keep this chest full of spruce logs": the chest by its owner is filled from the spruce near, a
     * second chest further off is left alone, and the order stands: with the chest full it looks again later, and says
     * nothing failed.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_stock", timeoutTicks = 1600)
    public void keepThisChestFullOfSpruceLogs(GameTestHelper helper) {
        Scene s = scene(helper, "Stocker");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        helper.setBlock(5, 1, 3, Blocks.CHEST);
        helper.setBlock(9, 1, 3, Blocks.CHEST);
        BlockPos mine = helper.absolutePos(new BlockPos(5, 1, 3)), other = helper.absolutePos(new BlockPos(9, 1, 3));
        var c = (net.minecraft.world.Container) helper.getLevel().getBlockEntity(mine);
        for (int i = 0; i < c.getContainerSize() - 1; i++) c.setItem(i, new ItemStack(Items.DIRT, 64));
        c.setItem(c.getContainerSize() - 1, new ItemStack(Items.SPRUCE_LOG, 60));
        c.setChanged();
        for (int y = 1; y <= 5; y++) helper.setBlock(11, y, 9, Blocks.SPRUCE_LOG);
        helper.runAfterDelay(5, () -> MineAgentEngine.onPlayerChat(s.owner(), "keep this chest full of spruce logs"));
        helper.onEachTick(() -> {
            trace(helper, s, "stock");
            int logs = inChest(helper, mine, Items.SPRUCE_LOG), stray = inChest(helper, other, Items.SPRUCE_LOG);
            String failed = lastSaid(s, "I couldn't");
            if (logs == 64 && helper.getTick() > 200) {
                boolean standing = s.loop().goalBook().active().map(g -> com.mineagent.engine.goal.Orders.stands(g.text())).orElse(false);
                tidy(s);
                if (stray == 0 && failed.isEmpty() && standing) helper.succeed();
                else fail(helper, "filled; other chest " + stray + ", failed: " + failed + ", still standing " + standing);
            } else if (helper.getTick() >= 1500) {
                String how = "logs in the chest " + logs + ", other chest " + stray + "; said: " + lastSaid(s, "");
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /**
     * Plan 70, MA9.1: "build a hut": on flat ground near it, walls from the inside, the roof from the walls in, the
     * torch, the door from in front; then every block checked, here as well: all 57 where the blueprint has them.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_build", timeoutTicks = 2400)
    public void buildAHutAndCheckItBlockByBlock(GameTestHelper helper) {
        Scene s = scene(helper, "Builder");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        var inv = s.body().getInventory();
        inv.setItem(1, new ItemStack(Items.OAK_PLANKS, 64));
        inv.setItem(2, new ItemStack(Items.OAK_DOOR));
        inv.setItem(3, new ItemStack(Items.TORCH));
        helper.runAfterDelay(5, () -> MineAgentEngine.onPlayerChat(s.owner(), "build a hut"));
        helper.onEachTick(() -> {
            trace(helper, s, "build");
            String built = lastSaid(s, "Built the hut at ");
            if (!built.isEmpty()) {
                var m = java.util.regex.Pattern.compile("at (-?\\d+), (-?\\d+), (-?\\d+)").matcher(built);
                int wrong = -1;
                if (m.find()) {
                    BlockPos corner = new BlockPos(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
                    wrong = 0;
                    for (var b : com.mineagent.engine.planning.building.Blueprint.hut("minecraft:oak_planks", "minecraft:oak_door").blocks()) {
                        var want = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.tryParse(b.id()));
                        if (!helper.getLevel().getBlockState(corner.offset(b.x(), b.y() - 1, b.z())).is(want)) wrong++;
                    }
                }
                tidy(s);
                if (wrong == 0 && built.contains("every block checked")) helper.succeed();
                else fail(helper, "blocks not as the blueprint has them: " + wrong + "; said: " + built);
            } else if (helper.getTick() >= 2300) {
                String how = "planks left " + inv.countItem(Items.OAK_PLANKS) + ", at " + rel(helper, s.body().blockPosition()).toShortString()
                        + "; said: " + lastSaid(s, "");
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /** Plan 70, MA9.4: "light up the area": torches down until no spot within 8 blocks a mob could stand on is dark. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_light", timeoutTicks = 1600)
    public void anAreaLitUntilNoSpotIsDark(GameTestHelper helper) {
        Scene s = scene(helper, "Lamplighter");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        s.body().getInventory().setItem(2, new ItemStack(Items.TORCH, 32));
        helper.runAfterDelay(5, () -> MineAgentEngine.onPlayerChat(s.owner(), "light up the area"));
        helper.onEachTick(() -> {
            trace(helper, s, "light");
            String lit = lastSaid(s, "Lit up");
            if (!lit.isEmpty()) {
                BlockPos c = s.owner().blockPosition();
                int dark = 0;
                for (BlockPos p : BlockPos.betweenClosed(c.offset(-8, -3, -8), c.offset(8, 3, 8))) {
                    if (p.distSqr(c) > 64) continue;
                    if (com.mineagent.engine.task.LightTask.spawnable(helper.getLevel(), p)
                            && helper.getLevel().getBrightness(net.minecraft.world.level.LightLayer.BLOCK, p) == 0) dark++;
                }
                tidy(s);
                if (dark == 0) helper.succeed();
                else fail(helper, dark + " dark spots left; said: " + lit);
            } else if (helper.getTick() >= 1500) {
                String how = "torches left " + s.body().getInventory().countItem(Items.TORCH) + "; said: " + lastSaid(s, "");
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /**
     * Plan 70, MA9.3, a small prime ("prime the world" with the settings scaled down: a helmet each, 4 meat each, no
     * torches), in a field with sheep and cows, the iron already mined: a hut built and checked, the helmets smelted
     * and made, beds from shorn wool, food hunted and cooked, the area lit, and its owner's share handed over.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_prime", timeoutTicks = 9000)
    public void aSmallPrime(GameTestHelper helper) {
        setting(helper, "behaviour.primeFullIron", "off");
        setting(helper, "behaviour.primeMeat", "4");
        setting(helper, "behaviour.primeTorches", "0");
        Scene s = scene(helper, "Pioneer");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        field(helper, 6);
        for (int i = 0; i < 3; i++) {
            var sh = helper.spawn(EntityType.SHEEP, new BlockPos(2 + i * 2, 1, 12));
            sh.setColor(net.minecraft.world.item.DyeColor.WHITE);
        }
        var inv = s.body().getInventory();
        inv.setItem(1, new ItemStack(Items.OAK_PLANKS, 64));
        inv.setItem(2, new ItemStack(Items.OAK_DOOR));
        inv.setItem(3, new ItemStack(Items.TORCH, 16));
        inv.setItem(4, new ItemStack(Items.RAW_IRON, 12));
        inv.setItem(5, new ItemStack(Items.COAL, 8));
        inv.setItem(6, new ItemStack(Items.FURNACE, 4));
        inv.setItem(7, new ItemStack(Items.STONE_SWORD));
        inv.setItem(8, new ItemStack(Items.OAK_PLANKS, 16));
        helper.runAfterDelay(5, () -> MineAgentEngine.onPlayerChat(s.owner(), "prime the world"));
        Runnable restore = () -> {
            setting(helper, "behaviour.primeFullIron", "on");
            setting(helper, "behaviour.primeMeat", "64");
            setting(helper, "behaviour.primeTorches", "32");
        };
        helper.onEachTick(() -> {
            if (helper.getTick() % 400 == 0) trace(helper, s, "prime");
            var owner = s.owner();
            int helmets = owner.getInventory().countItem(Items.IRON_HELMET), beds = owner.getInventory().countItem(Items.WHITE_BED);
            for (var e : helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, owner.getBoundingBox().inflate(4))) {
                if (e.getItem().is(Items.IRON_HELMET)) helmets += e.getItem().getCount();
                if (e.getItem().is(Items.WHITE_BED)) beds += e.getItem().getCount();
            }
            boolean hut = !lastSaid(s, "Built the hut at ").isEmpty() && lastSaid(s, "Built the hut at ").contains("every block checked");
            boolean done = s.loop().goalBook().active().isEmpty() && !lastSaid(s, "Got it").isEmpty() && helper.getTick() > 100;
            if (done) {
                int mine = s.body().getInventory().countItem(Items.IRON_HELMET);
                String how = "hut " + hut + ", helmets: owner " + helmets + ", its own " + mine + "; beds to owner " + beds + "; said: " + lastSaid(s, "");
                restore.run();
                tidy(s);
                if (hut && helmets >= 1 && mine >= 1 && beds >= 1) helper.succeed();
                else fail(helper, how);
            } else if (helper.getTick() >= 8900) {
                String how = "goal " + s.loop().goalBook().active().map(g -> g.text()).orElse("none") + "; hut " + hut + ", helmets to owner " + helmets
                        + ", beds " + beds + "; said: " + lastSaid(s, "");
                restore.run();
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /** Plan 70, MA7.5: "make me 8 iron ingots" from 8 raw iron: one furnace, one coal, 8 ingots out. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_smelt_iron", timeoutTicks = 2400)
    public void eightRawIronInEightIngotsOut(GameTestHelper helper) {
        Scene s = scene(helper, "Smith");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        var inv = s.body().getInventory();
        inv.setItem(1, new ItemStack(Items.RAW_IRON, 8));
        inv.setItem(2, new ItemStack(Items.COAL, 1));
        inv.setItem(3, new ItemStack(Items.FURNACE, 1));
        helper.runAfterDelay(5, () -> MineAgentEngine.onPlayerChat(s.owner(), "make me 8 iron ingots"));
        helper.onEachTick(() -> {
            trace(helper, s, "smelt iron");
            String done = lastSaid(s, "Done");
            if (!done.isEmpty()) {
                String plan = lastSaid(s, "On it");
                int ingots = inv.countItem(Items.IRON_INGOT), raw = inv.countItem(Items.RAW_IRON), coal = inv.countItem(Items.COAL);
                tidy(s);
                if (ingots == 8 && raw == 0 && coal == 0 && plan.contains("smelt 8 raw iron into iron ingot in 1 furnace with 1 coal")
                        && done.contains("1 coal used")) helper.succeed();
                else fail(helper, "ingots " + ingots + ", raw " + raw + ", coal " + coal + "; plan: " + plan + "; done: " + done);
            } else if (helper.getTick() >= 2300) {
                String how = "ingots " + inv.countItem(Items.IRON_INGOT) + ", raw " + inv.countItem(Items.RAW_IRON) + "; said: " + lastSaid(s, "");
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /**
     * Plan 70, MA7.9: 64 stone bricks made from cobblestone with the 4 furnaces it carries (8 coal: two each), into an
     * empty chest, and the chest read back from its memory.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_bricks_64", timeoutTicks = 5000)
    public void sixtyFourStoneBricksWithFourFurnaces(GameTestHelper helper) {
        Scene s = scene(helper, "Bricklayer");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        BlockPos chest = almostFullChest(helper, 0);
        var inv = s.body().getInventory();
        inv.setItem(1, new ItemStack(Items.COBBLESTONE, 64));
        inv.setItem(2, new ItemStack(Items.COAL, 8));
        inv.setItem(3, new ItemStack(Items.FURNACE, 4));
        helper.runAfterDelay(5, () -> MineAgentEngine.onPlayerChat(s.owner(), "fill this chest with stone bricks"));
        helper.onEachTick(() -> {
            trace(helper, s, "bricks 64");
            int bricks = inChest(helper, chest, Items.STONE_BRICKS);
            String done = lastSaid(s, "Your chest is full");
            if (bricks == 64 && !done.isEmpty()) {
                String plan = lastSaid(s, "On it");
                int furnaces = furnacesAround(helper);
                var dim = helper.getLevel().dimension().location().toString();
                var remembered = s.loop().chestMemory().near(dim, chest.getX(), chest.getY(), chest.getZ(), 1.0);
                boolean readBack = !remembered.isEmpty() && remembered.get(0).items().getOrDefault("stone_bricks",
                        remembered.get(0).items().getOrDefault("minecraft:stone_bricks", 0)) == 64;
                tidy(s);
                if (furnaces == 4 && readBack && plan.contains("smelt 64 cobblestone into stone in 4 furnaces with 8 coal")) helper.succeed();
                else fail(helper, "filled, but furnaces " + furnaces + ", remembered " + remembered + ", plan: " + plan);
            } else if (helper.getTick() >= 4900) {
                String how = "the chest holds " + bricks + " stone bricks; carries cobblestone " + inv.countItem(Items.COBBLESTONE)
                        + ", stone " + inv.countItem(Items.STONE) + ", coal " + inv.countItem(Items.COAL) + "; said: " + lastSaid(s, "");
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /** A chest at (7, 1, 3) with every slot but the last full of dirt (and that one holding {@code bricks} stone bricks). */
    private static BlockPos almostFullChest(GameTestHelper helper, int bricks) {
        helper.setBlock(7, 1, 3, Blocks.CHEST);
        BlockPos chest = helper.absolutePos(new BlockPos(7, 1, 3));
        var c = (net.minecraft.world.Container) helper.getLevel().getBlockEntity(chest);
        for (int i = 0; i < c.getContainerSize() - 1; i++) c.setItem(i, new ItemStack(Items.DIRT, 64));
        if (bricks > 0) c.setItem(c.getContainerSize() - 1, new ItemStack(Items.STONE_BRICKS, bricks));
        c.setChanged();
        return chest;
    }

    private static int furnacesAround(GameTestHelper helper) {
        int n = 0;
        for (int x = -16; x <= 24; x++) for (int z = -16; z <= 24; z++) for (int y = 0; y <= 3; y++) {
            if (helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(x, y, z))).is(Blocks.FURNACE)) n++;
        }
        return n;
    }

    /**
     * Plan 70, crafting knowledge: what the game says, read from the game: the stone bricks recipe, cobblestone from
     * stone with a pickaxe, stone with Silk Touch, coal's burn time, a stone pickaxe's uses; and a whole chest of stone
     * bricks planned from it.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_knowledge", timeoutTicks = 100)
    public void itKnowsTheGamesRecipesAndDropsFromTheGame(GameTestHelper helper) {
        Scene s = scene(helper, "Scholar");
        var book = com.mineagent.engine.planning.production.GameKnowledge.book(s.body());
        var bricks = book.recipes("minecraft:stone_bricks").stream()
                .filter(r -> r.kind() == com.mineagent.engine.planning.production.Production.Kind.CRAFT).findFirst();
        boolean recipe = bricks.isPresent() && bricks.get().outputCount() == 4 && !bricks.get().needsTable()
                && bricks.get().needs().get(0).anyOf().contains("minecraft:stone") && bricks.get().needs().get(0).count() == 4;
        boolean cobble = book.sources("minecraft:cobblestone").stream().anyMatch(x -> x.block().equals("minecraft:stone")
                && x.tool().equals("pickaxe") && x.tier() == 1 && !x.silkTouch());
        boolean silk = book.sources("minecraft:stone").stream().anyMatch(x -> x.block().equals("minecraft:stone") && x.silkTouch());
        boolean noHand = book.sources("minecraft:cobblestone").stream().noneMatch(x -> x.block().equals("minecraft:stone") && x.tier() == 0);
        boolean smelt = book.recipes("minecraft:stone").stream().anyMatch(r -> r.kind() == com.mineagent.engine.planning.production.Production.Kind.SMELT
                && r.cookTicks() == 200 && r.needs().get(0).anyOf().contains("minecraft:cobblestone"));
        boolean coal = book.fuelTicks().getOrDefault("minecraft:coal", 0) == 1600;
        boolean uses = book.durability().getOrDefault("minecraft:stone_pickaxe", 0) == 131;
        // MA7.1's own checks: iron ore needs a stone pickaxe; a cow drops beef (its loot table, rolled in memory).
        boolean iron = book.sources("minecraft:raw_iron").stream().anyMatch(x -> x.block().equals("minecraft:iron_ore") && x.tier() == 2)
                && book.sources("minecraft:raw_iron").stream().noneMatch(x -> x.block().equals("minecraft:iron_ore") && x.tier() < 2);
        boolean beef = book.sources("minecraft:beef").stream().anyMatch(x -> x.block().equals("entity:minecraft:cow")
                && x.perBlock() >= 1.0 && x.perBlock() <= 3.0);
        boolean noPets = book.sourcesByItem().values().stream().flatMap(java.util.List::stream)
                .noneMatch(x -> x.block().equals("entity:minecraft:wolf") || x.block().equals("entity:minecraft:horse")
                        || x.block().equals("entity:minecraft:cat"));
        boolean smoker = book.recipes("minecraft:cooked_beef").stream().anyMatch(r -> r.kind() == com.mineagent.engine.planning.production.Production.Kind.SMOKE
                && r.cookTicks() == 100);
        boolean blast = book.recipes("minecraft:iron_ingot").stream().anyMatch(r -> r.kind() == com.mineagent.engine.planning.production.Production.Kind.BLAST
                && r.cookTicks() == 100);
        // Storage blocks are built, not found: no plan mines an iron block for its ingots.
        boolean noStorage = book.sources("minecraft:iron_block").isEmpty() && book.sources("minecraft:coal_block").isEmpty();
        boolean foods = com.mineagent.engine.planning.production.GameKnowledge.foods().containsKey("minecraft:cooked_beef")
                && !com.mineagent.engine.planning.production.GameKnowledge.foods().containsKey("minecraft:rotten_flesh");
        var have = new com.mineagent.engine.planning.production.Production.Have(java.util.Map.of("minecraft:coal", 216, "minecraft:oak_log", 16),
                java.util.Map.of(), java.util.List.of(new com.mineagent.engine.planning.production.Production.Tool("pickaxe", 2, false, 131)),
                0, true, java.util.Map.of("minecraft:stone", com.mineagent.engine.planning.production.Production.Where.COMMON,
                        "minecraft:oak_log", com.mineagent.engine.planning.production.Production.Where.COMMON), java.util.Map.of());
        var plan = com.mineagent.engine.planning.production.ProductionPlanner.plan(book, have, "minecraft:stone_bricks", 1_728);
        System.out.println("[SCENE knowledge] " + book.recipesByOutput().size() + " items with recipes, " + book.sourcesByItem().size()
                + " with blocks that drop them; a chest of stone bricks: " + plan.summary());
        String how = String.format("recipe %b, cobblestone from stone %b, silk %b, not by hand %b, smelt %b, coal %b, uses %b, "
                        + "iron needs stone %b (%s), cow beef %b (%s), no pets %b, smoker %b, blast %b, foods %b, no storage %b, plan %s",
                recipe, cobble, silk, noHand, smelt, coal, uses, iron, book.sources("minecraft:raw_iron"), beef,
                book.sources("minecraft:beef"), noPets, smoker, blast, foods, noStorage, plan.summary());
        System.out.println("[SCENE knowledge] " + how);
        tidy(s);
        if (recipe && cobble && silk && noHand && smelt && coal && uses && iron && beef && noPets && smoker && blast && foods && noStorage
                && !plan.summary().contains("iron") && plan.possible()
                && plan.steps().stream().anyMatch(x -> x.action() == com.mineagent.engine.planning.production.ProductionPlanner.Action.SMELT)) helper.succeed();
        else fail(helper, how);
    }

    /**
     * Plan 70, the owner's words: "If I ask for an entire chest of stone bricks, it should find the most efficient
     * way to do that. And be able to do that." With a Silk Touch pickaxe the most efficient way mines stone itself, no
     * furnace: "fill this chest with stone bricks", the chest with room for one more stack.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_fill_chest_silk", timeoutTicks = 3000)
    public void fillThisChestWithStoneBricksWithSilkTouch(GameTestHelper helper) {
        Scene s = scene(helper, "Mason");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        BlockPos chest = almostFullChest(helper, 0);
        var silk = helper.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getHolderOrThrow(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH);
        ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
        pick.enchant(silk, 1);
        s.body().getInventory().setItem(0, pick);
        helper.runAfterDelay(5, () -> MineAgentEngine.onPlayerChat(s.owner(), "fill this chest with stone bricks"));
        helper.onEachTick(() -> {
            trace(helper, s, "fill chest silk");
            int bricks = inChest(helper, chest, Items.STONE_BRICKS);
            String done = lastSaid(s, "Your chest is full");
            if (bricks == 64 && !done.isEmpty()) {
                int furnaces = furnacesAround(helper);
                String plan = lastSaid(s, "On it");
                tidy(s);
                if (furnaces == 0 && plan.contains("mine 64 stone")) helper.succeed();
                else fail(helper, "filled, but furnaces " + furnaces + ", plan: " + plan);
            } else if (helper.getTick() >= 2900) {
                String how = "the chest holds " + bricks + " stone bricks; it carries " + s.body().getInventory().countItem(Items.STONE)
                        + " stone; said: " + lastSaid(s, "");
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /**
     * Plan 70, the smelting way: room for 16 stone bricks, and it carries 16 cobblestone, 2 coal and 4 furnaces: it
     * sets two furnaces round it (two coal light two), smelts the cobblestone, crafts the bricks and fills the chest.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_fill_chest_smelt", timeoutTicks = 2400)
    public void fillThisChestWithStoneBricksBySmelting(GameTestHelper helper) {
        Scene s = scene(helper, "Smelter");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        BlockPos chest = almostFullChest(helper, 48);
        var inv = s.body().getInventory();
        inv.setItem(1, new ItemStack(Items.COBBLESTONE, 16));
        inv.setItem(2, new ItemStack(Items.COAL, 2));
        inv.setItem(3, new ItemStack(Items.FURNACE, 4));
        helper.runAfterDelay(5, () -> MineAgentEngine.onPlayerChat(s.owner(), "fill this chest with stone bricks"));
        helper.onEachTick(() -> {
            trace(helper, s, "fill chest smelt");
            int bricks = inChest(helper, chest, Items.STONE_BRICKS);
            String done = lastSaid(s, "Your chest is full");
            if (bricks == 64 && !done.isEmpty()) {
                int furnaces = furnacesAround(helper);
                String plan = lastSaid(s, "On it");
                tidy(s);
                // Two coal light two furnaces (a coal burns in one furnace, 8 items): it sets two of its four.
                if (furnaces == 2 && plan.contains("smelt 16 cobblestone into stone in 2 furnaces with 2 coal")) helper.succeed();
                else fail(helper, "filled, but furnaces " + furnaces + ", plan: " + plan);
            } else if (helper.getTick() >= 2300) {
                String how = "the chest holds " + bricks + " stone bricks; carries cobblestone " + inv.countItem(Items.COBBLESTONE)
                        + ", stone " + inv.countItem(Items.STONE) + ", coal " + inv.countItem(Items.COAL) + ", furnaces placed "
                        + furnacesAround(helper) + "; said: " + lastSaid(s, "");
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /**
     * Plan 70, MA10.8: its owner dies 9 blocks off with diamonds and iron on them. The companion goes for their things
     * before they vanish and, with its owner not there to take them, puts them in the chest at home.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_owner_death", timeoutTicks = 900)
    public void ownersThingsFetchedToTheChestAtHome(GameTestHelper helper) {
        Scene s = scene(helper, "Fetcher");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        helper.setBlock(5, 1, 3, Blocks.CHEST);
        BlockPos chest = helper.absolutePos(new BlockPos(5, 1, 3));
        helper.runAfterDelay(2, () -> MineAgentEngine.onPlayerChat(s.owner(), "this is home"));
        helper.runAfterDelay(20, () -> {
            BlockPos far = helper.absolutePos(new BlockPos(12, 1, 12));
            s.owner().teleportTo(helper.getLevel(), far.getX() + 0.5, far.getY(), far.getZ() + 0.5, 0.0f, 0.0f);
            s.owner().getInventory().setItem(0, new ItemStack(Items.DIAMOND, 5));
            s.owner().getInventory().setItem(1, new ItemStack(Items.IRON_INGOT, 3));
            s.owner().kill();
        });
        helper.onEachTick(() -> {
            trace(helper, s, "owner-death");
            String done = lastSaid(s, "I got your things back");
            String failed = lastSaid(s, "I couldn't get your things back");
            if (!done.isEmpty() || !failed.isEmpty()) {
                var c = (net.minecraft.world.Container) helper.getLevel().getBlockEntity(chest);
                int diamonds = 0, iron = 0;
                for (int i = 0; c != null && i < c.getContainerSize(); i++) {
                    if (c.getItem(i).is(Items.DIAMOND)) diamonds += c.getItem(i).getCount();
                    if (c.getItem(i).is(Items.IRON_INGOT)) iron += c.getItem(i).getCount();
                }
                tidy(s);
                if (diamonds == 5 && iron == 3) helper.succeed();
                else fail(helper, "the chest has " + diamonds + " diamonds and " + iron + " iron; said: " + (done.isEmpty() ? failed : done));
            } else if (helper.getTick() >= 880) {
                String how = "said: " + lastSaid(s, "") + "; carrying " + s.body().getInventory().countItem(Items.DIAMOND) + " diamonds";
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /** Plan 70, MA10.8: its owner's food runs low (4 of 20) and it carries 12 bread: it hands them some, keeping its floor. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_feed", timeoutTicks = 400)
    public void aHungryOwnerIsHandedFood(GameTestHelper helper) {
        Scene s = scene(helper, "Feeder");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        s.body().getInventory().setItem(3, new ItemStack(Items.BREAD, 12));
        BlockPos off = helper.absolutePos(new BlockPos(10, 1, 3));
        s.body().teleportTo(helper.getLevel(), off.getX() + 0.5, off.getY(), off.getZ() + 0.5, 0.0f, 0.0f);
        helper.runAfterDelay(10, () -> s.owner().getFoodData().setFoodLevel(4));
        helper.onEachTick(() -> {
            trace(helper, s, "feed");
            int handed = s.owner().getInventory().countItem(Items.BREAD);
            for (var e : helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    s.owner().getBoundingBox().inflate(4.0), e -> e.getItem().is(Items.BREAD))) handed += e.getItem().getCount();
            int kept = s.body().getInventory().countItem(Items.BREAD);
            if (handed > 0 && kept + handed == 12) {
                String said = lastSaid(s, "You're low on food");
                tidy(s);
                if (handed >= 2 && kept >= 4 && !said.isEmpty()) helper.succeed();
                else fail(helper, "handed " + handed + ", kept " + kept + "; said: " + said);
            } else if (helper.getTick() >= 380) {
                String how = "handed " + handed + ", kept " + kept + "; said: " + lastSaid(s, "");
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /**
     * Plan 70, MA10.5: what its owner threw down is theirs to leave there. Two diamonds the owner dropped (looking
     * away from it) lie at its feet and stay there; an emerald nobody threw, next to them, it picks up as ever.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_manners", timeoutTicks = 300)
    public void whatItsOwnerThrewIsLeftAlone(GameTestHelper helper) {
        Scene s = scene(helper, "Mannerly");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        BlockPos spot = helper.absolutePos(new BlockPos(8, 1, 3));
        s.body().teleportTo(helper.getLevel(), spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0.0f, 0.0f);
        net.minecraft.world.entity.item.ItemEntity[] thrown = {null};
        helper.runAfterDelay(10, () -> {
            thrown[0] = s.owner().drop(new ItemStack(Items.DIAMOND, 2), false, true);
            if (thrown[0] != null) {
                thrown[0].setDeltaMovement(Vec3.ZERO);
                thrown[0].setPos(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
            }
            var emerald = new net.minecraft.world.entity.item.ItemEntity(helper.getLevel(), spot.getX() + 0.5, spot.getY(),
                    spot.getZ() + 0.5, new ItemStack(Items.EMERALD, 1));
            emerald.setDeltaMovement(Vec3.ZERO);
            helper.getLevel().addFreshEntity(emerald);
        });
        helper.onEachTick(() -> {
            trace(helper, s, "manners");
            if (helper.getTick() < 200) {
                if (thrown[0] != null && !thrown[0].isAlive() && s.body().getInventory().countItem(Items.DIAMOND) > 0) {
                    tidy(s);
                    fail(helper, "it took the diamonds its owner threw");
                }
                return;
            }
            boolean diamondsThere = thrown[0] != null && thrown[0].isAlive();
            int diamonds = s.body().getInventory().countItem(Items.DIAMOND);
            int emeralds = s.body().getInventory().countItem(Items.EMERALD);
            tidy(s);
            if (thrown[0] == null) fail(helper, "the owner's throw made no item");
            else if (diamondsThere && diamonds == 0 && emeralds == 1) helper.succeed();
            else fail(helper, "diamonds still there " + diamondsThere + ", it carries " + diamonds + " diamonds and " + emeralds + " emeralds");
        });
    }

    /**
     * Plan 70, MA10.5, out of its owner's line of fire: told to shoot a cow with its owner standing between, it never
     * looses an arrow whose path passes them; it steps aside for a clear line and hits the cow from there.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_line_of_fire", timeoutTicks = 600)
    public void noShotPastItsOwner(GameTestHelper helper) {
        Scene s = scene(helper, "Archer");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        BlockPos from = helper.absolutePos(new BlockPos(3, 1, 0));
        BlockPos ownerAt = helper.absolutePos(new BlockPos(3, 1, 4));
        s.body().teleportTo(helper.getLevel(), from.getX() + 0.5, from.getY(), from.getZ() + 0.5, 0.0f, 0.0f);
        s.owner().teleportTo(helper.getLevel(), ownerAt.getX() + 0.5, ownerAt.getY(), ownerAt.getZ() + 0.5, 0.0f, 0.0f);
        s.body().getInventory().selected = 0;
        s.body().getInventory().setItem(0, new ItemStack(Items.BOW));
        s.body().getInventory().setItem(1, new ItemStack(Items.ARROW, 32));
        var cow = EntityType.COW.create(helper.getLevel());
        BlockPos cowAt = helper.absolutePos(new BlockPos(3, 1, 11));
        cow.moveTo(cowAt.getX() + 0.5, cowAt.getY(), cowAt.getZ() + 0.5, 0.0f, 0.0f);
        cow.setNoAi(true);
        helper.getLevel().addFreshEntity(cow);
        float health = s.owner().getHealth();
        boolean[] crossed = {false};
        helper.runAfterDelay(10, () -> com.mineagent.api.task.TaskDispatch.dispatchAsync(s.companion(),
                new com.mineagent.tools.combat.RangedAttackTool.RangedAttackTaskRecord("shoot-" + java.util.UUID.randomUUID(),
                        cow.getId(), 20), reply -> { }));
        helper.onEachTick(() -> {
            trace(helper, s, "line-of-fire");
            var box = s.owner().getBoundingBox().inflate(0.3);
            for (var a : helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.projectile.AbstractArrow.class,
                    box.inflate(8.0), a -> a.getOwner() == s.body())) {
                if (box.clip(new Vec3(a.xo, a.yo, a.zo), a.position()).isPresent() || box.contains(a.position())) crossed[0] = true;
            }
            if (crossed[0] || s.owner().getHealth() < health) {
                tidy(s);
                fail(helper, "an arrow passed its owner (health " + s.owner().getHealth() + ")");
            } else if (cow.getHealth() < cow.getMaxHealth() || !cow.isAlive()) {
                double aside = Math.abs(s.body().getX() - (from.getX() + 0.5));
                tidy(s);
                if (aside >= 1.0) helper.succeed();
                else fail(helper, String.format("the cow was hit, but from %.1f blocks aside", aside));
            } else if (helper.getTick() >= 580) {
                String how = "the cow was never hit; at " + rel(helper, s.body().blockPosition()).toShortString();
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /**
     * A stand-in player with a name of its own, in survival (the helper's mock is in creative, and every mock has the
     * same name). Taken off the server with {@code server.getPlayerList().remove(p)}.
     */
    private static ServerPlayer player(GameTestHelper helper, String name, BlockPos at) {
        var server = helper.getLevel().getServer();
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), name), false);
        ServerPlayer p = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
            @Override public boolean isSpectator() { return false; }
            @Override public boolean isCreative() { return false; }
        };
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, p, cookie);
        // The test world's players join in creative, where nothing hurts them: a real player's survival instead.
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        BlockPos abs = helper.absolutePos(at);
        p.teleportTo(helper.getLevel(), abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0.0f, 0.0f);
        return p;
    }

    /**
     * Plan 70, MA10.6: with PvP on, its owner names Rival. It fights Rival, every hit at full charge; never its owner
     * (put on the list as well: code says no), a trusted player (on the list too), or a passer-by nobody named.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_pvp", timeoutTicks = 500)
    public void fightsOnlyTheNamedPlayerAtFullCharge(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        boolean pvp = server.isPvpAllowed();
        server.setPvpAllowed(true);
        ServerPlayer keeper = player(helper, "Keeper", new BlockPos(3, 1, 3));
        Scene s = scene(helper, "Duelist", false, keeper);
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        armed(s.body());
        ServerPlayer rival = player(helper, "Rival", new BlockPos(7, 1, 3));
        ServerPlayer friend = player(helper, "Friend", new BlockPos(3, 1, 6));
        ServerPlayer passer = player(helper, "Passerby", new BlockPos(1, 1, 3));
        s.loop().setAgentSettings(s.loop().getAgentSettings().withTrusted(friend.getUUID()).withTarget(rival.getUUID())
                .withTarget(keeper.getUUID()).withTarget(friend.getUUID()));
        float keeperHealth = keeper.getHealth(), friendHealth = friend.getHealth(), passerHealth = passer.getHealth();
        float[] last = {rival.getHealth()};
        double[] charge = {0.0};
        boolean[] weak = {false};
        Runnable done = () -> {
            for (var p : List.of(rival, friend, passer)) server.getPlayerList().remove(p);
            tidy(s);
            server.setPvpAllowed(pvp);
        };
        helper.onEachTick(() -> {
            trace(helper, s, "pvp");
            boolean hit = rival.getHealth() < last[0];
            if (hit && charge[0] < 0.85) weak[0] = true;
            last[0] = rival.getHealth();
            charge[0] = s.body().getAttackStrengthScale(0.5f);
            if (keeper.getHealth() < keeperHealth || friend.getHealth() < friendHealth || passer.getHealth() < passerHealth) {
                String who = keeper.getHealth() < keeperHealth ? "its owner" : friend.getHealth() < friendHealth ? "a trusted player" : "a passer-by";
                done.run();
                fail(helper, "it hit " + who);
            } else if (hit) {
                boolean full = !weak[0];
                done.run();
                if (full) helper.succeed();
                else fail(helper, "it hit Rival before its attack had recharged");
            } else if (helper.getTick() >= 480) {
                String how = "Rival never hit; at " + rel(helper, s.body().blockPosition()).toShortString() + ", said: " + lastSaid(s, "");
                done.run();
                fail(helper, how);
            }
        });
    }

    /** Plan 70, MA10.6: Rival raises a shield; it takes out its axe for the hit (an axe puts a shield down for 5 seconds). */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_pvp_axe", timeoutTicks = 400)
    public void anAxeAgainstARaisedShield(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        boolean pvp = server.isPvpAllowed();
        server.setPvpAllowed(true);
        Scene s = scene(helper, "Axeman");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        s.body().getInventory().selected = 0;
        s.body().getInventory().setItem(0, new ItemStack(Items.IRON_SWORD));
        s.body().getInventory().setItem(1, new ItemStack(Items.IRON_AXE));
        ServerPlayer rival = player(helper, "Rival", new BlockPos(6, 1, 3));
        rival.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        rival.startUsingItem(net.minecraft.world.InteractionHand.OFF_HAND);
        s.loop().setAgentSettings(s.loop().getAgentSettings().withTarget(rival.getUUID()));
        float[] last = {rival.getHealth()};
        Runnable done = () -> {
            server.getPlayerList().remove(rival);
            tidy(s);
            server.setPvpAllowed(pvp);
        };
        helper.onEachTick(() -> {
            trace(helper, s, "pvp-axe");
            boolean shieldDown = rival.getCooldowns().isOnCooldown(Items.SHIELD);
            if (rival.getHealth() < last[0] || shieldDown) {
                boolean axe = s.body().getMainHandItem().getItem() instanceof net.minecraft.world.item.AxeItem;
                String held = s.body().getMainHandItem().toString();
                done.run();
                if (axe) helper.succeed();
                else fail(helper, "it hit the shielded player with " + held);
            } else if (helper.getTick() >= 380) {
                String how = "never hit; holding " + s.body().getMainHandItem() + ", said: " + lastSaid(s, "");
                done.run();
                fail(helper, how);
            }
            last[0] = rival.getHealth();
        });
    }

    /**
     * Plan 70, MA10.7: a stranger's "hunt Rival" does nothing; its owner's sends it to where it last saw Rival (Rival
     * since gone out of sight, and the server's live position not allowed).
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_hunt_player", timeoutTicks = 700)
    public void aHuntGoesWhereItLastSawThemAndOnlyOnItsOwnersWord(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        boolean pvp = server.isPvpAllowed();
        server.setPvpAllowed(true);
        Scene s = scene(helper, "Tracker");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        armed(s.body());
        ServerPlayer rival = player(helper, "Rival", new BlockPos(14, 1, 14));
        ServerPlayer stranger = player(helper, "Stranger", new BlockPos(5, 1, 3));
        BlockPos seenAt = helper.absolutePos(new BlockPos(14, 1, 14));
        Runnable done = () -> {
            for (var p : List.of(rival, stranger)) server.getPlayerList().remove(p);
            tidy(s);
            server.setPvpAllowed(pvp);
        };
        // Seen at (14, 1, 14); then gone where it can't see them: under the floor, far off.
        helper.runAfterDelay(60, () -> {
            BlockPos gone = helper.absolutePos(new BlockPos(22, -2, 22));
            rival.teleportTo(helper.getLevel(), gone.getX() + 0.5, gone.getY(), gone.getZ() + 0.5, 0.0f, 0.0f);
        });
        helper.runAfterDelay(80, () -> MineAgentEngine.onPlayerChat(stranger, "Tracker, hunt Rival"));
        boolean[] ordered = {false};
        helper.onEachTick(() -> {
            trace(helper, s, "hunt-player");
            if (helper.getTick() == 160) {
                if (s.loop().getAgentSettings().isAllowedTarget(rival.getUUID()) || !lastSaid(s, "On it: hunting").isEmpty()) {
                    done.run();
                    fail(helper, "a stranger's hunt order was taken");
                    return;
                }
                MineAgentEngine.onPlayerChat(s.owner(), "hunt Rival");
                ordered[0] = true;
            }
            if (!ordered[0]) return;
            double d = Math.sqrt(s.body().blockPosition().distSqr(seenAt));
            if (d <= 3.5) {
                boolean listed = s.loop().getAgentSettings().isAllowedTarget(rival.getUUID());
                String said = lastSaid(s, "On it: hunting");
                done.run();
                if (listed && !said.isEmpty()) helper.succeed();
                else fail(helper, "listed " + listed + "; said: " + said);
            } else if (helper.getTick() >= 680) {
                String how = String.format("%.1f from where it last saw Rival; said: %s", d, lastSaid(s, ""));
                done.run();
                fail(helper, how);
            }
        });
    }

    /** Plan 70, MA10.1: "guard here radius 6": it lights the perimeter until no spot inside is dark. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_guard_light", timeoutTicks = 1600)
    public void onGuardThePerimeterIsLit(GameTestHelper helper) {
        Scene s = scene(helper, "Sentry");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        s.body().getInventory().setItem(2, new ItemStack(Items.TORCH, 32));
        BlockPos post = s.owner().blockPosition();
        helper.runAfterDelay(5, () -> MineAgentEngine.onPlayerChat(s.owner(), "guard here radius 6"));
        helper.onEachTick(() -> {
            trace(helper, s, "guard-light");
            String lit = lastSaid(s, "Lit up");
            if (!lit.isEmpty()) {
                int dark = 0;
                for (BlockPos p : BlockPos.betweenClosed(post.offset(-6, -3, -6), post.offset(6, 3, 6))) {
                    if (p.distSqr(post) > 36) continue;
                    if (com.mineagent.engine.task.LightTask.spawnable(helper.getLevel(), p)
                            && helper.getLevel().getBrightness(net.minecraft.world.level.LightLayer.BLOCK, p) == 0) dark++;
                }
                boolean guarding = s.loop().guarding();
                tidy(s);
                if (dark == 0 && guarding) helper.succeed();
                else fail(helper, dark + " dark spots inside the perimeter; guarding " + guarding + "; said: " + lit);
            } else if (helper.getTick() >= 1500) {
                String how = "torches left " + s.body().getInventory().countItem(Items.TORCH) + "; said: " + lastSaid(s, "");
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /** Plan 70, MA10.1: on guard with a bow, a creeper 16 blocks out (outside the 8-block perimeter) is shot at range. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_guard_creeper", timeoutTicks = 600)
    public void onGuardACreeperIsShotAtRange(GameTestHelper helper) {
        Scene s = scene(helper, "Warden");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        s.body().getInventory().setItem(1, new ItemStack(Items.BOW));
        s.body().getInventory().setItem(2, new ItemStack(Items.ARROW, 32));
        net.minecraft.world.entity.monster.Creeper[] creeper = {null};
        helper.runAfterDelay(5, () -> MineAgentEngine.onPlayerChat(s.owner(), "guard here radius 8"));
        helper.runAfterDelay(15, () -> {
            // Its owner steps well out of the way; the creeper waits out past the perimeter.
            BlockPos away = helper.absolutePos(new BlockPos(-12, 1, 3));
            s.owner().teleportTo(helper.getLevel(), away.getX() + 0.5, away.getY(), away.getZ() + 0.5, 0.0f, 0.0f);
            creeper[0] = helper.spawn(EntityType.CREEPER, new BlockPos(3, 1, 19));
            creeper[0].setNoAi(true);
        });
        helper.onEachTick(() -> {
            trace(helper, s, "guard-creeper");
            var c = creeper[0];
            if (c == null) return;
            int arrows = s.body().getInventory().countItem(Items.ARROW);
            if (!c.isAlive() || c.getHealth() < c.getMaxHealth()) {
                double far = c.distanceTo(s.body());
                tidy(s);
                if (c.isAlive()) c.discard();
                if (arrows < 32 && far >= 6.0) helper.succeed();
                else fail(helper, String.format("the creeper was hurt from %.1f blocks, %d arrows left", far, arrows));
            } else if (helper.getTick() >= 580) {
                String how = "the creeper was never hit; " + arrows + " arrows left; holding " + s.body().getMainHandItem()
                        + "; at " + rel(helper, s.body().blockPosition()).toShortString();
                tidy(s);
                c.discard();
                fail(helper, how);
            }
        });
    }

    /**
     * Plan 70, MA10.4: its owner digs along a stone wall with a pickaxe; the companion, with nothing else to do and
     * torches on it, lights the tunnel behind them.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_partner_mine", timeoutTicks = 900)
    public void aMinersTunnelIsLitBehindThem(GameTestHelper helper) {
        ServerPlayer miner = player(helper, "Miner", new BlockPos(3, 1, 5));
        Scene s = scene(helper, "Lantern", false, miner);
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        s.body().getInventory().setItem(2, new ItemStack(Items.TORCH, 16));
        miner.getInventory().setItem(0, new ItemStack(Items.IRON_PICKAXE));
        for (int x = 2; x <= 12; x++) for (int y = 1; y <= 3; y++) helper.setBlock(x, y, 6, Blocks.STONE);
        // Two blocks a column, a column every 20 ticks, along the wall.
        for (int i = 0; i < 8; i++) {
            final int x = 3 + i;
            helper.runAfterDelay(20 + i * 20L, () -> {
                BlockPos at = helper.absolutePos(new BlockPos(x, 1, 5));
                miner.teleportTo(helper.getLevel(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0f, 0.0f);
                miner.gameMode.destroyBlock(helper.absolutePos(new BlockPos(x, 1, 6)));
                miner.gameMode.destroyBlock(helper.absolutePos(new BlockPos(x, 2, 6)));
            });
        }
        helper.onEachTick(() -> {
            trace(helper, s, "partner-mine");
            int torches = 0;
            for (BlockPos p : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(-3, 0, 0)), helper.absolutePos(new BlockPos(16, 3, 12)))) {
                var st = helper.getLevel().getBlockState(p);
                if (st.is(Blocks.TORCH) || st.is(Blocks.WALL_TORCH)) torches++;
            }
            if (torches > 0 && helper.getTick() > 100) {
                tidy(s);
                helper.succeed();
            } else if (helper.getTick() >= 880) {
                String how = "no torch placed; it has " + s.body().getInventory().countItem(Items.TORCH) + "; said: " + lastSaid(s, "")
                        + "; doing " + s.loop().ownerWork().doing(helper.getLevel().getGameTime());
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /**
     * Plan 70, MA10.4: its owner lays oak planks, down to 14 of them; the companion, carrying 40, brings them more before
     * they run out.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_partner_build", timeoutTicks = 700)
    public void aBuilderIsBroughtTheBlocksTheyArePlacing(GameTestHelper helper) {
        ServerPlayer builder = player(helper, "Mason", new BlockPos(3, 1, 3));
        Scene s = scene(helper, "Hod", false, builder);
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        s.body().getInventory().setItem(4, new ItemStack(Items.OAK_PLANKS, 40));
        builder.getInventory().selected = 0;
        builder.getInventory().setItem(0, new ItemStack(Items.OAK_PLANKS, 20));
        BlockPos far = helper.absolutePos(new BlockPos(10, 1, 3));
        s.body().teleportTo(helper.getLevel(), far.getX() + 0.5, far.getY(), far.getZ() + 0.5, 0.0f, 0.0f);
        // A plank every 10 ticks, along a line in front of them, as a player places them (on the floor's top face).
        for (int i = 0; i < 6; i++) {
            final int x = 1 + i;
            helper.runAfterDelay(20 + i * 10L, () -> {
                BlockPos floor = helper.absolutePos(new BlockPos(x, 0, 5));
                var hit = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0),
                        net.minecraft.core.Direction.UP, floor, false);
                builder.gameMode.useItemOn(builder, helper.getLevel(), builder.getMainHandItem(),
                        net.minecraft.world.InteractionHand.MAIN_HAND, hit);
            });
        }
        helper.onEachTick(() -> {
            trace(helper, s, "partner-build");
            int theirs = builder.getInventory().countItem(Items.OAK_PLANKS);
            for (var e : helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    builder.getBoundingBox().inflate(4.0), e -> e.getItem().is(Items.OAK_PLANKS))) theirs += e.getItem().getCount();
            if (helper.getTick() > 100 && theirs >= 30) {
                tidy(s);
                helper.succeed();
            } else if (helper.getTick() >= 680) {
                String how = "they have " + theirs + " planks; it has " + s.body().getInventory().countItem(Items.OAK_PLANKS)
                        + "; doing " + s.loop().ownerWork().doing(helper.getLevel().getGameTime()) + "; said: " + lastSaid(s, "");
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /**
     * Plan 70, MA10.2, with a scripted model: its owner looks at a chest and says "walk over to that". The call carries
     * the referents code made; the model's first pick, somewhere else, is refused with the list; its second, the chest
     * from the list, is taken, and the companion goes there.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_referent", timeoutTicks = 1_000_000)
    public void aScriptedModelPicksThatFromTheReferents(GameTestHelper helper) {
        String fake = FakeSmh.start();
        if (fake != null) {
            fail(helper, fake);
            return;
        }
        Scene s = scene(helper, "Pointer", true, null);
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        helper.setBlock(3, 1, 9, Blocks.CHEST);
        BlockPos chest = helper.absolutePos(new BlockPos(3, 1, 9));
        // Its owner at (3, 1, 3) looks at the chest, six blocks south and a little down.
        BlockPos o = helper.absolutePos(new BlockPos(3, 1, 3));
        s.owner().teleportTo(helper.getLevel(), o.getX() + 0.5, o.getY(), o.getZ() + 0.5, 0.0f, 12.0f);
        int before = FakeSmh.CALLS.size();
        boolean[] refused = {false};
        var pattern = java.util.regex.Pattern.compile("\\[1\\] the chest they are looking at at (-?\\d+), (-?\\d+), (-?\\d+)");
        FakeSmh.script = call -> {
            String text = call.text();
            if (!call.hasTools()) return null;
            if (text.contains("That, it or there means one of these")) {
                refused[0] = true;
                var m = pattern.matcher(text);
                if (!m.find()) return null;
                var args = new com.google.gson.JsonObject();
                args.addProperty("goal_mode", "xzy");
                args.addProperty("x", Integer.parseInt(m.group(1)));
                args.addProperty("y", Integer.parseInt(m.group(2)));
                args.addProperty("z", Integer.parseInt(m.group(3)) - 1);
                return FakeSmh.toolCall("goto", args);
            }
            if (text.contains("[REFERENTS]") && text.contains("walk over to that")) {
                var args = new com.google.gson.JsonObject();
                args.addProperty("goal_mode", "xzy");
                args.addProperty("x", chest.getX() + 30);
                args.addProperty("y", chest.getY());
                args.addProperty("z", chest.getZ() + 30);
                return FakeSmh.toolCall("goto", args);
            }
            return null;
        };
        helper.runAfterDelay(5, () -> MineAgentEngine.onPlayerChat(s.owner(), "walk over to that"));
        long deadline = now() + 60_000L;
        helper.onEachTick(() -> {
            trace(helper, s, "referent");
            double d = Math.sqrt(s.body().blockPosition().distSqr(chest));
            boolean listed = FakeSmh.CALLS.stream().skip(before).anyMatch(c -> c.text().contains("[REFERENTS]")
                    && c.text().contains(chest.getX() + ", " + chest.getY() + ", " + chest.getZ()));
            if (refused[0] && listed && d <= 2.5) {
                FakeSmh.script = null;
                tidy(s);
                helper.succeed();
            } else if (now() > deadline) {
                FakeSmh.script = null;
                String how = "listed " + listed + ", wrong pick refused " + refused[0] + String.format(", %.1f from the chest", d)
                        + "; calls " + (FakeSmh.CALLS.size() - before);
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /**
     * Plan 70, MA11.1: "build a nether portal" with 10 obsidian, cobblestone and flint and steel: the frame goes up (the
     * corners of cobblestone, the top reached from a pillar it takes down after), and the portal is lit.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_portal_build", timeoutTicks = 1600)
    public void aPortalFromTheObsidianItWasGivenAndLit(GameTestHelper helper) {
        Scene s = scene(helper, "Gatewright");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        var inv = s.body().getInventory();
        inv.setItem(1, new ItemStack(Items.OBSIDIAN, 10));
        inv.setItem(2, new ItemStack(Items.COBBLESTONE, 16));
        inv.setItem(3, new ItemStack(Items.FLINT_AND_STEEL));
        helper.runAfterDelay(5, () -> MineAgentEngine.onPlayerChat(s.owner(), "build a nether portal"));
        helper.onEachTick(() -> {
            trace(helper, s, "portal-build");
            String lit = lastSaid(s, "The portal is lit");
            String failed = lastSaid(s, "I couldn't build the portal");
            if (!lit.isEmpty() || !failed.isEmpty()) {
                int portal = 0;
                for (BlockPos p : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(-10, 0, -10)), helper.absolutePos(new BlockPos(18, 8, 18)))) {
                    if (helper.getLevel().getBlockState(p).is(Blocks.NETHER_PORTAL)) portal++;
                }
                tidy(s);
                if (!lit.isEmpty() && portal >= 6) helper.succeed();
                else fail(helper, portal + " portal blocks; said: " + (lit.isEmpty() ? failed : lit));
            } else if (helper.getTick() >= 1550) {
                String how = "said: " + lastSaid(s, "") + "; obsidian left " + inv.countItem(Items.OBSIDIAN);
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /** Plan 70, MA11.2: "enchant your pickaxe" with a table, 15 bookshelves, lapis and 30 levels: the ring goes down, the pickaxe is enchanted. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_enchant", timeoutTicks = 900)
    public void aPickaxeEnchantedAtATableWithItsShelves(GameTestHelper helper) {
        Scene s = scene(helper, "Enchanter");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        BlockPos spot = helper.absolutePos(new BlockPos(10, 1, 10));
        s.body().teleportTo(helper.getLevel(), spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0.0f, 0.0f);
        var inv = s.body().getInventory();
        inv.setItem(1, new ItemStack(Items.ENCHANTING_TABLE));
        inv.setItem(2, new ItemStack(Items.BOOKSHELF, 15));
        inv.setItem(3, new ItemStack(Items.LAPIS_LAZULI, 3));
        inv.setItem(4, new ItemStack(Items.IRON_PICKAXE));
        s.body().giveExperienceLevels(30);
        helper.runAfterDelay(5, () -> MineAgentEngine.onPlayerChat(s.owner(), "enchant your pickaxe"));
        helper.onEachTick(() -> {
            trace(helper, s, "enchant");
            String done = lastSaid(s, "Enchanted my");
            String failed = lastSaid(s, "I couldn't enchant");
            if (!done.isEmpty() || !failed.isEmpty()) {
                boolean enchanted = false;
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    if (inv.getItem(i).is(Items.IRON_PICKAXE) && inv.getItem(i).isEnchanted()) enchanted = true;
                }
                int shelves = 0;
                for (BlockPos p : BlockPos.betweenClosed(spot.offset(-6, -1, -6), spot.offset(6, 2, 6))) {
                    if (helper.getLevel().getBlockState(p).is(Blocks.BOOKSHELF)) shelves++;
                }
                tidy(s);
                if (enchanted && shelves >= 15) helper.succeed();
                else fail(helper, "enchanted " + enchanted + ", " + shelves + " shelves; said: " + (done.isEmpty() ? failed : done));
            } else if (helper.getTick() >= 880) {
                String how = "said: " + lastSaid(s, "");
                tidy(s);
                fail(helper, how);
            }
        });
    }

    /** Plan 70, MA11.3: "trade for emeralds" with a farmer placed beside it and crops to sell: emeralds, by the trading menu. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_trade", timeoutTicks = 600)
    public void anEmeraldTradeWithAPlacedVillager(GameTestHelper helper) {
        Scene s = scene(helper, "Trader");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        var inv = s.body().getInventory();
        inv.setItem(1, new ItemStack(Items.WHEAT, 64));
        inv.setItem(2, new ItemStack(Items.POTATO, 64));
        inv.setItem(3, new ItemStack(Items.CARROT, 64));
        inv.setItem(4, new ItemStack(Items.BEETROOT, 64));
        var villager = EntityType.VILLAGER.create(helper.getLevel());
        BlockPos vAt = helper.absolutePos(new BlockPos(7, 1, 3));
        villager.moveTo(vAt.getX() + 0.5, vAt.getY(), vAt.getZ() + 0.5, 0.0f, 0.0f);
        villager.setVillagerData(villager.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.FARMER).setLevel(1));
        villager.setNoAi(true);
        helper.getLevel().addFreshEntity(villager);
        float health = villager.getHealth();
        helper.runAfterDelay(10, () -> MineAgentEngine.onPlayerChat(s.owner(), "trade for emeralds"));
        helper.onEachTick(() -> {
            trace(helper, s, "trade");
            int emeralds = inv.countItem(Items.EMERALD);
            String done = lastSaid(s, "Traded");
            String failed = lastSaid(s, "I couldn't trade");
            if (!done.isEmpty() || !failed.isEmpty()) {
                boolean unhurt = villager.isAlive() && villager.getHealth() >= health;
                tidy(s);
                villager.discard();
                if (emeralds >= 1 && unhurt) helper.succeed();
                else fail(helper, emeralds + " emeralds, villager unhurt " + unhurt + "; said: " + (done.isEmpty() ? failed : done));
            } else if (helper.getTick() >= 580) {
                String how = emeralds + " emeralds; said: " + lastSaid(s, "");
                tidy(s);
                villager.discard();
                fail(helper, how);
            }
        });
    }

    /** Plan 70, MA11.4: "brew a potion of fire resistance" from given ingredients: three potions of fire resistance. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_brew", timeoutTicks = 1600)
    public void aPotionOfFireResistanceBrewed(GameTestHelper helper) {
        Scene s = scene(helper, "Brewer");
        MineAgentEngine.setCompanionMode(s.companion().companionId(), MineAgentEngine.CompanionMode.FREE);
        var inv = s.body().getInventory();
        inv.setItem(1, new ItemStack(Items.BREWING_STAND));
        inv.setItem(2, new ItemStack(Items.BLAZE_POWDER));
        for (int i = 3; i <= 5; i++) inv.setItem(i, com.mineagent.engine.task.BrewTask.waterBottle());
        inv.setItem(6, new ItemStack(Items.NETHER_WART));
        inv.setItem(7, new ItemStack(Items.MAGMA_CREAM));
        helper.runAfterDelay(5, () -> MineAgentEngine.onPlayerChat(s.owner(), "brew a potion of fire resistance"));
        helper.onEachTick(() -> {
            trace(helper, s, "brew");
            String done = lastSaid(s, "Brewed");
            String failed = lastSaid(s, "I couldn't brew");
            if (!done.isEmpty() || !failed.isEmpty()) {
                int potions = 0;
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    var c = inv.getItem(i).get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
                    if (inv.getItem(i).is(Items.POTION) && c != null && c.is(net.minecraft.world.item.alchemy.Potions.FIRE_RESISTANCE)) potions++;
                }
                tidy(s);
                if (potions >= 1) helper.succeed();
                else fail(helper, potions + " potions of fire resistance; said: " + (done.isEmpty() ? failed : done));
            } else if (helper.getTick() >= 1550) {
                String how = "said: " + lastSaid(s, "");
                tidy(s);
                fail(helper, how);
            }
        });
    }
}
