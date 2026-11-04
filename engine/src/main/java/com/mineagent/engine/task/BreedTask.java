package com.mineagent.engine.task;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.task.CompanionTask;
import com.mineagent.api.task.TaskRecord;
import com.mineagent.api.task.TaskState;
import com.mineagent.engine.act.Interaction;
import com.mineagent.engine.pathing.execute.PlayerNav;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Breeding (SMH plan 70, MA8.5, food that renews): two grown animals of a kind, each fed the food they breed on (wheat
 * for cows and sheep, carrots for pigs, seeds for chickens...) by hand, as a player does; the young one counted. Never a
 * pet or a named animal.
 */
public class BreedTask extends CompanionTask<BreedTask.BreedRecord> {
    public static final class BreedRecord extends TaskRecord {
        /** The animal as the game names it ("minecraft:cow"), or null for any it has food for. */
        public final String animal;
        /** Pairs to breed. */
        public final int pairs;

        public BreedRecord(String toolCallId, String animal, int pairs) {
            super(toolCallId);
            this.animal = animal;
            this.pairs = Math.max(1, Math.min(8, pairs));
        }
    }

    private enum Phase { FIND, FEED, WAIT, DONE, FAILED }

    private Phase phase = Phase.FIND;
    private final List<Animal> pair = new ArrayList<>();
    private PlayerNav nav;
    private int ticks, bred, babiesBefore;
    private String kind;
    private String outcome = "";

    public BreedTask(AgentPlayer player, BreedRecord record) {
        super(player, record);
    }

    @Override
    protected void onStart() {
        if (nav == null) nav = new PlayerNav(player, TaskContext.navCaches(player));
    }

    @Override
    protected TaskState onTick() {
        ServerPlayer sp = TaskContext.serverPlayer(player);
        ticks++;
        try {
            switch (phase) {
                case FIND -> {
                    if (bred >= record.pairs) return end(true, line());
                    pair.clear();
                    List<Animal> ready = new ArrayList<>();
                    for (Animal a : sp.serverLevel().getEntitiesOfClass(Animal.class, new AABB(sp.blockPosition()).inflate(16, 6, 16),
                            a -> a.isAlive() && a.getAge() == 0 && a.canFallInLove() && !a.hasCustomName()
                                    && !(a instanceof net.minecraft.world.entity.TamableAnimal)
                                    && (record.animal == null || BuiltInRegistries.ENTITY_TYPE.getKey(a.getType()).toString().equals(record.animal))
                                    && food(sp, a) >= 0)) {
                        ready.add(a);
                    }
                    // Two of one kind, the nearest pair.
                    ready.sort((x, y) -> Double.compare(x.distanceToSqr(sp), y.distanceToSqr(sp)));
                    for (Animal a : ready) {
                        for (Animal b : ready) {
                            if (a != b && a.getType() == b.getType() && pair.isEmpty()) {
                                pair.add(a);
                                pair.add(b);
                            }
                        }
                    }
                    if (pair.isEmpty()) {
                        if (bred > 0) return end(true, line());
                        return end(false, "I see no two grown " + (record.animal == null ? "animals" : name(record.animal))
                                + " of a kind I have the food for");
                    }
                    kind = BuiltInRegistries.ENTITY_TYPE.getKey(pair.get(0).getType()).toString();
                    babiesBefore = babies(sp);
                    phase = Phase.FEED;
                    ticks = 0;
                    return TaskState.RUNNING;
                }
                case FEED -> {
                    Animal next = null;
                    for (Animal a : pair) if (!a.isInLove() && a.isAlive()) next = next == null ? a : next;
                    if (next == null) {
                        nav.cancel();
                        TaskContext.inputDriver(player).clear();
                        phase = Phase.WAIT;
                        ticks = 0;
                        return TaskState.RUNNING;
                    }
                    if (ticks > 1_200) return end(bred > 0, bred > 0 ? line() : "I couldn't get to the " + name(kind));
                    if (!Interaction.inEntityReach(sp, next)) {
                        if (ticks % 20 == 1) nav.navigateNear(next.getBlockX(), next.getBlockY(), next.getBlockZ(), 1);
                        nav.tick();
                        return TaskState.RUNNING;
                    }
                    nav.cancel();
                    int slot = food(sp, next);
                    if (slot < 0) return end(bred > 0, bred > 0 ? line() : "I ran out of their food");
                    String id = BuiltInRegistries.ITEM.getKey(sp.getInventory().getItem(slot).getItem()).toString();
                    if (TaskContext.selectInventoryItemForHand(player, id, InteractionHand.MAIN_HAND)) {
                        Interaction.interactEntity(sp, next, InteractionHand.MAIN_HAND);
                        TaskContext.syncInventory(sp);
                    }
                    return TaskState.RUNNING;
                }
                case WAIT -> {
                    // They find each other; a young one comes.
                    if (babies(sp) > babiesBefore) {
                        bred++;
                        phase = Phase.FIND;
                        return TaskState.RUNNING;
                    }
                    if (ticks > 600) return end(bred > 0, bred > 0 ? line() : "they were fed but no young one came");
                    return TaskState.RUNNING;
                }
                default -> {
                    return phase == Phase.DONE ? TaskState.SUCCESS : TaskState.FAILED;
                }
            }
        } catch (RuntimeException e) {
            return end(false, "something went wrong: " + e.getMessage());
        }
    }

    /** The slot of a food this animal breeds on, or -1. */
    private static int food(ServerPlayer sp, Animal a) {
        var inv = sp.getInventory();
        for (int i = 0; i < Math.min(36, inv.getContainerSize()); i++) {
            ItemStack st = inv.getItem(i);
            if (!st.isEmpty() && a.isFood(st)) return i;
        }
        return -1;
    }

    private int babies(ServerPlayer sp) {
        if (kind == null) return 0;
        return sp.serverLevel().getEntitiesOfClass(Animal.class, new AABB(sp.blockPosition()).inflate(20, 8, 20),
                a -> a.isAlive() && a.isBaby() && BuiltInRegistries.ENTITY_TYPE.getKey(a.getType()).toString().equals(kind)).size();
    }

    private static String name(String id) {
        return id == null ? "animals" : id.substring(id.indexOf(':') + 1).replace('_', ' ') + "s";
    }

    private String line() {
        return "Bred " + bred + (bred == 1 ? " pair" : " pairs") + " of " + name(kind) + ".";
    }

    private TaskState end(boolean ok, String line) {
        if (nav != null) nav.cancel();
        TaskContext.inputDriver(player).clear();
        outcome = line;
        var loop = TaskContext.agentLoop(player);
        if (loop != null) loop.say(ok ? line : "I couldn't breed them: " + line + ".");
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
        if (nav != null) nav.cancel();
        TaskContext.inputDriver(player).clear();
    }
}
