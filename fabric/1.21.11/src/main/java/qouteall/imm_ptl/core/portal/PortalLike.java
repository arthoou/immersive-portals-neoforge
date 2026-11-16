package qouteall.imm_ptl.core.portal;

import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.DQuaternion;
import qouteall.q_misc_util.my_util.Plane;

public interface PortalLike {
   boolean isConventionalPortal();

   AABB getThinBoundingBox();

   Vec3 transformPoint(Vec3 var1);

   Vec3 transformLocalVec(Vec3 var1);

   Vec3 transformLocalVecNonScale(Vec3 var1);

   Vec3 inverseTransformLocalVec(Vec3 var1);

   Vec3 inverseTransformPoint(Vec3 var1);

   double getDistanceToNearestPointInPortal(Vec3 var1);

   double getDestAreaRadiusEstimation();

   Vec3 getOriginPos();

   Vec3 getDestPos();

   Level getOriginWorld();

   Level getDestWorld();

   ResourceKey<Level> getDestDim();

   @Environment(EnvType.CLIENT)
   boolean isRoughlyVisibleTo(Vec3 var1);

   @Nullable
   Plane getInnerClipping();

   @Nullable
   DQuaternion getRotation();

   double getScale();

   boolean getIsGlobal();

   boolean isVisible();

   @Nullable
   Vec3[] getOuterFrustumCullingVertices();

   @Nullable
   Matrix4f getAdditionalCameraTransformation();

   @Nullable
   UUID getDiscriminator();

   boolean cannotRenderInMe(Portal var1);

   boolean isFuseView();

   boolean getDoRenderPlayer();

   boolean getHasCrossPortalCollision();

   default boolean hasScaling() {
      return Math.abs(this.getScale() - 1.0) > 0.01;
   }

   default ResourceKey<Level> getOriginDim() {
      return this.getOriginWorld().dimension();
   }

   default boolean isOnDestinationSide(Vec3 entityPos, double valve) {
      Plane innerClipping = this.getInnerClipping();
      if (innerClipping == null) {
         return true;
      }

      double v = entityPos.subtract(innerClipping.pos()).dot(innerClipping.normal());
      return v > valve;
   }

   default double getSizeEstimation() {
      Vec3 boxSize = Helper.getBoxSize(this.getThinBoundingBox());
      return Math.max(Math.max(boxSize.x, boxSize.y), boxSize.z);
   }
}
