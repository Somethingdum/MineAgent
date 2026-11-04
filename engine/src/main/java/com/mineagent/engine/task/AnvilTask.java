package com.mineagent.engine.task;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.task.CompanionTask;
import com.mineagent.api.task.TaskRecord;
import com.mineagent.api.task.TaskState;
import com.mineagent.engine.act.Interaction;
import com.mineagent.engine.act.Placement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * Anvils and grindstones (SMH plan 70, MA11.2), through their own menus as a player uses them. Repair: a worn piece of
 * gear and what mends it (the ingots, gems or planks it is made of, a quarter of its wear each, or a second worn one of
 * the same kind) in an anvil, paid for in experience; its best and most worn gear first. Grind: a piece's enchantments
 * taken off at a grindstone (curses stay), the experience picked up. An anvil or grindstone near, or one it carries put
 * down beside it.
 */
public class AnvilTask extends CompanionTask<AnvilTask.AnvilRecord> {
    public static final class AnvilRecord extends TaskRecord {
        /** "repair" or "grind". */
        public final String mode;
        /** A word in the item's name ("pickaxe"), or "gear" for the best it has. */
        public final String what;

        /** For "book": the enchantment of the book to put on ("minecraft:mending"), or null for any book it has. */
        public final String book;

        public AnvilRecord(String toolCallId, String mode, String what) {
            this(toolCallId, mode, what, null);
        }

        public AnvilRecord(String toolCallId, String mode, String what, String book) {
            super(toolCallId);
            this.mode = "grind".equals(mode) || "book".equals(mode) || "best".equals(mode) ? mode : "repair";
            this.what = what == null || what.isBlank() ? "gear" : what;
            this.book = book;
        }
    }

    private enum Phase { SITE, OPEN, WORK, DONE, FAILED }

    /** At most this many pieces seen to in one order ("repair your gear"). */
    static final int MOST = 6;

    private Phase phase = Phase.SITE;
    private BlockPos block;
    private int tries, done, levelsSpent;
    private final List<String> worked = new ArrayList<>();
    private final java.util.Set<Integer> skipped = new java.util.HashSet<>();
    private String outcome = "";

    public AnvilTask(AgentPlayer player, AnvilRecord record) {
        super(player, record);
    }

    private boolean grind() {
        return "grind".equals(record.mode);
    }

    private boolean bookMode() {
        return "book".equals(record.mode);
    }

    @Override
    protected void onStart() {
    }

