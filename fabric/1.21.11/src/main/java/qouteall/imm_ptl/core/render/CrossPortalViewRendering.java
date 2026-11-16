package qouteall.imm_ptl.core.render;

import com.mojang.datafixers.util.Pair;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.ScaleUtils;
import qouteall.imm_ptl.core.commands.PortalCommand;
import qouteall.imm_ptl.core.ducks.IECamera;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.imm_ptl.core.teleportation.ClientTeleportationManager;

public class CrossPortalViewRendering {
   public static final Minecraft client = Minecraft.getInstance();

   public static boolean renderCrossPortalView() {
      if (!IPGlobal.enableCrossPortalView) {
         return false;
      }

      Entity cameraEntity = client.getCameraEntity();
      Camera camera1 = new Camera();
      float cameraY = ((IECamera)RenderStates.originalCamera).ip_getCameraY();
      float lastCameraY = ((IECamera)RenderStates.originalCamera).ip_getLastCameraY();
      ((IECamera)camera1).ip_setCameraY(cameraY, lastCameraY);
      Camera camera = camera1;
      camera.setup(client.level, cameraEntity, isThirdPerson(), isFrontView(), RenderStates.getPartialTick());
      Vec3 realCameraPos = camera.position();
      Vec3 isometricAdjustedOriginalCameraPos = TransformationManager.getIsometricAdjustedCameraPos(camera);
      Vec3 physicalPlayerHeadPos = ClientTeleportationManager.getPlayerEyePos(RenderStates.getPartialTick());
      Pair<Portal, Vec3> portalHit = PortalCommand.raytracePortals(client.level, physicalPlayerHeadPos, isometricAdjustedOriginalCameraPos, true).orElse(null);
      if (portalHit == null) {
         return false;
      }

      Portal portal = portalHit.getFirst();
      Vec3 hitPos = portalHit.getSecond();
      if (!portal.canTeleportEntity(cameraEntity)) {
         return false;
      }

      Vec3 renderingCameraPos;
      if (isThirdPerson()) {
         double distance = getThirdPersonMaxDistance();
         Vec3 thirdPersonPos = realCameraPos.subtract(physicalPlayerHeadPos).normalize().scale(distance).add(physicalPlayerHeadPos);
         renderingCameraPos = getThirdPersonCameraPos(thirdPersonPos, portal, hitPos);
      } else {
         renderingCameraPos = portal.transformPoint(realCameraPos);
      }

      ((IECamera)RenderStates.originalCamera).portal_setPos(renderingCameraPos);
      WorldRenderInfo worldRenderInfo = new WorldRenderInfo.Builder()
         .setWorld(ClientWorldLoader.getWorld(portal.getDestDim()))
         .setCameraPos(renderingCameraPos)
         .setCameraTransformation(portal.getAdditionalCameraTransformation())
         .setOverwriteCameraTransformation(false)
         .setDescription(null)
         .setRenderDistance(client.options.getEffectiveRenderDistance())
         .setDoRenderHand(false)
         .setEnableViewBobbing(false)
         .build();
      IPCGlobal.renderer.invokeWorldRendering(worldRenderInfo);
      return true;
   }

   private static boolean isFrontView() {
      return client.options.getCameraType().isMirrored();
   }

   private static boolean isThirdPerson() {
      return !client.options.getCameraType().isFirstPerson();
   }

   private static Vec3 getThirdPersonCameraPos(Vec3 endPos, Portal portal, Vec3 startPos) {
      Vec3 rtStart = portal.transformPoint(startPos);
      Vec3 rtEnd = portal.transformPoint(endPos);
      assert client.getCameraEntity() != null;
      BlockHitResult blockHitResult = portal.getDestinationWorld().clip(new ClipContext(rtStart, rtEnd, Block.VISUAL, Fluid.NONE, client.getCameraEntity()));
      return blockHitResult.getType() == Type.BLOCK
         ? rtStart.add(rtEnd.subtract(rtStart).normalize().scale(getThirdPersonMaxDistance()))
         : blockHitResult.getLocation();
   }

   private static double getThirdPersonMaxDistance() {
      return 4.0 * ScaleUtils.computeThirdPersonScale(client.player);
   }
}
