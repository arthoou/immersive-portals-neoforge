package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.Lighting.Entry;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.Stack;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.fog.FogRenderer.FogMode;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.client.renderer.state.ParticlesRenderState;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.block_manipulation.BlockManipulationClient;
import qouteall.imm_ptl.core.compat.distant_horizons.DhRenderStateGuard;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface;
import qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumInterface;
import qouteall.imm_ptl.core.ducks.IEGameRenderer;
import qouteall.imm_ptl.core.ducks.IEMinecraftClient;
import qouteall.imm_ptl.core.ducks.IEParticleManager;
import qouteall.imm_ptl.core.ducks.IEWorldRenderer;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;
import qouteall.imm_ptl.core.mixin.client.render.IERenderSystem;
import qouteall.imm_ptl.core.mixin.client.render.IESectionRenderDispatcher;
import qouteall.imm_ptl.core.render.context_management.DimensionRenderHelper;
import qouteall.imm_ptl.core.render.context_management.FogRendererContext;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.q_misc_util.my_util.LimitedLogger;
import qouteall.q_misc_util.platform.ClientPlatform;

@Environment(EnvType.CLIENT)
public class MyGameRenderer {
   public static final Minecraft client = Minecraft.getInstance();
   private static final LimitedLogger limitedLogger = new LimitedLogger(10);
   private static final ArrayList<FogRenderer> fogRendererPool = new ArrayList<>();
   private static final ArrayList<FogRenderer> fogRenderersOfThisFrame = new ArrayList<>();
   private static final Stack<MyGameRenderer.NestedRenderContext> contextPool = new Stack<>();
   private static int usingContextNum = 0;
   public static int vanillaTerrainSetupOverride = 0;
   public static boolean enablePortalCaveCulling = true;
   public static int nestedRenderSerial = 0;

   private static FogRenderer acquireFogRenderer() {
      FogRenderer fogRenderer;
      if (!fogRendererPool.isEmpty()) {
         fogRenderer = fogRendererPool.remove(fogRendererPool.size() - 1);
      } else {
         GpuBufferSlice currentFog = RenderSystem.getShaderFog();
         fogRenderer = new FogRenderer();
         RenderSystem.setShaderFog(currentFog);
      }

      fogRenderersOfThisFrame.add(fogRenderer);
      return fogRenderer;
   }

   public static void onFrameEnd() {
      for (FogRenderer fogRenderer : fogRenderersOfThisFrame) {
         fogRenderer.endFrame();
         fogRendererPool.add(fogRenderer);
      }

      fogRenderersOfThisFrame.clear();
   }

   public static void init() {
      IPCGlobal.CLIENT_CLEANUP_EVENT.register(() -> {
         for (MyGameRenderer.NestedRenderContext context : contextPool) {
            context.close();
         }

         contextPool.clear();
         fogRendererPool.forEach(FogRenderer::close);
         fogRendererPool.clear();
         fogRenderersOfThisFrame.forEach(FogRenderer::close);
         fogRenderersOfThisFrame.clear();
      });
   }

   private static MyGameRenderer.NestedRenderContext acquireContext() {
      usingContextNum++;
      boolean useSecondaryRenderBuffers = IPGlobal.useSecondaryEntityVertexConsumer;

      while (!contextPool.isEmpty()) {
         MyGameRenderer.NestedRenderContext context = contextPool.pop();
         if (context.renderBuffers != null == useSecondaryRenderBuffers) {
            return context;
         }

         context.close();
      }

      return new MyGameRenderer.NestedRenderContext(useSecondaryRenderBuffers);
   }

   private static void returnContext(MyGameRenderer.NestedRenderContext context) {
      usingContextNum--;
      contextPool.push(context);
   }

   public static void renderWorldNew(WorldRenderInfo worldRenderInfo, Consumer<Runnable> invokeWrapper) {
      WorldRenderInfo.pushRenderInfo(worldRenderInfo);
      nestedRenderSerial++;
      switchAndRenderTheWorld(
         worldRenderInfo.world,
         worldRenderInfo.cameraPos,
         worldRenderInfo.cameraPos,
         invokeWrapper,
         worldRenderInfo.renderDistance,
         worldRenderInfo.doRenderHand
      );
      nestedRenderSerial++;
      WorldRenderInfo.popRenderInfo();
   }

