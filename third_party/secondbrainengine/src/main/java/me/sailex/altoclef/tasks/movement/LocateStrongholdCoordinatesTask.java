package me.sailex.altoclef.tasks.movement;

import me.sailex.altoclef.AltoClefController;
import me.sailex.altoclef.Debug;
import me.sailex.altoclef.TaskCatalogue;
import me.sailex.altoclef.multiversion.blockpos.BlockPosVer;
import me.sailex.altoclef.multiversion.EntityVer;
import me.sailex.altoclef.tasksystem.Task;
import me.sailex.altoclef.util.Dimension;
import me.sailex.altoclef.util.helpers.LookHelper;
import me.sailex.altoclef.util.helpers.WorldHelper;
import me.sailex.altoclef.util.time.TimerGame;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEvent.Context;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Optional;

public class LocateStrongholdCoordinatesTask extends Task {
   private static final int EYE_RETHROW_DISTANCE = 10;
   private static final int SECOND_EYE_THROW_DISTANCE = 30;
   private final int targetEyes;
   private final int minimumEyes;
   private final TimerGame throwTimer = new TimerGame(5.0);
   private EyeDirection cachedEyeDirection = null;
   private EyeDirection cachedEyeDirection2 = null;
   private Entity currentThrownEye = null;
   private Vec3i strongholdEstimatePos = null;

   public LocateStrongholdCoordinatesTask(int targetEyes, int minimumEyes) {
      this.targetEyes = targetEyes;
      this.minimumEyes = minimumEyes;
   }

   public LocateStrongholdCoordinatesTask(int targetEyes) {
      this(targetEyes, 12);
   }

   static Vec3i calculateIntersection(Vec3 start1, Vec3 direction1, Vec3 start2, Vec3 direction2) {
      double t2 = (direction1.z * start2.x - direction1.z * start1.x - direction1.x * start2.z + direction1.x * start1.z)
         / (direction1.x * direction2.z - direction1.z * direction2.x);
      BlockPos blockPos = BlockPosVer.ofFloored(start2.add(direction2.scale(t2)));
      return new Vec3i(blockPos.getX(), 0, blockPos.getZ());
   }

   @Override
   protected void onStart() {
   }

   public boolean isSearching() {
      return this.cachedEyeDirection != null;
   }

   @Override
   protected Task onTick() {
      AltoClefController mod = this.controller;
      if (WorldHelper.getCurrentDimension(this.controller) != Dimension.OVERWORLD) {
         this.setDebugState("Going to overworld");
         return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
      } else if (mod.getItemStorage().getItemCount(Items.ENDER_EYE) < this.minimumEyes
         && mod.getEntityTracker().itemDropped(Items.ENDER_EYE)
         && !mod.getEntityTracker().entityFound(EyeOfEnder.class)) {
         this.setDebugState("Picking up dropped ender eye.");
         return new PickupDroppedItemTask(Items.ENDER_EYE, this.targetEyes);
      } else if (mod.getEntityTracker().entityFound(EyeOfEnder.class)) {
         if (this.currentThrownEye == null || !this.currentThrownEye.isAlive()) {
            Debug.logMessage("New eye direction");
            Debug.logMessage(this.currentThrownEye == null ? "null" : "is not alive");
            List<EyeOfEnder> enderEyes = mod.getEntityTracker().getTrackedEntities(EyeOfEnder.class);
            if (!enderEyes.isEmpty()) {
               for (EyeOfEnder enderEye : enderEyes) {
                  this.currentThrownEye = enderEye;
               }
            }

            if (this.cachedEyeDirection2 != null) {
               this.cachedEyeDirection = null;
               this.cachedEyeDirection2 = null;
            } else if (this.cachedEyeDirection == null) {
               this.cachedEyeDirection = new EyeDirection(EntityVer.getPos(this.currentThrownEye));
            } else {
               this.cachedEyeDirection2 = new EyeDirection(EntityVer.getPos(this.currentThrownEye));
            }
         }

         if (this.cachedEyeDirection2 != null) {
            this.cachedEyeDirection2.updateEyePos(EntityVer.getPos(this.currentThrownEye));
         } else if (this.cachedEyeDirection != null) {
            this.cachedEyeDirection.updateEyePos(EntityVer.getPos(this.currentThrownEye));
         }

         if (mod.getEntityTracker().getClosestEntity(EyeOfEnder.class).isPresent() && !mod.getBaritone().getPathingBehavior().isPathing()) {
            LookHelper.lookAt(mod, mod.getEntityTracker().getClosestEntity(EyeOfEnder.class).get().getEyePosition());
         }

         this.setDebugState("Waiting for eye to travel.");
         return null;
      } else {
         if (this.cachedEyeDirection2 != null && !mod.getEntityTracker().entityFound(EyeOfEnder.class) && this.strongholdEstimatePos == null) {
            if (this.cachedEyeDirection2.getAngle() >= this.cachedEyeDirection.getAngle()) {
               Debug.logMessage("2nd eye thrown at wrong position, or points to different stronghold. Rethrowing");
               this.cachedEyeDirection = this.cachedEyeDirection2;
               this.cachedEyeDirection2 = null;
            } else {
               Vec3 throwOrigin = this.cachedEyeDirection.getOrigin();
               Vec3 throwOrigin2 = this.cachedEyeDirection2.getOrigin();
               Vec3 throwDelta = this.cachedEyeDirection.getDelta();
               Vec3 throwDelta2 = this.cachedEyeDirection2.getDelta();
               this.strongholdEstimatePos = calculateIntersection(throwOrigin, throwDelta, throwOrigin2, throwDelta2);
               Debug.logMessage(
                 "Stronghold is at "
                       + this.strongholdEstimatePos.getX()
                       + ", "
                       + this.strongholdEstimatePos.getZ()
                       + " ("
                       + (int) EntityVer.getPos(mod.getPlayer()).distanceTo(Vec3.atLowerCornerOf(this.strongholdEstimatePos))
                       + " blocks away)");
            }
         }

         if (this.strongholdEstimatePos != null
               && EntityVer.getPos(mod.getPlayer()).distanceTo(Vec3.atLowerCornerOf(this.strongholdEstimatePos)) < 10.0
               && WorldHelper.getCurrentDimension(this.controller) == Dimension.OVERWORLD) {
            this.strongholdEstimatePos = null;
            this.cachedEyeDirection = null;
            this.cachedEyeDirection2 = null;
         }

         if (!mod.getEntityTracker().entityFound(EyeOfEnder.class) && this.strongholdEstimatePos == null) {
            if (WorldHelper.getCurrentDimension(this.controller) == Dimension.NETHER) {
               this.setDebugState("Going to overworld.");
               return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
            } else if (!mod.getItemStorage().hasItem(Items.ENDER_EYE)) {
               this.setDebugState("Collecting eye of ender.");
               return TaskCatalogue.getItemTask(Items.ENDER_EYE, 1);
            } else {
               if (this.cachedEyeDirection == null) {
                  this.setDebugState("Throwing first eye.");
               } else {
                  this.setDebugState("Throwing second eye.");
                  double sqDist = mod.getPlayer().distanceToSqr(this.cachedEyeDirection.getOrigin());
                  if (sqDist < 900.0 && this.cachedEyeDirection != null) {
                     return new GoInDirectionXZTask(this.cachedEyeDirection.getOrigin(), this.cachedEyeDirection.getDelta().yRot((float) (Math.PI / 2)), 1.0);
                  }
               }

               if (mod.getSlotHandler().forceEquipItem(Items.ENDER_EYE)) {
                  this.throwEye(this.controller.getWorld(), this.controller.getEntity());
               } else {
                  Debug.logWarning("Failed to equip eye of ender to throw.");
               }

               return null;
            }
         } else if ((this.cachedEyeDirection == null || this.cachedEyeDirection.hasDelta())
            && (this.cachedEyeDirection2 == null || this.cachedEyeDirection2.hasDelta())) {
            return null;
         } else {
            this.setDebugState("Waiting for thrown eye to appear...");
            return null;
         }
      }
   }

