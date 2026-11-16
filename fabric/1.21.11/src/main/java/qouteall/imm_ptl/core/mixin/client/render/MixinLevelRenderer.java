package qouteall.imm_ptl.core.mixin.client.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.platform.Lighting.Entry;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderPass.Draw;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat.IndexType;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.DynamicUniforms.ChunkSectionInfo;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionBuffers;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.client.renderer.debug.GameTestBlockHighlightRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.client.renderer.state.ParticlesRenderState;
import net.minecraft.client.renderer.state.WeatherRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface;
import qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumInterface;
import qouteall.imm_ptl.core.ducks.IEWorldRenderer;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;
import qouteall.imm_ptl.core.render.CrossPortalEntityRenderer;
import qouteall.imm_ptl.core.render.FrontClipping;
import qouteall.imm_ptl.core.render.ImmPtlViewArea;
import qouteall.imm_ptl.core.render.MyGameRenderer;
import qouteall.imm_ptl.core.render.MyRenderHelper;
import qouteall.imm_ptl.core.render.VisibleSectionDiscovery;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.q_misc_util.Helper;

@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer implements IEWorldRenderer {
   @Shadow
   private ClientLevel level;
   @Shadow
   @Final
   private EntityRenderDispatcher entityRenderDispatcher;
   @Shadow
   @Final
   private Minecraft minecraft;
   @Shadow
   private ViewArea viewArea;
   @Mutable
   @Shadow
   @Final
   private RenderBuffers renderBuffers;
   @Shadow
   private int lastViewDistance;
   @Shadow
   @Nullable
   private SectionRenderDispatcher sectionRenderDispatcher;
   @Shadow
   @Final
   @Mutable
   private ObjectArrayList<RenderSection> visibleSections;
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
   private ParticlesRenderState particlesRenderState;
   @Shadow
   @Final
   private LevelTargetBundle targets;
   @Unique
   private int ip_nestedRenderSerialAtPrepare = 0;

   @Shadow
   public abstract boolean isSectionCompiledAndVisible(BlockPos var1);

   @Inject(method = "prepareChunkRenders", at = @At("RETURN"))
   private void onPrepareChunkRenders(Matrix4fc modelView, double camX, double camY, double camZ, CallbackInfoReturnable<ChunkSectionsToRender> cir) {
      this.ip_nestedRenderSerialAtPrepare = MyGameRenderer.nestedRenderSerial;
   }

   @Unique
   @IPVanillaCopy
   private ChunkSectionsToRender ip_prepareChunkRenders(Matrix4fc modelView, double camX, double camY, double camZ) {
      ObjectListIterator<RenderSection> iterator = this.visibleSections.listIterator(0);
      EnumMap<ChunkSectionLayer, List<Draw<GpuBufferSlice[]>>> drawsPerLayer = new EnumMap<>(ChunkSectionLayer.class);
      int maxIndicesRequired = 0;

      for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
         drawsPerLayer.put(layer, new ArrayList<>());
      }

      List<ChunkSectionInfo> sectionInfos = new ArrayList<>();
      GpuTextureView atlasView = this.minecraft.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getTextureView();
      int atlasWidth = atlasView.getWidth(0);
      int atlasHeight = atlasView.getHeight(0);

      while (iterator.hasNext()) {
         RenderSection section = iterator.next();
         SectionMesh sectionMesh = section.getSectionMesh();
         BlockPos origin = section.getRenderOrigin();
         long now = Util.getMillis();
         int infoIndex = -1;

         for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
            SectionBuffers buffers = sectionMesh.getBuffers(layer);
            if (buffers != null) {
               if (infoIndex == -1) {
                  infoIndex = sectionInfos.size();
                  sectionInfos.add(
                     new ChunkSectionInfo(
                        new Matrix4f(modelView), origin.getX(), origin.getY(), origin.getZ(), section.getVisibility(now), atlasWidth, atlasHeight
                     )
                  );
               }

               GpuBuffer indexBuffer;
               IndexType indexType;
               if (buffers.getIndexBuffer() == null) {
                  if (buffers.getIndexCount() > maxIndicesRequired) {
                     maxIndicesRequired = buffers.getIndexCount();
                  }

                  indexBuffer = null;
                  indexType = null;
               } else {
                  indexBuffer = buffers.getIndexBuffer();
                  indexType = buffers.getIndexType();
               }

               int sliceIndex = infoIndex;
               drawsPerLayer.get(layer)
                  .add(
                     new Draw<>(
                        0,
                        buffers.getVertexBuffer(),
                        indexBuffer,
                        indexType,
                        0,
                        buffers.getIndexCount(),
                        (slicesx, uploader) -> uploader.upload("ChunkSection", slicesx[sliceIndex])
                     )
                  );
            }
         }
      }

      GpuBufferSlice[] slices = RenderSystem.getDynamicUniforms().writeChunkSections(sectionInfos.toArray(new ChunkSectionInfo[0]));
      return new ChunkSectionsToRender(atlasView, drawsPerLayer, maxIndicesRequired, slices);
   }

   @WrapOperation(
      method = {"lambda$addMainPass$1", "method_62214"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;renderGroup(Lnet/minecraft/client/renderer/chunk/ChunkSectionLayerGroup;Lcom/mojang/blaze3d/textures/GpuSampler;)V"
      ),
      require = 1
   )
   private void wrapRenderSectionLayerGroup(
      ChunkSectionsToRender instance,
      ChunkSectionLayerGroup group,
      GpuSampler sampler,
      Operation<Void> original,
      @Local(argsOnly = true) Matrix4f modelView,
      @Local LocalRef<ChunkSectionsToRender> chunkSectionsToRender
   ) {
      if (MyGameRenderer.nestedRenderSerial != this.ip_nestedRenderSerialAtPrepare && !SodiumInterface.invoker.isSodiumPresent()) {
         Vec3 cameraPos = this.levelRenderState.cameraRenderState.pos;
         instance = this.ip_prepareChunkRenders(modelView, cameraPos.x, cameraPos.y, cameraPos.z);
         chunkSectionsToRender.set(instance);
         this.ip_nestedRenderSerialAtPrepare = MyGameRenderer.nestedRenderSerial;
      }

      this.onBeforeRenderingLayer(modelView);
      RenderStates.isRenderingTerrainLayer = true;

      try {
         original.call(new Object[]{instance, group, sampler});
      } finally {
         RenderStates.isRenderingTerrainLayer = false;
      }

      this.onAfterRenderingLayer();
      if (group == ChunkSectionLayerGroup.OPAQUE) {
         this.onAfterCutoutRendering(modelView);
      }
   }

   private void onAfterCutoutRendering(Matrix4f modelView) {
      CrossPortalEntityRenderer.onBeginRenderingEntitiesAndBlockEntities(modelView);
   }

   private void onBeforeRenderingLayer(Matrix4f modelView) {
      if (PortalRendering.isRendering()) {
         FrontClipping.setupInnerClipping(PortalRendering.getActiveClippingPlane(), modelView, -0.01);
         if (PortalRendering.isRenderingOddNumberOfMirrors()) {
            MyRenderHelper.applyMirrorFaceCulling();
         }

         if (IPGlobal.enableDepthClampForPortalRendering) {
            CHelper.enableDepthClamp();
         }
      }
   }

   private void onAfterRenderingLayer() {
      if (PortalRendering.isRendering()) {
         FrontClipping.disableClipping();
         MyRenderHelper.recoverFaceCulling();
         if (IPGlobal.enableDepthClampForPortalRendering) {
            CHelper.disableDepthClamp();
         }
      }
   }

   @Inject(
      method = {"lambda$addMainPass$1", "method_62214"},
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Sheets;translucentItemSheet()Lnet/minecraft/client/renderer/rendertype/RenderType;"),
      require = 1
   )
   private void onMyBeforeTranslucentRendering(CallbackInfo ci, @Local(argsOnly = true) Matrix4f modelView) {
      IPCGlobal.renderer.onBeforeTranslucentRendering(modelView);
      MyGameRenderer.updateFogColor();
      MyGameRenderer.resetFogState();
      MyGameRenderer.resetDiffuseLighting();
      FrontClipping.disableClipping();
   }

   @Inject(
      method = {"lambda$addMainPass$1", "method_62214"},
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endLastBatch()V", ordinal = 0, shift = Shift.AFTER),
      require = 1
   )
   @IPVanillaCopy
   private void onEndRenderingEntities(CallbackInfo ci, @Local PoseStack poseStack) {
      CrossPortalEntityRenderer.onEndRenderingEntitiesAndBlockEntities(poseStack);
   }

   @WrapOperation(
      method = {"lambda$addMainPass$1", "method_62214"},
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;finalizeGizmoCollection()V"),
      require = 1
   )
   private void wrapFinalizeGizmoCollection(LevelRenderer instance, Operation<Void> original) {
      if (!WorldRenderInfo.isRendering()) {
         original.call(new Object[]{instance});
      }
   }

   @WrapOperation(
      method = {"lambda$addMainPass$1", "method_62214"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/gizmos/DrawableGizmoPrimitives;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/renderer/state/CameraRenderState;Lorg/joml/Matrix4f;)V"
      ),
      require = 1
   )
   private void wrapRenderGizmos(
      DrawableGizmoPrimitives instance,
      PoseStack poseStack,
      MultiBufferSource bufferSource,
      CameraRenderState cameraRenderState,
      Matrix4f projection,
      Operation<Void> original
   ) {
      if (!WorldRenderInfo.isRendering()) {
         original.call(new Object[]{instance, poseStack, bufferSource, cameraRenderState, projection});
      }
   }

   @WrapOperation(
      method = "renderLevel",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/debug/DebugRenderer;emitGizmos(Lnet/minecraft/client/renderer/culling/Frustum;DDDF)V"
      )
   )
   private void wrapEmitDebugGizmos(DebugRenderer instance, Frustum frustum, double x, double y, double z, float partialTick, Operation<Void> original) {
      if (!WorldRenderInfo.isRendering()) {
         original.call(new Object[]{instance, frustum, x, y, z, partialTick});
      }
   }

   @WrapOperation(
      method = "renderLevel",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/debug/GameTestBlockHighlightRenderer;emitGizmos()V")
   )
   private void wrapEmitGameTestGizmos(GameTestBlockHighlightRenderer instance, Operation<Void> original) {
      if (!WorldRenderInfo.isRendering()) {
         original.call(new Object[]{instance});
      }
   }

   @Inject(method = "addLateDebugPass", at = @At("HEAD"), cancellable = true)
   private void onAddLateDebugPass(CallbackInfo ci) {
      if (WorldRenderInfo.isRendering()) {
         ci.cancel();
      }
   }

   @Inject(method = "renderLevel", at = @At("RETURN"))
   private void onAfterTranslucentRendering(
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
      CallbackInfo ci
   ) {
      IPCGlobal.renderer.onAfterTranslucentRendering(modelView);
      this.minecraft.gameRenderer.getLighting().setupFor(Entry.LEVEL);
   }

   @WrapOperation(
      method = "renderLevel",
      at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/framegraph/FramePass;executes(Ljava/lang/Runnable;)V", ordinal = 0)
   )
   private void wrapClearPass(FramePass pass, Runnable runnable, Operation<Void> original) {
      original.call(new Object[]{pass, (Runnable)() -> {
         if (!IPCGlobal.renderer.replaceFrameBufferClearing()) {
            runnable.run();
         }
      }});
   }

   @Redirect(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;pollLightUpdates()V"))
   private void redirectRunQueuedChunkUpdates(ClientLevel world) {
      ClientWorldLoader.withSwitchedWorld(world, world::pollLightUpdates);
   }

   @Inject(
      method = {
            "addSkyPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/Camera;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V",
            "addSkyPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/Camera;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Matrix4f;)V"
      },
      at = @At("HEAD"),
      cancellable = true,
      require = 1
   )
   private void onRenderSkyBegin(CallbackInfo ci) {
      if (WorldRenderInfo.isRendering() && !WorldRenderInfo.getTopRenderInfo().doRenderSky && !IrisInterface.invoker.isShaders()) {
         ci.cancel();
      }
   }

   @WrapOperation(
      method = {
            "addSkyPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/Camera;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V",
            "addSkyPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/Camera;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Matrix4f;)V"
      },
      at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/framegraph/FramePass;executes(Ljava/lang/Runnable;)V"),
      require = 1
   )
   private void wrapSkyPass(FramePass pass, Runnable runnable, Operation<Void> original) {
      original.call(new Object[]{pass, (Runnable)() -> {
         if (PortalRendering.isRenderingOddNumberOfMirrors()) {
            MyRenderHelper.applyMirrorFaceCulling();
         }

         runnable.run();
         MyRenderHelper.recoverFaceCulling();
      }});
   }

   @WrapOperation(
      method = {"lambda$addWeatherPass$4", "method_62216"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/WeatherEffectRenderer;render(Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/state/WeatherRenderState;)V"
      ),
      require = 1
   )
   private void wrapRenderWeather(
      WeatherEffectRenderer instance, MultiBufferSource bufferSource, Vec3 cameraPos, WeatherRenderState weatherRenderState, Operation<Void> original
   ) {
      if (PortalRendering.isRendering()) {
         FrontClipping.setupInnerClipping(PortalRendering.getActiveClippingPlane(), new Matrix4f(RenderSystem.getModelViewMatrix()), 0.0);
         RenderStates.isRenderingPortalWeather = true;
      }

      original.call(new Object[]{instance, bufferSource, cameraPos, weatherRenderState});
      if (PortalRendering.isRendering()) {
         FrontClipping.disableClipping();
         RenderStates.isRenderingPortalWeather = false;
      }
   }

   @Inject(method = "cullTerrain", at = @At("HEAD"), cancellable = true)
   private void onSetupTerrainBegin(Camera camera, Frustum frustum, boolean spectator, CallbackInfo ci) {
      if (WorldRenderInfo.isRendering() && this.level.dimension() != RenderStates.originalPlayerDimension) {
         this.sectionRenderDispatcher.setCameraPosition(camera.position());
      }

      if (this.ip_allowOverrideTerrainSetup() && WorldRenderInfo.isRendering()) {
         Profiler.get().push("ip_terrain_setup");
         VisibleSectionDiscovery.discoverVisibleSections(
            this.level, (ImmPtlViewArea)this.viewArea, camera, new Frustum(frustum).offsetToFullyIncludeCameraCube(8), this.visibleSections
         );
         Profiler.get().pop();
         ci.cancel();
      }
   }

   private boolean ip_allowOverrideTerrainSetup() {
      return !SodiumInterface.invoker.isSodiumPresent() && !IrisInterface.invoker.isRenderingShadowMap();
   }

   @Inject(method = "cullTerrain", at = @At("RETURN"), cancellable = true)
   private void onSetupTerrainEnd(Camera camera, Frustum frustum, boolean spectator, CallbackInfo ci) {
      if (!WorldRenderInfo.isRendering() && this.ip_allowOverrideTerrainSetup()) {
         if (MyGameRenderer.vanillaTerrainSetupOverride > 0) {
            MyGameRenderer.vanillaTerrainSetupOverride--;
            Profiler.get().push("ip_terrain_setup");
            VisibleSectionDiscovery.discoverVisibleSections(
               this.level, (ImmPtlViewArea)this.viewArea, camera, new Frustum(frustum).offsetToFullyIncludeCameraCube(8), this.visibleSections
            );
            Profiler.get().pop();
         } else if (IPGlobal.alwaysOverrideTerrainSetup) {
            Profiler.get().push("ip_terrain_setup_debug");
            VisibleSectionDiscovery.discoverVisibleSections(
               this.level, (ImmPtlViewArea)this.viewArea, camera, new Frustum(frustum).offsetToFullyIncludeCameraCube(8), this.visibleSections
            );
            Profiler.get().pop();
         }
      }
   }

   @ModifyVariable(method = "cullTerrain", at = @At("HEAD"), argsOnly = true, ordinal = 0)
   private boolean modifyIsSpectator(boolean value) {
      return WorldRenderInfo.isRendering() ? true : value;
   }

   @Redirect(
      method = "allChanged",
      at = @At(
         value = "NEW",
         target = "(Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;Lnet/minecraft/world/level/Level;ILnet/minecraft/client/renderer/LevelRenderer;)Lnet/minecraft/client/renderer/ViewArea;"
      )
   )
   private ViewArea redirectConstructingBuildChunkStorage(SectionRenderDispatcher chunkBuilder_1, Level world_1, int int_1, LevelRenderer worldRenderer_1) {
      return IPCGlobal.useHackedChunkRenderDispatcher
         ? new ImmPtlViewArea(chunkBuilder_1, world_1, int_1, worldRenderer_1)
         : new ViewArea(chunkBuilder_1, world_1, int_1, worldRenderer_1);
   }

   @Inject(method = "extractVisibleEntities", at = @At("HEAD"))
   private void onBeginExtractingEntities(Camera camera, Frustum frustum, DeltaTracker deltaTracker, LevelRenderState levelRenderState, CallbackInfo ci) {
      CrossPortalEntityRenderer.onBeginExtractingEntities();
   }

   @WrapOperation(
      method = "extractVisibleEntities",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z"
      )
   )
   private boolean wrapShouldRenderForDeferring(
      EntityRenderDispatcher dispatcher,
      Entity entity,
      Frustum frustum,
      double cameraX,
      double cameraY,
      double cameraZ,
      Operation<Boolean> original,
      @Local(argsOnly = true) Camera camera
   ) {
      boolean result = (Boolean)original.call(new Object[]{dispatcher, entity, frustum, cameraX, cameraY, cameraZ});
      if (result && CrossPortalEntityRenderer.shouldDeferEntityRendering(entity) && this.ip_vanillaWouldExtract(entity, camera)) {
         CrossPortalEntityRenderer.deferEntity(entity);
         return false;
      } else {
         return result;
      }
   }

   @WrapOperation(
      method = "extractVisibleEntities",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;hasIndirectPassenger(Lnet/minecraft/world/entity/Entity;)Z")
   )
   private boolean wrapHasIndirectPassengerForDeferring(Entity entity, Entity passenger, Operation<Boolean> original) {
      return CrossPortalEntityRenderer.isLastDeferredEntity(entity) ? false : (Boolean)original.call(new Object[]{entity, passenger});
   }

   @IPVanillaCopy
   private boolean ip_vanillaWouldExtract(Entity entity, Camera camera) {
      BlockPos blockPos = entity.blockPosition();
      return (this.level.isOutsideBuildHeight(blockPos.getY()) || this.isSectionCompiledAndVisible(blockPos))
         && (entity != camera.entity() || camera.isDetached() || camera.entity() instanceof LivingEntity livingEntity && livingEntity.isSleeping())
         && (!(entity instanceof LocalPlayer) || camera.entity() == entity);
   }

   @Inject(method = "allChanged", at = @At("HEAD"), cancellable = true)
   private void onReloadStarted(CallbackInfo ci) {
      if (WorldRenderInfo.isRendering()) {
         Helper.log("world renderer reloading cancelled during portal rendering");
         ci.cancel();
      }
   }

   @Inject(method = "allChanged", at = @At("TAIL"))
   private void onReloadFinished(CallbackInfo ci) {
      LevelRenderer this_ = (LevelRenderer)(Object)this;
      if (!ClientWorldLoader.getIsCreatingClientWorld()) {
         Validate.isTrue(Minecraft.getInstance().levelRenderer == this_);
         ClientWorldLoader._onWorldRendererReloaded();
      }
   }

   @Inject(method = "isSectionCompiledAndVisible", at = @At("HEAD"), cancellable = true)
   private void onIsChunkCompiled(BlockPos blockPos, CallbackInfoReturnable<Boolean> cir) {
      if (PortalRendering.isRendering() && !SodiumInterface.invoker.isSodiumPresent() && this.viewArea instanceof ImmPtlViewArea immPtlViewArea) {
         cir.setReturnValue(this.ip_isChunkCompiled(immPtlViewArea, blockPos));
      }
   }

   private boolean ip_isChunkCompiled(ImmPtlViewArea immPtlViewArea, BlockPos blockPos) {
      SectionPos sectionPos = SectionPos.of(blockPos);
      RenderSection renderChunk = immPtlViewArea.rawGet(sectionPos.x(), sectionPos.y(), sectionPos.z());
      return renderChunk != null && renderChunk.sectionMesh.get() != CompiledSectionMesh.UNCOMPILED;
   }

   @Override
   public EntityRenderDispatcher ip_getEntityRenderDispatcher() {
      return this.entityRenderDispatcher;
   }

   @Override
   public ViewArea ip_getBuiltChunkStorage() {
      return this.viewArea;
   }

   @Override
   public void ip_renderSingleEntity(
      Entity entity, double cameraX, double cameraY, double cameraZ, float partialTick, PoseStack matrixStack, BufferSource bufferSource
   ) {
      EntityRenderState renderState = this.entityRenderDispatcher.extractEntity(entity, partialTick);
      this.entityRenderDispatcher
         .submit(
            renderState,
            this.levelRenderState.cameraRenderState,
            renderState.x - cameraX,
            renderState.y - cameraY,
            renderState.z - cameraZ,
            matrixStack,
            this.submitNodeStorage
         );
      this.featureRenderDispatcher.renderAllFeatures();
   }

   @Override
   public RenderBuffers ip_getRenderBuffers() {
      return this.renderBuffers;
   }

   @Override
   public void ip_setRenderBuffers(RenderBuffers arg) {
      this.renderBuffers = arg;
   }

   @Override
   public void portal_fullyDispose() {
      this.level = null;
   }

   @Override
   public void portal_setChunkInfoList(ObjectArrayList<RenderSection> arg) {
      this.visibleSections = arg;
   }

   @Override
   public ObjectArrayList<RenderSection> portal_getChunkInfoList() {
      return this.visibleSections;
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
   public ParticlesRenderState ip_getParticlesRenderState() {
      return this.particlesRenderState;
   }

   @Override
   public void ip_setParticlesRenderState(ParticlesRenderState arg) {
      this.particlesRenderState = arg;
   }

   @Override
   public LevelTargetBundle ip_getTargets() {
      return this.targets;
   }
}
