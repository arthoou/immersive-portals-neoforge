package qouteall.imm_ptl.peripheral.wand;

import com.mojang.logging.LogUtils;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.animation.UnilateralPortalState;
import qouteall.q_misc_util.my_util.Circle;
import qouteall.q_misc_util.my_util.DQuaternion;
import qouteall.q_misc_util.my_util.Plane;
import qouteall.q_misc_util.my_util.Sphere;

public enum PortalCorner {
   LEFT_BOTTOM,
   LEFT_TOP,
   RIGHT_BOTTOM,
   RIGHT_TOP;

   private static final Logger LOGGER = LogUtils.getLogger();

   public int getXSign() {
      return switch (this) {
         case LEFT_BOTTOM, LEFT_TOP -> -1;
         case RIGHT_BOTTOM, RIGHT_TOP -> 1;
      };
   }

   public int getYSign() {
      return switch (this) {
         case LEFT_BOTTOM, RIGHT_BOTTOM -> -1;
         case LEFT_TOP, RIGHT_TOP -> 1;
      };
   }

   public Vec3 getOffset(Portal portal) {
      return portal.getAxisW().scale(portal.getWidth() / 2.0 * this.getXSign()).add(portal.getAxisH().scale(portal.getHeight() / 2.0 * this.getYSign()));
   }

   public Vec3 getPos(Portal portal) {
      return portal.getOriginPos().add(this.getOffset(portal));
   }

   public Vec3 getOffset(UnilateralPortalState ups) {
      return ups.getAxisW().scale(ups.width() / 2.0 * this.getXSign()).add(ups.getAxisH().scale(ups.height() / 2.0 * this.getYSign()));
   }

   public Vec3 getPos(UnilateralPortalState ups) {
      return ups.position().add(this.getOffset(ups));
   }

   public static UnilateralPortalState performDragWithNoLockedCorner(UnilateralPortalState originalState, PortalCorner freeCorner, Vec3 freePos) {
      Vec3 offset = freeCorner.getOffset(originalState);
      Vec3 newPos = freePos.subtract(offset);
      return new UnilateralPortalState.Builder().from(originalState).position(newPos).build();
   }

   @Nullable
   public static UnilateralPortalState performDragWith1LockedCorner(
      UnilateralPortalState originalState, PortalCorner lockedCorner, Vec3 lockedPos, PortalCorner freeCorner, Vec3 freePos
   ) {
      Vec3 originalFreeCornerPos = freeCorner.getPos(originalState);
      Vec3 originalOffset = originalFreeCornerPos.subtract(lockedPos);
      Vec3 newOffset = freePos.subtract(lockedPos);
      if (!(originalOffset.lengthSqr() < 0.001) && !(newOffset.lengthSqr() < 0.001)) {
         double dot = originalOffset.normalize().dot(newOffset.normalize());
         DQuaternion rotation;
         if (Math.abs(dot) > 0.99999) {
            rotation = null;
         } else {
            rotation = DQuaternion.getRotationBetween(originalOffset, newOffset);
         }

         double scaling = newOffset.length() / originalOffset.length();
         Vec3 offset = originalState.position().subtract(lockedPos).scale(scaling);
         if (rotation != null) {
            offset = rotation.rotate(offset);
         }

         Vec3 newOrigin = lockedPos.add(offset);
         DQuaternion newOrientation = rotation == null ? originalState.orientation() : rotation.hamiltonProduct(originalState.orientation());
         double newWidth = originalState.width() * scaling;
         double newHeight = originalState.height() * scaling;
         return new UnilateralPortalState.Builder()
            .from(originalState)
            .position(newOrigin)
            .orientation(newOrientation)
            .width(newWidth)
            .height(newHeight)
            .build();
      } else {
         return null;
      }
   }

