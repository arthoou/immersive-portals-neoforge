package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.collision.PortalCollisionEntry;
import qouteall.imm_ptl.core.collision.PortalCollisionHandler;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.ducks.IEWorldRenderer;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;
import qouteall.imm_ptl.core.portal.Mirror;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalManipulation;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.imm_ptl.core.render.renderer.PortalRenderer;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.Plane;

@Environment(EnvType.CLIENT)
public class CrossPortalEntityRenderer {
   private static final Minecraft client = Minecraft.getInstance();
   private static final WeakHashMap<Entity, Object> collidedEntities = new WeakHashMap<>();
   public static boolean isRenderingEntityNormally = false;
   public static boolean isRenderingEntityProjection = false;
   private static final List<Entity> deferredEntities = new ArrayList<>();
   @Nullable
   private static Entity lastDeferredEntity = null;

   public static void init() {
      IPGlobal.POST_CLIENT_TICK_EVENT.register(CrossPortalEntityRenderer::onClientTick);
      IPCGlobal.CLIENT_CLEANUP_EVENT.register(CrossPortalEntityRenderer::cleanUp);
      ClientWorldLoader.CLIENT_DIMENSION_DYNAMIC_REMOVE_EVENT.register(dim -> cleanUp());
   }

   private static void cleanUp() {
      collidedEntities.clear();
      deferredEntities.clear();
      lastDeferredEntity = null;
   }

   private static void onClientTick() {
      collidedEntities.entrySet().removeIf(entry -> {
         Entity entity = entry.getKey();
         return entity.isRemoved() || !((IEEntity)entity).ip_isCollidingWithPortal();
      });
   }

   public static void onEntityTickClient(Entity entity) {
      if (!(entity instanceof Portal)) {
         if (((IEEntity)entity).ip_isCollidingWithPortal()) {
            collidedEntities.put(entity, null);
         }
      }
   }

   public static void onBeginRenderingEntitiesAndBlockEntities(Matrix4f modelView) {
      isRenderingEntityNormally = true;
      if (PortalRendering.isRendering()) {
         FrontClipping.setupInnerClipping(PortalRendering.getActiveClippingPlane(), modelView, 0.0);
      }
   }

   private static boolean isCrossPortalRenderingEnabled() {
      return IrisInterface.invoker.isIrisPresent() ? false : IPGlobal.correctCrossPortalEntityRendering;
   }

   public static void onEndRenderingEntitiesAndBlockEntities(PoseStack matrixStack) {
      renderDeferredEntities(matrixStack);
      isRenderingEntityNormally = false;
      FrontClipping.disableClipping();
      if (isCrossPortalRenderingEnabled()) {
         renderEntityProjections(matrixStack);
      }
   }

   public static void onBeginExtractingEntities() {
      deferredEntities.clear();
      lastDeferredEntity = null;
   }

   public static boolean shouldDeferEntityRendering(Entity entity) {
      if (!isCrossPortalRenderingEnabled()) {
         return false;
      }

      if (PortalRendering.isRendering()) {
         return false;
      }

      if (!collidedEntities.containsKey(entity)) {
         return false;
      }

      PortalCollisionHandler collisionHandler = ((IEEntity)entity).ip_getPortalCollisionHandler();
      return collisionHandler != null && !collisionHandler.portalCollisions.isEmpty();
   }

   public static void deferEntity(Entity entity) {
      deferredEntities.add(entity);
      lastDeferredEntity = entity;
   }

   public static boolean isLastDeferredEntity(Entity entity) {
      return lastDeferredEntity == entity;
   }

   private static void renderDeferredEntities(PoseStack matrixStack) {
      if (!deferredEntities.isEmpty()) {
         try {
            for (Entity entity : deferredEntities) {
               beforeRenderingEntity(entity, matrixStack);
               renderEntityNormallyNow(entity, matrixStack);
               afterRenderingEntity(entity);
            }
         } finally {
            deferredEntities.clear();
            lastDeferredEntity = null;
         }
      }
   }

   public static void beforeRenderingEntity(Entity entity, PoseStack matrixStack) {
      if (isCrossPortalRenderingEnabled()) {
         if (!PortalRendering.isRendering() && collidedEntities.containsKey(entity)) {
            PortalCollisionHandler collisionHandler = ((IEEntity)entity).ip_getPortalCollisionHandler();
            if (collisionHandler != null) {
               for (PortalCollisionEntry e : collisionHandler.portalCollisions) {
                  Portal collidingPortal = e.portal;
                  client.renderBuffers().bufferSource().endBatch();
                  FrontClipping.setupOuterClipping(matrixStack, collidingPortal);
               }
            }
         }
      }
   }

