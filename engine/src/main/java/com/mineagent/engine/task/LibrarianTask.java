package com.mineagent.engine.task;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.task.CompanionTask;
import com.mineagent.api.task.TaskRecord;
import com.mineagent.api.task.TaskState;
import com.mineagent.engine.act.Placement;
import com.mineagent.engine.pathing.execute.PlayerNav;
import com.mineagent.engine.pathing.util.BlockHelper;
import com.mineagent.engine.villager.VillagerWorth;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * A librarian rolled for the book it is wanted for (SMH plan 70, "trading MASTERY": Mending above all): a villager
 * without a job (or a librarian no one has traded with) given a lectern; its offers read; not the book, the lectern
 * broken and put down again (a never-traded librarian loses its trade with its lectern and takes new ones with the next);
 * the book found, one bought to fix the offer for good, and the villager walled into a cell with its lectern, a window
 * left over the lectern to trade through. On the game's 1.21.1 rules (no trade rebalance: any librarian can sell it).
 */
public class LibrarianTask extends CompanionTask<LibrarianTask.RollRecord> {
    public static final class RollRecord extends TaskRecord {
        /** The enchantment wanted ("minecraft:mending"). */
        public final String enchantment;

        public RollRecord(String toolCallId, String enchantment) {
            super(toolCallId);
            this.enchantment = enchantment == null ? "minecraft:mending" : enchantment;
        }
    }

    private enum Phase { FIND, LECTERN, PLACE, WAIT_JOB, READ, BREAK, PICKUP, WAIT_JOBLESS, LOCK, CELL, DONE, FAILED }

    /** Rolls before it gives up (each about ten seconds). */
    static final int MAX_ROLLS = 80;

    private Phase phase = Phase.FIND;
    private Villager villager;
    private BlockPos lectern;
    private PlayerNav nav;
    private CompanionTask<?> sub;
    private int rolls, ticks, digTicks;
    private boolean digging, locked;
    private String outcome = "";

    public LibrarianTask(AgentPlayer player, RollRecord record) {
        super(player, record);
    }

    @Override
    protected void onStart() {
        if (nav == null) nav = new PlayerNav(player, TaskContext.navCaches(player));
    }

    private ServerPlayer sp() {
        return TaskContext.serverPlayer(player);
    }

    private String book() {
        return record.enchantment.substring(record.enchantment.indexOf(':') + 1).replace('_', ' ');
    }

