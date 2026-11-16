package qouteall.imm_ptl.core.render.context_management;

import com.mojang.logging.LogUtils;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.block_manipulation.BlockManipulationClient;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.ducks.IEGameRenderer;
import qouteall.imm_ptl.core.miscellaneous.ClientPerformanceMonitor;
import qouteall.imm_ptl.core.mixin.client.particle.IEParticle;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.animation.StableClientTimer;
import qouteall.imm_ptl.core.render.ForceMainThreadRebuild;
import qouteall.imm_ptl.core.render.MyRenderHelper;
import qouteall.imm_ptl.core.render.QueryManager;
import qouteall.q_misc_util.Helper;

public class RenderStates {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static int frameIndex = 0;
   public static ResourceKey<Level> originalPlayerDimension;
   public static Vec3 originalPlayerPos = Vec3.ZERO;
   public static Vec3 originalPlayerLastTickPos = Vec3.ZERO;
   public static GameType originalGameMode;
   public static AABB originalPlayerBoundingBox;
   private static float partialTick = 0.0F;
   public static Set<ResourceKey<Level>> renderedDimensions = new HashSet<>();
   public static List<List<WeakReference<Portal>>> lastPortalRenderInfos = new ArrayList<>();
   public static List<List<WeakReference<Portal>>> portalRenderInfos = new ArrayList<>();
   public static int portalsRenderedThisFrame = 0;
   public static Vec3 lastCameraPos = Vec3.ZERO;
   public static Vec3 cameraPosDelta = Vec3.ZERO;
   public static boolean shouldForceDisableCull = false;
   public static long renderStartNanoTime;
   public static double viewBobFactor;
   public static Matrix4f basicProjectionMatrix;
   @Nullable
   public static Matrix4f projectionMatrix;
   public static Vec3 currentFogColor = Vec3.ZERO;
   public static boolean isRenderingTerrainLayer = false;
   public static Camera originalCamera;
   public static String debugText;
   public static boolean isLaggy = false;
   public static boolean isRenderingEntities = false;
   public static boolean renderedScalingPortal = false;
   public static boolean isRenderingPortalWeather = false;

   public static void updatePreRenderInfo(float newPartialTick) {
      ClientWorldLoader.initializeIfNeeded();
      Entity cameraEntity = MyRenderHelper.client.getCameraEntity();
      if (cameraEntity != null) {
         originalPlayerDimension = cameraEntity.level().dimension();
         originalPlayerPos = cameraEntity.position();
         originalPlayerLastTickPos = McHelper.lastTickPosOf(cameraEntity);
         PlayerInfo entry = CHelper.getClientPlayerListEntry();
         originalGameMode = entry != null ? entry.getGameMode() : GameType.CREATIVE;
         partialTick = newPartialTick;
         renderedDimensions.clear();
         lastPortalRenderInfos = portalRenderInfos;
         portalRenderInfos = new ArrayList<>();
         portalsRenderedThisFrame = 0;
         FogRendererContext.update();
         renderStartNanoTime = System.nanoTime();
         updateViewBobbingFactor(cameraEntity);
         basicProjectionMatrix = null;
         originalCamera = MyRenderHelper.client.gameRenderer.getMainCamera();
         updateIsLaggy();
         ForceMainThreadRebuild.onPreRender();
         debugText = "";
         QueryManager.queryStallCounter = 0;
         Vec3 velocity = McHelper.getWorldVelocity(cameraEntity);
         originalPlayerBoundingBox = cameraEntity.getBoundingBox().expandTowards(-velocity.x, -velocity.y, -velocity.z);
      }
   }

   private static void updateIsLaggy() {
      if (!IPGlobal.lagAttackProof) {
         isLaggy = false;
      } else {
         if (isLaggy) {
            if (ClientPerformanceMonitor.getMinimumFps() > 15) {
               isLaggy = false;
            }
         } else if (lastPortalRenderInfos.size() > 10 && (ClientPerformanceMonitor.getAverageFps() < 8 || ClientPerformanceMonitor.getMinimumFps() < 6)) {
            MyRenderHelper.client.gui.setOverlayMessage(Component.translatable("imm_ptl.laggy"), false);
            isLaggy = true;
         }
      }
   }