   public static void afterRenderingEntity(Entity entity) {
      if (isCrossPortalRenderingEnabled()) {
         if (!PortalRendering.isRendering() && collidedEntities.containsKey(entity)) {
            client.renderBuffers().bufferSource().endBatch();
            FrontClipping.disableClipping();
         }
      }
   }

   @IPVanillaCopy
   private static void renderEntityNormallyNow(Entity entity, PoseStack matrixStack) {
      if (entity.tickCount == 0) {
         entity.xOld = entity.getX();
         entity.yOld = entity.getY();
         entity.zOld = entity.getZ();
      }

      float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(!client.level.tickRateManager().isEntityFrozen(entity));
      Vec3 cameraPos = client.gameRenderer.getMainCamera().position();
      BufferSource consumers = client.renderBuffers().bufferSource();
      ((IEWorldRenderer)client.levelRenderer).ip_renderSingleEntity(entity, cameraPos.x, cameraPos.y, cameraPos.z, partialTick, matrixStack, consumers);
   }

   private static void renderEntityProjections(PoseStack matrixStack) {
      if (isCrossPortalRenderingEnabled()) {
         ResourceKey<Level> clientDim = client.level.dimension();

         for (Entity entity : collidedEntities.keySet()) {
            PortalCollisionHandler collisionHandler = ((IEEntity)entity).ip_getPortalCollisionHandler();
            if (collisionHandler != null) {
               for (PortalCollisionEntry e : collisionHandler.portalCollisions) {
                  Portal collidingPortal = e.portal;
                  if (!(collidingPortal instanceof Mirror)) {
                     ResourceKey<Level> projectionDimension = collidingPortal.getDestDim();
                     if (clientDim == projectionDimension) {
                        renderProjectedEntity(entity, collidingPortal, matrixStack);
                     }
                  }
               }
            }
         }
      }
   }

   public static boolean hasIntersection(Vec3 outerPlanePos, Vec3 outerPlaneNormal, Vec3 entityPos, Vec3 collidingPortalNormal) {
      return entityPos.subtract(outerPlanePos).dot(outerPlaneNormal) > 0.01 && outerPlanePos.subtract(entityPos).dot(collidingPortalNormal) > 0.01;
   }

   private static void renderProjectedEntity(Entity entity, Portal collidingPortal, PoseStack matrixStack) {
      if (PortalRendering.isRendering()) {
         Portal renderingPortal = PortalRendering.getRenderingPortal();
         if (renderingPortal instanceof Portal
            && !Portal.isFlippedPortal(renderingPortal, collidingPortal)
            && !Portal.isReversePortal(renderingPortal, collidingPortal)) {
            Vec3 cameraPos = client.gameRenderer.getMainCamera().position();
            Plane innerClipping = collidingPortal.getInnerClipping();
            boolean isHidden = innerClipping != null && !innerClipping.isPointOnPositiveSide(cameraPos);
            if (renderingPortal == collidingPortal || !isHidden) {
               renderEntity(entity, collidingPortal, matrixStack);
            }
         }
      } else {
         FrontClipping.disableClipping();
         client.renderBuffers().bufferSource().endBatch();
         FrontClipping.setupInnerClipping(collidingPortal.getInnerClipping(), matrixStack.last().pose(), 0.0);
         renderEntity(entity, collidingPortal, matrixStack);
         FrontClipping.disableClipping();
      }
   }

   private static void renderEntity(Entity entity, Portal transformingPortal, PoseStack matrixStack) {
      Vec3 cameraPos = client.gameRenderer.getMainCamera().position();
      ClientLevel newWorld = ClientWorldLoader.getWorld(transformingPortal.getDestDim());
      Vec3 entityPos = entity.position();
      Vec3 entityEyePos = McHelper.getEyePos(entity);
      Vec3 entityLastTickPos = McHelper.lastTickPosOf(entity);
      Vec3 entityLastTickEyePos = McHelper.getLastTickEyePos(entity);
      Level oldWorld = entity.level();
      Vec3 newEyePos = transformingPortal.transformPoint(entityEyePos);
      if (PortalRendering.isRendering()) {
         Portal renderingPortal = PortalRendering.getRenderingPortal();
         Vec3 transformedEntityPos = newEyePos.subtract(McHelper.getEyeOffset(entity));
         AABB transformedBoundingBox = McHelper.getBoundingBoxWithMovedPosition(entity, transformedEntityPos);
         boolean intersects = PortalManipulation.isOtherSideBoxInside(transformedBoundingBox, renderingPortal);
         if (!intersects) {
            return;
         }
      }

      if (entity instanceof LocalPlayer) {
         if (!IPGlobal.renderYourselfInPortal) {
            return;
         }

         if (!transformingPortal.getDoRenderPlayer()) {
            return;
         }

         if (client.options.getCameraType().isFirstPerson()) {
            double dis = newEyePos.distanceTo(cameraPos);
            double valve = 0.5 + entityLastTickPos.distanceTo(entityPos);
            if (transformingPortal.getScaling() > 1.0) {
               valve *= transformingPortal.getScaling();
            }

            if (dis < valve) {
               return;
            }

            AABB transformedBoundingBox = Helper.transformBox(RenderStates.originalPlayerBoundingBox, transformingPortal::transformPoint);
            if (transformedBoundingBox.contains(CHelper.getCurrentCameraPos())) {
               return;
            }
         }
      }

      isRenderingEntityProjection = true;
      matrixStack.pushPose();

      try {
         Vec3 entityInstantPos = entityLastTickPos.lerp(entityPos, RenderStates.getPartialTick());
         Vec3 newEntityInstantPos = transformingPortal.transformPoint(entityInstantPos);
         Vec3 newCameraPos = entityInstantPos.subtract(newEntityInstantPos).add(cameraPos);
         setupEntityProjectionRenderingTransformation(transformingPortal, matrixStack, entityPos, entityLastTickPos, newCameraPos);
         BufferSource consumers = client.renderBuffers().bufferSource();
         ((IEWorldRenderer)client.levelRenderer)
            .ip_renderSingleEntity(entity, newCameraPos.x, newCameraPos.y, newCameraPos.z, RenderStates.getPartialTick(), matrixStack, consumers);
         consumers.endBatch();
      } finally {
         matrixStack.popPose();
         isRenderingEntityProjection = false;
      }
   }

