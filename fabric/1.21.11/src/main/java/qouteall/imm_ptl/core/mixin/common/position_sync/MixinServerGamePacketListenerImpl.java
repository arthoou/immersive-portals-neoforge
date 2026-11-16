package qouteall.imm_ptl.core.mixin.common.position_sync;

import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.ducks.IEPlayerMoveC2SPacket;
import qouteall.imm_ptl.core.ducks.IEPlayerPositionLookS2CPacket;
import qouteall.imm_ptl.core.ducks.IEServerPlayNetworkHandler;
import qouteall.imm_ptl.core.mc_utils.ServerTaskList;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;
import qouteall.imm_ptl.core.platform_specific.IPConfig;
import qouteall.imm_ptl.core.teleportation.ServerTeleportationManager;
import qouteall.q_misc_util.my_util.CountDownInt;

@Mixin(value = ServerGamePacketListenerImpl.class, priority = 900)
public abstract class MixinServerGamePacketListenerImpl implements IEServerPlayNetworkHandler {
   @Shadow
   public ServerPlayer player;
   @Shadow
   private Vec3 awaitingPositionFromClient;
   @Shadow
   private int awaitingTeleport;
   @Shadow
   private int awaitingTeleportTime;
   @Shadow
   private int tickCount;
   @Shadow
   private double vehicleLastGoodX;
   @Shadow
   private double vehicleLastGoodY;
   @Shadow
   private double vehicleLastGoodZ;
   @Shadow
   private double vehicleFirstGoodX;
   @Shadow
   private double vehicleFirstGoodY;
   @Shadow
   private double vehicleFirstGoodZ;
   @Shadow
   private Entity lastVehicle;
   @Shadow
   private boolean clientVehicleIsFloating;
   @Shadow
   @Final
   static Logger LOGGER;
   @Shadow
   private boolean clientIsFloating;
   @Unique
   private static final CountDownInt LOG_LIMIT = new CountDownInt(20);
   @Unique
   private int ip_wrongMovePacketCount = 0;
   @Unique
   @Nullable
   private ResourceKey<Level> ip_dimOfAwaitingPosition;

   @Shadow
   public abstract ServerPlayer getPlayer();

   @Inject(
      method = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;handleMovePlayer(Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket;)V",
      at = @At(
         value = "INVOKE",
         shift = Shift.AFTER,
         target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V"
      ),
      cancellable = true
   )
   private void onProcessMovePacket(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
      ResourceKey<Level> packetDimension = ((IEPlayerMoveC2SPacket)packet).ip_getPlayerDimension();
      if (packetDimension == null) {
         LOGGER.error("Player move packet is missing dimension info. Maybe the player client doesn't install iPortal");
         ServerTaskList.of(this.player.server).addTask(() -> {
            this.player.connection.disconnect(Component.literal("The client does not have Immersive Portals mod"));
            return true;
         });
      } else {
         if (this.player.level().dimension() != packetDimension) {
            if (LOG_LIMIT.tryDecrement()) {
               LOGGER.info(
                  "[ImmPtl] Ignoring player move packet. Player: {} Packet: {} {} {} {}",
                  this.player,
                  packetDimension.identifier(),
                  packet.getX(this.player.getX()),
                  packet.getY(this.player.getY()),
                  packet.getZ(this.player.getZ())
               );
            }

            this.ip_wrongMovePacketCount++;
            if (this.ip_wrongMovePacketCount > 10) {
               LOGGER.info("[ImmPtl] Force move player {} {} {}", this.player, this.player.level().dimension().identifier(), this.player.position());
               ServerTeleportationManager.of(this.player.server).forceTeleportPlayer(this.player, this.player.level().dimension(), this.player.position());
               this.ip_wrongMovePacketCount = 0;
            }

            ci.cancel();
         } else {
            this.ip_wrongMovePacketCount = 0;
         }
      }
   }

