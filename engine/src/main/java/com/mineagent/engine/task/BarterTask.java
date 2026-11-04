package com.mineagent.engine.task;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.task.CompanionTask;
import com.mineagent.api.task.TaskRecord;
import com.mineagent.api.task.TaskState;
import com.mineagent.engine.pathing.execute.PlayerNav;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bartering with piglins (SMH plan 70, MA11.1), as a player does: gold armour on (a piglin turns on a player without
 * it), a gold ingot thrown to each piglin that isn't busy, and what they throw back after looking it over picked up.
 * Never in a bastion's treasure: it only throws gold and picks up what lies loose. Ender pearls, fire resistance,
 * string and obsidian come this way.
 */
public class BarterTask extends CompanionTask<BarterTask.BarterRecord> {
    public static final class BarterRecord extends TaskRecord {
        public final int gold;

        public BarterRecord(String toolCallId, int gold) {
            super(toolCallId);
            this.gold = Math.max(1, Math.min(64, gold));
        }
    }

    private enum Phase { ARMOUR, BARTER, COLLECT, DONE, FAILED }

    /** A piglin looks gold over for six seconds before it throws something back. */
    static final int ADMIRE = 140;

    private Phase phase = Phase.ARMOUR;
    private PlayerNav nav;
    private final Map<UUID, Long> given = new HashMap<>();
    private final Map<String, Integer> before = new HashMap<>();
    private int thrown, ticks, collectTicks;
    private long lastThrow;
    private String outcome = "";

    public BarterTask(AgentPlayer player, BarterRecord record) {
        super(player, record);
    }

    @Override
    protected void onStart() {
        if (nav == null) nav = new PlayerNav(player, TaskContext.navCaches(player));
    }

