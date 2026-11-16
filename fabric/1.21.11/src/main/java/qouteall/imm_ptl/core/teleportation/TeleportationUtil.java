package qouteall.imm_ptl.core.teleportation;

import com.mojang.logging.LogUtils;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalState;
import qouteall.imm_ptl.core.portal.animation.UnilateralPortalState;
import qouteall.imm_ptl.core.portal.shape.PortalShape;
import qouteall.q_misc_util.my_util.RayTraceResult;

public class TeleportationUtil {
   private static final Logger LOGGER = LogUtils.getLogger();

   public static TeleportationUtil.PortalPointVelocity getPortalPointVelocity(PortalState lastTickState, PortalState thisTickState, Vec3 localPos) {
      Vec3 lastThisSidePos = lastTickState.getThisSideState().transformLocalToGlobal(localPos);
      Vec3 currentThisSidePos = thisTickState.getThisSideState().transformLocalToGlobal(localPos);
      Vec3 thisSideVelocity = currentThisSidePos.subtract(lastThisSidePos);
      Vec3 lastOtherSidePos = lastTickState.transformPoint(lastThisSidePos);
      Vec3 currentOtherSidePos = thisTickState.transformPoint(currentThisSidePos);
      Vec3 otherSideVelocity = currentOtherSidePos.subtract(lastOtherSidePos);
      return new TeleportationUtil.PortalPointVelocity(thisSideVelocity, otherSideVelocity);
   }

   public static void transformEntityVelocity(Portal portal, Entity entity, TeleportationUtil.PortalPointVelocity portalPointVelocity, Vec3 oldEntityPos) {
      Vec3 oldVelocityRelativeToPortal = McHelper.getWorldVelocity(entity).subtract(portalPointVelocity.thisSidePointVelocity());
      Vec3 transformedVelocityRelativeToPortal = portal.transformVelocityRelativeToPortal(oldVelocityRelativeToPortal, entity, oldEntityPos);
      Vec3 newVelocity = transformedVelocityRelativeToPortal.add(portalPointVelocity.otherSidePointVelocity());
      McHelper.setWorldVelocity(entity, newVelocity);
   }

   @Nullable
   public static TeleportationUtil.Teleportation checkStaticTeleportation(
      Portal portal, Vec3 lastPos, Vec3 currentPos, Vec3 lastTickEyePos, Vec3 thisTickEyePos
   ) {
      Vec3 lastLocalPos = portal.transformFromWorldToPortalLocal(lastPos);
      Vec3 currentLocalPos = portal.transformFromWorldToPortalLocal(currentPos);
      PortalShape portalShape = portal.getPortalShape();
      UnilateralPortalState portalThisSideState = portal.getThisSideState();
      RayTraceResult localRayTraceResult = portalShape.raytracePortalShapeByLocalPos(portalThisSideState, lastLocalPos, currentLocalPos, 0.0);
      if (localRayTraceResult == null) {
         return null;
      }

      Vec3 worldHitPos = portalThisSideState.transformLocalToGlobal(localRayTraceResult.hitPos());
      Vec3 newLastTickEyePos = portal.transformPoint(lastTickEyePos);
      Vec3 newThisTickEyePos = portal.transformPoint(thisTickEyePos);
      PortalState portalState = portal.getPortalState();
      return new TeleportationUtil.Teleportation(
         false,
         portal,
         lastPos,
         currentPos,
         lastLocalPos,
         currentLocalPos,
         localRayTraceResult.hitPos(),
         localRayTraceResult.t(),
         worldHitPos,
         portalThisSideState.transformVecLocalToGlobal(localRayTraceResult.surfaceNormal()),
         portalState,
         portalState,
         portalState,
         portalState,
         portalState,
         TeleportationUtil.PortalPointVelocity.ZERO,
         portal.transformPoint(worldHitPos),
         newLastTickEyePos,
         newThisTickEyePos
      );
   }

