package qouteall.imm_ptl.core.mixin.client.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.PerspectiveProjectionMatrixBuffer;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionfc;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.ducks.IEGameRenderer;
import qouteall.imm_ptl.core.portal.animation.ClientPortalAnimationManagement;
import qouteall.imm_ptl.core.portal.animation.StableClientTimer;
import qouteall.imm_ptl.core.render.CrossPortalViewRendering;
import qouteall.imm_ptl.core.render.GuiPortalRendering;
import qouteall.imm_ptl.core.render.MyGameRenderer;
import qouteall.imm_ptl.core.render.MyRenderHelper;
import qouteall.imm_ptl.core.render.TransformationManager;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.renderer.PortalRenderer;
import qouteall.imm_ptl.core.teleportation.ClientTeleportationManager;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer implements IEGameRenderer {
   @Shadow
   @Final
   @Mutable
   private LightTexture lightTexture;
   @Shadow
   @Final
   @Mutable
   private Camera mainCamera;
   @Shadow
   @Final
   private Minecraft minecraft;
   @Shadow
   @Final
   private static Logger LOGGER;
   @Shadow
   @Final
   @Mutable
   private LevelRenderState levelRenderState;
   @Shadow
   @Final
   @Mutable
   private SubmitNodeStorage submitNodeStorage;
   @Shadow
   @Final
   @Mutable
   private FeatureRenderDispatcher featureRenderDispatcher;
   @Shadow
   @Final
   @Mutable
   private FogRenderer fogRenderer;
   @Shadow
   @Final
   private PerspectiveProjectionMatrixBuffer levelProjectionMatrixBuffer;
   @Unique
   private boolean ip_doRenderHand = true;
   private static boolean portal_isRenderingHand = false;

   @Shadow
   protected abstract void bobView(PoseStack var1, float var2);

   @Inject(method = "render", at = @At("HEAD"))
   private void onFarBeforeRendering(DeltaTracker deltaTracker, boolean renderWorldIn, CallbackInfo ci) {
      Profiler.get().push("ip_pre_total_render");
      IPGlobal.PRE_TOTAL_RENDER_TASK_LIST.processTasks();
      Profiler.get().pop();
      if (this.minecraft.level != null) {
         if (renderWorldIn) {
            Profiler.get().push("ip_pre_render");
            float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
            RenderStates.updatePreRenderInfo(partialTick);
            StableClientTimer.update(this.minecraft.level.getGameTime(), partialTick);
            ClientPortalAnimationManagement.update();
            ClientTeleportationManager.manageTeleportation(false);
            IPGlobal.PRE_GAME_RENDER_EVENT.invoker().run();
            if (IPCGlobal.earlyRemoteUpload) {
               MyRenderHelper.earlyRemoteUpload();
            }

            Profiler.get().pop();
            RenderStates.frameIndex++;
         }
      }
   }

   @Inject(
      method = "render",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;)V")
   )
   private void onBeforeRenderingCenter(DeltaTracker deltaTracker, boolean bl, CallbackInfo ci) {
      PortalRenderer.switchToCorrectRenderer();
      IPCGlobal.renderer.prepareRendering();
   }

   @Inject(
      method = "render",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;)V", shift = Shift.AFTER)
   )
   private void onAfterRenderingCenter(DeltaTracker deltaTracker, boolean bl, CallbackInfo ci) {
      IPCGlobal.renderer.finishRendering();
      RenderStates.onTotalRenderEnd();
      GuiPortalRendering._onGameRenderEnd();
      MyGameRenderer.onFrameEnd();
      if (IPCGlobal.lateClientLightUpdate) {
         Profiler.get().push("ip_late_update_light");
         MyRenderHelper.lateUpdateLight();
         Profiler.get().pop();
      }
   }

   @Redirect(
      method = "render",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;)V")
   )
   private void redirectRenderingWorld(GameRenderer gameRenderer, DeltaTracker deltaTracker) {
      if (!CrossPortalViewRendering.renderCrossPortalView()) {
         gameRenderer.renderLevel(deltaTracker);
      }
   }

   @Inject(method = "renderLevel", at = @At("TAIL"))
   private void onRenderCenterEnded(DeltaTracker deltaTracker, CallbackInfo ci) {
      IPCGlobal.renderer.onHandRenderingEnded();
   }

   @WrapOperation(
      method = "renderLevel",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V"
      )
   )
   private void wrapRenderLevel(
      LevelRenderer instance,
      GraphicsResourceAllocator resourceAllocator,
      DeltaTracker deltaTracker,
      boolean renderBlockOutline,
      Camera camera,
      Matrix4f modelView,
      Matrix4f projection,
      Matrix4f cullingProjection,
      GpuBufferSlice fog,
      Vector4f fogColor,
      boolean renderSky,
      Operation<Void> original
   ) {
      original.call(
         new Object[]{
            instance, resourceAllocator, deltaTracker, renderBlockOutline, camera, modelView, projection, cullingProjection, fog, fogColor, renderSky
         }
      );
      IPCGlobal.renderer.onBeforeHandRendering(modelView);
   }

   @Inject(method = "Lnet/minecraft/client/renderer/GameRenderer;resize(II)V", at = @At("RETURN"))
   private void onOnResized(int int_1, int int_2, CallbackInfo ci) {
      if (ClientWorldLoader.getIsInitialized()) {
         ClientWorldLoader.WORLD_RENDERER_MAP
            .values()
            .stream()
            .filter(worldRenderer -> worldRenderer != this.minecraft.levelRenderer)
            .forEach(worldRenderer -> worldRenderer.resize(int_1, int_2));
      }
   }

   @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
   private void onRenderHandBegins(float partialTick, boolean sleeping, Matrix4f modelView, CallbackInfo ci) {
      if (!this.ip_doRenderHand) {
         ci.cancel();
      } else {
         portal_isRenderingHand = true;
      }
   }

   @Inject(method = "renderItemInHand", at = @At("RETURN"))
   private void onRenderHandEnds(float partialTick, boolean sleeping, Matrix4f modelView, CallbackInfo ci) {
      portal_isRenderingHand = false;
   }

   @WrapOperation(
      method = "renderLevel",
      at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearDepthTexture(Lcom/mojang/blaze3d/textures/GpuTexture;D)V")
   )
   private void wrapClearDepthForHand(CommandEncoder encoder, GpuTexture depthTexture, double depth, Operation<Void> original) {
      if (this.ip_doRenderHand) {
         original.call(new Object[]{encoder, depthTexture, depth});
      }
   }

   @WrapOperation(
      method = "renderLevel",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/ScreenEffectRenderer;renderScreenEffect(ZFLnet/minecraft/client/renderer/SubmitNodeCollector;)V"
      )
   )
   private void wrapRenderScreenEffect(
      ScreenEffectRenderer instance, boolean sleeping, float partialTick, SubmitNodeCollector collector, Operation<Void> original
   ) {
      if (this.ip_doRenderHand) {
         original.call(new Object[]{instance, sleeping, partialTick, collector});
      }
   }

   @ModifyArg(method = "bobView", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"), index = 0)
   private float modifyBobViewTranslateX(float f) {
      return portal_isRenderingHand ? f : (float)(f * RenderStates.getViewBobbingOffsetMultiplier());
   }

   @ModifyArg(method = "bobView", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"), index = 1)
   private float modifyBobViewTranslateY(float f) {
      return portal_isRenderingHand ? f : (float)(f * RenderStates.getViewBobbingOffsetMultiplier());
   }

   @ModifyArg(method = "bobView", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"), index = 2)
   private float modifyBobViewTranslateZ(float f) {
      return portal_isRenderingHand ? f : (float)(f * RenderStates.getViewBobbingOffsetMultiplier());
   }

   @Redirect(
      method = "renderLevel",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;getProjectionMatrix(F)Lorg/joml/Matrix4f;", ordinal = 0)
   )
   private Matrix4f redirectGetBasicProjectionMatrix(GameRenderer instance, float fov) {
      if (PortalRendering.isRendering()) {
         if (RenderStates.basicProjectionMatrix != null) {
            return new Matrix4f(RenderStates.basicProjectionMatrix);
         }

         LOGGER.error("[iPortal] Projection matrix state abnormal");
      }

      Matrix4f result = instance.getProjectionMatrix(fov);
      RenderStates.basicProjectionMatrix = new Matrix4f(result);
      return result;
   }

   @WrapOperation(
      method = "renderLevel",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/PerspectiveProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"
      )
   )
   private GpuBufferSlice wrapUploadProjectionMatrix(PerspectiveProjectionMatrixBuffer instance, Matrix4f projectionMatrix, Operation<GpuBufferSlice> original) {
      RenderStates.projectionMatrix = new Matrix4f(projectionMatrix);
      return (GpuBufferSlice)original.call(new Object[]{instance, projectionMatrix});
   }

   @WrapOperation(
      method = "renderLevel",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/fog/FogRenderer;setupFog(Lnet/minecraft/client/Camera;ILnet/minecraft/client/DeltaTracker;FLnet/minecraft/client/multiplayer/ClientLevel;)Lorg/joml/Vector4f;"
      )
   )
   private Vector4f wrapSetupFog(
      FogRenderer instance,
      Camera camera,
      int renderDistance,
      DeltaTracker deltaTracker,
      float darkenWorldAmount,
      ClientLevel level,
      Operation<Vector4f> original
   ) {
      Vector4f color = (Vector4f)original.call(new Object[]{instance, camera, renderDistance, deltaTracker, darkenWorldAmount, level});
      RenderStates.currentFogColor = new Vec3(color.x, color.y, color.z);
      return color;
   }

   @WrapOperation(
      method = "renderLevel",
      at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;rotation(Lorg/joml/Quaternionfc;)Lorg/joml/Matrix4f;", remap = false)
   )
   private Matrix4f wrapCameraTransformation(Matrix4f instance, Quaternionfc quat, Operation<Matrix4f> original) {
      Matrix4f r = (Matrix4f)original.call(new Object[]{instance, quat});
      return TransformationManager.processTransformation(this.mainCamera, r);
   }

   @Override
   public void ip_setLightmapTextureManager(LightTexture manager) {
      this.lightTexture = manager;
   }

   @Override
   public boolean ip_getDoRenderHand() {
      return this.ip_doRenderHand;
   }

   @Override
   public void ip_setDoRenderHand(boolean cond) {
      this.ip_doRenderHand = cond;
   }

   @Override
   public void ip_setCamera(Camera camera_) {
      this.mainCamera = camera_;
   }

   @Override
   public LevelRenderState ip_getLevelRenderState() {
      return this.levelRenderState;
   }

   @Override
   public void ip_setLevelRenderState(LevelRenderState arg) {
      this.levelRenderState = arg;
   }

   @Override
   public SubmitNodeStorage ip_getSubmitNodeStorage() {
      return this.submitNodeStorage;
   }

   @Override
   public void ip_setSubmitNodeStorage(SubmitNodeStorage arg) {
      this.submitNodeStorage = arg;
   }

   @Override
   public FeatureRenderDispatcher ip_getFeatureRenderDispatcher() {
      return this.featureRenderDispatcher;
   }

   @Override
   public void ip_setFeatureRenderDispatcher(FeatureRenderDispatcher arg) {
      this.featureRenderDispatcher = arg;
   }

   @Override
   public FogRenderer ip_getFogRenderer() {
      return this.fogRenderer;
   }

   @Override
   public void ip_setFogRenderer(FogRenderer arg) {
      this.fogRenderer = arg;
   }

   @Override
   public void ip_resetProjectionMatrix(Matrix4f matrix, ProjectionType projectionType) {
      RenderSystem.setProjectionMatrix(this.levelProjectionMatrixBuffer.getBuffer(matrix), projectionType);
      RenderStates.projectionMatrix = new Matrix4f(matrix);
   }
}
