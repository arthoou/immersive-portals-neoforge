package qouteall.imm_ptl.core.network;

import com.mojang.logging.LogUtils;
import java.util.Objects;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.global_portals.GlobalPortalStorage;
import qouteall.imm_ptl.core.teleportation.ServerTeleportationManager;
import qouteall.q_misc_util.platform.PlatformNetworking;

public class ImmPtlNetworking {
   private static final Logger LOGGER = LogUtils.getLogger();

   public static void init() {
      PlatformNetworking.get().registerPlayC2S(ImmPtlNetworking.TeleportPacket.TYPE, ImmPtlNetworking.TeleportPacket.CODEC);
      PlatformNetworking.get().registerPlayS2C(ImmPtlNetworking.GlobalPortalSyncPacket.TYPE, ImmPtlNetworking.GlobalPortalSyncPacket.CODEC);
      PlatformNetworking.get().registerPlayS2C(ImmPtlNetworking.PortalSyncPacket.TYPE, ImmPtlNetworking.PortalSyncPacket.CODEC);
      PlatformNetworking.get().registerServerReceiver(ImmPtlNetworking.TeleportPacket.TYPE, (packet, player) -> packet.handle(player));
   }

   public static void initClient() {
      PlatformNetworking.get().registerClientReceiver(ImmPtlNetworking.GlobalPortalSyncPacket.TYPE, (packet, c) -> packet.handle());
      PlatformNetworking.get().registerClientReceiver(ImmPtlNetworking.PortalSyncPacket.TYPE, (packet, c) -> packet.handle());
   }

   public record GlobalPortalSyncPacket(int dimensionId, CompoundTag data) implements CustomPacketPayload {
      public static final Type<ImmPtlNetworking.GlobalPortalSyncPacket> TYPE = new Type<>(McHelper.newResourceLocation("imm_ptl:upd_glb_ptl"));
      public static final StreamCodec<FriendlyByteBuf, ImmPtlNetworking.GlobalPortalSyncPacket> CODEC = StreamCodec.of(
         (b, p) -> p.write(b), ImmPtlNetworking.GlobalPortalSyncPacket::read
      );

      public static ImmPtlNetworking.GlobalPortalSyncPacket read(FriendlyByteBuf buf) {
         int dimId = buf.readVarInt();
         CompoundTag compoundTag = buf.readNbt();
         return new ImmPtlNetworking.GlobalPortalSyncPacket(dimId, compoundTag);
      }

      public void write(FriendlyByteBuf buf) {
         buf.writeVarInt(this.dimensionId);
         buf.writeNbt(this.data);
      }

      @Environment(EnvType.CLIENT)
      public void handle() {
         ResourceKey<Level> dim = PortalAPI.clientIntToDimKey(this.dimensionId);
         GlobalPortalStorage.receiveGlobalPortalSync(dim, this.data);
      }

