package com.mineagent.engine.task;

import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.task.CompanionTask;
import com.mineagent.api.task.TaskRecord;
import com.mineagent.api.task.TaskState;
import com.mineagent.engine.act.Interaction;
import com.mineagent.engine.pathing.execute.PlayerNav;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.phys.AABB;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Trading (SMH plan 70, MA11.3): to the nearest villager with a trade it can pay for, the trading menu opened as a
 * player opens it, and the trades made by the menu's own rules (the villager's prices, its stock). "For emeralds"
 * sells what it carries for emeralds; "for X" buys X. Villagers are never hurt (the fighting code leaves them be, and
 * no sweep goes near one). None near: to the nearest village or villagers it remembers (within 512 blocks), in legs,
 * and the trading there.
 */
public class TradeTask extends CompanionTask<TradeTask.TradeRecord> {
    public static final class TradeRecord extends TaskRecord {
        /** What it wants out of the trades, as the game names it ("minecraft:emerald" to sell for emeralds). */
        public final String want;
        /** How many trades at most. */
        public final int times;

        public TradeRecord(String toolCallId, String want, int times) {
            super(toolCallId);
            this.want = want;
            this.times = Math.max(1, Math.min(64, times));
        }
    }

    private enum Phase { FIND, TRAVEL, OPEN, TRADE, DONE, FAILED }

    /** How far a remembered village may be, and each leg of the way there. */
    static final int REMEMBERED = 512, LEG = 40;

    private Phase phase = Phase.FIND;
    private Villager villager;
    private PlayerNav nav;
    private int ticks, tries, made, travelTicks;
    private BlockPos village, leg;
    private boolean travelled;
    private final Map<String, Integer> got = new LinkedHashMap<>();
    private String outcome = "";

