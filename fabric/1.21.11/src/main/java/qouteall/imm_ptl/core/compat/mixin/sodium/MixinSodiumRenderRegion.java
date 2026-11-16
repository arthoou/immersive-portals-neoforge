package qouteall.imm_ptl.core.compat.mixin.sodium;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import java.util.Map;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.gl.device.MultiDrawBatch;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion.DeviceResources;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.q_misc_util.Helper;

@Mixin(value = RenderRegion.class, remap = false)
public class MixinSodiumRenderRegion {
   @Shadow
   @Final
   private ChunkRenderList renderList;
   @Shadow
   @Final
   private Map<TerrainRenderPass, MultiDrawBatch> cachedBatches;
   @Shadow
   private DeviceResources resources;
   @Unique
   @Nullable
   private ObjectArrayList<ChunkRenderList> chunkRenderListsForPortalRendering = null;
   @Unique
   @Nullable
   private ObjectArrayList<Map<TerrainRenderPass, MultiDrawBatch>> ip_cachedBatchesForPortalRendering = null;

   @Overwrite
   public ChunkRenderList getRenderList() {
      if (!PortalRendering.isRendering()) {
         return this.renderList;
      }

      RenderRegion this_ = (RenderRegion)(Object)this;
      if (this.chunkRenderListsForPortalRendering == null) {
         this.chunkRenderListsForPortalRendering = new ObjectArrayList<>();
      }

      int layer = PortalRendering.getPortalLayer();
      int index = layer - 1;
      return Helper.arrayListComputeIfAbsent(this.chunkRenderListsForPortalRendering, index, () -> new ChunkRenderList(this_));
   }

   @Unique
   private Map<TerrainRenderPass, MultiDrawBatch> ip_currentCachedBatches() {
      if (!PortalRendering.isRendering()) {
         return this.cachedBatches;
      }

      if (this.ip_cachedBatchesForPortalRendering == null) {
         this.ip_cachedBatchesForPortalRendering = new ObjectArrayList<>();
      }

      int index = PortalRendering.getPortalLayer() - 1;
      return Helper.arrayListComputeIfAbsent(this.ip_cachedBatchesForPortalRendering, index, Reference2ReferenceOpenHashMap::new);
   }

   @Overwrite
   public void clearAllCachedBatches() {
      for (MultiDrawBatch batch : this.ip_currentCachedBatches().values()) {
         batch.clear();
      }
   }

   @Overwrite
   public void clearCachedBatchFor(TerrainRenderPass pass) {
      MultiDrawBatch batch = this.ip_currentCachedBatches().get(pass);
      if (batch != null) {
         batch.clear();
      }
   }

   @Overwrite
   public MultiDrawBatch getCachedBatch(TerrainRenderPass pass) {
      Map<TerrainRenderPass, MultiDrawBatch> batches = this.ip_currentCachedBatches();
      MultiDrawBatch batch = batches.get(pass);
      if (batch != null) {
         return batch;
      }

      batch = new MultiDrawBatch(ModelQuadFacing.COUNT * 256 + 1);
      batches.put(pass, batch);
      return batch;
   }

   @Inject(method = "update", at = @At("RETURN"))
   private void ip_onUpdate(CommandList commandList, CallbackInfo ci) {
      if (this.resources == null && this.ip_cachedBatchesForPortalRendering != null) {
         for (Map<TerrainRenderPass, MultiDrawBatch> batches : this.ip_cachedBatchesForPortalRendering) {
            for (MultiDrawBatch batch : batches.values()) {
               batch.clear();
            }
         }
      }
   }

   @Inject(method = "delete", at = @At("RETURN"))
   private void ip_onDelete(CommandList commandList, CallbackInfo ci) {
      if (this.ip_cachedBatchesForPortalRendering != null) {
         for (Map<TerrainRenderPass, MultiDrawBatch> batches : this.ip_cachedBatchesForPortalRendering) {
            for (MultiDrawBatch batch : batches.values()) {
               batch.delete();
            }

            batches.clear();
         }

         this.ip_cachedBatchesForPortalRendering = null;
      }
   }
}
