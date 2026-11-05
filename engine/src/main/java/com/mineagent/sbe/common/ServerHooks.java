// SMH plan 70, one agent: what the ported SecondBrainEngine (com.mineagent.sbe) took from Fabric's and Carpet's APIs, given
// it without either, so it runs the same under both loaders. Written for this mod (.github/port-sbe/port.py routes the
// calls here): the game's own directories, the loader's chunk events (each loader passes them in), the chunks already
// loaded round a player, and the companion's own input in place of Carpet's "/player" commands.
package com.mineagent.sbe.common;

import com.mineagent.sbe.automatone.api.IBaritone;
import com.mineagent.sbe.automatone.api.utils.ICommandHelper;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;

public final class ServerHooks {

    private ServerHooks() {}

    /** A loaded or unloaded chunk, in a level. */
    public interface ChunkListener {
        void on(ServerLevel level, ChunkPos pos);
    }

    private static volatile MinecraftServer server;
    private static final List<ChunkListener> LOADS = new CopyOnWriteArrayList<>();
    private static final List<ChunkListener> UNLOADS = new CopyOnWriteArrayList<>();
    private static volatile BiFunction<IBaritone, ServerPlayer, ICommandHelper> commandHelpers;

    /** The server it runs in (the engine passes it at start). */
    public static void serverStarted(MinecraftServer s) {
        server = s;
    }

    public static void serverStopped() {
        server = null;
        LOADS.clear();
        UNLOADS.clear();
    }

    /** The game's directory (Fabric's getGameDir, NeoForge's FMLPaths.GAMEDIR: both the server's own directory). */
    public static Path gameDir() {
        MinecraftServer s = server;
        return s != null ? s.getServerDirectory() : Path.of(".");
    }

    /** The config directory under it (where both loaders keep it). */
    public static Path configDir() {
        return gameDir().resolve("config");
    }

    /** A chunk loaded: a listener told of each, until the returned action is run. */
    public static Runnable onChunkLoad(ChunkListener listener) {
        LOADS.add(listener);
        return () -> LOADS.remove(listener);
    }

    public static Runnable onChunkUnload(ChunkListener listener) {
        UNLOADS.add(listener);
        return () -> UNLOADS.remove(listener);
    }

    /** From each loader's chunk-load event. */
    public static void chunkLoaded(ServerLevel level, ChunkPos pos) {
        for (ChunkListener l : LOADS) l.on(level, pos);
    }

    /** From each loader's chunk-unload event. */
    public static void chunkUnloaded(ServerLevel level, ChunkPos pos) {
        for (ChunkListener l : UNLOADS) l.on(level, pos);
    }

    /** The chunks loaded round a player now, within the server's view distance, asked without loading any. */
    public static List<ChunkPos> loadedChunksAround(ServerPlayer player) {
        List<ChunkPos> out = new ArrayList<>();
        if (player == null) return out;
        ServerLevel level = player.serverLevel();
        int view = Math.max(2, level.getServer().getPlayerList().getViewDistance());
        ChunkPos at = player.chunkPosition();
        for (int dx = -view; dx <= view; dx++) {
            for (int dz = -view; dz <= view; dz++) {
                if (level.getChunkSource().getChunkNow(at.x + dx, at.z + dz) != null) out.add(new ChunkPos(at.x + dx, at.z + dz));
            }
        }
        return out;
    }

    /** How a companion's body is moved and used (the engine sets it: its own input driver). */
    public static void setCommandHelpers(BiFunction<IBaritone, ServerPlayer, ICommandHelper> factory) {
        commandHelpers = factory;
    }

    public static ICommandHelper commandHelper(IBaritone baritone, ServerPlayer player) {
        BiFunction<IBaritone, ServerPlayer, ICommandHelper> f = commandHelpers;
        if (f == null) throw new IllegalStateException("No input for the companion's body: ServerHooks.setCommandHelpers was not called");
        return f.apply(baritone, player);
    }
}