   @Overwrite
   @IPVanillaCopy
   public void teleport(PositionMoveRotation posMoveRot, Set<Relative> relativeAttrs) {
      if (this.player.getRemovalReason() != null) {
         LOGGER.error("[ImmPtl] Tries to send player pos packet to a removed player {}", this.player, new Throwable());
      } else {
         if (IPConfig.getConfig().serverTeleportLogging) {
            LOGGER.info(
               "Teleporting player {} to {} {} {} {}",
               this.player,
               this.player.level().dimension().identifier(),
               posMoveRot.position().x,
               posMoveRot.position().y,
               posMoveRot.position().z
            );
         }

         this.awaitingTeleportTime = this.tickCount;
         if (++this.awaitingTeleport == Integer.MAX_VALUE) {
            this.awaitingTeleport = 0;
         }

         this.player.teleportSetPosition(posMoveRot, relativeAttrs);
         this.awaitingPositionFromClient = this.player.position();
         this.ip_dimOfAwaitingPosition = this.player.level().dimension();
         ClientboundPlayerPositionPacket lookPacket = ClientboundPlayerPositionPacket.of(this.awaitingTeleport, posMoveRot, relativeAttrs);
         ((IEPlayerPositionLookS2CPacket)(Object)lookPacket).ip_setPlayerDimension(this.player.level().dimension());
         this.player.connection.send(lookPacket);
      }
   }

   @Inject(method = "isEntityCollidingWithAnythingNew", at = @At("HEAD"), cancellable = true)
   @IPVanillaCopy
   private void onIsPlayerCollidingWithAnythingNew(
      LevelReader level, Entity entity, AABB playerBB, double newX, double newY, double newZ, CallbackInfoReturnable<Boolean> cir
   ) {
      if (IPGlobal.crossPortalCollision) {
         AABB activePlayerBB = ((IEEntity)entity).ip_getActiveCollisionBox(playerBB);
         if (activePlayerBB == null) {
            cir.setReturnValue(false);
         } else {
            AABB newBB = entity.getBoundingBox().move(newX - entity.getX(), newY - entity.getY(), newZ - entity.getZ());
            AABB activeNewBB = ((IEEntity)entity).ip_getActiveCollisionBox(newBB);
            if (activeNewBB == null) {
               cir.setReturnValue(false);
            } else {
               Iterable<VoxelShape> newBBCollisions = level.getPreMoveCollisions(entity, activeNewBB.deflate(1.0E-5F), playerBB.getBottomCenter());
               VoxelShape activePlayerBBShape = Shapes.create(activePlayerBB.deflate(1.0E-5F));

               for (VoxelShape shape : newBBCollisions) {
                  if (!Shapes.joinIsNotEmpty(shape, activePlayerBBShape, BooleanOp.AND)) {
                     cir.setReturnValue(true);
                     return;
                  }
               }

               cir.setReturnValue(false);
            }
         }
      }
   }

   @Inject(method = "tick", at = @At("HEAD"))
   private void onTick(CallbackInfo ci) {
      if (((IEEntity)this.player).ip_isRecentlyCollidingWithPortal()) {
         this.clientIsFloating = false;
      }
   }

   @Inject(method = "handleAcceptTeleportPacket", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;absSnapTo(DDDFF)V"))
   private void onHandleAcceptTeleportPacket(ServerboundAcceptTeleportationPacket packet, CallbackInfo ci) {
      if (this.ip_dimOfAwaitingPosition == null) {
         LOGGER.error("[ImmPtl] ip_dimOfAwaitingPosition is null {}", this.player);
      } else {
         if (this.ip_dimOfAwaitingPosition != this.player.level().dimension()) {
            LOGGER.info("Accepted teleport to another dimension {} {}", this.ip_dimOfAwaitingPosition, this.awaitingPositionFromClient);
            ServerLevel destWorld = this.player.server.getLevel(this.ip_dimOfAwaitingPosition);
            if (destWorld == null) {
               LOGGER.error("[ImmPtl] Cannot find destination world {}", this.ip_dimOfAwaitingPosition.identifier());
               return;
            }

            ServerTeleportationManager.of(this.player.server)
               .forceTeleportPlayer(this.player, this.ip_dimOfAwaitingPosition, this.awaitingPositionFromClient, false);
            this.ip_dimOfAwaitingPosition = null;
         }
      }
   }

   @Inject(
      method = "handlePlayerCommand",
      at = @At(
         value = "FIELD",
         target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;awaitingPositionFromClient:Lnet/minecraft/world/phys/Vec3;",
         opcode = 181
      )
   )
   private void onTeleportPlayerCancelSleeping(ServerboundPlayerCommandPacket packet, CallbackInfo ci) {
      this.ip_dimOfAwaitingPosition = this.player.level().dimension();
   }

   @Override
   public boolean ip_hasAwaitingTeleport() {
      return this.awaitingPositionFromClient != null;
   }
}
