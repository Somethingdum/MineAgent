package com.mineagent.gametest.mixin;

import com.mineagent.gametest.OffThreadChunks;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * SMH plan 71, invariant I1: the suite fails if the off-thread chunk record is not empty. A scene about to succeed
 * while it holds anything fails instead, with the calls (which are then taken out of the record, so one fault fails
 * one scene, the next to finish). Scenes run several at a time, so the scene that fails is not always the one whose
 * companion made the call: the thread and the place in our code it came from are in the message. Game tests only.
 */
@Mixin(value = GameTestInfo.class, remap = false)
public abstract class SceneResultMixin {
    @Shadow public abstract void fail(Throwable error);

    @Inject(method = "succeed", at = @At("HEAD"), cancellable = true, remap = false)
    private void mineagent$failOnOffThreadChunks(CallbackInfo ci) {
        List<OffThreadChunks.Call> calls = OffThreadChunks.takeAll();
        if (calls.isEmpty()) return;
        String why = "I1: " + calls.size() + " chunk call(s) off the server thread from our code; the first: " + calls.get(0);
        System.out.println("[GAMETEST FAILED] " + why);
        fail(new GameTestAssertException(why));
        ci.cancel();
    }
}
