package adris.altoclef.mixins;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer extends AbstractClientPlayer {

    public MixinLocalPlayer(ClientLevel world, GameProfile profile) {
        super(world, profile);
    }

    @Inject(method = "getPitch", at = @At("RETURN"), cancellable = true)
    public void getPitch(float tickDelta, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(super.getViewXRot(tickDelta));
    }

    @Inject(method = "getYaw", at = @At("RETURN"), cancellable = true)
    public void getYaw(float tickDelta, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(super.getViewYRot(tickDelta));
    }
}