package qouteall.imm_ptl.core.mixin.common;

import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTracking;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.ducks.IEServerWorld;

@Mixin(ServerLevel.class)
public abstract class MixinServerLevel implements IEServerWorld {
   @Shadow
   @Final
   private ServerLevelData serverLevelData;
   @Shadow
   @Final
   private PersistentEntitySectionManager<Entity> entityManager;

   @Shadow
   public abstract DimensionDataStorage getDataStorage();

   @Shadow
   public abstract ServerChunkCache getChunkSource();

   @Redirect(
      method = "Lnet/minecraft/server/level/ServerLevel;tick(Ljava/util/function/BooleanSupplier;)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;hasActiveTickets()Z")
   )
   private boolean redirectHasActiveTickets(ServerChunkCache chunkSource) {
      ServerLevel this_ = (ServerLevel)(Object)this;
      return ImmPtlChunkTracking.shouldLoadDimension(this_.dimension()) ? true : chunkSource.hasActiveTickets();
   }

   @Inject(method = "Lnet/minecraft/server/level/ServerLevel;toString()Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
   private void onToString(CallbackInfoReturnable<String> cir) {
      ServerLevel this_ = (ServerLevel)(Object)this;
      cir.setReturnValue("ServerWorld " + this_.dimension().identifier() + " " + this.serverLevelData.getLevelName());
   }

   @Inject(method = "tickNonPassenger", at = @At("HEAD"))
   private void onTickNonPassenger(Entity entity, CallbackInfo ci) {
      ((IEEntity)entity).ip_tickCollidingPortal();
   }

   @Override
   public PersistentEntitySectionManager<Entity> ip_getEntityManager() {
      return this.entityManager;
   }
}
