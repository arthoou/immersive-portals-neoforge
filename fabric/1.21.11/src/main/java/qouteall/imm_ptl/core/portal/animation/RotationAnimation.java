package qouteall.imm_ptl.core.portal.animation;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.McHelper;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.DQuaternion;

public class RotationAnimation implements PortalAnimationDriver {
   public final Vec3 initialOffset;
   public final Vec3 rotationAxis;
   public final double degreesPerTick;
   public final long startGameTime;
   public final long endGameTime;
   @Nullable
   public final TimingFunction timingFunction;

   public static void init() {
      PortalAnimationDriver.registerDeserializer(McHelper.newResourceLocation("imm_ptl:rotation"), RotationAnimation::deserialize);
   }

   public RotationAnimation(
      Vec3 initialOffset, Vec3 rotationAxis, double degreesPerTick, long startGameTime, long endGameTime, @Nullable TimingFunction timingFunction
   ) {
      this.initialOffset = initialOffset;
      this.rotationAxis = rotationAxis;
      this.degreesPerTick = degreesPerTick;
      this.startGameTime = startGameTime;
      this.endGameTime = endGameTime;
      this.timingFunction = timingFunction;
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", "imm_ptl:rotation");
      Helper.putVec3d(tag, "initialOffset", this.initialOffset);
      Helper.putVec3d(tag, "rotationAxis", this.rotationAxis);
      tag.putDouble("degreesPerTick", this.degreesPerTick);
      tag.putLong("startGameTime", this.startGameTime);
      tag.putLong("endGameTime", this.endGameTime);
      if (this.timingFunction != null) {
         tag.putString("timingFunction", this.timingFunction.toString());
      }

      return tag;
   }

   private static RotationAnimation deserialize(CompoundTag tag) {
      Vec3 initialOffset = Helper.getVec3d(tag, "initialOffset");
      Vec3 rotationAxis = Helper.getVec3d(tag, "rotationAxis");
      double degreesPerTick = tag.getDoubleOr("degreesPerTick", 0.0);
      long startGameTime = tag.getLongOr("startGameTime", 0L);
      long endGameTime = tag.getLongOr("endGameTime", 0L);
      TimingFunction timingFunction = tag.contains("timingFunction") ? TimingFunction.fromString(tag.getStringOr("timingFunction", "")) : null;
      return new RotationAnimation(initialOffset, rotationAxis, degreesPerTick, startGameTime, endGameTime, timingFunction);
   }

   @NotNull
   @Override
   public AnimationResult getAnimationResult(long tickTime, float partialTicks, AnimationContext context) {
      double passedTicks = (double)(tickTime - 1L - this.startGameTime) + partialTicks;
      boolean ended = false;
      long durationTicks = this.endGameTime - this.startGameTime;
      if (passedTicks >= durationTicks) {
         ended = true;
         passedTicks = durationTicks;
      }

      if (this.timingFunction != null) {
         passedTicks = this.timingFunction.mapProgress(passedTicks / durationTicks) * durationTicks;
      }

      double angle = this.degreesPerTick * passedTicks;
      DQuaternion rotation = DQuaternion.rotationByDegrees(this.rotationAxis, angle);
      Vec3 vec = this.initialOffset;
      Vec3 rotatedVec = rotation.rotate(vec);
      Vec3 offset = rotatedVec.subtract(vec);
      return new AnimationResult(new DeltaUnilateralPortalState(offset, rotation, null), ended);
   }

   @Nullable
   @Override
   public DeltaUnilateralPortalState getEndingResult(long tickTime, AnimationContext context) {
      return this.endGameTime == Long.MAX_VALUE ? null : this.getAnimationResult(this.endGameTime, 0.0F, context).delta();
   }

   @Override
   public PortalAnimationDriver getFlippedVersion() {
      return new RotationAnimation(this.initialOffset, this.rotationAxis, this.degreesPerTick, this.startGameTime, this.endGameTime, this.timingFunction);
   }

   @Override
   public Component getInfo() {
      return Component.literal(
         "Rotation[offset=(%.3f %.3f %.3f),axis=(%.3f %.3f %.3f),angVelo=%.3f]"
            .formatted(
               this.initialOffset.x,
               this.initialOffset.y,
               this.initialOffset.z,
               this.rotationAxis.x,
               this.rotationAxis.y,
               this.rotationAxis.z,
               this.degreesPerTick
            )
      );
   }

   public static class Builder {
      private Vec3 initialOffset;
      public Vec3 rotationAxis;
      public double degreesPerTick;
      public long startGameTime;
      public long endGameTime;
      public TimingFunction timingFunction;

      public RotationAnimation build() {
         return new RotationAnimation(this.initialOffset, this.rotationAxis, this.degreesPerTick, this.startGameTime, this.endGameTime, this.timingFunction);
      }

      public RotationAnimation.Builder setInitialOffset(Vec3 initialOffset) {
         this.initialOffset = initialOffset;
         return this;
      }

      public RotationAnimation.Builder setRotationAxis(Vec3 rotationAxis) {
         this.rotationAxis = rotationAxis;
         return this;
      }

      public RotationAnimation.Builder setDegreesPerTick(double degreesPerTick) {
         this.degreesPerTick = degreesPerTick;
         return this;
      }

      public RotationAnimation.Builder setStartGameTime(long startGameTime) {
         this.startGameTime = startGameTime;
         return this;
      }

      public RotationAnimation.Builder setEndGameTime(long endGameTime) {
         this.endGameTime = endGameTime;
         return this;
      }

      public RotationAnimation.Builder setTimingFunction(TimingFunction timingFunction) {
         this.timingFunction = timingFunction;
         return this;
      }
   }
}
