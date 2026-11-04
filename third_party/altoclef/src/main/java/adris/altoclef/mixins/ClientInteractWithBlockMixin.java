package adris.altoclef.mixins;

import adris.altoclef.eventbus.EventBus;
import adris.altoclef.eventbus.events.BlockInteractEvent;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public final class ClientInteractWithBlockMixin {
    @Inject(
            method = "interactBlock",
            at = @At("HEAD")
    )

    //#if MC >= 11904
    private void onClientBlockInteract(LocalPlayer player, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> ci) {
    //#else
    //$$ private void onClientBlockInteract(ClientPlayerEntity player, ClientWorld world, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
    //#endif
        //Debug.logMessage("(client) INTERACTED WITH: " + (hitResult != null? hitResult.getBlockPos() : "(nothing)"));
        if (hitResult != null) {
            EventBus.publish(new BlockInteractEvent(hitResult));
        }

    }
}