    public TradeTask(AgentPlayer player, TradeRecord record) {
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
                    if (villager == null || !villager.isAlive()) {
                        villager = nearest(sp);
                        if (villager == null && !travelled && (village = remembered(sp)) != null) {
                            // None here: the village it remembers.
                            travelled = true;
                            travelTicks = 0;
                            leg = null;
                            phase = Phase.TRAVEL;
                            var loop = TaskContext.agentLoop(player);
                            if (loop != null) loop.say("No villager here: off to the one I remember at " + village.toShortString() + ".");
                            return TaskState.RUNNING;
                        }
                        if (villager == null) return end(false, "no villager near has a trade for " + name(record.want) + " I can pay for");
                    }
                    if (Interaction.inEntityReach(sp, villager)) {
                        nav.cancel();
                        TaskContext.inputDriver(player).clear();
                        phase = Phase.OPEN;
                        tries = 0;
                        return TaskState.RUNNING;
                    }
                    if (ticks > 1_200) return end(false, "I couldn't get to the villager");
                    if (ticks % 20 == 1) nav.navigateNear(villager.getBlockX(), villager.getBlockY(), villager.getBlockZ(), 1);
                    nav.tick();
                    return TaskState.RUNNING;
                }
                case TRAVEL -> {
                    if (sp.blockPosition().distSqr(village) <= 20 * 20 || ++travelTicks > 6_000) {
                        nav.cancel();
                        TaskContext.inputDriver(player).clear();
                        phase = Phase.FIND;
                        ticks = 0;
                        return TaskState.RUNNING;
                    }
                    if (leg == null || sp.blockPosition().distSqr(leg) <= 4 * 4 || navDone()) {
                        leg = legToward(sp, village);
                        nav.navigateNear(leg.getX(), leg.getY(), leg.getZ(), 3);
                    }
                    nav.tick();
                    return TaskState.RUNNING;
                }
                case OPEN -> {
                    if (sp.containerMenu instanceof MerchantMenu) {
                        phase = Phase.TRADE;
                        return TaskState.RUNNING;
                    }
                    if (++tries > 40) return end(false, "the villager wouldn't trade");
                    if (tries % 5 == 1) {
                        // An empty hand: a player opens the trades with a plain click.
                        sp.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, villager.getEyePosition());
                        Interaction.interactEntity(sp, villager, InteractionHand.MAIN_HAND);
                    }
                    return TaskState.RUNNING;
                }
                case TRADE -> {
                    return trade(sp);
                }
                default -> {
                    return phase == Phase.DONE ? TaskState.SUCCESS : TaskState.FAILED;
                }
            }
        } catch (RuntimeException e) {
            return end(false, "something went wrong: " + e.getMessage());
        }
    }

    /** One trade a tick, by the menu: the offer chosen, the payment moved in, the result taken. */
    private TaskState trade(ServerPlayer sp) {
        if (!(sp.containerMenu instanceof MerchantMenu menu)) return end(made > 0, made > 0 ? line() : "the trading closed");
        if (made >= record.times) return close(sp, true, line());
        var offers = menu.getOffers();
        int pick = -1;
        for (int i = 0; i < offers.size(); i++) {
            if (fits(offers.get(i), sp)) {
                pick = i;
                break;
            }
        }
        if (pick < 0) return close(sp, made > 0, made > 0 ? line() : "nothing it offers I can pay for");
        menu.setSelectionHint(pick);
        menu.tryMoveItems(pick);
        ItemStack result = menu.getSlot(2).getItem();
        if (result.isEmpty()) return close(sp, made > 0, made > 0 ? line() : "the trade wouldn't go through");
        String id = BuiltInRegistries.ITEM.getKey(result.getItem()).toString();
        int n = result.getCount();
        menu.quickMoveStack(sp, 2); // as a shift-click on the result: the trade is made, into its pack
        got.merge(id, n, Integer::sum);
        made++;
        TaskContext.syncInventory(sp);
        return TaskState.RUNNING;
    }

    /** An offer for what it wants, in stock, that it can pay for from what it carries. */
    private boolean fits(MerchantOffer o, ServerPlayer sp) {
        if (o.isOutOfStock()) return false;
        String result = BuiltInRegistries.ITEM.getKey(o.getResult().getItem()).toString();
        int hash = record.want.indexOf('#');
        if (hash > 0) {
            // A book of one enchantment ("minecraft:enchanted_book#minecraft:mending"): a librarian's.
            if (!record.want.substring(0, hash).equals(result)) return false;
            if (!bookHas(o.getResult(), record.want.substring(hash + 1))) return false;
        } else if (!record.want.equals(result)) return false;
        ItemStack a = o.getCostA(), b = o.getCostB();
        if (ProductionTask.count(sp, a.getItem()) < a.getCount()) return false;
        return b.isEmpty() || ProductionTask.count(sp, b.getItem()) >= b.getCount() + (b.is(a.getItem()) ? a.getCount() : 0);
    }

    private boolean navDone() {
        return !nav.isNavigating();
    }

    /**
     * The nearest village it remembers in this world, or the villagers it saw last, within {@link #REMEMBERED}: where
     * it goes when none is near. Null when it knows of none.
     */
    private BlockPos remembered(ServerPlayer sp) {
        var loop = TaskContext.agentLoop(player);
        if (loop == null) return null;
        String dim = sp.serverLevel().dimension().location().toString();
        BlockPos at = sp.blockPosition();
        BlockPos best = null;
        double bestD = (double) REMEMBERED * REMEMBERED;
        for (var p : loop.placeKeeper().memory().places()) {
            if (!"village".equals(p.kind()) || !p.at().dim().equals(dim)) continue;
            BlockPos b = new BlockPos(p.at().x(), p.at().y(), p.at().z());
            double d = b.distSqr(at);
            if (d < bestD && d > 32 * 32) {
                bestD = d;
                best = b;
            }
        }
        for (var f : loop.placeKeeper().sightings().nearest("villager", dim, at.getX(), at.getY(), at.getZ(), 4, REMEMBERED)) {
            var v = f.sighting();
            BlockPos b = new BlockPos(v.x(), v.y(), v.z());
            double d = b.distSqr(at);
            if (d < bestD && d > 32 * 32) {
                bestD = d;
                best = b;
            }
        }
        return best;
    }

    /** The next stretch of the way: {@link #LEG} blocks toward it, on the ground there when that is loaded. */
    private static BlockPos legToward(ServerPlayer sp, BlockPos to) {
        BlockPos at = sp.blockPosition();
        double dx = to.getX() - at.getX(), dz = to.getZ() - at.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len <= LEG) return to;
        int x = at.getX() + (int) Math.round(dx / len * LEG), z = at.getZ() + (int) Math.round(dz / len * LEG);
        var level = sp.serverLevel();
        int y = level.isLoaded(new BlockPos(x, at.getY(), z))
                ? level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : at.getY();
        return new BlockPos(x, y, z);
    }

    /** Whether an enchanted book holds this enchantment ("minecraft:mending"). */
    public static boolean bookHas(ItemStack book, String enchantment) {
        var stored = book.get(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS);
        if (stored == null) return false;
        for (var e : stored.keySet()) if (e.unwrapKey().map(k -> k.location().toString().equals(enchantment)).orElse(false)) return true;
        return false;
    }

    /** The nearest villager within 32 blocks with a trade it can pay for. */
    private Villager nearest(ServerPlayer sp) {
        Villager best = null;
        for (Villager v : sp.serverLevel().getEntitiesOfClass(Villager.class, new AABB(sp.blockPosition()).inflate(32, 8, 32),
                v -> v.isAlive() && !v.isBaby() && !v.isTrading())) {
            boolean any = false;
            for (MerchantOffer o : v.getOffers()) any |= fits(o, sp);
            if (!any) continue;
            if (best == null || v.distanceToSqr(sp) < best.distanceToSqr(sp)) best = v;
        }
        return best;
    }

    private String line() {
        java.util.List<String> parts = new java.util.ArrayList<>();
        got.forEach((id, n) -> parts.add(n + " " + com.mineagent.engine.planning.production.ProductionPlanner.plural(id, n)));
        return "Traded " + made + (made == 1 ? " time" : " times") + ": " + String.join(", ", parts) + ".";
    }

    private static String name(String id) {
        return id.substring(id.indexOf(':') + 1).replace('_', ' ');
    }

    private TaskState close(ServerPlayer sp, boolean ok, String line) {
        sp.closeContainer();
        return end(ok, line);
    }

    private TaskState end(boolean ok, String line) {
        if (nav != null) nav.cancel();
        TaskContext.inputDriver(player).clear();
        outcome = line;
        var loop = TaskContext.agentLoop(player);
        if (loop != null) loop.say(ok ? line : "I couldn't trade: " + line + ".");
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
        var sp = TaskContext.serverPlayer(player);
        if (sp != null && sp.containerMenu instanceof MerchantMenu) sp.closeContainer();
    }
}