   private static void switchAndRenderTheWorld(
      ClientLevel newWorld, Vec3 thisTickCameraPos, Vec3 lastTickCameraPos, Consumer<Runnable> invokeWrapper, int renderDistance, boolean doRenderHand
   ) {
      if (!enablePortalCaveCulling) {
         client.smartCull = false;
      }

      if (!PortalRendering.shouldEnableSodiumCaveCulling()) {
         client.smartCull = false;
      }

      ResourceKey<Level> newDimension = newWorld.dimension();
      LevelRenderer worldRenderer = ClientWorldLoader.getWorldRenderer(newDimension);
      CHelper.checkGlError();
      IEGameRenderer ieGameRenderer = (IEGameRenderer)client.gameRenderer;
      IEWorldRenderer ieWorldRenderer = (IEWorldRenderer)worldRenderer;
      DimensionRenderHelper helper = ClientWorldLoader.getDimensionRenderHelper(newDimension);
      Camera newCamera = new Camera();
      ClientLevel oldWorld = client.level;
      LevelRenderer oldWorldRenderer = client.levelRenderer;
      LightTexture oldLightmap = client.gameRenderer.lightTexture();
      boolean oldNoClip = client.player.noPhysics;
      boolean oldDoRenderHand = ieGameRenderer.ip_getDoRenderHand();
      ObjectArrayList<RenderSection> oldChunkInfoList = ((IEWorldRenderer)oldWorldRenderer).portal_getChunkInfoList();
      HitResult oldCrosshairTarget = client.hitResult;
      Camera oldCamera = client.gameRenderer.getMainCamera();
      RenderBuffers oldRenderBuffers = ieWorldRenderer.ip_getRenderBuffers();
      RenderBuffers oldClientRenderBuffers = client.renderBuffers();
      SectionBufferBuilderPack oldSectionRenderDispatcherFixedBuffers = ((IESectionRenderDispatcher)worldRenderer.getSectionRenderDispatcher())
         .ip_getFixedBuffers();
      Matrix4f oldProjectionMatrix = RenderStates.projectionMatrix == null ? null : new Matrix4f(RenderStates.projectionMatrix);
      ProjectionType oldProjectionType = RenderSystem.getProjectionType();
      Matrix4fStack oldModelViewStack = IERenderSystem.ip_getModelViewStack();
      LevelRenderState oldGameLevelRenderState = ieGameRenderer.ip_getLevelRenderState();
      SubmitNodeStorage oldGameSubmitNodeStorage = ieGameRenderer.ip_getSubmitNodeStorage();
      FeatureRenderDispatcher oldGameFeatureRenderDispatcher = ieGameRenderer.ip_getFeatureRenderDispatcher();
      FogRenderer oldFogRenderer = ieGameRenderer.ip_getFogRenderer();
      LevelRenderState oldWorldLevelRenderState = ieWorldRenderer.ip_getLevelRenderState();
      SubmitNodeStorage oldWorldSubmitNodeStorage = ieWorldRenderer.ip_getSubmitNodeStorage();
      FeatureRenderDispatcher oldWorldFeatureRenderDispatcher = ieWorldRenderer.ip_getFeatureRenderDispatcher();
      ParticlesRenderState oldParticlesRenderState = ieWorldRenderer.ip_getParticlesRenderState();
      LevelTargetBundle targets = ieWorldRenderer.ip_getTargets();
      ResourceHandle<RenderTarget> oldTargetMain = targets.main;
      ResourceHandle<RenderTarget> oldTargetTranslucent = targets.translucent;
      ResourceHandle<RenderTarget> oldTargetItemEntity = targets.itemEntity;
      ResourceHandle<RenderTarget> oldTargetParticles = targets.particles;
      ResourceHandle<RenderTarget> oldTargetWeather = targets.weather;
      ResourceHandle<RenderTarget> oldTargetClouds = targets.clouds;
      ResourceHandle<RenderTarget> oldTargetEntityOutline = targets.entityOutline;
      GpuBufferSlice oldShaderFog = RenderSystem.getShaderFog();
      GpuBufferSlice oldShaderLights = RenderSystem.getShaderLights();
      Vec3 oldFogColor = RenderStates.currentFogColor;
      ObjectArrayList<RenderSection> newChunkInfoList = VisibleSectionDiscovery.takeList();
      ((IEWorldRenderer)oldWorldRenderer).portal_setChunkInfoList(newChunkInfoList);
      Object irisPipeline = IrisInterface.invoker.getPipeline(worldRenderer);
      ((IEMinecraftClient)client).ip_setWorldRenderer(worldRenderer);
      client.level = newWorld;
      ieGameRenderer.ip_setLightmapTextureManager(helper.lightmapTexture);
      client.player.noPhysics = true;
      ieGameRenderer.ip_setDoRenderHand(doRenderHand);
      FogRendererContext.swappingManager.pushSwapping(newDimension);
      ((IEParticleManager)client.particleEngine).ip_setWorld(newWorld);
      if (BlockManipulationClient.remotePointedDim == newDimension) {
         client.hitResult = BlockManipulationClient.remoteHitResult;
      }

      if (!PortalRendering.shouldRenderHitResult()) {
         client.hitResult = null;
      }

      ieGameRenderer.ip_setCamera(newCamera);
      MyGameRenderer.NestedRenderContext context = acquireContext();
      ieGameRenderer.ip_setLevelRenderState(context.levelRenderState);
      ieGameRenderer.ip_setSubmitNodeStorage(context.submitNodeStorage);
      ieGameRenderer.ip_setFeatureRenderDispatcher(context.featureRenderDispatcher);
      ieGameRenderer.ip_setFogRenderer(acquireFogRenderer());
      ieWorldRenderer.ip_setLevelRenderState(context.levelRenderState);
      ieWorldRenderer.ip_setSubmitNodeStorage(context.submitNodeStorage);
      ieWorldRenderer.ip_setFeatureRenderDispatcher(context.featureRenderDispatcher);
      ieWorldRenderer.ip_setParticlesRenderState(context.particlesRenderState);
      RenderBuffers newRenderBuffers = null;
      if (IPGlobal.useSecondaryEntityVertexConsumer) {
         newRenderBuffers = context.renderBuffers;
         if (newRenderBuffers != null) {
            ieWorldRenderer.ip_setRenderBuffers(newRenderBuffers);
            ((IEMinecraftClient)client).ip_setRenderBuffers(newRenderBuffers);
            ((IESectionRenderDispatcher)worldRenderer.getSectionRenderDispatcher()).ip_setFixedBuffers(newRenderBuffers.fixedBufferPack());
         } else {
            client.renderBuffers().bufferSource().endBatch();
         }
      }

      Object newSodiumContext = SodiumInterface.invoker.createNewContext(renderDistance);
      SodiumInterface.invoker.switchContextWithCurrentWorldRenderer(newSodiumContext);
      IERenderSystem.ip_setModelViewStack(new Matrix4fStack(16));
      IrisInterface.invoker.setPipeline(worldRenderer, null);
      client.gameRenderer.updateCamera(client.getDeltaTracker());
      updateGlobalSettingsUniform(client.gameRenderer.getMainCamera());
      if (!RenderStates.isDimensionRendered(newDimension)) {
         helper.lightmapTexture.updateLightTexture(0.0F);
      }

      client.gameRenderer.getLighting().updateLevel(newWorld.dimensionType().cardinalLightType());
      Object nestedRenderToken = ClientPlatform.get().beforeNestedWorldRender(oldWorldRenderer);
      Object[] dhRenderState = DhRenderStateGuard.snapshot();
      invokeWrapper.accept(() -> {
         Profiler.get().push("render_portal_content");
         client.gameRenderer.renderLevel(client.getDeltaTracker());
         Profiler.get().pop();
      });
      DhRenderStateGuard.restore(dhRenderState);
      ClientPlatform.get().afterNestedWorldRender(oldWorldRenderer, nestedRenderToken);
      SodiumInterface.invoker.switchContextWithCurrentWorldRenderer(newSodiumContext);
      ((IEMinecraftClient)client).ip_setWorldRenderer(oldWorldRenderer);
      client.level = oldWorld;
      ieGameRenderer.ip_setLightmapTextureManager(oldLightmap);
      client.player.noPhysics = oldNoClip;
      ieGameRenderer.ip_setDoRenderHand(oldDoRenderHand);
      ((IEParticleManager)client.particleEngine).ip_setWorld(oldWorld);
      client.hitResult = oldCrosshairTarget;
      ieGameRenderer.ip_setCamera(oldCamera);
      updateGlobalSettingsUniform(oldCamera);
      FogRendererContext.swappingManager.popSwapping();
      ((IEWorldRenderer)oldWorldRenderer).portal_setChunkInfoList(oldChunkInfoList);
      VisibleSectionDiscovery.returnList(newChunkInfoList);
      ieWorldRenderer.ip_setRenderBuffers(oldRenderBuffers);
      ((IEMinecraftClient)client).ip_setRenderBuffers(oldClientRenderBuffers);
      ((IESectionRenderDispatcher)worldRenderer.getSectionRenderDispatcher()).ip_setFixedBuffers(oldSectionRenderDispatcherFixedBuffers);
      ieGameRenderer.ip_setLevelRenderState(oldGameLevelRenderState);
      ieGameRenderer.ip_setSubmitNodeStorage(oldGameSubmitNodeStorage);
      ieGameRenderer.ip_setFeatureRenderDispatcher(oldGameFeatureRenderDispatcher);
      ieGameRenderer.ip_setFogRenderer(oldFogRenderer);
      ieWorldRenderer.ip_setLevelRenderState(oldWorldLevelRenderState);
      ieWorldRenderer.ip_setSubmitNodeStorage(oldWorldSubmitNodeStorage);
      ieWorldRenderer.ip_setFeatureRenderDispatcher(oldWorldFeatureRenderDispatcher);
      ieWorldRenderer.ip_setParticlesRenderState(oldParticlesRenderState);
      targets.main = oldTargetMain;
      targets.translucent = oldTargetTranslucent;
      targets.itemEntity = oldTargetItemEntity;
      targets.particles = oldTargetParticles;
      targets.weather = oldTargetWeather;
      targets.clouds = oldTargetClouds;
      targets.entityOutline = oldTargetEntityOutline;
      returnContext(context);
      if (oldProjectionMatrix != null) {
         ieGameRenderer.ip_resetProjectionMatrix(oldProjectionMatrix, oldProjectionType);
      }

      RenderSystem.setShaderFog(oldShaderFog);
      RenderSystem.setShaderLights(oldShaderLights);
      RenderStates.currentFogColor = oldFogColor;
      IERenderSystem.ip_setModelViewStack(oldModelViewStack);
      IrisInterface.invoker.setPipeline(worldRenderer, irisPipeline);
      client.getEntityRenderDispatcher().prepare(oldCamera, client.crosshairPickEntity);
      client.getBlockEntityRenderDispatcher().prepare(oldCamera);
      CHelper.checkGlError();
      client.smartCull = true;
   }

