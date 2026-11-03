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

    private record Scene(ServerPlayer owner, CompanionEntity companion, ServerPlayer body, AgentLoop loop) {}

    /** A stone floor, an owner (a mock player) at (3, 1, 3), and a companion beside them. */
    private static Scene scene(GameTestHelper helper, String name) {
        for (var s : List.copyOf(MineAgentEngine.allCompanions())) {
            MineAgentEngine.despawnCompanion(s.companion.companionId(), true);
        }
        for (int x = -16; x <= 24; x++) {
            for (int z = -16; z <= 24; z++) helper.setBlock(x, 0, z, Blocks.STONE);
        }
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        BlockPos at = helper.absolutePos(new BlockPos(3, 1, 3));
        owner.teleportTo(helper.getLevel(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0f, 0.0f);
        CompanionEntity companion = MineAgentEngine.spawnCompanion(owner, name, "openai-compatible", "test",
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

    private static void tidy(Scene s) {
        MineAgentEngine.despawnCompanion(s.companion().companionId(), true);
        s.owner().getServer().getPlayerList().remove(s.owner());
    }

    private static double flat(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Scene 6: a husk comes at it; it fights back and kills it. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_fight", timeoutTicks = 600)
    public void aHuskThatAttacksItIsKilled(GameTestHelper helper) {
        Scene s = scene(helper, "Fighter");
        armed(s.body());
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(6, 1, 3));
        husk.setTarget(s.body());
        helper.onEachTick(() -> {
            if (!s.body().isAlive()) {
                tidy(s);
                helper.fail("the companion died to one husk with an iron sword and a shield");
            } else if (!husk.isAlive()) {
                boolean byIt = husk.getLastHurtByMob() == s.body();
                tidy(s);
                if (byIt) helper.succeed();
                else helper.fail("the husk died, but not by the companion");
            }
        });
    }

    /** On guard, a husk 14 blocks away, outside a 6-block perimeter and not moving: it stays at its post. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_guard_far", timeoutTicks = 200)
    public void onGuardItDoesNotRunOffAfterAFarMob(GameTestHelper helper) {
        Scene s = scene(helper, "Sentry");
        armed(s.body());
        if (!s.loop().ownerCommand(new CommandParser.Command(CommandParser.Kind.GUARD_HERE, null, 6))) {
            tidy(s);
            helper.fail("guard here was refused");
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
                helper.fail("it left its post for a mob outside the perimeter: " + String.format("%.1f", away) + " blocks out");
            } else if (helper.getTick() >= 120) {
                tidy(s);
                helper.succeed();
            }
        });
    }

    /** On guard, a husk inside the perimeter is fought and killed, and it never leaves the perimeter to do it. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_guard_near", timeoutTicks = 600)
    public void onGuardItClearsItsPerimeterWithoutLeavingIt(GameTestHelper helper) {
        Scene s = scene(helper, "Warden");
        armed(s.body());
        if (!s.loop().ownerCommand(new CommandParser.Command(CommandParser.Kind.GUARD_HERE, null, 8))) {
            tidy(s);
            helper.fail("guard here was refused");
            return;
        }
        Vec3 post = s.owner().position();
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(8, 1, 3));
        husk.setTarget(s.body());
        helper.onEachTick(() -> {
            double away = flat(s.body().position(), post);
            if (away > 8 + 3.5) {
                tidy(s);
                helper.fail("it went " + String.format("%.1f", away) + " blocks from its post, past its 8-block perimeter");
            } else if (!husk.isAlive()) {
                tidy(s);
                helper.succeed();
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
                else helper.fail("the goal it was on was not kept behind the recovery goal");
            } else if (helper.getTick() >= 150) {
                tidy(s);
                helper.fail("no recovery goal after the death; active: " + active.map(GoalBook.Goal::text).orElse("none"));
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
                helper.fail("a stranger's order moved its things: " + dirt(s.body()) + " dirt left of 16");
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
                helper.fail("a trusted player's \"drop 4 dirt\" left " + left + " of 16");
            }
        });
    }

    /** "get 3 diamonds" becomes a goal whose amount is counted, the item checked against the game's registry. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "mineagent_goal", timeoutTicks = 100)
    public void aGoalsAmountsAreReadAgainstTheGame(GameTestHelper helper) {
        Scene s = scene(helper, "Planner");
        s.loop().addGoal("get 3 diamonds and 2 unobtainium", "owner");
        var active = s.loop().goalBook().active();
        tidy(s);
        if (active.isEmpty()) {
            helper.fail("the goal was not added");
        } else if (!active.get().needs().equals(List.of(new GoalBook.Need("diamond", 3)))) {
            helper.fail("needs read as " + active.get().needs() + ", expected 3 diamond only");
        } else {
            helper.succeed();
        }
    }
}