    @Override
    protected TaskState onTick() {
        ServerPlayer sp = TaskContext.serverPlayer(player);
        long now = sp.serverLevel().getGameTime();
        ticks++;
        try {
            switch (phase) {
                case ARMOUR -> {
                    if (ProductionTask.count(sp, Items.GOLD_INGOT) == 0) return end(false, "I have no gold to barter with");
                    if (!wearingGold(sp) && !putOnGold(sp)) return end(false, "I have no gold armour, and piglins turn on anyone without it");
                    var inv = sp.getInventory();
                    for (int i = 0; i < inv.getContainerSize(); i++) {
                        ItemStack st = inv.getItem(i);
                        if (!st.isEmpty()) before.merge(id(st), st.getCount(), Integer::sum);
                    }
                    phase = Phase.BARTER;
                    return TaskState.RUNNING;
                }
                case BARTER -> {
                    if (thrown >= record.gold || ProductionTask.count(sp, Items.GOLD_INGOT) == 0) {
                        // The last piglin's look over, then what they threw picked up.
                        if (now - lastThrow > ADMIRE + 20) {
                            phase = Phase.COLLECT;
                            collectTicks = 0;
                        }
                        return TaskState.RUNNING;
                    }
                    if (ticks > 3_600) return end(thrown > 0, thrown > 0 ? line(sp) : "no piglin would take my gold");
                    Piglin next = null;
                    double best = Double.MAX_VALUE;
                    boolean any = false;
                    for (Piglin p : sp.serverLevel().getEntitiesOfClass(Piglin.class, new AABB(sp.blockPosition()).inflate(24, 8, 24),
                            p -> p.isAlive() && !p.isBaby())) {
                        any = true;
                        Long at = given.get(p.getUUID());
                        boolean busy = at != null && now - at < ADMIRE + 20 || !p.getOffhandItem().isEmpty() || p.getTarget() != null;
                        if (busy) continue;
                        double d = p.distanceToSqr(sp);
                        if (d < best) {
                            best = d;
                            next = p;
                        }
                    }
                    if (!any) return end(thrown > 0, thrown > 0 ? line(sp) : "I see no piglins near");
                    if (next == null) {
                        nav.cancel();
                        TaskContext.inputDriver(player).clear();
                        return TaskState.RUNNING; // all of them looking gold over: the next one free
                    }
                    if (next.distanceTo(sp) > 3.0) {
                        if (ticks % 20 == 1) nav.navigateNear(next.getBlockX(), next.getBlockY(), next.getBlockZ(), 2);
                        nav.tick();
                        return TaskState.RUNNING;
                    }
                    nav.cancel();
                    TaskContext.inputDriver(player).clear();
                    sp.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, next.position().add(0, 0.2, 0));
                    var inv = sp.getInventory();
                    for (int i = 0; i < inv.getContainerSize(); i++) {
                        if (!inv.getItem(i).is(Items.GOLD_INGOT)) continue;
                        ItemEntity e = sp.drop(inv.removeItem(i, 1), false, true);
                        com.mineagent.engine.survival.Manners.handedOver(e, sp); // theirs now: not picked back up
                        break;
                    }
                    TaskContext.syncInventory(sp);
                    given.put(next.getUUID(), now);
                    thrown++;
                    lastThrow = now;
                    return TaskState.RUNNING;
                }
                case COLLECT -> {
                    // What they threw back, lying loose near: walked over and picked up.
                    collectTicks++;
                    ItemEntity loot = null;
                    double best = Double.MAX_VALUE;
                    for (ItemEntity e : sp.serverLevel().getEntitiesOfClass(ItemEntity.class, new AABB(sp.blockPosition()).inflate(10, 4, 10),
                            e -> e.isAlive() && !e.getItem().is(Items.GOLD_INGOT) && com.mineagent.engine.survival.Manners.mayTake(e, sp))) {
                        double d = e.distanceToSqr(sp);
                        if (d < best) {
                            best = d;
                            loot = e;
                        }
                    }
                    if (loot == null || collectTicks > 400) return end(true, line(sp));
                    if (ticks % 10 == 0) nav.navigateTo(loot.getBlockX(), loot.getBlockY(), loot.getBlockZ());
                    nav.tick();
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

    /** "Bartered 4 gold: 2 ender pearls, 8 gravel." */
    private String line(ServerPlayer sp) {
        Map<String, Integer> now = new HashMap<>();
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (!st.isEmpty()) now.merge(id(st), st.getCount(), Integer::sum);
        }
        Map<String, Integer> got = new LinkedHashMap<>();
        now.forEach((k, v) -> {
            int more = v - before.getOrDefault(k, 0);
            if (more > 0 && !k.equals("minecraft:gold_ingot")) got.put(k, more);
        });
        List<String> parts = new ArrayList<>();
        got.forEach((k, v) -> parts.add(v + " " + com.mineagent.engine.planning.production.ProductionPlanner.plural(k, v)));
        return "Bartered " + thrown + " gold" + (parts.isEmpty() ? "; nothing came back yet." : ": " + String.join(", ", parts) + ".");
    }

    private static String id(ItemStack st) {
        return BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
    }

    private static boolean golden(ItemStack st) {
        return BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().startsWith("golden_");
    }

    private static boolean wearingGold(ServerPlayer sp) {
        for (var slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            ItemStack worn = sp.getItemBySlot(slot);
            if (worn.getItem() instanceof ArmorItem && golden(worn)) return true;
        }
        return false;
    }

    /** One piece of gold armour on, in its own slot; what was there back in the pack. */
    private static boolean putOnGold(ServerPlayer sp) {
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (!(st.getItem() instanceof ArmorItem a) || !golden(st)) continue;
            var slot = a.getEquipmentSlot();
            ItemStack old = sp.getItemBySlot(slot);
            sp.setItemSlot(slot, st.copy());
            inv.setItem(i, old);
            TaskContext.syncInventory(sp);
            return true;
        }
        return false;
    }

    private TaskState end(boolean ok, String line) {
        if (nav != null) nav.cancel();
        TaskContext.inputDriver(player).clear();
        outcome = line;
        var loop = TaskContext.agentLoop(player);
        if (loop != null) loop.say(ok ? line : "I couldn't barter: " + line + ".");
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
