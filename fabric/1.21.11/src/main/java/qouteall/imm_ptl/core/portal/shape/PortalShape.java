package qouteall.imm_ptl.core.portal.shape;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.animation.UnilateralPortalState;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.BoxPredicateF;
import qouteall.q_misc_util.my_util.Plane;
import qouteall.q_misc_util.my_util.RayTraceResult;
import qouteall.q_misc_util.my_util.TriangleConsumer;

public interface PortalShape {
   boolean isPlanar();

   AABB getBoundingBox(UnilateralPortalState var1, boolean var2, double var3);

   double roughDistanceToPortalShape(UnilateralPortalState var1, Vec3 var2);

   @Nullable
   RayTraceResult raytracePortalShapeByLocalPos(UnilateralPortalState var1, Vec3 var2, Vec3 var3, double var4);

   @Nullable
   default RayTraceResult raytracePortalShape(UnilateralPortalState portalState, Vec3 from, Vec3 to, double leniency) {
      Vec3 localFrom = portalState.transformGlobalToLocal(from);
      Vec3 localTo = portalState.transformGlobalToLocal(to);
      RayTraceResult hit = this.raytracePortalShapeByLocalPos(portalState, localFrom, localTo, leniency);
      return hit != null
         ? new RayTraceResult(hit.t(), portalState.transformLocalToGlobal(hit.hitPos()), portalState.transformVecLocalToGlobal(hit.surfaceNormal()))
         : null;
   }

   @Nullable
   Plane getOuterClipping(UnilateralPortalState var1);

   @Nullable
   Plane getInnerClipping(UnilateralPortalState var1, UnilateralPortalState var2, Portal var3);

   @Nullable
   default List<Plane> getNearbyPortalPlanes(UnilateralPortalState portalState, AABB box) {
      Plane outerClipping = this.getOuterClipping(portalState);
      return outerClipping != null ? List.of(outerClipping) : null;
   }

   PortalShape getFlipped();

   PortalShape getReverse();

   boolean roughTestVisibility(UnilateralPortalState var1, Vec3 var2, boolean var3);

   @Environment(EnvType.CLIENT)
   void renderViewAreaMesh(Vec3 var1, UnilateralPortalState var2, TriangleConsumer var3, boolean var4);

   boolean canCollideWith(Portal var1, UnilateralPortalState var2, Vec3 var3, AABB var4);

   default boolean isBoxInPortalProjection(UnilateralPortalState portalState, AABB box) {
      Vec3[] vertexes = Helper.eightVerticesOf(box);
      Vec3 originPos = portalState.position();
      Vec3[] transformed = new Vec3[vertexes.length];

      for (int i = 0; i < transformed.length; i++) {
         transformed[i] = portalState.transformGlobalToLocal(vertexes[i]);
      }

      double minX = transformed[0].x();
      double maxX = minX;
      double minY = transformed[0].y();
      double maxY = minY;
      double minZ = transformed[0].z();
      double maxZ = minZ;

      for (int i = 1; i < 8; i++) {
         Vec3 v = transformed[i];
         minX = Math.min(minX, v.x());
         maxX = Math.max(maxX, v.x());
         minY = Math.min(minY, v.y());
         maxY = Math.max(maxY, v.y());
         minZ = Math.min(minZ, v.z());
         maxZ = Math.max(maxZ, v.z());
      }

      return this.isLocalBoxInPortalProjection(portalState, minX, minY, minZ, maxX, maxY, maxZ);
   }

   boolean isLocalBoxInPortalProjection(UnilateralPortalState var1, double var2, double var4, double var6, double var8, double var10, double var12);

   @Nullable
   default VoxelShape getThisSideCollisionExclusion(UnilateralPortalState portalState) {
      return null;
   }

   Vec3 getMovementForPushingEntityOutOfPortal(Portal var1, UnilateralPortalState var2, Entity var3, Vec3 var4);

   PortalShape cloneIfNecessary();

   @Environment(EnvType.CLIENT)
   default boolean canDoOuterFrustumCulling() {
      return false;
   }

   @Environment(EnvType.CLIENT)
   @Nullable
   default BoxPredicateF getInnerFrustumCullingFunc(Portal portal, Vec3 cameraPos) {
      return null;
   }

   @Nullable
   default AABB transformEntityActiveCollisionBox(Portal portal, AABB box, Entity entity) {
      return box;
   }

   @Environment(EnvType.CLIENT)
   @Nullable
   default BoxPredicateF getOuterFrustumCullingFunc(Portal portal, Vec3 cameraPos) {
      return null;
   }

   @Environment(EnvType.CLIENT)
   @Nullable
   default SectionPos getModifiedVisibleSectionIterationOrigin(Portal portal, Vec3 cameraPos) {
      return null;
   }

   boolean shouldRenderInside(Portal var1, AABB var2);
}
