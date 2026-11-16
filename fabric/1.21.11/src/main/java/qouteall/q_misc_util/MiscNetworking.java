package qouteall.q_misc_util;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientCommonPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.McHelper;
import qouteall.q_misc_util.dimension.DimIntIdMap;
import qouteall.q_misc_util.dimension.DimensionIntId;
import qouteall.q_misc_util.platform.PlatformNetworking;

public class MiscNetworking {
   private static final Logger LOGGER = LogUtils.getLogger();

   @Environment(EnvType.CLIENT)
   public static void initClient() {
      PlatformNetworking.get().registerClientReceiver(MiscNetworking.DimIdSyncPacket.TYPE, (p, c) -> p.handle());
   }

   public static void init() {
      PlatformNetworking.get().registerPlayS2C(MiscNetworking.DimIdSyncPacket.TYPE, MiscNetworking.DimIdSyncPacket.CODEC);
   }

   public record DimIdSyncPacket(CompoundTag dimIntIdTag, CompoundTag dimTypeTag) implements CustomPacketPayload {
      public static final Type<MiscNetworking.DimIdSyncPacket> TYPE = new Type<>(McHelper.newResourceLocation("imm_ptl:dim_int_id_sync"));
      public static final StreamCodec<FriendlyByteBuf, MiscNetworking.DimIdSyncPacket> CODEC = StreamCodec.of(
         (b, p) -> p.write(b), MiscNetworking.DimIdSyncPacket::read
      );

      public static MiscNetworking.DimIdSyncPacket createFromServer(MinecraftServer server) {
         DimIntIdMap rec = DimensionIntId.getServerMap(server);
         CompoundTag dimIntIdTag = rec.toTag(dim -> true);
         RegistryAccess registryManager = server.registryAccess();
         Registry<DimensionType> dimensionTypes = registryManager.lookupOrThrow(Registries.DIMENSION_TYPE);
         CompoundTag dimIdToDimTypeIdTag = new CompoundTag();

         for (ServerLevel world : server.getAllLevels()) {
            ResourceKey<Level> dimId = world.dimension();
            DimensionType dimType = world.dimensionType();
            Identifier dimTypeId = dimensionTypes.getKey(dimType);
            if (dimTypeId == null) {
               MiscNetworking.LOGGER.error("Cannot find dimension type for {}", dimId.identifier());
               MiscNetworking.LOGGER.error("Registered dimension types {}", dimensionTypes.keySet());
               dimTypeId = BuiltinDimensionTypes.OVERWORLD.identifier();
            }

            dimIdToDimTypeIdTag.putString(dimId.identifier().toString(), dimTypeId.toString());
         }

         return new MiscNetworking.DimIdSyncPacket(dimIntIdTag, dimIdToDimTypeIdTag);
      }

      public static Packet<ClientCommonPacketListener> createPacket(MinecraftServer server) {
         return PlatformNetworking.get().createS2CPacket(createFromServer(server));
      }

      public void write(FriendlyByteBuf buf) {
         buf.writeNbt(this.dimIntIdTag);
         buf.writeNbt(this.dimTypeTag);
      }

      public static MiscNetworking.DimIdSyncPacket read(FriendlyByteBuf buf) {
         CompoundTag idMapTag = buf.readNbt();
         CompoundTag typeTag = buf.readNbt();
         return new MiscNetworking.DimIdSyncPacket(idMapTag, typeTag);
      }

      public void handle() {
         DimIntIdMap rec = DimIntIdMap.fromTag(this.dimIntIdTag);
         MiscNetworking.LOGGER.info("Client received dim id sync packet\n{}", rec);
         DimensionIntId.clientRecord = rec;
         Builder<ResourceKey<Level>, ResourceKey<DimensionType>> builder = new Builder<>();

         for (String key : this.dimTypeTag.keySet()) {
            ResourceKey<Level> dimId = ResourceKey.create(Registries.DIMENSION, McHelper.newResourceLocation(key));
            String dimTypeId = this.dimTypeTag.getStringOr(key, "");
            ResourceKey<DimensionType> dimType = ResourceKey.create(Registries.DIMENSION_TYPE, McHelper.newResourceLocation(dimTypeId));
            builder.put(dimId, dimType);
         }

         ImmutableMap<ResourceKey<Level>, ResourceKey<DimensionType>> dimTypeMap = builder.build();
         ClientWorldLoader.dimIdToDimTypeId = dimTypeMap;
         MiscNetworking.LOGGER.info("Client accepted dimension type mapping {}", dimTypeMap);
      }

      @NotNull
      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }
}
