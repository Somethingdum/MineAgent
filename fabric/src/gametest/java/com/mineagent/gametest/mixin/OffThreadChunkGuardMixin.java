package com.mineagent.gametest.mixin;

import com.mineagent.gametest.OffThreadChunks;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/**
 * SMH plan 71, invariant I1: every call of the server's chunk map made off the server thread is brought to
 * {@link OffThreadChunks}. These are the two ways in that can load or generate a chunk: {@code getChunk(x, z, status,
 * load)}, where {@code Level.getBlockState} and {@code Level.getChunk} end, and {@code getChunkFuture}. Off the server
 * thread each hands the work to the server thread and waits for it. (Vanilla's {@code getChunkNow} only returns null
 * off the server thread, and Automatone's view of the visible chunk map reads without loading; neither is a call here.)
 * Game tests only, never shipped; they run where the game's names are Mojang's, so nothing is remapped.
 */
@Mixin(value = ServerChunkCache.class, remap = false)
public abstract class OffThreadChunkGuardMixin {
    @Shadow @Final Thread mainThread;

    @Inject(method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;",
            at = @At("HEAD"), remap = false)
    private void mineagent$getChunkOffThread(int x, int z, ChunkStatus status, boolean load, CallbackInfoReturnable<ChunkAccess> cir) {
        if (Thread.currentThread() != this.mainThread) OffThreadChunks.offThread(x, z, status, load);
    }

    @Inject(method = "getChunkFuture", at = @At("HEAD"), remap = false)
    private void mineagent$getChunkFutureOffThread(int x, int z, ChunkStatus status, boolean load,
            CallbackInfoReturnable<CompletableFuture<ChunkResult<ChunkAccess>>> cir) {
        if (Thread.currentThread() != this.mainThread) OffThreadChunks.offThread(x, z, status, load);
    }
}
