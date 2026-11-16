package qouteall.imm_ptl.core.mixin.common.chunk_sync;

import java.util.List;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ChunkHolder.PlayerProvider;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTracking;
import qouteall.imm_ptl.core.ducks.IEChunkHolder;
import qouteall.imm_ptl.core.network.PacketRedirection;

@Mixin(ChunkHolder.class)
public class MixinChunkHolder implements IEChunkHolder {
   @Shadow
   @Final
   private LevelHeightAccessor levelHeightAccessor;

   @ModifyVariable(method = "broadcast", at = @At("HEAD"), argsOnly = true)
   private Packet<?> modifyPacket(Packet<?> packet) {
      ServerLevel serverWorld = (ServerLevel)this.levelHeightAccessor;
      return PacketRedirection.createRedirectedMessage(serverWorld.getServer(), serverWorld.dimension(), (Packet<ClientGamePacketListener>)packet);
   }

   @Redirect(
      method = "broadcastChanges",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/server/level/ChunkHolder$PlayerProvider;getPlayers(Lnet/minecraft/world/level/ChunkPos;Z)Ljava/util/List;"
      )
   )
   private List<ServerPlayer> redirectGetPlayers(PlayerProvider playerProvider, ChunkPos chunkPos, boolean boundaryOnly) {
      return ImmPtlChunkTracking.getPlayersViewingChunk(((Level)this.levelHeightAccessor).dimension(), chunkPos.x, chunkPos.z, boundaryOnly);
   }
}