      @NotNull
      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public record PortalSyncPacket(int id, UUID uuid, EntityType<?> entityType, int dimensionId, double x, double y, double z, CompoundTag extraData)
      implements CustomPacketPayload {
      public static final Type<ImmPtlNetworking.PortalSyncPacket> TYPE = new Type<>(McHelper.newResourceLocation("imm_ptl:spawn_portal"));
      public static final StreamCodec<RegistryFriendlyByteBuf, ImmPtlNetworking.PortalSyncPacket> CODEC = StreamCodec.of(
         (b, p) -> p.write(b), ImmPtlNetworking.PortalSyncPacket::read
      );

      public void write(RegistryFriendlyByteBuf buf) {
         buf.writeVarInt(this.id);
         buf.writeUUID(this.uuid);
         ByteBufCodecs.registry(Registries.ENTITY_TYPE).encode(buf, this.entityType);
         buf.writeVarInt(this.dimensionId);
         buf.writeDouble(this.x);
         buf.writeDouble(this.y);
         buf.writeDouble(this.z);
         buf.writeNbt(this.extraData);
      }

      public static ImmPtlNetworking.PortalSyncPacket read(RegistryFriendlyByteBuf buf) {
         int id = buf.readVarInt();
         UUID uuid = buf.readUUID();
         EntityType<?> type = ByteBufCodecs.registry(Registries.ENTITY_TYPE).decode(buf);
         int dimensionId = buf.readVarInt();
         double x = buf.readDouble();
         double y = buf.readDouble();
         double z = buf.readDouble();
         CompoundTag extraData = buf.readNbt();
         return new ImmPtlNetworking.PortalSyncPacket(id, uuid, type, dimensionId, x, y, z, extraData);
      }

      @Environment(EnvType.CLIENT)
      public void handle() {
         ResourceKey<Level> dimension = PortalAPI.clientIntToDimKey(this.dimensionId);
         ClientLevel world = ClientWorldLoader.getWorld(dimension);
         if (world.getEntity(this.id) instanceof Portal existingPortal) {
            if (!Objects.equals(existingPortal.getUUID(), this.uuid)) {
               ImmPtlNetworking.LOGGER.error("UUID mismatch when syncing portal {} {}", existingPortal, this.uuid);
               return;
            }

            if (existingPortal.getType() != this.entityType) {
               ImmPtlNetworking.LOGGER.error("Entity type mismatch when syncing portal {} {}", existingPortal, this.entityType);
               return;
            }

            existingPortal.acceptDataSync(new Vec3(this.x, this.y, this.z), this.extraData);
         } else {
            Entity entity = this.entityType.create(world, EntitySpawnReason.LOAD);
            Validate.notNull(entity, "Entity type is null");
            if (!(entity instanceof Portal portal)) {
               ImmPtlNetworking.LOGGER.error("Spawned entity is not a portal. {} {}", entity, this.entityType);
               return;
            }

            entity.setId(this.id);
            entity.setUUID(this.uuid);
            entity.syncPacketPositionCodec(this.x, this.y, this.z);
            entity.snapTo(this.x, this.y, this.z);
            portal.readPortalDataFromNbt(this.extraData);
            world.addEntity(entity);
            ClientWorldLoader.getWorld(portal.getDestDim());
            Portal.CLIENT_PORTAL_SPAWN_EVENT.invoker().accept(portal);
            if (IPGlobal.clientPortalLoadDebug) {
               ImmPtlNetworking.LOGGER.info("Portal loaded to client {}", portal);
            }
         }
      }

      @NotNull
      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public record TeleportPacket(int dimensionId, Vec3 eyePosBeforeTeleportation, UUID portalId) implements CustomPacketPayload {
      public static final Type<ImmPtlNetworking.TeleportPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath("imm_ptl", "teleport"));
      public static final StreamCodec<FriendlyByteBuf, ImmPtlNetworking.TeleportPacket> CODEC = StreamCodec.of(
         (b, p) -> p.write(b), ImmPtlNetworking.TeleportPacket::read
      );

      public static ImmPtlNetworking.TeleportPacket read(FriendlyByteBuf buf) {
         int dimId = buf.readVarInt();
         Vec3 pos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
         UUID portalId = buf.readUUID();
         return new ImmPtlNetworking.TeleportPacket(dimId, pos, portalId);
      }

      public void write(FriendlyByteBuf buf) {
         buf.writeVarInt(this.dimensionId);
         buf.writeDouble(this.eyePosBeforeTeleportation.x);
         buf.writeDouble(this.eyePosBeforeTeleportation.y);
         buf.writeDouble(this.eyePosBeforeTeleportation.z);
         buf.writeUUID(this.portalId);
      }

      public void handle(ServerPlayer player) {
         ResourceKey<Level> dim = PortalAPI.serverIntToDimKey(player.server, this.dimensionId);
         ServerTeleportationManager.of(player.server).onPlayerTeleportedInClient(player, dim, this.eyePosBeforeTeleportation, this.portalId);
      }

      @NotNull
      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }
}