    @Override
    protected TaskState onTick() {
        ServerPlayer sp = sp();
        ServerLevel level = sp.serverLevel();
        try {
            if (sub != null) {
                TaskState st = sub.tick();
                if (st == TaskState.RUNNING) return TaskState.RUNNING;
                sub = null;
                if (phase == Phase.LECTERN && ProductionTask.count(sp, Items.LECTERN) == 0) return end(false, "I have no lectern and can't make one");
                if (phase == Phase.LOCK) {
                    locked = st == TaskState.SUCCESS;
                    phase = Phase.CELL;
                    ticks = 0;
                }
                return TaskState.RUNNING;
            }
            ticks++;
            switch (phase) {
                case FIND -> {
                    villager = candidate(sp);
                    if (villager == null) return end(false, "there's no villager without a job near me, nor a librarian no one has traded with");
                    var site = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
                    if (villager.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN && site.isPresent()) {
                        lectern = site.get().pos();
                        phase = Phase.READ;
                    } else {
                        phase = Phase.LECTERN;
                    }
                    ticks = 0;
                }
                case LECTERN -> {
                    if (ProductionTask.count(sp, Items.LECTERN) > 0) {
                        phase = Phase.PLACE;
                        ticks = 0;
                        return TaskState.RUNNING;
                    }
                    sub = new ProductionTask(player, new ProductionTask.ProduceRecord(record.toolCallId() + ":lectern", "minecraft:lectern", 1, null, false, true));
                    sub.start();
                }
                case PLACE -> {
                    if (lectern == null) lectern = spotBeside(level, villager);
                    if (lectern == null) {
                        // It may be standing where the lectern would go (in a cell, the open side): a few seconds for
                        // it to step back.
                        if (ticks < 200) return TaskState.RUNNING;
                        return end(false, "there's nowhere by the villager to put a lectern");
                    }
                    if (lectern.distToCenterSqr(sp.getEyePosition()) > 4.0 * 4.0) {
                        if (ticks % 20 == 1) nav.navigateNear(lectern.getX(), lectern.getY(), lectern.getZ(), 2);
                        nav.tick();
                        if (ticks > 600) return end(false, "I couldn't get to the villager");
                        return TaskState.RUNNING;
                    }
                    nav.cancel();
                    TaskContext.inputDriver(player).clear();
                    if (level.getBlockState(lectern).is(Blocks.LECTERN) || Placement.placeBlock(sp, lectern, Blocks.LECTERN.defaultBlockState())) {
                        phase = Phase.WAIT_JOB;
                        ticks = 0;
                    } else if (ticks > 100) {
                        lectern = null; // that spot won't take it: another
                        ticks = 0;
                    }
                }
                case WAIT_JOB -> {
                    // Whichever villager takes the lectern up is the one rolled.
                    Villager took = holder(level, lectern);
                    if (took != null && took.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN) {
                        villager = took;
                        phase = Phase.READ;
                        ticks = 0;
                    } else if (ticks > 1_200) {
                        return end(false, "no villager took up the lectern");
                    }
                }
                case READ -> {
                    var offers = offers(villager);
                    if (VillagerWorth.sells(offers, record.enchantment)) {
                        say("Found it: a librarian selling " + book() + " after " + rolls + (rolls == 1 ? " roll." : " rolls."));
                        var loop = TaskContext.agentLoop(player);
                        if (loop != null) loop.keepVillager(villager.getUUID());
                        phase = Phase.LOCK;
                        ticks = 0;
                        return TaskState.RUNNING;
                    }
                    if (villager.getVillagerXp() > 0) return end(false, "that librarian has been traded with: its books are fixed now");
                    if (++rolls > MAX_ROLLS) return end(false, "no " + book() + " in " + MAX_ROLLS + " rolls");
                    if (rolls % 10 == 0) say("Rolled the librarian " + rolls + " times; no " + book() + " yet.");
                    phase = Phase.BREAK;
                    ticks = 0;
                    digging = false;
                }
                case BREAK -> {
                    if (level.getBlockState(lectern).isAir()) {
                        phase = Phase.PICKUP;
                        ticks = 0;
                        return TaskState.RUNNING;
                    }
                    if (!digging || ++digTicks > 200) {
                        digging = BlockDigger.startBreaking(sp, lectern);
                        digTicks = 0;
                        if (!digging && ticks > 200) return end(false, "I couldn't break the lectern to roll again");
                    }
                }
                case PICKUP -> {
                    if (ProductionTask.count(sp, Items.LECTERN) > 0 || ticks > 120) {
                        phase = Phase.WAIT_JOBLESS;
                        ticks = 0;
                        return TaskState.RUNNING;
                    }
                    if (ticks % 10 == 1) nav.navigateTo(lectern.getX(), lectern.getY(), lectern.getZ());
                    nav.tick();
                }
                case WAIT_JOBLESS -> {
                    // A librarian no one traded with goes back to no job without its lectern: then the next one.
                    if (villager.getVillagerData().getProfession() == VillagerProfession.NONE || ticks > 300) {
                        nav.cancel();
                        phase = Phase.LECTERN;
                        ticks = 0;
                    }
                }
                case LOCK -> {
                    // One bought fixes the offer for good (and starts its levels).
                    int price = price(villager, record.enchantment);
                    if (ProductionTask.count(sp, Items.EMERALD) < price) {
                        say("Its " + book() + " book costs " + price + " emeralds; I'll buy one to fix the offer when I have them.");
                        phase = Phase.CELL;
                        ticks = 0;
                        return TaskState.RUNNING;
                    }
                    sub = new TradeTask(player, new TradeTask.TradeRecord(record.toolCallId() + ":lock", "minecraft:enchanted_book#" + record.enchantment,
                            1, null, villager.getUUID()));
                    sub.start();
                }
                case CELL -> {
                    TaskState c = cell(sp, level);
                    if (c != TaskState.RUNNING) return end(true, "a librarian selling " + book() + (locked ? ", one bought to fix it" : "")
                            + (c == TaskState.SUCCESS ? ", walled in with its lectern" : ", not walled in (no blocks, or it wouldn't stay put)"));
                }
                default -> {
                    return phase == Phase.DONE ? TaskState.SUCCESS : TaskState.FAILED;
                }
            }
            return TaskState.RUNNING;
        } catch (RuntimeException e) {
            return end(false, "something went wrong: " + e.getMessage());
        }
    }

    /** The cell's blocks placed so far, and the cell it is building round. */
    private BlockPos cellAt;

