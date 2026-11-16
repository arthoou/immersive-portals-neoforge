package qouteall.dimlib;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qouteall.dimlib.api.DimensionAPI;
import qouteall.dimlib.mixin.client.IClientPacketListener;
import qouteall.q_misc_util.platform.PlatformNetworking;

public class DimLibNetworking {
   public static final Logger LOGGER = LoggerFactory.getLogger(DimLibNetworking.class);

   public static void init() {
      PlatformNetworking.get().registerPlayS2C(DimLibNetworking.DimSyncPacket.TYPE, DimLibNetworking.DimSyncPacket.CODEC);
   }

   @Environment(EnvType.CLIENT)
   public static void initClient() {
      PlatformNetworking.get().registerClientReceiver(DimLibNetworking.DimSyncPacket.TYPE, (p, c) -> p.handle(c.getConnection()));
   }

   public record DimSyncPacket(CompoundTag dimIdToTypeIdTag) implements CustomPacketPayload {
      public static final Type<DimLibNetworking.DimSyncPacket> TYPE = new Type<>(Identifier.parse("dimlib:dim_sync"));
      public static final StreamCodec<FriendlyByteBuf, DimLibNetworking.DimSyncPacket> CODEC = StreamCodec.of(
         (b, p) -> p.write(b), DimLibNetworking.DimSyncPacket::read
      );

      public static DimLibNetworking.DimSyncPacket read(FriendlyByteBuf buf) {
         CompoundTag compoundTag = buf.readNbt();
         return new DimLibNetworking.DimSyncPacket(compoundTag);
      }

      public void write(FriendlyByteBuf buf) {
         buf.writeNbt(this.dimIdToTypeIdTag);
      }

      public static DimLibNetworking.DimSyncPacket createPacket(MinecraftServer server) {
         RegistryAccess registryManager = server.registryAccess();
         Registry<DimensionType> dimensionTypes = registryManager.lookupOrThrow(Registries.DIMENSION_TYPE);
         CompoundTag dimIdToDimTypeId = new CompoundTag();

         for (ServerLevel world : server.getAllLevels()) {
            ResourceKey<Level> dimId = world.dimension();
            DimensionType dimType = world.dimensionType();
            Identifier dimTypeId = dimensionTypes.getKey(dimType);
            if (dimTypeId == null) {
               DimLibNetworking.LOGGER.error("Cannot find dimension type for {}", dimId.identifier());
               DimLibNetworking.LOGGER.error("Registered dimension types {}", dimensionTypes.keySet());
               dimTypeId = BuiltinDimensionTypes.OVERWORLD.identifier();
            }

            dimIdToDimTypeId.putString(dimId.identifier().toString(), dimTypeId.toString());
         }

         return new DimLibNetworking.DimSyncPacket(dimIdToDimTypeId);
      }

      public ImmutableMap<ResourceKey<Level>, ResourceKey<DimensionType>> toMap() {
         CompoundTag tag = this.dimIdToTypeIdTag();
         Builder<ResourceKey<Level>, ResourceKey<DimensionType>> builder = new Builder<>();

         for (String key : tag.keySet()) {
            ResourceKey<Level> dimId = ResourceKey.create(Registries.DIMENSION, Identifier.parse(key));
            String dimTypeId = tag.getStringOr(key, "");
            ResourceKey<DimensionType> dimType = ResourceKey.create(Registries.DIMENSION_TYPE, Identifier.parse(dimTypeId));
            builder.put(dimId, dimType);
         }

         return builder.build();
      }

      @Environment(EnvType.CLIENT)
      public void handle(ClientGamePacketListener listener) {
         Validate.isTrue(Minecraft.getInstance().isSameThread(), "Not running in client thread");
         DimLibNetworking.LOGGER.info("Client received dimension info\n{}", String.join("\n", this.dimIdToTypeIdTag.keySet()));
         ImmutableMap<ResourceKey<Level>, ResourceKey<DimensionType>> dimIdToDimType = this.toMap();
         ClientDimensionInfo.accept(dimIdToDimType);
         ((IClientPacketListener)listener).ip_setLevels(dimIdToDimType.keySet());
         DimensionAPI.CLIENT_DIMENSION_UPDATE_EVENT.invoker().run(ClientDimensionInfo.getDimensionIds());
      }

      @NotNull
      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }
}
