package adris.altoclef.mixins;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;

@Mixin(OptionInstance.class)
public class SimpleOptionMixin<T> {


    @Shadow
    T value;

    @Inject(method = "setValue",at = @At("HEAD"), cancellable = true)
    public void inject(T value, CallbackInfo ci) {
        if (Minecraft.getInstance() == null || Minecraft.getInstance().options == null) return;
        if (((Object)this) == Minecraft.getInstance().options.gamma()) {
            this.value = value;
            ci.cancel();
        }
    }

}
