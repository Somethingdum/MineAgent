package com.mineagent.engine.world;

import com.mineagent.engine.loop.AgentLoop;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who opens a companion's own chests, and what they take (SMH plan 70, its owner: "If something is stolen, the agent
 * should confront the other player and demand it's items back"): a player other than its owner, a trusted player or a
 * companion with one of its chests open is watched; what the chest held when they opened it is set against what it holds
 * when they shut it (or leave the game), and what went is theirs to give back ({@link AgentLoop#stolen}); what they put
 * back is set against what they owe ({@link AgentLoop#givenBack}). Looked at every five ticks; a person can't open a chest
 * and empty it faster than that.
 */
public final class ChestWatch {
    private ChestWatch() {}

    private record Open(AbstractContainerMenu menu, Container container, AgentLoop loop, String dim, BlockPos chest, String name,
                        Map<String, Integer> before) {}

    private static final Map<UUID, Open> OPEN = new ConcurrentHashMap<>();

    /** Each server tick: the open chests looked at every fifth. */
    public static void tick(MinecraftServer server, Collection<AgentLoop> loops) {
        if (server == null || server.getTickCount() % 5 != 0) return;
        Set<UUID> online = new HashSet<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            online.add(p.getUUID());
            if (com.mineagent.engine.MineAgentEngine.isCompanionPlayer(p.getUUID())) continue;
            Open o = OPEN.get(p.getUUID());
            AbstractContainerMenu m = p.containerMenu;
            if (o != null && m != o.menu()) {
                settle(p.getUUID(), o);
                OPEN.remove(p.getUUID());
                o = null;
            }
            if (o != null || m == null || m == p.inventoryMenu || !(m instanceof ChestMenu cm)) continue;
            Container c = cm.getContainer();
            for (AgentLoop loop : loops) {
                if (loop == null || loop.mayTakeFromMyChests(p.getUUID())) continue;
                BlockPos chest = loop.ownChestFor(c, p.serverLevel(), p.blockPosition());
                if (chest == null) continue;
                OPEN.put(p.getUUID(), new Open(m, c, loop, p.serverLevel().dimension().location().toString(), chest,
                        p.getGameProfile().getName(), contents(c)));
                break;
            }
        }
        // Gone from the game with it open: what they took went with them.
        for (var e : java.util.List.copyOf(OPEN.entrySet())) {
            if (online.contains(e.getKey())) continue;
            settle(e.getKey(), e.getValue());
            OPEN.remove(e.getKey());
        }
    }

    private static void settle(UUID player, Open o) {
        try {
            Map<String, Integer> after = contents(o.container());
            Map<String, Integer> taken = new LinkedHashMap<>(), given = new LinkedHashMap<>();
            Set<String> kinds = new HashSet<>(o.before().keySet());
            kinds.addAll(after.keySet());
            for (String k : kinds) {
                int d = after.getOrDefault(k, 0) - o.before().getOrDefault(k, 0);
                if (d < 0) taken.put(k, -d);
                else if (d > 0) given.put(k, d);
            }
            o.loop().chestSeen(o.dim(), o.chest(), after);
            if (!given.isEmpty()) o.loop().givenBack(player, o.name(), given);
            if (!taken.isEmpty()) o.loop().stolen(player, o.name(), taken, o.dim(), o.chest(), false);
        } catch (RuntimeException e) {
            System.err.println("[MineAgent] Chest watch failed: " + e.getMessage());
        }
    }

    /** What a container holds, item to how many. */
    public static Map<String, Integer> contents(Container c) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack st = c.getItem(i);
            if (!st.isEmpty()) out.merge(BuiltInRegistries.ITEM.getKey(st.getItem()).toString(), st.getCount(), Integer::sum);
        }
        return out;
    }
}
