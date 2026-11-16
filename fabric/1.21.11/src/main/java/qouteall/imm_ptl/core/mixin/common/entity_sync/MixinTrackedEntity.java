package qouteall.imm_ptl.core.mixin.common.entity_sync;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ChunkMap.TrackedEntity;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTracking;
import qouteall.imm_ptl.core.ducks.IEChunkMap;
import qouteall.imm_ptl.core.ducks.IEEntityTrackerEntry;
import qouteall.imm_ptl.core.ducks.IETrackedEntity;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;
import qouteall.imm_ptl.core.network.PacketRedirection;

@Mixin(TrackedEntity.class)
public abstract class MixinTrackedEntity implements IETrackedEntity {
   @Shadow
   @Final
   private ServerEntity serverEntity;
   @Shadow
   @Final
   private Entity entity;
   @Shadow
   @Final
   private Set<ServerPlayerConnection> seenBy;
   @Shadow
   private SectionPos lastSectionPos;

   @Shadow
   public abstract void broadcastRemoved();

   @Shadow
   protected abstract int getEffectiveRange();

   @Redirect(
      method = {
            "sendToTrackingPlayers(Lnet/minecraft/network/protocol/Packet;)V",
            "sendToTrackingPlayersFiltered(Lnet/minecraft/network/protocol/Packet;Ljava/util/function/Predicate;)V"
      },
      at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerConnection;send(Lnet/minecraft/network/protocol/Packet;)V")
   )
   private void onSendToOtherNearbyPlayers(ServerPlayerConnection entityTrackingListener, Packet<?> packet) {
      PacketRedirection.withForceRedirect((ServerLevel)this.entity.level(), () -> entityTrackingListener.send(packet));
   }

   @Redirect(
      method = "sendToTrackingPlayersAndSelf(Lnet/minecraft/network/protocol/Packet;)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V")
   )
   private void onSendToNearbyPlayers(ServerGamePacketListenerImpl serverPlayNetworkHandler, Packet packet) {
      PacketRedirection.sendRedirectedPacket(serverPlayNetworkHandler, packet, this.entity.level().dimension());
   }

   @Inject(method = "updatePlayer", at = @At("HEAD"), cancellable = true)
   private void onUpdatePlayer(ServerPlayer player, CallbackInfo ci) {
      ci.cancel();
   }

   @Inject(method = "updatePlayers", at = @At("HEAD"), cancellable = true)
   private void onUpdatePlayers(List<ServerPlayer> list, CallbackInfo ci) {
      ci.cancel();
   }

   @Override
   public Entity ip_getEntity() {
      return this.entity;
   }

   @IPVanillaCopy
   @Override
   public void ip_updateEntityTrackingStatus() {
      IEChunkMap chunkMap = (IEChunkMap)((ServerLevel)this.entity.level()).getChunkSource().chunkMap;
      Object2ObjectOpenHashMap<ServerPlayer, ImmPtlChunkTracking.PlayerWatchRecord> watchRecMap = ImmPtlChunkTracking.getWatchRecordForChunk(
         this.entity.level().dimension(), this.entity.chunkPosition().x, this.entity.chunkPosition().z
      );
      int effectiveRange = this.getEffectiveRange();
      this.seenBy.removeIf(connection -> {
         ServerPlayer playerx = connection.getPlayer();
         boolean shouldRemove = !watches(this.entity, watchRecMap, effectiveRange, playerx);
         if (shouldRemove) {
            PacketRedirection.withForceRedirect((ServerLevel)this.entity.level(), () -> this.serverEntity.removePairing(playerx));
         }

         return shouldRemove;
      });
      if (watchRecMap != null) {
         for (Entry<ServerPlayer, ImmPtlChunkTracking.PlayerWatchRecord> e : watchRecMap.entrySet()) {
            ServerPlayer player = e.getKey();
            ImmPtlChunkTracking.PlayerWatchRecord rec = e.getValue();
            if (recWatches(this.entity, effectiveRange, rec, player) && this.seenBy.add(player.connection)) {
               PacketRedirection.withForceRedirect((ServerLevel)this.entity.level(), () -> this.serverEntity.addPairing(player));
            }
         }
      }
   }

   @Unique
   private static boolean watches(
      Entity entity, @Nullable Map<ServerPlayer, ImmPtlChunkTracking.PlayerWatchRecord> watchRec, int effectiveRange, ServerPlayer player
   ) {
      if (watchRec == null) {
         return false;
      }

      if (entity == player) {
         return false;
      }

      ImmPtlChunkTracking.PlayerWatchRecord rec = watchRec.get(player);
      return recWatches(entity, effectiveRange, rec, player);
   }

   @Unique
   private static boolean recWatches(Entity entity, int effectiveRange, ImmPtlChunkTracking.PlayerWatchRecord rec, ServerPlayer player) {
      if (rec == null) {
         return false;
      } else if (!rec.isLoadedToPlayer) {
         return false;
      } else {
         return entity == player ? false : rec.distanceToSource * 16 + 8 <= effectiveRange;
      }
   }

   @Override
   public void ip_onDimensionRemove() {
      for (ServerPlayerConnection connection : this.seenBy) {
         this.serverEntity.removePairing(connection.getPlayer());
      }

      this.seenBy.clear();
   }

   @Override
   public void ip_resendSpawnPacketToTrackers() {
      ((IEEntityTrackerEntry)this.serverEntity).ip_updateTrackedEntityPosition();
      Packet spawnPacket = this.entity.getAddEntityPacket(this.serverEntity);
      Packet<ClientGamePacketListener> redirected = PacketRedirection.createRedirectedMessage(
         this.entity.level().getServer(), this.entity.level().dimension(), spawnPacket
      );
      this.seenBy.forEach(handler -> handler.send(redirected));
   }

   @Override
   public void ip_stopTrackingToAllPlayers() {
      this.broadcastRemoved();
   }

   @Override
   public void ip_sendChanges() {
      this.serverEntity.sendChanges();
   }

   @Override
   public SectionPos ip_getLastSectionPos() {
      return this.lastSectionPos;
   }

   @Override
   public void ip_setLastSectionPos(SectionPos arg) {
      this.lastSectionPos = arg;
   }
}