   public static PortalCorner.DraggingConstraint getDraggingConstraintWith2LockedCorners(
      PortalCorner lockedCorner1, Vec3 lockedPos1, PortalCorner lockedCorner2, Vec3 lockedPos2, PortalCorner freeCorner
   ) {
      int corner1XSign = lockedCorner1.getXSign();
      int corner1YSign = lockedCorner1.getYSign();
      int corner2XSign = lockedCorner2.getXSign();
      int corner2YSign = lockedCorner2.getYSign();
      int freeCornerXSign = freeCorner.getXSign();
      int freeCornerYSign = freeCorner.getYSign();
      if (corner1XSign != corner2XSign && corner1YSign != corner2YSign) {
         Vec3 center = lockedPos1.add(lockedPos2).scale(0.5);
         double radius = lockedPos1.distanceTo(lockedPos2) * 0.5;
         return new PortalCorner.DraggingConstraint(null, new Sphere(center, radius));
      } else if (freeCornerXSign != corner1XSign && freeCornerYSign != corner1YSign) {
         Vec3 planeNormal = lockedPos1.subtract(lockedPos2);
         Plane plane = new Plane(lockedPos2, planeNormal);
         return new PortalCorner.DraggingConstraint(plane, null);
      } else {
         Vec3 planeNormal = lockedPos2.subtract(lockedPos1);
         Plane plane = new Plane(lockedPos1, planeNormal);
         return new PortalCorner.DraggingConstraint(plane, null);
      }
   }

   @Nullable
   public static UnilateralPortalState performDragWith2LockedCorners(
      UnilateralPortalState originalState,
      PortalCorner lockedCorner1,
      Vec3 lockedPos1,
      PortalCorner lockedCorner2,
      Vec3 lockedPos2,
      PortalCorner freeCorner,
      Vec3 freePos
   ) {
      PortalCorner.DraggingConstraint constraint = getDraggingConstraintWith2LockedCorners(lockedCorner1, lockedPos1, lockedCorner2, lockedPos2, freeCorner);
      Vec3 freePosLimited = constraint.constrain(freePos);
      if (freePosLimited == null) {
         return null;
      }

      Vec3[][] vertices = new Vec3[2][2];
      int corner1XSign = lockedCorner1.getXSign();
      int corner1YSign = lockedCorner1.getYSign();
      int corner2XSign = lockedCorner2.getXSign();
      int corner2YSign = lockedCorner2.getYSign();
      int freeCornerXSign = freeCorner.getXSign();
      int freeCornerYSign = freeCorner.getYSign();
      vertices[corner1XSign == -1 ? 0 : 1][corner1YSign == -1 ? 0 : 1] = lockedPos1;
      vertices[corner2XSign == -1 ? 0 : 1][corner2YSign == -1 ? 0 : 1] = lockedPos2;
      vertices[freeCornerXSign == -1 ? 0 : 1][freeCornerYSign == -1 ? 0 : 1] = freePosLimited;

      for (int cx = 0; cx <= 1; cx++) {
         for (int cy = 0; cy <= 1; cy++) {
            if (vertices[cx][cy] == null) {
               Vec3 sideVertex1 = vertices[1 - cx][cy];
               Vec3 sideVertex2 = vertices[cx][1 - cy];
               Vec3 diagonalVertex = vertices[1 - cx][1 - cy];
               Vec3 v1 = sideVertex1.subtract(diagonalVertex);
               Vec3 v2 = sideVertex2.subtract(diagonalVertex);
               vertices[cx][cy] = diagonalVertex.add(v1).add(v2);
            }
         }
      }

      Vec3 horizontalAxis = vertices[1][0].subtract(vertices[0][0]);
      Vec3 verticalAxis = vertices[0][1].subtract(vertices[0][0]);
      Vec3 axisW = horizontalAxis.normalize();
      Vec3 axisH = verticalAxis.normalize();
      Vec3 normal = axisW.cross(axisH);
      if (Math.abs(axisW.dot(axisH)) > 0.01) {
         LOGGER.error("The dragged portal vertices are ill-formed");
         return null;
      } else {
         DQuaternion orientation = DQuaternion.matrixToQuaternion(axisW, axisH, normal);
         return new UnilateralPortalState.Builder()
            .dimension(originalState.dimension())
            .position(vertices[0][0].add(vertices[1][1]).scale(0.5))
            .orientation(orientation)
            .width(horizontalAxis.length())
            .height(verticalAxis.length())
            .build();
      }
   }

   public record DraggingConstraint(@Nullable Plane plane, @Nullable Sphere sphere) {
      @Nullable
      public Vec3 constrain(Vec3 pos) {
         if (this.sphere != null) {
            if (this.plane != null) {
               Circle circle = this.sphere.getIntersectionWithPlane(this.plane);
               return circle == null ? null : circle.projectToCircle(pos);
            } else {
               return this.sphere.projectToSphere(pos);
            }
         } else {
            return this.plane != null ? this.plane.getProjection(pos) : pos;
         }
      }
   }
}
