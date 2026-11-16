// Reconstructed from the published Apache-2.0 MC 1.21.11 port; see SOURCE-PROVENANCE.md.
package qouteall.imm_ptl.core.mixin.common.chunk_sync;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTracking;
import qouteall.imm_ptl.core.ducks.IEChunkMap;

@Mixin(value = ChunkMap.class, priority = 1100)
public abstract class MixinChunkMap_C implements IEChunkMap {
   @Shadow
   @Final
   private ServerLevel level;
   @Shadow
   @Final
   private ThreadedLevelLightEngine lightEngine;

   @Shadow
   protected abstract ChunkHolder getVisibleChunkIfPresent(long var1);

   @Shadow
   abstract int getPlayerViewDistance(ServerPlayer var1);

   @Override
   public int ip_getPlayerViewDistance(ServerPlayer player) {
      return this.getPlayerViewDistance(player);
   }

   @Override
   public ServerLevel ip_getWorld() {
      return this.level;
   }

   @Override
   public ThreadedLevelLightEngine ip_getLightingProvider() {
      return this.lightEngine;
   }

   @Override
   public ChunkHolder ip_getChunkHolder(long chunkPosLong) {
      return this.getVisibleChunkIfPresent(chunkPosLong);
   }

   @Inject(method = "applyChunkTrackingView", at = @At("HEAD"), cancellable = true)
   private void onUpdateChunkTracking(ServerPlayer serverPlayer, ChunkTrackingView chunkTrackingView, CallbackInfo ci) {
      ci.cancel();
   }

   @Inject(method = "getPlayers", at = @At("HEAD"), cancellable = true)
   private void onGetPlayers(ChunkPos chunkPos, boolean boundaryOnly, CallbackInfoReturnable<List<ServerPlayer>> cir) {
      List<ServerPlayer> viewers = ImmPtlChunkTracking.getPlayersViewingChunk(this.level.dimension(), chunkPos.x, chunkPos.z, boundaryOnly);
      List<ServerPlayer> result = new ArrayList<>(viewers.size());

      for (ServerPlayer viewer : viewers) {
         if (viewer.level() == this.level) {
            result.add(viewer);
         }
      }

      cir.setReturnValue(result);
   }

   // Arthous: preserve cancellable injection and the modern broadcast/debug tail.
   @Inject(method = "onChunkReadyToSend", at = @At("HEAD"), cancellable = true)
   private void ip_onChunkReadyToSend(ChunkHolder holder, LevelChunk chunk, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
      ImmPtlChunkTracking.onChunkProvidedDeferred(chunk);
      this.level.getChunkSource().onChunkReadyToSend(holder);
      this.level.debugSynchronizers().registerChunk(chunk);
      ci.cancel();
   }
}