    /**
     * A cell round the villager where it stands by its lectern: its four sides at its feet and head walled, a roof, the
     * lectern for one side at its feet and a window over it to trade through. Built when it stands in the cell beside
     * the lectern; RUNNING while it builds, SUCCESS when sealed, FAILED with no blocks or after two minutes.
     */
    private TaskState cell(ServerPlayer sp, ServerLevel level) {
        if (ticks > 2_400 || !Placement.hasSupportBlock(sp)) return TaskState.FAILED;
        BlockPos v = villager.blockPosition();
        // Beside its lectern (or diagonal to it): the cell is where it stands.
        if (Math.abs(v.getX() - lectern.getX()) > 1 || Math.abs(v.getZ() - lectern.getZ()) > 1 || v.getY() != lectern.getY()) return TaskState.RUNNING;
        cellAt = v;
        List<BlockPos> walls = new ArrayList<>();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos side = v.relative(d);
            if (side.equals(lectern)) continue; // the lectern is that side; the window is over it
            walls.add(side);
            walls.add(side.above());
        }
        walls.add(v.above(2));
        boolean sealed = true;
        for (BlockPos w : walls) {
            var st = level.getBlockState(w);
            if (!st.canBeReplaced()) continue;
            sealed = false;
            if (w.distToCenterSqr(sp.getEyePosition()) > 4.0 * 4.0) {
                if (ticks % 20 == 1) nav.navigateNear(w.getX(), w.getY(), w.getZ(), 2);
                nav.tick();
                return TaskState.RUNNING;
            }
            nav.cancel();
            Placement.placeAnySupportBlock(sp, w);
            return TaskState.RUNNING; // one a tick
        }
        return sealed ? TaskState.SUCCESS : TaskState.RUNNING;
    }

    /** The nearest grown villager with no job, or a librarian no one has traded with, within 32 blocks. */
    private Villager candidate(ServerPlayer sp) {
        Villager best = null;
        for (Villager v : sp.serverLevel().getEntitiesOfClass(Villager.class, new AABB(sp.blockPosition()).inflate(32, 8, 32), Villager::isAlive)) {
            if (v.isBaby()) continue;
            var prof = v.getVillagerData().getProfession();
            boolean free = prof == VillagerProfession.NONE || prof == VillagerProfession.LIBRARIAN && v.getVillagerXp() == 0;
            if (!free) continue;
            if (best == null || v.distanceToSqr(sp) < best.distanceToSqr(sp)) best = v;
        }
        return best;
    }

    /** A spot beside the villager for the lectern: open, on firm ground, not where anyone stands. */
    private static BlockPos spotBeside(ServerLevel level, Villager v) {
        BlockPos at = v.blockPosition();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos p = at.relative(d);
            if (level.getBlockState(p).canBeReplaced() && level.getFluidState(p).isEmpty() && BlockHelper.canStandOn(level.getBlockState(p.below()))
                    && level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new AABB(p)).isEmpty()) return p.immutable();
        }
        return null;
    }

    /** The villager whose job site is this lectern, if any near. */
    private static Villager holder(ServerLevel level, BlockPos lectern) {
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(lectern).inflate(32, 8, 32), Villager::isAlive)) {
            var site = v.getBrain().getMemory(MemoryModuleType.JOB_SITE);
            if (site.isPresent() && site.get().pos().equals(lectern)) return v;
        }
        return null;
    }

    /** A villager's offers as {@link VillagerWorth} reads them. */
    public static List<VillagerWorth.Offer> offers(Villager v) {
        List<VillagerWorth.Offer> out = new ArrayList<>();
        for (var o : v.getOffers()) {
            var result = o.getResult();
            String id = BuiltInRegistries.ITEM.getKey(result.getItem()).toString();
            var stored = result.get(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS);
            if (stored != null && !stored.isEmpty()) {
                for (var e : stored.entrySet()) {
                    String ench = e.getKey().unwrapKey().map(k -> k.location().toString()).orElse(null);
                    out.add(new VillagerWorth.Offer(id, ench, e.getIntValue(), e.getKey().value().getMaxLevel(), o.getCostA().getCount()));
                }
            } else {
                out.add(new VillagerWorth.Offer(id, null, 0, 0, o.getCostA().getCount()));
            }
        }
        return out;
    }

    /** The emeralds its book of that enchantment costs now (64 when it has none). */
    private static int price(Villager v, String enchantment) {
        for (var o : v.getOffers()) {
            if (TradeTask.bookHas(o.getResult(), enchantment) && o.getCostA().is(Items.EMERALD)) return o.getCostA().getCount();
        }
        return 64;
    }

    private void say(String line) {
        var loop = TaskContext.agentLoop(player);
        if (loop != null) loop.say(line);
    }

    private TaskState end(boolean ok, String line) {
        if (nav != null) nav.cancel();
        TaskContext.inputDriver(player).clear();
        outcome = line;
        say(ok ? "Done: " + line + "." : "I couldn't roll a librarian: " + line + ".");
        phase = ok ? Phase.DONE : Phase.FAILED;
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
        if (sub != null) sub.interrupt();
        if (nav != null) nav.cancel();
        TaskContext.inputDriver(player).clear();
    }
}