    @Override
    protected TaskState onTick() {
        ServerPlayer sp = TaskContext.serverPlayer(player);
        ServerLevel level = sp.serverLevel();
        try {
            switch (phase) {
                case SITE -> {
                    if (pick(sp) < 0) return end(false, nothingTo());
                    block = near(level, sp);
                    if (block == null) {
                        var item = grind() ? Items.GRINDSTONE : Items.ANVIL;
                        if (ProductionTask.count(sp, item) == 0) return end(false, "I have no " + (grind() ? "grindstone" : "anvil") + " and know of none near");
                        BlockPos here = ProductionTask.standing(sp);
                        var state = (grind() ? Blocks.GRINDSTONE : Blocks.ANVIL).defaultBlockState();
                        for (int[] o : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}}) {
                            BlockPos p = here.offset(o[0], 0, o[1]);
                            if (level.getBlockState(p).isAir() && Placement.placeBlock(sp, p, state)) {
                                block = p;
                                break;
                            }
                        }
                        if (block == null) {
                            if (++tries > 40) return end(false, "I couldn't put the " + (grind() ? "grindstone" : "anvil") + " down");
                            return TaskState.RUNNING;
                        }
                    }
                    phase = Phase.OPEN;
                    tries = 0;
                    return TaskState.RUNNING;
                }
                case OPEN -> {
                    if (grind() ? sp.containerMenu instanceof GrindstoneMenu : sp.containerMenu instanceof AnvilMenu) {
                        phase = Phase.WORK;
                        return TaskState.RUNNING;
                    }
                    if (!level.getBlockState(block).is(grind() ? Blocks.GRINDSTONE : Blocks.ANVIL)
                            && !level.getBlockState(block).is(BlockTags.ANVIL)) return end(false, "the " + (grind() ? "grindstone" : "anvil") + " is gone");
                    if (++tries > 40) return end(false, "I couldn't open the " + (grind() ? "grindstone" : "anvil"));
                    if (tries % 5 == 1) Interaction.interactBlock(sp, block, InteractionHand.MAIN_HAND);
                    return TaskState.RUNNING;
                }
                case WORK -> {
                    return grind() ? grindOne(sp) : bestMode() ? bestStep(sp) : repairOne(sp);
                }
                default -> {
                    return phase == Phase.DONE ? TaskState.SUCCESS : TaskState.FAILED;
                }
            }
        } catch (RuntimeException e) {
            return end(false, "something went wrong: " + e.getMessage());
        }
    }

    /** One piece mended in the anvil: it and what mends it in, the cost read, the result taken when it can pay. */
    private TaskState repairOne(ServerPlayer sp) {
        if (!(sp.containerMenu instanceof AnvilMenu menu)) return finish(sp);
        var inv = sp.getInventory();
        int slot = pick(sp);
        if (slot < 0 || done >= MOST) return finish(sp);
        ItemStack gear = inv.getItem(slot);
        int mender = bookMode() ? bookSlot(sp) : menderFor(sp, slot);
        if (bookMode() && mender < 0) return close(sp, false, "I have no " + (record.book == null ? "enchanted book" : bookName() + " book"));
        if (mender < 0) {
            skipped.add(slot);
            return TaskState.RUNNING;
        }
        String name = BuiltInRegistries.ITEM.getKey(gear.getItem()).getPath().replace('_', ' ');
        ItemStack mend = inv.getItem(mender);
        // The material: a quarter of its wear each, no more than it takes; a second piece: the whole of it.
        int units = bookMode() || mend.is(gear.getItem()) ? 1
                : Math.max(1, Math.min(mend.getCount(), (int) Math.ceil(gear.getDamageValue() / Math.max(1.0, gear.getMaxDamage() / 4.0))));
        menu.getSlot(0).set(inv.removeItem(slot, 1));
        menu.getSlot(1).set(inv.removeItem(mender, units));
        int cost = menu.getCost();
        boolean can = cost > 0 && cost < 40 && sp.experienceLevel >= cost && !menu.getSlot(2).getItem().isEmpty();
        if (can) {
            int before = sp.experienceLevel;
            menu.clicked(2, 0, ClickType.QUICK_MOVE, sp);
            levelsSpent += Math.max(0, before - sp.experienceLevel);
            done++;
            worked.add(name);
        }
        // What is left in the anvil back in its pack (the result, if it couldn't pay).
        back(sp, menu);
        TaskContext.syncInventory(sp);
        if (!can) {
            if (done > 0) return finish(sp);
            String doing = bookMode() ? "putting the book on my " + name : "mending my " + name;
            return close(sp, false, cost >= 40 ? doing + " is too expensive for an anvil now" : cost <= 0 ? "the anvil won't put that book on my " + name
                    : doing + " costs " + cost + " levels and I have " + sp.experienceLevel);
        }
        if (bookMode()) return finish(sp); // one book, one piece
        return TaskState.RUNNING;
    }

    private boolean bestMode() {
        return "best".equals(record.mode);
    }

    /** The order the books go on in (gear/GodGear), and how far it has got. */
    private java.util.List<com.mineagent.engine.gear.GodGear.Step> plan;
    private int stepAt;
    private net.minecraft.world.item.Item gearItem;

    /**
     * The books it carries that the piece lacks of its best, put on in the cheapest order with no step too expensive
     * (books into books first where that is cheaper, then onto the piece): one anvil step a tick, each paid for in levels.
     */
    private TaskState bestStep(ServerPlayer sp) {
        if (!(sp.containerMenu instanceof AnvilMenu menu)) return finish(sp);
        var inv = sp.getInventory();
        if (plan == null) {
            int slot = pick(sp);
            if (slot < 0) return close(sp, false, "I have nothing" + ("gear".equals(record.what) ? "" : " called " + record.what) + " to enchant");
            ItemStack gear = inv.getItem(slot);
            gearItem = gear.getItem();
            String kind = com.mineagent.engine.gear.GodGear.kindOf(BuiltInRegistries.ITEM.getKey(gearItem).toString());
            if (kind == null) return close(sp, false, "I don't know the best enchantments for that");
            java.util.Map<String, Integer> has = enchants(gear);
            java.util.List<com.mineagent.engine.gear.GodGear.Ench> books = new java.util.ArrayList<>();
            java.util.Set<String> taken = new java.util.HashSet<>();
            for (var want : com.mineagent.engine.gear.GodGear.missing(kind, has)) {
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    var b = bookOf(inv.getItem(i));
                    if (b != null && b.size() == 1 && b.getOrDefault(want.id(), 0) >= want.level() && taken.add(want.id())) {
                        books.add(want);
                        break;
                    }
                }
            }
            if (books.isEmpty()) return close(sp, false, "I have no books my " + kind.replace('_', ' ') + " lacks");
            Integer rc = gear.get(net.minecraft.core.component.DataComponents.REPAIR_COST);
            int work = rc == null ? 0 : 32 - Integer.numberOfLeadingZeros(Math.max(0, rc));
            var order = com.mineagent.engine.gear.GodGear.order(books, work);
            if (!order.possible()) return close(sp, false, "those books on my " + kind.replace('_', ' ') + " would be too expensive for an anvil in any order");
            if (sp.experienceLevel < order.steps().stream().mapToInt(com.mineagent.engine.gear.GodGear.Step::cost).max().orElse(0)) {
                return close(sp, false, "that takes " + order.total() + " levels all told, and I have " + sp.experienceLevel);
            }
            plan = order.steps();
            stepAt = 0;
        }
        if (stepAt >= plan.size()) {
            worked.add(BuiltInRegistries.ITEM.getKey(gearItem).getPath().replace('_', ' '));
            return finish(sp);
        }
        var step = plan.get(stepAt);
        int t = slotFor(sp, step.target()), s = slotFor(sp, step.sacrifice());
        if (t < 0 || s < 0 || t == s) return close(sp, false, "I lost track of a book in the order");
        menu.getSlot(0).set(inv.removeItem(t, 1));
        menu.getSlot(1).set(inv.removeItem(s, 1));
        int cost = menu.getCost();
        boolean can = cost > 0 && cost < 40 && sp.experienceLevel >= cost && !menu.getSlot(2).getItem().isEmpty();
        if (can) {
            int before = sp.experienceLevel;
            menu.clicked(2, 0, ClickType.QUICK_MOVE, sp);
            levelsSpent += Math.max(0, before - sp.experienceLevel);
            done++;
            stepAt++;
        }
        back(sp, menu);
        TaskContext.syncInventory(sp);
        if (!can) return close(sp, false, "a step cost " + cost + " levels and I have " + sp.experienceLevel);
        return TaskState.RUNNING;
    }

    /** The slot holding what a step names: the piece ("item" in it) or the book holding just those enchantments. */
    private int slotFor(ServerPlayer sp, java.util.List<String> names) {
        var inv = sp.getInventory();
        boolean item = names.contains("item");
        java.util.Set<String> want = new java.util.HashSet<>(names);
        want.remove("item");
        int best = -1;
        for (int i = 0; i < Math.min(36, inv.getContainerSize()); i++) {
            ItemStack st = inv.getItem(i);
            if (item) {
                if (!st.is(gearItem)) continue;
                java.util.Set<String> got = new java.util.HashSet<>();
                enchants(st).forEach((k, v) -> got.add(k + " " + v));
                if (got.containsAll(want)) return i;
                if (best < 0) best = i;
            } else {
                var b = bookOf(st);
                if (b == null) continue;
                java.util.Set<String> got = new java.util.HashSet<>();
                b.forEach((k, v) -> got.add(k + " " + v));
                if (got.equals(want)) return i;
            }
        }
        return best;
    }

    /** A piece's enchantments, by name ("sharpness") and level. */
    private static java.util.Map<String, Integer> enchants(ItemStack st) {
        java.util.Map<String, Integer> out = new java.util.HashMap<>();
        var e = st.get(net.minecraft.core.component.DataComponents.ENCHANTMENTS);
        if (e != null) for (var x : e.entrySet()) x.getKey().unwrapKey().ifPresent(k -> out.put(k.location().getPath(), x.getIntValue()));
        return out;
    }

    /** An enchanted book's enchantments by name and level, or null for anything else. */
    private static java.util.Map<String, Integer> bookOf(ItemStack st) {
        if (!st.is(Items.ENCHANTED_BOOK)) return null;
        java.util.Map<String, Integer> out = new java.util.HashMap<>();
        var e = st.get(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS);
        if (e != null) for (var x : e.entrySet()) x.getKey().unwrapKey().ifPresent(k -> out.put(k.location().getPath(), x.getIntValue()));
        return out;
    }

    /** One piece's enchantments ground off: it in, the result taken; the experience it gives back is picked up. */
    private TaskState grindOne(ServerPlayer sp) {
        if (!(sp.containerMenu instanceof GrindstoneMenu menu)) return finish(sp);
        var inv = sp.getInventory();
        int slot = pick(sp);
        if (slot < 0 || done >= MOST) return finish(sp);
        String name = BuiltInRegistries.ITEM.getKey(inv.getItem(slot).getItem()).getPath().replace('_', ' ');
        menu.getSlot(0).set(inv.removeItem(slot, 1));
        boolean can = !menu.getSlot(2).getItem().isEmpty();
        if (can) {
            menu.clicked(2, 0, ClickType.QUICK_MOVE, sp);
            done++;
            worked.add(name);
        }
        back(sp, menu);
        TaskContext.syncInventory(sp);
        if (!can) {
            skipped.add(slot);
            if (done > 0) return finish(sp);
            return close(sp, false, "the grindstone takes nothing off my " + name);
        }
        return TaskState.RUNNING;
    }

    private static void back(ServerPlayer sp, net.minecraft.world.inventory.AbstractContainerMenu menu) {
        for (int i = 0; i < 3; i++) {
            ItemStack left = menu.getSlot(i).getItem();
            if (left.isEmpty()) continue;
            ItemStack copy = left.copy();
            menu.getSlot(i).set(ItemStack.EMPTY);
            if (!sp.getInventory().add(copy)) sp.drop(copy, false);
        }
    }

    private TaskState finish(ServerPlayer sp) {
        if (done == 0) return close(sp, false, nothingTo());
        String list = String.join(", ", worked);
        return close(sp, true, grind() ? "Ground the enchantments off my " + list + "."
                : bestMode() ? "Enchanted my " + list + " in the best order: " + done + (done == 1 ? " step, " : " steps, ") + levelsSpent + (levelsSpent == 1 ? " level." : " levels.")
                : bookMode() ? "Put " + (record.book == null ? "the book" : bookName()) + " on my " + list + " (" + levelsSpent + (levelsSpent == 1 ? " level)." : " levels).")
                : "Mended my " + list + " (" + levelsSpent + (levelsSpent == 1 ? " level)." : " levels)."));
    }

    private String bookName() {
        return record.book == null ? "the book" : record.book.substring(record.book.indexOf(':') + 1).replace('_', ' ');
    }

    private String nothingTo() {
        String called = "gear".equals(record.what) ? "" : " called " + record.what;
        return grind() ? "I have nothing enchanted" + called + " to grind" : bookMode() ? "I have nothing" + called + " to put the book on"
                : "I have nothing worn" + called + " to mend";
    }

    /**
     * The piece to see to: for a repair, worn, with something to mend it, best material and most worn first; for a
     * grind, enchanted with more than curses. The slot, or -1.
     */
    private int pick(ServerPlayer sp) {
        var inv = sp.getInventory();
        int best = -1;
        double bestScore = -1;
        for (int i = 0; i < Math.min(36, inv.getContainerSize()); i++) {
            if (skipped.contains(i)) continue;
            ItemStack st = inv.getItem(i);
            if (st.isEmpty()) continue;
            String id = BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
            if (!"gear".equals(record.what) && !id.contains(record.what.replace(' ', '_'))) continue;
            if (grind()) {
                if (!st.isEnchanted() && !st.is(Items.ENCHANTED_BOOK)) continue;
            } else if (bookMode() || bestMode()) {
                if (st.is(Items.ENCHANTED_BOOK) || st.is(Items.BOOK) || !st.isEnchantable() && !st.isDamageableItem()) continue;
            } else {
                if (!st.isDamageableItem() || st.getDamageValue() <= 0 || menderFor(sp, i) < 0) continue;
            }
            double worn = st.isDamageableItem() ? st.getDamageValue() / (double) Math.max(1, st.getMaxDamage()) : 0;
            double score = (id.startsWith("netherite_") ? 50 : id.startsWith("diamond_") ? 40 : id.startsWith("iron_") ? 30
                    : id.startsWith("stone_") ? 20 : 10) + worn * 10;
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    /** For a book: the enchanted book to put on (of the named enchantment, if one was named). The slot, or -1. */
    private int bookSlot(ServerPlayer sp) {
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack c = inv.getItem(i);
            if (!c.is(Items.ENCHANTED_BOOK)) continue;
            if (record.book == null || TradeTask.bookHas(c, record.book)) return i;
        }
        return -1;
    }

    /** What mends the piece in this slot: its material, or a second worn one of the same kind. The slot, or -1. */
    private static int menderFor(ServerPlayer sp, int slot) {
        var inv = sp.getInventory();
        ItemStack gear = inv.getItem(slot);
        int same = -1;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (i == slot) continue;
            ItemStack c = inv.getItem(i);
            if (c.isEmpty()) continue;
            if (gear.getItem().isValidRepairItem(gear, c)) return i;
            if (same < 0 && c.is(gear.getItem()) && c.getDamageValue() > 0) same = i; // a fresh one isn't spent on it
        }
        return same;
    }

    /** An anvil (any wear) or a grindstone within 8 blocks, or null. */
    private BlockPos near(ServerLevel level, ServerPlayer sp) {
        BlockPos at = sp.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-8, -3, -8), at.offset(8, 3, 8))) {
            var st = level.getBlockState(p);
            if (grind() ? st.is(Blocks.GRINDSTONE) : st.is(BlockTags.ANVIL)) return p.immutable();
        }
        return null;
    }

    private TaskState close(ServerPlayer sp, boolean ok, String line) {
        // Picked up: the experience a grindstone gives back lands at it.
        sp.closeContainer();
        return end(ok, line);
    }

    private TaskState end(boolean ok, String line) {
        TaskContext.inputDriver(player).clear();
        outcome = line;
        var loop = TaskContext.agentLoop(player);
        if (loop != null) loop.say(ok ? line : "I couldn't " + (grind() ? "grind that" : bookMode() ? "put the book on" : "mend that") + ": " + line + ".");
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
        TaskContext.inputDriver(player).clear();
        var sp = TaskContext.serverPlayer(player);
        if (sp != null && (sp.containerMenu instanceof AnvilMenu || sp.containerMenu instanceof GrindstoneMenu)) sp.closeContainer();
    }
}
