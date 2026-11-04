package adris.altoclef.mixins;

import adris.altoclef.eventbus.EventBus;
import adris.altoclef.eventbus.events.ClientRenderEvent;
import adris.altoclef.multiversion.DrawContextWrapper;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public final class ClientUIMixin {
    @Inject(
            method = "render",
            at = @At("TAIL")
    )
    //#if MC >= 12100
    private void clientRender(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        EventBus.publish(new ClientRenderEvent(DrawContextWrapper.of(context), tickCounter.getGameTimeDeltaPartialTick(true)));
    }
    //#else
    //#if MC >= 12001
    //$$ private void clientRender(DrawContext obj, float tickDelta, CallbackInfo ci) {
    //#else
    //$$ private void clientRender(MatrixStack obj, float tickDelta, CallbackInfo ci) {
    //#endif
    //$$    EventBus.publish(new ClientRenderEvent(DrawContextWrapper.of(obj), tickDelta));
    //$$ }
    //#endif


}
