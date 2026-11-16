package qouteall.imm_ptl.core.mixin.common.entity_sync;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ChunkMap.TrackedEntity;
import net.minecraft.world.entity.Entity;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.ducks.IEChunkMap;
import qouteall.imm_ptl.core.ducks.IETrackedEntity;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;
import qouteall.imm_ptl.core.teleportation.ServerTeleportationManager;

@Mixin(ChunkMap.class)
public abstract class MixinChunkMap_E implements IEChunkMap {
   @Shadow
   @Final
   public Int2ObjectMap<TrackedEntity> entityMap;
   @Shadow
   @Final
   private ServerLevel level;

   @Shadow
   abstract void updatePlayerStatus(ServerPlayer var1, boolean var2);

   @Shadow
   @Nullable
   protected abstract ChunkHolder getUpdatingChunkIfPresent(long var1);

   @Inject(method = "addEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap$TrackedEntity;updatePlayers(Ljava/util/List;)V"))
   private void onAddEntityUpdatePlayers(Entity entity, CallbackInfo ci) {
      TrackedEntity trackedEntity = this.entityMap.get(entity.getId());
      if (trackedEntity != null) {
         ((IETrackedEntity)trackedEntity).ip_updateEntityTrackingStatus();
      }
   }

   @Inject(method = "Lnet/minecraft/server/level/ChunkMap;removeEntity(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
   @IPVanillaCopy
   private void onUnloadEntity(Entity entity, CallbackInfo ci) {
      if (ServerTeleportationManager.of(entity.level().getServer()).isTeleporting(entity)) {
         if (entity instanceof ServerPlayer player) {
            Object tracker = this.entityMap.remove(entity.getId());
            ((IETrackedEntity)tracker).ip_stopTrackingToAllPlayers();
            this.updatePlayerStatus(player, false);
         } else {
            this.entityMap.remove(entity.getId());
         }

         ci.cancel();
      }
   }

   @Inject(method = "Lnet/minecraft/server/level/ChunkMap;tick()V", at = @At("HEAD"), cancellable = true)
   private void onTickEntityMovement(CallbackInfo ci) {
      ci.cancel();
   }

   @Override
   public void ip_onPlayerUnload(ServerPlayer oldPlayer) {
      this.entityMap.values().forEach(obj -> obj.removePlayer(oldPlayer));
   }

   @Override
   public void ip_onDimensionRemove() {
      this.entityMap.values().forEach(obj -> ((IETrackedEntity)obj).ip_onDimensionRemove());
   }

   @Override
   public void ip_resendSpawnPacketToTrackers(Entity entity) {
      Object tracker = this.entityMap.get(entity.getId());
      Validate.notNull(tracker, "entity not yet tracked");
      ((IETrackedEntity)tracker).ip_resendSpawnPacketToTrackers();
   }

   @Override
   public Int2ObjectMap<TrackedEntity> ip_getEntityTrackerMap() {
      return this.entityMap;
   }

   @Nullable
   @Override
   public ChunkHolder ip_getUpdatingChunkIfPresent(long chunkPos) {
      return this.getUpdatingChunkIfPresent(chunkPos);
   }
}
