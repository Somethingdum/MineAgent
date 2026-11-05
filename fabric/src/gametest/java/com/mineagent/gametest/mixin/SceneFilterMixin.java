package com.mineagent.gametest.mixin;

import net.minecraft.gametest.framework.TestFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * One scene, or a few, run alone (SMH plan 71, step 0.5): {@code ./gradlew :fabric:runGametest -Pscenes=<pattern>}
 * keeps only the scenes whose name (as the report prints it, "companiongametests.<method>", lower case) the pattern
 * finds, case ignored. Fabric API's game-test module at this version (fabric-gametest-api-v1 2.0.4) reads no filter of
 * its own (only fabric-api.gametest, .report-file and .command), so this narrows the list it collects in
 * FabricGameTestHelper.getTestFunctions, the one place every scene passes through. No pattern: every scene, as before.
 */
@Mixin(targets = "net.fabricmc.fabric.impl.gametest.FabricGameTestHelper", remap = false)
public abstract class SceneFilterMixin {
    @Inject(method = "getTestFunctions", at = @At("RETURN"), cancellable = true, remap = false)
    private static void mineagent$onlyTheseScenes(CallbackInfoReturnable<Collection<TestFunction>> cir) {
        String pattern = System.getProperty("mineagent.scenes", "").trim();
        if (pattern.isEmpty()) return;
        Pattern p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
        List<TestFunction> kept = cir.getReturnValue().stream().filter(f -> p.matcher(f.testName()).find()).toList();
        System.out.println("[MineAgent] scenes matching \"" + pattern + "\": " + kept.size() + " of " + cir.getReturnValue().size());
        cir.setReturnValue(kept);
    }
}
