/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.sailex.mixins;

import me.sailex.automatone.Automatone;
import net.minecraft.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@Mixin(Util.class)
public abstract class MixinUtil {

    @Unique
    private static void attemptShutdown(ExecutorService service) {
        service.shutdown();

        boolean flag;
        try {
            flag = service.awaitTermination(3L, TimeUnit.SECONDS);
        } catch (InterruptedException var3) {
            flag = false;
        }

        if (!flag) {
            service.shutdownNow();
        }
    }

    @Inject(method = "shutdownExecutors", at = @At("RETURN"))
    private static void shutdownBaritoneExecutor(CallbackInfo ci) {
        attemptShutdown(Automatone.getExecutor());
    }
}