   @Nullable
   public static TeleportationUtil.Teleportation checkDynamicTeleportation(
      Portal portal,
      PortalState lastFrameState,
      PortalState currentFrameState,
      Vec3 lastFrameEyePos,
      Vec3 currentFrameEyePos,
      PortalState lastTickState,
      PortalState thisTickState,
      Vec3 lastTickEyePos,
      Vec3 thisTickEyePos,
      float partialTicks
   ) {
      Vec3 lastLocalPos = lastFrameState.worldPosToPortalLocalPos(lastFrameEyePos);
      Vec3 currentLocalPos = currentFrameState.worldPosToPortalLocalPos(currentFrameEyePos);
      UnilateralPortalState portalThisSideState = portal.getThisSideState();
      PortalShape portalShape = portal.getPortalShape();
      RayTraceResult rayTraceResult = portalShape.raytracePortalShapeByLocalPos(portalThisSideState, lastLocalPos, currentLocalPos, 0.0);
      if (rayTraceResult == null) {
         return null;
      }

      Vec3 localHitPos = rayTraceResult.hitPos();
      Vec3 worldHitPos = portalThisSideState.transformLocalToGlobal(localHitPos);
      TeleportationUtil.PortalPointVelocity portalPointVelocity = getPortalPointVelocity(lastTickState, thisTickState, localHitPos);
      PortalState collisionPortalState = PortalState.interpolate(lastFrameState, currentFrameState, rayTraceResult.t(), false);
      Vec3 collisionPointMappedToThisFrame = thisTickState.getThisSideState().transformLocalToGlobal(localHitPos);
      Vec3 collisionPointMappedToLastFrame = lastFrameState.getThisSideState().transformLocalToGlobal(localHitPos);
      Vec3 newOtherSideLastTickPos = lastTickState.transformPoint(lastTickEyePos);
      Vec3 newOtherSideThisTickPos = thisTickState.transformPoint(thisTickEyePos);
      Vec3 newImmediateCameraPos = newOtherSideLastTickPos.lerp(newOtherSideThisTickPos, partialTicks);
      Vec3 correctImmediateCameraPos = collisionPortalState.transformPoint(currentFrameEyePos);
      Vec3 deltaVelocity = portalPointVelocity.otherSidePointVelocity()
         .subtract(collisionPortalState.transformVec(portalPointVelocity.thisSidePointVelocity()));
      Vec3 offset = correctImmediateCameraPos.subtract(newImmediateCameraPos);
      newOtherSideLastTickPos = newOtherSideLastTickPos.add(offset);
      newOtherSideThisTickPos = newOtherSideThisTickPos.add(offset);
      if (portalShape.isPlanar()) {
         double dot = newOtherSideThisTickPos.subtract(thisTickState.toPos).dot(thisTickState.getContentDirection());
         if (dot < 1.0E-5) {
            LOGGER.info("Teleported to behind the end-tick portal destination. Corrected.");
            Vec3 offset1 = thisTickState.getContentDirection().scale(Math.max(-dot, 0.0) + 1.0E-5);
            newOtherSideThisTickPos = newOtherSideThisTickPos.add(offset1);
            newOtherSideLastTickPos = newOtherSideLastTickPos.add(offset1);
         }

         newImmediateCameraPos = newOtherSideLastTickPos.lerp(newOtherSideThisTickPos, partialTicks);
         dot = newImmediateCameraPos.subtract(currentFrameState.toPos).dot(currentFrameState.getContentDirection());
         if (dot < 1.0E-5) {
            LOGGER.info("Teleported to behind the end-frame portal destination. Corrected.");
            Vec3 offset1 = currentFrameState.getContentDirection().scale(Math.max(-dot, 0.0) + 1.0E-5);
            newOtherSideThisTickPos = newOtherSideThisTickPos.add(offset1);
            newOtherSideLastTickPos = newOtherSideLastTickPos.add(offset1);
         }

         newImmediateCameraPos = newOtherSideLastTickPos.lerp(newOtherSideThisTickPos, partialTicks);
         dot = newImmediateCameraPos.subtract(lastFrameState.toPos).dot(lastFrameState.getContentDirection());
         if (dot < 1.0E-5) {
            LOGGER.info("Teleported to behind the last-frame portal destination. Corrected.");
            Vec3 offset1 = lastFrameState.getContentDirection().scale(Math.max(-dot, 0.0) + 0.001);
            newOtherSideThisTickPos = newOtherSideThisTickPos.add(offset1);
            newOtherSideLastTickPos = newOtherSideLastTickPos.add(offset1);
         }
      }

      Vec3 teleportationCheckpoint = newOtherSideLastTickPos.lerp(newOtherSideThisTickPos, partialTicks);
      return new TeleportationUtil.Teleportation(
         true,
         portal,
         lastFrameEyePos,
         currentFrameEyePos,
         lastLocalPos,
         currentLocalPos,
         localHitPos,
         rayTraceResult.t(),
         worldHitPos,
         portalThisSideState.transformVecLocalToGlobal(rayTraceResult.surfaceNormal()),
         collisionPortalState,
         lastFrameState,
         currentFrameState,
         lastTickState,
         thisTickState,
         portalPointVelocity,
         teleportationCheckpoint,
         newOtherSideLastTickPos,
         newOtherSideThisTickPos
      );
   }

