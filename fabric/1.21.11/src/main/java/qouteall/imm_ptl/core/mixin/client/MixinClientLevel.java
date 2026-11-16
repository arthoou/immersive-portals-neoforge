package qouteall.imm_ptl.core.mixin.client;

import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientLevel.ClientLevelData;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.entity.EntityTickList;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.ducks.IEClientWorld;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.platform_specific.O_O;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.q_misc_util.my_util.LimitedLogger;

@Mixin(ClientLevel.class)
public abstract class MixinClientLevel implements IEClientWorld {
   private List<Portal> portal_globalPortals;
   private static final LimitedLogger limitedLogger = new LimitedLogger(100);
   @Shadow
   @Final
   @Mutable
   private ClientPacketListener connection;
   @Mutable
   @Shadow
   @Final
   private ClientChunkCache chunkSource;
   @Shadow
   @Final
   private Minecraft minecraft;
   @Mutable
   @Shadow
   @Final
   private LevelRenderer levelRenderer;
   @Shadow
   @Final
   private EntityTickList tickingEntities;
   @Shadow
   @Final
   private BlockStatePredictionHandler blockStatePredictionHandler;
   @Shadow
   @Final
   @Mutable
   private TickRateManager tickRateManager;

   @Shadow
   public abstract Entity getEntity(int var1);

   @Shadow
   protected abstract Map<String, MapItemSavedData> getAllMapData();

   @Shadow
   protected abstract void addMapData(Map<String, MapItemSavedData> var1);

   @Override
   public List<Portal> ip_getGlobalPortals() {
      return this.portal_globalPortals;
   }

   @Override
   public void ip_setGlobalPortals(List<Portal> arg) {
      this.portal_globalPortals = arg;
   }

   @Inject(method = "<init>", at = @At("RETURN"))
   void onConstructed(
      ClientPacketListener clientPacketListener,
      ClientLevelData clientLevelData,
      ResourceKey resourceKey,
      Holder holder,
      int loadDistance,
      int j,
      LevelRenderer levelRenderer,
      boolean bl,
      long l,
      int seaLevel,
      CallbackInfo ci
   ) {
      ClientLevel clientWorld = (ClientLevel)(Object)this;
      ClientChunkCache myClientChunkManager = O_O.createMyClientChunkManager(clientWorld, loadDistance);
      this.chunkSource = myClientChunkManager;
   }

   @Inject(method = "addEntity", at = @At("TAIL"))
   private void onOnEntityAdded(Entity entityIn, CallbackInfo ci) {
      if (ClientWorldLoader.getIsInitialized()) {
         for (ClientLevel world : ClientWorldLoader.getClientWorlds()) {
            if (world != (Object)this) {
               world.removeEntity(entityIn.getId(), RemovalReason.DISCARDED);
            }
         }
      }
   }

   @Inject(method = "Lnet/minecraft/client/multiplayer/ClientLevel;hasChunk(II)Z", at = @At("HEAD"), cancellable = true)
   private void onHasChunk(int chunkX, int chunkZ, CallbackInfoReturnable<Boolean> cir) {
      if (IPGlobal.tickOnlyIfChunkLoaded) {
         LevelChunk chunk = this.chunkSource.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
         if (chunk == null || chunk instanceof EmptyLevelChunk) {
            cir.setReturnValue(false);
         }
      }
   }

   @Inject(method = "Lnet/minecraft/client/multiplayer/ClientLevel;toString()Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
   private void onToString(CallbackInfoReturnable<String> cir) {
      ClientLevel this_ = (ClientLevel)(Object)this;
      cir.setReturnValue("ClientWorld " + this_.dimension().identifier());
   }

   @Inject(method = "tickNonPassenger", at = @At("HEAD"))
   private void onTickNonPassenger(Entity entity, CallbackInfo ci) {
      ((IEEntity)entity).ip_tickCollidingPortal();
   }

   @Override
   public void ip_resetWorldRendererRef() {
      this.levelRenderer = null;
   }

   @Override
   public EntityTickList ip_getEntityList() {
      return this.tickingEntities;
   }

   @Override
   public Map<String, MapItemSavedData> ip_getAllMapData() {
      return this.getAllMapData();
   }

   @Override
   public void ip_addMapData(Map<String, MapItemSavedData> map) {
      this.addMapData(map);
   }

   @Override
   public BlockStatePredictionHandler ip_getBlockStatePredictionHandler() {
      return this.blockStatePredictionHandler;
   }

   @Override
   public void ip_setTickRateManager(TickRateManager cond) {
      this.tickRateManager = cond;
   }
}
