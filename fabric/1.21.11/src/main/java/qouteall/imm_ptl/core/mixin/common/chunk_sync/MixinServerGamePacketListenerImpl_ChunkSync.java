package qouteall.imm_ptl.core.mixin.common.chunk_sync;

import net.minecraft.network.protocol.game.ServerboundChunkBatchReceivedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTracking;
import qouteall.imm_ptl.core.ducks.IEPlayerChunkSender;

@Mixin(ServerGamePacketListenerImpl.class)
public class MixinServerGamePacketListenerImpl_ChunkSync {
   @Shadow
   public ServerPlayer player;
   @Shadow
   @Final
   public PlayerChunkSender chunkSender;

   @Inject(method = "<init>", at = @At("RETURN"))
   private void ip_attachChunkSender(CallbackInfo ci) {
      ((IEPlayerChunkSender)this.chunkSender).ip_setListener((ServerGamePacketListenerImpl)(Object)this);
   }

   @Inject(
      method = "handleChunkBatchReceived",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/PlayerChunkSender;onChunkBatchReceivedByClient(F)V")
   )
   private void redirectOnChunkBatchReceivedByClient(ServerboundChunkBatchReceivedPacket packet, CallbackInfo ci) {
      ImmPtlChunkTracking.getPlayerInfo(this.player).onChunkBatchReceivedByClient(packet.desiredChunksPerTick());
   }
}