   @Deprecated
   private static TeleportationUtil.PortalPointVelocity getConservativePortalPointVelocity(
      PortalState lastTickState, PortalState thisTickState, Vec3 lastTickPos, Vec3 thisTickPos
   ) {
      List<Vec3> localPoses = List.of(
         lastTickState.worldPosToPortalLocalPos(lastTickPos),
         lastTickState.worldPosToPortalLocalPos(thisTickPos),
         thisTickState.worldPosToPortalLocalPos(lastTickPos),
         thisTickState.worldPosToPortalLocalPos(thisTickPos)
      );
      List<TeleportationUtil.PortalPointVelocity> portalPointVelocities = localPoses.stream()
         .map(localPos -> getPortalPointVelocity(lastTickState, thisTickState, localPos))
         .toList();
      return new TeleportationUtil.PortalPointVelocity(
         portalPointVelocities.stream().map(v -> v.thisSidePointVelocity).max(Comparator.comparingDouble(p -> p.dot(lastTickState.getNormal()))).orElseThrow(),
         portalPointVelocities.stream()
            .map(v -> v.otherSidePointVelocity)
            .max(Comparator.comparingDouble(p -> p.dot(thisTickState.getContentDirection())))
            .orElseThrow()
      );
   }

   private record CollisionInfo(double portalLocalX, double portalLocalY, double tOfCollision, Vec3 collisionPos) {
   }

   public record PortalPointOffset(Vec3 thisSideOffset, Vec3 otherSideOffse) {
   }

   public record PortalPointVelocity(Vec3 thisSidePointVelocity, Vec3 otherSidePointVelocity) {
      public static final TeleportationUtil.PortalPointVelocity ZERO = new TeleportationUtil.PortalPointVelocity(Vec3.ZERO, Vec3.ZERO);
   }

   public record Teleportation(
      boolean isDynamic,
      Portal portal,
      Vec3 lastWorldEyePos,
      Vec3 currentWorldEyePos,
      Vec3 lastLocalEyePos,
      Vec3 currentLocalEyePos,
      Vec3 localCollisionPoint,
      double tOfCollision,
      Vec3 worldCollisionPoint,
      Vec3 worldSurfaceNormal,
      PortalState collidingPortalState,
      PortalState lastFrameState,
      PortalState thisFrameState,
      PortalState lastTickState,
      PortalState thisTickState,
      TeleportationUtil.PortalPointVelocity portalPointVelocity,
      Vec3 teleportationCheckpoint,
      Vec3 newLastTickEyePos,
      Vec3 newThisTickEyePos
   ) {
   }
}