   @IPVanillaCopy
   private static void updateGlobalSettingsUniform(Camera camera) {
      ClientLevel level = client.level;
      client.gameRenderer
         .getGlobalSettingsUniform()
         .update(
            client.getWindow().getWidth(),
            client.getWindow().getHeight(),
            client.options.glintStrength().get(),
            level == null ? 0L : level.getGameTime(),
            client.getDeltaTracker(),
            client.options.getMenuBackgroundBlurriness(),
            camera,
            client.options.textureFiltering().get() == TextureFilteringMethod.RGSS
         );
   }

   @IPVanillaCopy
   public static void resetFogState() {
      FogRenderer fogRenderer = ((IEGameRenderer)client.gameRenderer).ip_getFogRenderer();
      RenderSystem.setShaderFog(fogRenderer.getBuffer(FogMode.WORLD));
   }

   public static void updateFogColor() {
   }

   @IPVanillaCopy
   public static void resetDiffuseLighting() {
      ClientLevel world = client.level;
      assert world != null;
      Lighting lighting = client.gameRenderer.getLighting();
      lighting.updateLevel(world.dimensionType().cardinalLightType());
      lighting.setupFor(Entry.LEVEL);
   }

   public static class NestedRenderContext {
      @Nullable
      public final RenderBuffers renderBuffers;
      public final SubmitNodeStorage submitNodeStorage;
      public final FeatureRenderDispatcher featureRenderDispatcher;
      public final LevelRenderState levelRenderState;
      public final ParticlesRenderState particlesRenderState;

      NestedRenderContext(boolean useSecondaryRenderBuffers) {
         this.renderBuffers = useSecondaryRenderBuffers ? new RenderBuffers(0) : null;
         RenderBuffers buffers = this.renderBuffers != null ? this.renderBuffers : MyGameRenderer.client.renderBuffers();
         this.submitNodeStorage = new SubmitNodeStorage();
         this.featureRenderDispatcher = new FeatureRenderDispatcher(
            this.submitNodeStorage,
            MyGameRenderer.client.getBlockRenderer(),
            buffers.bufferSource(),
            MyGameRenderer.client.getAtlasManager(),
            buffers.outlineBufferSource(),
            buffers.crumblingBufferSource(),
            MyGameRenderer.client.font
         );
         this.levelRenderState = new LevelRenderState();
         this.particlesRenderState = new ParticlesRenderState();
      }

      void close() {
         this.featureRenderDispatcher.close();
      }
   }
}
