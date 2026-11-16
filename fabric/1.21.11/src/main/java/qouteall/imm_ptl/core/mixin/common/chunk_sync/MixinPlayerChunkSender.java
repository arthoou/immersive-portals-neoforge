package qouteall.imm_ptl.core.mixin.common.chunk_sync;

import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTracking;
import qouteall.imm_ptl.core.ducks.IEPlayerChunkSender;
import qouteall.q_misc_util.platform.Platform;

@Mixin(PlayerChunkSender.class)
public class MixinPlayerChunkSender implements IEPlayerChunkSender {
   @Shadow
   @Final
   private static Logger LOGGER;
   @Unique
   @Nullable
   private ServerGamePacketListenerImpl ip_listener;
   @Unique
   private static final AtomicBoolean ip_unexpectedCallerReported = new AtomicBoolean();
   @Unique
   private static volatile Boolean ip_vmpLoaded;

   @Overwrite
   public void markChunkPendingToSend(LevelChunk levelChunk) {
   }

   @Overwrite
   public void dropChunk(ServerPlayer serverPlayer, ChunkPos chunkPos) {
   }

   @Inject(method = "sendNextChunks", at = @At("HEAD"), cancellable = true)
   public void sendNextChunks(ServerPlayer serverPlayer, CallbackInfo ci) {
      ImmPtlChunkTracking.getPlayerInfo(serverPlayer).doChunkSending(serverPlayer);
      ci.cancel();
   }

   @Overwrite
   public void onChunkBatchReceivedByClient(float f) {
   }

   @Override
   public void ip_setListener(ServerGamePacketListenerImpl listener) {
      this.ip_listener = listener;
   }

   @Overwrite
   public boolean isPending(long l) {
      ServerGamePacketListenerImpl listener = this.ip_listener;
      ServerPlayer player = listener == null ? null : listener.player;
      if (player == null) {
         LOGGER.error("This should not be called", new Throwable());
         return false;
      }

      if (!ip_isVmpLoaded() && ip_unexpectedCallerReported.compareAndSet(false, true)) {
         LOGGER.warn(
            "PlayerChunkSender.isPending was called without Very Many Players loaded: some code bypasses ImmPtl's chunk tracking. Answered from ImmPtl's own records; reported once per run.",
            new Throwable("isPending caller")
         );
      }

      return ImmPtlChunkTracking.isChunkPendingToPlayer(player, player.level().dimension(), l);
   }

   @Unique
   private static boolean ip_isVmpLoaded() {
      Boolean cached = ip_vmpLoaded;
      if (cached == null) {
         cached = Platform.get().isModLoaded("vmp");
         ip_vmpLoaded = cached;
      }

      return cached;
   }
}
