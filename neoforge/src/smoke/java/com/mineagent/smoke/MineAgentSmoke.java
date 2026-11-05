package com.mineagent.smoke;

import com.mineagent.engine.MineAgentEngine;
import com.mineagent.engine.entity.CompanionEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The companion on a real NeoForge server (SMH plan 71, step 0.6): {@code ./gradlew :neoforge:runSmoke} starts a
 * dedicated server with the mod and this one, an owner joins (a stand-in player, as the Fabric benchmark's), the
 * companion is spawned for them and must be in the server's player list, still there and alive 200 ticks later, and
 * gone from it once despawned. NeoForge's PlayerList.getPlayers() is unmodifiable, which broke every spawn until
 * upstream's fix (4452e0a); this is the check that it stays fixed. The JVM exits 0 when all of that holds, 1 when not,
 * 2 when the server never gets that far. Only with -Dmineagent.smoke=true; never in the jar.
 */
@Mod("mineagent_smoke")
public final class MineAgentSmoke {
    private static CompanionEntity companion;
    private static long spawnedAt = -1;

    public MineAgentSmoke() {
        if (!Boolean.getBoolean("mineagent.smoke")) return;
        NeoForge.EVENT_BUS.addListener(MineAgentSmoke::onStarted);
        NeoForge.EVENT_BUS.addListener(MineAgentSmoke::onTick);
        Thread timeout = new Thread(() -> {
            try {
                Thread.sleep(600_000);
            } catch (InterruptedException e) {
                return;
            }
            end(2, "the server did not get through the check in ten minutes");
        }, "MineAgent smoke timeout");
        timeout.setDaemon(true);
        timeout.start();
    }

    private static void onStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        try {
            ServerLevel level = server.overworld();
            var spawn = level.getSharedSpawnPos();
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
            var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                    new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes("OfflinePlayer:Owner".getBytes()), "Owner"), false);
            ServerPlayer owner = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation());
            var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
            new io.netty.channel.embedded.EmbeddedChannel(connection);
            server.getPlayerList().placeNewPlayer(connection, owner, cookie);
            owner.teleportTo(level, spawn.getX() + 0.5, y, spawn.getZ() + 0.5, 0.0f, 0.0f);
            companion = MineAgentEngine.spawnCompanion(owner, "Smoke", "openai-compatible", "smoke", "smoke",
                    "http://127.0.0.1:9", 0.2, null, false);
            if (companion == null) {
                end(1, "the companion did not spawn");
                return;
            }
            boolean listed = server.getPlayerList().getPlayers().contains(companion.serverPlayer());
            System.out.println("[SMOKE] spawned " + companion.companionId() + ", in the player list: " + listed);
            if (!listed) {
                end(1, "the companion spawned but is not in the player list");
                return;
            }
            spawnedAt = server.getTickCount();
        } catch (Throwable t) {
            t.printStackTrace();
            end(1, "spawning threw " + t);
        }
    }

    private static void onTick(ServerTickEvent.Post event) {
        if (spawnedAt < 0 || companion == null) return;
        MinecraftServer server = event.getServer();
        if (server.getTickCount() - spawnedAt < 200) return;
        spawnedAt = -1;
        try {
            ServerPlayer body = companion.serverPlayer();
            boolean listed = body != null && server.getPlayerList().getPlayers().contains(body);
            boolean alive = body != null && body.isAlive();
            System.out.println("[SMOKE] 200 ticks on: in the player list " + listed + ", alive " + alive);
            if (!listed || !alive) {
                end(1, "the companion did not stay");
                return;
            }
            MineAgentEngine.despawnCompanion(companion.companionId(), true);
            boolean gone = !server.getPlayerList().getPlayers().contains(body);
            System.out.println("[SMOKE] despawned: gone from the player list " + gone);
            end(gone ? 0 : 1, gone ? "spawn, stay and despawn all held" : "the companion stayed in the player list after despawning");
        } catch (Throwable t) {
            t.printStackTrace();
            end(1, "the check threw " + t);
        }
    }

    private static void end(int code, String why) {
        System.out.println("[SMOKE] " + (code == 0 ? "PASSED: " : "FAILED: ") + why);
        System.out.flush();
        Runtime.getRuntime().halt(code);
    }
}
