package adris.altoclef.mixins;

import adris.altoclef.eventbus.EventBus;
import adris.altoclef.eventbus.events.BlockBrokenEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public class BlockModifiedByPlayerMixin {

    @Inject(
            method = "onBreak",
            at = @At("HEAD")
    )
    //#if MC>12002
    public void onBlockBroken(Level world, BlockPos pos, BlockState state, Player player, CallbackInfoReturnable<BlockState> cir) {
    //#else
    //$$ public void onBlockBroken(World world, BlockPos pos, BlockState state, PlayerEntity player, CallbackInfo ci) {
    //#endif
        if (player.level() == world) {
            BlockBrokenEvent evt = new BlockBrokenEvent();
            evt.blockPos = pos;
            evt.blockState = state;
            evt.player = player;
            EventBus.publish(evt);
        }
    }

}