   private static void setupEntityProjectionRenderingTransformation(
      Portal portal, PoseStack matrixStack, Vec3 entityPos, Vec3 entityLastTickPos, Vec3 cameraPos
   ) {
      if (portal.getScaling() != 1.0 || portal.getRotation() != null) {
         Vec3 anchor = entityLastTickPos.lerp(entityPos, RenderStates.getPartialTick()).subtract(cameraPos);
         matrixStack.translate(anchor.x, anchor.y, anchor.z);
         float scaling = (float)portal.getScaling();
         matrixStack.scale(scaling, scaling, scaling);
         if (portal.getRotation() != null) {
            matrixStack.mulPose(portal.getRotation().toMcQuaternion());
         }

         matrixStack.translate(-anchor.x, -anchor.y, -anchor.z);
      }
   }

   public static boolean shouldRenderPlayerDefault() {
      if (!IPGlobal.renderYourselfInPortal) {
         return false;
      }

      if (!WorldRenderInfo.isRendering()) {
         return false;
      }

      LocalPlayer player = client.player;
      assert player != null;
      if (PortalRendering.isRendering()) {
         Portal renderingPortal = PortalRendering.getRenderingPortal();
         if (renderingPortal instanceof Mirror) {
            float width = player.getBbWidth();
            if (renderingPortal.getDistanceToNearestPointInPortal(player.getEyePosition()) < width * 0.8) {
               return false;
            }
         }
      }

      return client.level == player.level();
   }

   public static boolean shouldRenderEntityNow(Entity entity) {
      Validate.notNull(entity);
      if (IrisInterface.invoker.isRenderingShadowMap()) {
         return true;
      }

      if (PortalRendering.isRendering()) {
         Portal renderingPortal = PortalRendering.getRenderingPortal();
         Portal collidingPortal = ((IEEntity)entity).ip_getCollidingPortal();
         if (entity instanceof Player && !renderingPortal.getDoRenderPlayer()) {
            return false;
         }

         if (collidingPortal != null
            && !(entity instanceof LocalPlayer)
            && renderingPortal instanceof Portal
            && !Portal.isReversePortal(collidingPortal, renderingPortal)) {
            Vec3 cameraPos = PortalRenderer.client.gameRenderer.getMainCamera().position();
            boolean isHidden = cameraPos.subtract(collidingPortal.getOriginPos()).dot(collidingPortal.getNormal()) < 0.0;
            if (isHidden) {
               return false;
            }
         }

         return renderingPortal.isOnDestinationSide(getRenderingCameraPos(entity), -0.01);
      } else {
         return true;
      }
   }

   public static boolean shouldRenderPlayerNormally(Entity entity) {
      if (!client.options.getCameraType().isFirstPerson()) {
         return true;
      }

      if (RenderStates.originalPlayerBoundingBox.contains(CHelper.getCurrentCameraPos())) {
         return false;
      }

      double distanceToCamera = getRenderingCameraPos(entity).distanceTo(client.gameRenderer.getMainCamera().position());
      return distanceToCamera > 1.0 || PortalRendering.isRenderingOddNumberOfMirrors();
   }

   public static Vec3 getRenderingCameraPos(Entity entity) {
      return entity instanceof LocalPlayer
         ? RenderStates.originalPlayerPos.add(McHelper.getEyeOffset(entity))
         : entity.getEyePosition(RenderStates.getPartialTick());
   }
}
