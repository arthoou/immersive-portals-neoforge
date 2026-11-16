package qouteall.imm_ptl.core.portal.animation;

import java.util.Objects;
import java.util.stream.Stream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.portal.PortalManipulation;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.DQuaternion;
import qouteall.q_misc_util.my_util.Vec2d;

public record DeltaUnilateralPortalState(@Nullable Vec3 offset, @Nullable DQuaternion rotation, @Nullable Vec3 sizeScaling) {
   public static final DeltaUnilateralPortalState identity = new DeltaUnilateralPortalState(null, null, (Vec3)null);

   public DeltaUnilateralPortalState getInverse() {
      return new DeltaUnilateralPortalState(
         this.offset == null ? null : this.offset.scale(-1.0),
         this.rotation == null ? null : this.rotation.getConjugated(),
         this.sizeScaling == null
            ? null
            : new Vec3(1.0 / this.sizeScaling.x(), 1.0 / this.sizeScaling.y(), this.sizeScaling.z() == 0.0 ? 1.0 : 1.0 / this.sizeScaling.z())
      );
   }

   public DeltaUnilateralPortalState combine(DeltaUnilateralPortalState then) {
      return new DeltaUnilateralPortalState(
         Helper.combineNullable(this.offset, then.offset, Vec3::add),
         Helper.combineNullable(this.rotation, then.rotation, DQuaternion::hamiltonProduct),
         Helper.combineNullable(this.sizeScaling, then.sizeScaling, (a, b) -> new Vec3(a.x() * b.x(), a.y() * b.y(), a.z() * b.z()))
      );
   }

   public static DeltaUnilateralPortalState fromTag(CompoundTag tag) {
      return new DeltaUnilateralPortalState(
         Helper.getVec3dOptional(tag, "offset"),
         tag.contains("rotation") ? DQuaternion.fromTag(tag.getCompoundOrEmpty("rotation")) : null,
         tag.contains("sizeScalingX")
            ? new Vec3(
               tag.getDoubleOr("sizeScalingX", 0.0),
               tag.getDoubleOr("sizeScalingY", 0.0),
               tag.contains("sizeScalingZ") ? tag.getDoubleOr("sizeScalingZ", 0.0) : 1.0
            )
            : null
      );
   }

   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      if (this.offset != null) {
         Helper.putVec3d(tag, "offset", this.offset);
      }

      if (this.rotation != null) {
         tag.put("rotation", this.rotation.toTag());
      }

      if (this.sizeScaling != null) {
         tag.putDouble("sizeScalingX", this.sizeScaling.x());
         tag.putDouble("sizeScalingY", this.sizeScaling.y());
         tag.putDouble("sizeScalingZ", this.sizeScaling.z());
      }

      return tag;
   }

   public DeltaUnilateralPortalState getPartial(double progress) {
      return new DeltaUnilateralPortalState(
         this.offset == null ? null : this.offset.scale(progress),
         this.rotation == null ? null : DQuaternion.interpolate(DQuaternion.identity, this.rotation, progress),
         this.sizeScaling == null
            ? null
            : new Vec3(
               Mth.lerp(progress, 1.0, this.sizeScaling.x()), Mth.lerp(progress, 1.0, this.sizeScaling.y()), Mth.lerp(progress, 1.0, this.sizeScaling.z())
            )
      );
   }

   public static DeltaUnilateralPortalState interpolate(DeltaUnilateralPortalState a, DeltaUnilateralPortalState b, double progress) {
      return a.getPartial(1.0 - progress).combine(b.getPartial(progress));
   }

   public DeltaUnilateralPortalState getFlipped() {
      return new DeltaUnilateralPortalState(
         this.offset, this.rotation == null ? null : this.rotation.hamiltonProduct(PortalManipulation.flipAxisW), this.sizeScaling
      );
   }

   public void apply(UnilateralPortalState.Builder builder) {
      if (this.offset != null) {
         builder.offset(this.offset);
      }

      if (this.rotation != null) {
         builder.rotate(this.rotation);
      }

      if (this.sizeScaling != null) {
         builder.scaleWidth(this.sizeScaling.x());
         builder.scaleHeight(this.sizeScaling.y());
      }
   }

   public DeltaUnilateralPortalState purgeFPError() {
      Vec3 offset = this.offset;
      if (offset != null && offset.lengthSqr() < 1.0E-4) {
         offset = null;
      }

      DQuaternion rotation = this.rotation;
      if (rotation != null && DQuaternion.isClose(rotation, DQuaternion.identity)) {
         rotation = null;
      }

      Vec3 sizeScaling = this.sizeScaling;
      if (sizeScaling != null
         && Math.abs(sizeScaling.x() - 1.0) < 1.0E-5
         && Math.abs(sizeScaling.y() - 1.0) < 1.0E-5
         && Math.abs(sizeScaling.z() - 1.0) < 1.0E-5) {
         sizeScaling = null;
      }

      return new DeltaUnilateralPortalState(offset, rotation, sizeScaling);
   }

   public boolean isIdentity() {
      return this.offset == null && this.rotation == null && this.sizeScaling == null;
   }

   @Override
   public String toString() {
      String str = Stream.of(
            this.offset == null ? null : "offset=(%.3f,%.3f,%.3f)".formatted(this.offset.x(), this.offset.y(), this.offset.z()),
            this.rotation == null ? null : "rotation=(%.3f,%.3f,%.3f,%.3f)".formatted(this.rotation.x, this.rotation.y, this.rotation.z, this.rotation.w),
            this.sizeScaling == null ? null : "sizeScaling=(%.3f,%.3f)".formatted(this.sizeScaling.x(), this.sizeScaling.y())
         )
         .filter(Objects::nonNull)
         .reduce((a, b) -> a + ", " + b)
         .orElse("");
      return "Delta(" + str + ")";
   }

   public static DeltaUnilateralPortalState fromDiff(UnilateralPortalState before, UnilateralPortalState after) {
      Validate.isTrue(before.dimension() == after.dimension(), "dimension mismatch");
      return new DeltaUnilateralPortalState(
            after.position().subtract(before.position()),
            after.orientation().hamiltonProduct(before.orientation().getConjugated()),
            new Vec3(
               before.width() == 0.0 ? 1.0 : after.width() / before.width(),
               before.height() == 0.0 ? 1.0 : after.height() / before.height(),
               before.width() == 0.0 ? 1.0 : after.width() / before.width()
            )
         )
         .purgeFPError();
   }

   public static class Builder {
      @Nullable
      private Vec3 offset;
      @Nullable
      private DQuaternion rotation;
      @Nullable
      private Vec3 sizeScaling;

      public DeltaUnilateralPortalState.Builder offset(Vec3 offset) {
         this.offset = offset;
         return this;
      }

      public DeltaUnilateralPortalState.Builder rotate(DQuaternion rotation) {
         this.rotation = rotation;
         return this;
      }

      public DeltaUnilateralPortalState.Builder scaleSize(double scale) {
         this.sizeScaling = new Vec3(scale, scale, scale);
         return this;
      }

      public DeltaUnilateralPortalState.Builder scaleSize(Vec2d scale) {
         this.sizeScaling = new Vec3(scale.x(), scale.y(), 1.0);
         return this;
      }

      public DeltaUnilateralPortalState.Builder scaleSize(Vec3 scale) {
         this.sizeScaling = scale;
         return this;
      }

      public DeltaUnilateralPortalState build() {
         return new DeltaUnilateralPortalState(this.offset, this.rotation, this.sizeScaling);
      }
   }
}