   private static void updateViewBobbingFactor(Entity cameraEntity) {
      Vec3 cameraPosVec = cameraEntity.getEyePosition(getPartialTick());
      double minPortalDistance = CHelper.getClientNearbyPortals(16.0)
         .map(portal -> portal.getDistanceToNearestPointInPortal(cameraPosVec))
         .min(Double::compareTo)
         .orElse(100.0);
      if (minPortalDistance < 2.0) {
         if (minPortalDistance < 1.0) {
            setViewBobFactor(0.0);
         } else {
            setViewBobFactor(minPortalDistance - 1.0);
         }
      } else {
         setViewBobFactor(1.0);
      }
   }

   public static double getViewBobbingOffsetMultiplier() {
      if (!IPGlobal.viewBobbingReduce) {
         return 1.0;
      }

      if (!WorldRenderInfo.isViewBobbingEnabled()) {
         return 0.0;
      }

      double allScaling = PortalRendering.getExtraModelViewScaling();
      return viewBobFactor * allScaling;
   }

   private static void setViewBobFactor(double arg) {
      if (arg < viewBobFactor) {
         viewBobFactor = arg;
      } else {
         viewBobFactor = Mth.lerp(0.1, viewBobFactor, arg);
      }
   }

   public static void onTotalRenderEnd() {
      Minecraft client = Minecraft.getInstance();
      IEGameRenderer gameRenderer = (IEGameRenderer)Minecraft.getInstance().gameRenderer;
      gameRenderer.ip_setLightmapTextureManager(ClientWorldLoader.getDimensionRenderHelper(client.level.dimension()).lightmapTexture);
      Vec3 currCameraPos = client.gameRenderer.getMainCamera().position();
      cameraPosDelta = currCameraPos.subtract(lastCameraPos);
      if (cameraPosDelta.lengthSqr() > 1.0) {
         cameraPosDelta = Vec3.ZERO;
      }

      lastCameraPos = currCameraPos;
   }

   public static int getRenderedPortalNum() {
      return portalRenderInfos.size();
   }

   public static boolean isDimensionRendered(ResourceKey<Level> dimensionType) {
      return dimensionType == originalPlayerDimension ? true : renderedDimensions.contains(dimensionType);
   }

   public static boolean shouldRenderParticle(Particle particle) {
      if (((IEParticle)particle).portal_getWorld() != Minecraft.getInstance().level) {
         return false;
      } else if (PortalRendering.isRendering()) {
         Portal renderingPortal = PortalRendering.getRenderingPortal();
         Vec3 particlePos = particle.getBoundingBox().getCenter();
         return renderingPortal.isOnDestinationSide(particlePos, 0.5);
      } else {
         return true;
      }
   }

   public static void setPartialTick(float partialTick_) {
      partialTick = partialTick_;
   }

   public static float getPartialTick() {
      return partialTick;
   }

   public static List<String> collectDebugText() {
      List<String> result = new ArrayList<>();
      result.add("Rendered Portals: " + lastPortalRenderInfos.size());
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         Portal collidingPortal = ((IEEntity)player).ip_getCollidingPortal();
         if (collidingPortal != null) {
            String text = "Colliding " + collidingPortal.toString();
            result.addAll(Helper.splitStringByLen(text, 50));
         }
      }

      result.add("Occlusion Query Stall: " + QueryManager.queryStallCounter);
      result.add(
         "Client Perf %s %d %d"
            .formatted(ClientPerformanceMonitor.level, ClientPerformanceMonitor.getAverageFps(), ClientPerformanceMonitor.getAverageFreeMemoryMB())
      );
      result.add(StableClientTimer.getDebugString());
      String blockPointingInfo = BlockManipulationClient.getDebugString();
      if (blockPointingInfo != null) {
         result.add(blockPointingInfo);
      }

      if (debugText != null && !debugText.isEmpty()) {
         result.addAll(Helper.splitStringByLen("Debug: " + debugText, 50));
      }

      return result;
   }
}