   private void throwEye(ServerLevel world, LivingEntity user) {
      BlockPos blockPos = world.findNearestMapStructure(StructureTags.EYE_OF_ENDER_LOCATED, user.blockPosition(), 100, false);
      if (blockPos != null) {
         EyeOfEnder eyeOfEnderEntity = new EyeOfEnder(world, user.getX(), user.getY(0.5), user.getZ());
         eyeOfEnderEntity.setItem(user.getMainHandItem());
         eyeOfEnderEntity.signalTo(  blockPos );
         world.gameEvent(GameEvent.PROJECTILE_SHOOT, EntityVer.getPos(eyeOfEnderEntity), Context.of(user));
         world.addFreshEntity(eyeOfEnderEntity);
         world.playSound(
            (Player)null,
            user.getX(),
            user.getY(),
            user.getZ(),
            SoundEvents.ENDER_EYE_LAUNCH,
            SoundSource.NEUTRAL,
            0.5F,
            0.4F / (world.getRandom().nextFloat() * 0.4F + 0.8F)
         );
         world.levelEvent((Player)null, 1003, user.blockPosition(), 0);
         user.getMainHandItem().shrink(1);
         user.swing(InteractionHand.MAIN_HAND, true);
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
   }

   public Optional<BlockPos> getStrongholdCoordinates() {
      return this.strongholdEstimatePos == null ? Optional.empty() : Optional.of(new BlockPos(this.strongholdEstimatePos));
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof LocateStrongholdCoordinatesTask;
   }

   @Override
   protected String toDebugString() {
      return "Locating stronghold coordinates";
   }

   @Override
   public boolean isFinished() {
      return this.strongholdEstimatePos != null;
   }

   private static class EyeDirection {
      private final Vec3 start;
      private Vec3 end;

      public EyeDirection(Vec3 startPos) {
         this.start = startPos;
      }

      public void updateEyePos(Vec3 endPos) {
         this.end = endPos;
      }

      public Vec3 getOrigin() {
         return this.start;
      }

      public Vec3 getDelta() {
         return this.end == null ? Vec3.ZERO : this.end.subtract(this.start);
      }

      public double getAngle() {
         return this.end == null ? 0.0 : Math.atan2(this.getDelta().x(), this.getDelta().z());
      }

      public boolean hasDelta() {
         return this.end != null && this.getDelta().lengthSqr() > 1.0E-5;
      }
   }
}
