package qouteall.imm_ptl.core.mixin.client.sync;

import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.RegistryAccess.Frozen;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.ducks.IEClientPlayNetworkHandler;
import qouteall.imm_ptl.core.ducks.IEPlayerPositionLookS2CPacket;
import qouteall.imm_ptl.core.network.ImmPtlNetworkConfig;
import qouteall.imm_ptl.core.teleportation.ClientTeleportationManager;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.CountDownInt;

@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener implements IEClientPlayNetworkHandler {
   private static CountDownInt LOG_LIMIT = new CountDownInt(20);
   @Shadow
   private ClientLevel level;
   @Mutable
   @Shadow
   @Final
   private Map<UUID, PlayerInfo> playerInfoMap;
   @Shadow
   @Final
   private static Logger LOGGER;
   private boolean isReProcessingPassengerPacket;

   @Shadow
   public abstract void handleSetEntityPassengersPacket(ClientboundSetPassengersPacket var1);

   @Shadow
   protected abstract void applyLightData(int var1, int var2, ClientboundLightUpdatePacketData var3, boolean var4);

   @Shadow
   public abstract Frozen registryAccess();

   @Shadow
   protected abstract void enableChunkLight(LevelChunk var1, int var2, int var3);

   @Override
   public void ip_setWorld(ClientLevel world) {
      this.level = world;
   }

   @Inject(method = "<init>", at = @At("RETURN"))
   private void onInit(Minecraft minecraft, Connection connection, CommonListenerCookie commonListenerCookie, CallbackInfo ci) {
      this.isReProcessingPassengerPacket = false;
   }

   @Inject(
      method = "Lnet/minecraft/client/multiplayer/ClientPacketListener;handleMovePlayer(Lnet/minecraft/network/protocol/game/ClientboundPlayerPositionPacket;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
         shift = Shift.AFTER
      )
   )
   private void onProcessingPositionPacket(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
      if (ImmPtlNetworkConfig.doesServerHaveImmPtl()) {
         ResourceKey<Level> packetDim = ((IEPlayerPositionLookS2CPacket)(Object)packet).ip_getPlayerDimension();
         LocalPlayer player = Minecraft.getInstance().player;
         assert player != null;
         Level playerWorld = player.level();
         if (packetDim != playerWorld.dimension()) {
            LOGGER.info(
               "[ImmPtl] Client accepted position packet in another dimension. Packet: {} {} {} {}. Player: {} {} {} {}",
               packetDim.identifier(),
               packet.change().position().x,
               packet.change().position().y,
               packet.change().position().z,
               playerWorld.dimension().identifier(),
               player.getX(),
               player.getY(),
               player.getZ()
            );
            ClientTeleportationManager.forceTeleportPlayer(
               packetDim, new Vec3(packet.change().position().x, packet.change().position().y, packet.change().position().z)
            );
         }

         LOGGER.info(
            "[ImmPtl] Client accepted position packet {} {} {} {}",
            packetDim.identifier(),
            packet.change().position().x,
            packet.change().position().y,
            packet.change().position().z
         );
      }
   }

   @Inject(
      method = "Lnet/minecraft/client/multiplayer/ClientPacketListener;handleSetEntityPassengersPacket(Lnet/minecraft/network/protocol/game/ClientboundSetPassengersPacket;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
         shift = Shift.AFTER
      ),
      cancellable = true
   )
   private void onOnEntityPassengersSet(ClientboundSetPassengersPacket entityPassengersSetS2CPacket_1, CallbackInfo ci) {
      Entity entity_1 = this.level.getEntity(entityPassengersSetS2CPacket_1.getVehicle());
      if (entity_1 == null && !this.isReProcessingPassengerPacket) {
         Helper.log("Re-processed riding packet");
         IPGlobal.CLIENT_TASK_LIST.addTask(() -> {
            this.isReProcessingPassengerPacket = true;
            this.handleSetEntityPassengersPacket(entityPassengersSetS2CPacket_1);
            this.isReProcessingPassengerPacket = false;
            return true;
         });
         ci.cancel();
      }
   }

   @Redirect(
      method = "Lnet/minecraft/client/multiplayer/ClientPacketListener;handleSetEntityData(Lnet/minecraft/network/protocol/game/ClientboundSetEntityDataPacket;)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getEntity(I)Lnet/minecraft/world/entity/Entity;")
   )
   private Entity redirectGetEntityById(ClientLevel clientWorld, int id) {
      Entity entity = clientWorld.getEntity(id);
      if (entity == null && LOG_LIMIT.tryDecrement()) {
         LOGGER.warn("missing entity for data tracking {} {}", clientWorld, id);
      }

      return entity;
   }

   @Inject(method = "handleSetTime", at = @At("RETURN"))
   private void onSetTime(ClientboundSetTimePacket packet, CallbackInfo ci) {
      if (ClientWorldLoader.getIsInitialized()) {
         ClientLevel currentWorld = Minecraft.getInstance().level;

         for (ClientLevel clientWorld : ClientWorldLoader.getClientWorlds()) {
            if (clientWorld != currentWorld) {
               clientWorld.getLevelData().setGameTime(packet.gameTime());
            }
         }
      }
   }

   @Redirect(method = "handleBlockChangedAck", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;handleBlockChangedAck(I)V"))
   private void redirectHandleBlockChangedAck(ClientLevel instance, int seqNumber) {
      for (ClientLevel clientWorld : ClientWorldLoader.getClientWorlds()) {
         clientWorld.handleBlockChangedAck(seqNumber);
      }
   }

   @Inject(
      method = "handleAddEntity",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
         shift = Shift.AFTER
      ),
      cancellable = true
   )
   private void onHandleAddEntity(ClientboundAddEntityPacket packet, CallbackInfo ci) {
      int entityId = packet.getId();
      Entity existingEntity = this.level.getEntity(entityId);
      if (existingEntity != null && !existingEntity.getPassengers().isEmpty()) {
         LOGGER.warn("[ImmPtl] Entity already exists and has passengers when accepting add-entity packet. Ignoring. {} {}", existingEntity, packet);
         ci.cancel();
      }
   }

   @Inject(
      method = "handleLevelChunkWithLight",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
         shift = Shift.AFTER
      )
   )
   private void onHandleLevelChunkWithLight(ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci) {
      if (IPGlobal.chunkPacketDebug) {
         LOGGER.info("Chunk Load Packet {} {} {}", this.level.dimension().identifier(), packet.getX(), packet.getZ());
      }
   }

   @Inject(
      method = "handleForgetLevelChunk",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
         shift = Shift.AFTER
      )
   )
   private void onHandleForgetLevelChunk(ClientboundForgetLevelChunkPacket packet, CallbackInfo ci) {
      if (IPGlobal.chunkPacketDebug) {
         LOGGER.info("Chunk Unload Packet {} {} {}", this.level.dimension().identifier(), packet.pos().x, packet.pos().z);
      }
   }
}
