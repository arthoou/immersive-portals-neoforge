package qouteall.imm_ptl.peripheral.wand;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.portal.animation.UnilateralPortalState;
import qouteall.q_misc_util.my_util.Circle;
import qouteall.q_misc_util.my_util.DQuaternion;
import qouteall.q_misc_util.my_util.Plane;
import qouteall.q_misc_util.my_util.WithDim;

public class ProtoPortalSide {
   @NotNull
   public ResourceKey<Level> dimension;
   @NotNull
   public Vec3 leftBottom;
   @Nullable
   public Vec3 rightBottom;
   @Nullable
   public Vec3 leftTop;

   public ProtoPortalSide(@NotNull ResourceKey<Level> dimension, @NotNull Vec3 leftBottom) {
      this.leftBottom = leftBottom;
      this.dimension = dimension;
   }

   public ProtoPortalSide copy() {
      ProtoPortalSide newPortal = new ProtoPortalSide(this.dimension, this.leftBottom);
      newPortal.rightBottom = this.rightBottom;
      newPortal.leftTop = this.leftTop;
      return newPortal;
   }

   public Vec3 getHorizontalAxis() {
      assert this.rightBottom != null;
      return this.rightBottom.subtract(this.leftBottom);
   }

   public Vec3 getVerticalAxis() {
      assert this.leftTop != null;
      return this.leftTop.subtract(this.leftBottom);
   }

   public Vec3 getNormal() {
      return this.getHorizontalAxis().cross(this.getVerticalAxis()).normalize();
   }

   public double getWidth() {
      return this.getHorizontalAxis().length();
   }

   public double getHeight() {
      return this.getVerticalAxis().length();
   }

   public double getHeightDivWidth() {
      double width = this.getWidth();
      double height = this.getHeight();
      return width == 0.0 ? 0.0 : height / width;
   }

   @Nullable
   public ProtoPortalSide undo() {
      if (this.leftTop != null) {
         this.leftTop = null;
         return this;
      } else if (this.rightBottom != null) {
         this.rightBottom = null;
         return this;
      } else {
         return null;
      }
   }

   public boolean isComplete() {
      return this.leftTop != null;
   }

   @Nullable
   public WithDim<Plane> getCursorConstraintPlane() {
      if (this.isComplete()) {
         return null;
      } else {
         return this.rightBottom == null ? null : new WithDim<>(this.dimension, new Plane(this.leftBottom, this.getHorizontalAxis()));
      }
   }

   @Nullable
   public WithDim<Circle> getCursorConstraintCircle(double heightDivWidth) {
      if (this.isComplete()) {
         return null;
      }

      if (this.rightBottom == null) {
         return null;
      }

      WithDim<Plane> plane = this.getCursorConstraintPlane();
      Validate.notNull(plane);
      Vec3 horizontalAxis = this.getHorizontalAxis();
      double width = horizontalAxis.length();
      double constraintHeight = width * heightDivWidth;
      return new WithDim<>(this.dimension, new Circle(plane.value(), this.leftBottom, constraintHeight));
   }

   public boolean isValidPlacement(@Nullable Double heightDivWidth) {
      if (this.rightBottom != null) {
         double width = this.getHorizontalAxis().length();
         if (width < 0.001 || width > 64.001) {
            return false;
         }

         if (this.leftTop != null) {
            double height = this.getVerticalAxis().length();
            if (height < 0.001 || height > 64.001) {
               return false;
            }
         } else if (heightDivWidth != null) {
            double height = width * heightDivWidth;
            if (height < 0.001 || height > 64.001) {
               return false;
            }
         }
      }

      return true;
   }

   public void placeCursor(Vec3 pos) {
      if (this.rightBottom == null) {
         this.rightBottom = pos;
      } else {
         if (this.leftTop != null) {
            throw new IllegalStateException();
         }

         this.leftTop = pos;
      }
   }

   public UnilateralPortalState toUnilateralPortalState() {
      Validate.isTrue(this.isComplete());
      Vec3 horizontalAxis = this.getHorizontalAxis();
      Vec3 verticalAxis = this.getVerticalAxis();
      return new UnilateralPortalState(
         this.dimension,
         this.leftBottom.add(horizontalAxis.scale(0.5)).add(verticalAxis.scale(0.5)),
         DQuaternion.fromFacingVecs(horizontalAxis.normalize(), verticalAxis.normalize()),
         horizontalAxis.length(),
         verticalAxis.length()
      );
   }
}
