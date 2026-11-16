package qouteall.imm_ptl.core.mixin.common.other_sync;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTracking;
import qouteall.imm_ptl.core.network.PacketRedirection;
import qouteall.imm_ptl.core.portal.global_portals.GlobalPortalStorage;
import qouteall.imm_ptl.core.teleportation.ServerTeleportationManager;
import qouteall.q_misc_util.Helper;

@Mixin(value = PlayerList.class, priority = 800)
public class MixinPlayerList {
   @Shadow
   @Final
   private List<ServerPlayer> players;
   @Shadow
   @Final
   private MinecraftServer server;

   @Inject(
      method = "Lnet/minecraft/server/players/PlayerList;sendLevelInfo(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/level/ServerLevel;)V",
      at = @At("RETURN")
   )
   private void onSendWorldInfo(ServerPlayer player, ServerLevel world, CallbackInfo ci) {
      if (!ServerTeleportationManager.of(player.server).isFiringMyChangeDimensionEvent) {
         GlobalPortalStorage.onPlayerLoggedIn(player);
      }
   }

   @Inject(method = "placeNewPlayer", at = @At("TAIL"))
   private void onOnPlayerConnect(Connection connection, ServerPlayer player, CommonListenerCookie commonListenerCookie, CallbackInfo ci) {
      ImmPtlChunkTracking.immediatelyUpdateForPlayer(player);
      Helper.LOGGER.info("Player login {} {}", player.level().getGameTime(), player);
   }

   @Inject(
      method = "Lnet/minecraft/server/players/PlayerList;broadcastAll(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/resources/ResourceKey;)V",
      at = @At("HEAD"),
      cancellable = true
   )
   public void sendToDimension(Packet<?> packet, ResourceKey<Level> dimension, CallbackInfo ci) {
      for (ServerPlayer player : this.players) {
         if (player.level().dimension() == dimension) {
            PacketRedirection.sendRedirectedMessage(player, dimension, (Packet<ClientGamePacketListener>)packet);
         }
      }

      ci.cancel();
   }

   @Redirect(
      method = "respawn",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;restoreFrom(Lnet/minecraft/server/level/ServerPlayer;Z)V")
   )
   private void onRestoreFrom(ServerPlayer newPlayer, ServerPlayer that, boolean keepEverything) {
      newPlayer.restoreFrom(that, keepEverything);
      newPlayer.connection.player = newPlayer;
   }

   @Overwrite
   public void broadcast(@Nullable Player excludingPlayer, double x, double y, double z, double distance, ResourceKey<Level> dimension, Packet<?> packet) {
      ChunkPos chunkPos = new ChunkPos(BlockPos.containing(new Vec3(x, y, z)));
      Object2ObjectOpenHashMap<ServerPlayer, ImmPtlChunkTracking.PlayerWatchRecord> recs = ImmPtlChunkTracking.getWatchRecordForChunk(
         dimension, chunkPos.x, chunkPos.z
      );
      if (recs != null) {
         for (ImmPtlChunkTracking.PlayerWatchRecord rec : recs.values()) {
            if (rec.isLoadedToPlayer
               && rec.player != excludingPlayer
               && ImmPtlChunkTracking.isPlayerWatchingChunkWithinRadius(rec.player, dimension, chunkPos.x, chunkPos.z, (int)distance + 16)) {
               rec.player
                  .connection
                  .send(PacketRedirection.createRedirectedMessage(rec.player.level().getServer(), dimension, (Packet<ClientGamePacketListener>)packet));
            }
         }
      }
   }
}
