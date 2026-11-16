package qouteall.q_misc_util;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientCommonPacketListener;
import net.minecraft.network.protocol.common.ServerCommonPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.McHelper;
import qouteall.q_misc_util.my_util.CountDownInt;
import qouteall.q_misc_util.my_util.DQuaternion;
import qouteall.q_misc_util.platform.PlatformNetworking;

public class ImplRemoteProcedureCall {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final CountDownInt LOGGING_LIMIT = new CountDownInt(100);
   private static final CountDownInt ERROR_MESSAGE_LIMIT = new CountDownInt(10);
   public static final Gson gson = MiscHelper.gson;
   private static final ConcurrentHashMap<String, Method> methodCache = new ConcurrentHashMap<>();
   private static final ImmutableMap<Class, BiConsumer<RegistryFriendlyByteBuf, Object>> serializerMap = ImmutableMap.<Class, BiConsumer<RegistryFriendlyByteBuf, Object>>builder()
      .put(Identifier.class, (buf, o) -> buf.writeIdentifier((Identifier)o))
      .put(ResourceKey.class, (buf, o) -> buf.writeIdentifier(((ResourceKey)o).identifier()))
      .put(BlockPos.class, (buf, o) -> buf.writeBlockPos((BlockPos)o))
      .put(Vec3.class, (buf, o) -> {
         Vec3 vec = (Vec3)o;
         buf.writeDouble(vec.x);
         buf.writeDouble(vec.y);
         buf.writeDouble(vec.z);
      })
      .put(UUID.class, (buf, o) -> buf.writeUUID((UUID)o))
      .put(Block.class, (buf, o) -> serializeByCodec(buf, BuiltInRegistries.BLOCK.byNameCodec(), o))
      .put(Item.class, (buf, o) -> serializeByCodec(buf, BuiltInRegistries.ITEM.byNameCodec(), o))
      .put(BlockState.class, (buf, o) -> serializeByCodec(buf, BlockState.CODEC, o))
      .put(ItemStack.class, (buf, o) -> serializeByCodec(buf, ItemStack.CODEC, o))
      .put(CompoundTag.class, (buf, o) -> buf.writeNbt((CompoundTag)o))
      .put(Component.class, (buf, o) -> ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, (Component)o))
      .put(DQuaternion.class, (buf, o) -> {
         DQuaternion dQuaternion = (DQuaternion)o;
         buf.writeDouble(dQuaternion.x);
         buf.writeDouble(dQuaternion.y);
         buf.writeDouble(dQuaternion.z);
         buf.writeDouble(dQuaternion.w);
      })
      .put(byte[].class, (buf, o) -> buf.writeByteArray((byte[])o))
      .build();
   private static final ImmutableMap<Type, Function<RegistryFriendlyByteBuf, Object>> deserializerMap = ImmutableMap.<Type, Function<RegistryFriendlyByteBuf, Object>>builder()
      .put(Identifier.class, FriendlyByteBuf::readIdentifier)
      .put((new TypeToken<ResourceKey<Level>>() {}).getType(), buf -> ResourceKey.create(Registries.DIMENSION, buf.readIdentifier()))
      .put((new TypeToken<ResourceKey<Biome>>() {}).getType(), buf -> ResourceKey.create(Registries.BIOME, buf.readIdentifier()))
      .put(BlockPos.class, buf -> buf.readBlockPos())
      .put(Vec3.class, buf -> new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()))
      .put(UUID.class, buf -> buf.readUUID())
      .put(Block.class, buf -> deserializeByCodec(buf, BuiltInRegistries.BLOCK.byNameCodec()))
      .put(Item.class, buf -> deserializeByCodec(buf, BuiltInRegistries.ITEM.byNameCodec()))
      .put(BlockState.class, buf -> deserializeByCodec(buf, BlockState.CODEC))
      .put(ItemStack.class, buf -> deserializeByCodec(buf, ItemStack.CODEC))
      .put(CompoundTag.class, buf -> buf.readNbt())
      .put(Component.class, ComponentSerialization.TRUSTED_STREAM_CODEC::decode)
      .put(DQuaternion.class, buf -> new DQuaternion(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble()))
      .put(byte[].class, buf -> buf.readByteArray())
      .build();

   public static void init() {
      PlatformNetworking.get().registerPlayC2S(ImplRemoteProcedureCall.C2SRPCPayload.TYPE, ImplRemoteProcedureCall.C2SRPCPayload.CODEC);
      PlatformNetworking.get().registerPlayS2C(ImplRemoteProcedureCall.S2CRPCPayload.TYPE, ImplRemoteProcedureCall.S2CRPCPayload.CODEC);
      PlatformNetworking.get().registerServerReceiver(ImplRemoteProcedureCall.C2SRPCPayload.TYPE, ImplRemoteProcedureCall.C2SRPCPayload::handle);
   }

   @Environment(EnvType.CLIENT)
   public static void initClient() {
      PlatformNetworking.get().registerClientReceiver(ImplRemoteProcedureCall.S2CRPCPayload.TYPE, ImplRemoteProcedureCall.S2CRPCPayload::handle);
   }

   private static Object deserializeByCodec(RegistryFriendlyByteBuf buf, Codec codec) {
      String jsonString = buf.readUtf();
      JsonElement jsonElement = JsonParser.parseString(jsonString);
      return codec.parse(JsonOps.INSTANCE, jsonElement).getOrThrow();
   }

   private static Object deserializeArgument(RegistryFriendlyByteBuf buf, Type type) {
      Function<RegistryFriendlyByteBuf, Object> deserializer = deserializerMap.get(type);
      if (deserializer == null) {
         String json = buf.readUtf();
         return gson.fromJson(json, type);
      } else {
         return deserializer.apply(buf);
      }
   }

   private static void serializeArgument(RegistryFriendlyByteBuf buf, Object object) {
      BiConsumer<RegistryFriendlyByteBuf, Object> serializer = serializerMap.get(object.getClass());
      if (serializer == null) {
         serializer = serializerMap.entrySet()
            .stream()
            .filter(e -> e.getKey().isAssignableFrom(object.getClass()))
            .findFirst()
            .map(Entry::getValue)
            .orElse(null);
      }

      if (serializer == null) {
         String json = gson.toJson(object);
         buf.writeUtf(json);
      } else {
         serializer.accept(buf, object);
      }
   }

   private static void serializeByCodec(FriendlyByteBuf buf, Codec codec, Object object) {
      JsonElement result = (JsonElement)codec.encodeStart(JsonOps.INSTANCE, object).getOrThrow();
      String jsonString = gson.toJson(result);
      buf.writeUtf(jsonString);
   }

   @Environment(EnvType.CLIENT)
   public static Packet<ServerCommonPacketListener> createC2SPacket(String methodPath, Object... arguments) {
      return PlatformNetworking.get().createC2SPacket(new ImplRemoteProcedureCall.C2SRPCPayload(true, methodPath, null, List.of(arguments)));
   }

   public static Packet<ClientCommonPacketListener> createS2CPacket(String methodPath, Object... arguments) {
      return PlatformNetworking.get().createS2CPacket(new ImplRemoteProcedureCall.S2CRPCPayload(true, methodPath, null, List.of(arguments)));
   }

   @Environment(EnvType.CLIENT)
   private static void clientTellFailure() {
      Minecraft.getInstance()
         .gui
         .getChat()
         .addMessage(Component.literal("The client failed to process a packet from server. See the log for details.").withStyle(ChatFormatting.RED));
   }

   private static void serverTellFailure(ServerPlayer player) {
      player.sendSystemMessage(Component.literal("The server failed to process a packet sent from client.").withStyle(ChatFormatting.RED));
   }

   private static Method getMethodByPath(String methodPath) {
      Method result = methodCache.get(methodPath);
      if (result != null) {
         return result;
      }

      Method method = findMethodByPath(methodPath);
      Validate.notNull(method);
      methodCache.put(methodPath, method);
      return method;
   }

   private static Method findMethodByPath(String methodPath) {
      int lastDotIndex = methodPath.lastIndexOf(46);
      Validate.isTrue(lastDotIndex != -1);
      String classPath = methodPath.substring(0, lastDotIndex);
      String methodName = methodPath.substring(lastDotIndex + 1);
      if (!classPath.contains("RemoteCallable")) {
         throw new RuntimeException("The class path must contain \"RemoteCallable\"");
      }

      Class<?> aClass;
      try {
         aClass = Class.forName(classPath);
      } catch (ClassNotFoundException e) {
         int dotIndex = classPath.lastIndexOf(46);
         if (dotIndex == -1) {
            throw new RuntimeException("Cannot find class " + classPath, e);
         }

         String newClassPath = classPath.substring(0, dotIndex) + "$" + classPath.substring(dotIndex + 1);

         try {
            aClass = Class.forName(newClassPath);
         } catch (ClassNotFoundException e1) {
            throw new RuntimeException("Cannot find class " + classPath, e);
         }
      }

      return Arrays.stream(aClass.getMethods())
         .filter(m -> m.getName().equals(methodName))
         .findFirst()
         .orElseThrow(() -> new RuntimeException("Cannot find method " + methodPath + " . If it's a private method, make it public."));
   }

   public record C2SRPCPayload(boolean deserializeSuccess, @Nullable String methodPath, @Nullable Method method, @Nullable List<Object> args)
      implements CustomPacketPayload {
      public static final net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<ImplRemoteProcedureCall.C2SRPCPayload> TYPE = new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(
         McHelper.newResourceLocation("iportal:remote_c2s")
      );
      public static final StreamCodec<RegistryFriendlyByteBuf, ImplRemoteProcedureCall.C2SRPCPayload> CODEC = StreamCodec.of(
         (b, p) -> p.write(b), ImplRemoteProcedureCall.C2SRPCPayload::read
      );

      public static ImplRemoteProcedureCall.C2SRPCPayload read(RegistryFriendlyByteBuf buf) {
         String methodPath = null;

         try {
            methodPath = buf.readUtf();
            Method method = ImplRemoteProcedureCall.getMethodByPath(methodPath);
            java.lang.reflect.Type[] genericParameterTypes = method.getGenericParameterTypes();
            List<Object> args = new ArrayList<>();

            for (int i = 1; i < genericParameterTypes.length; i++) {
               java.lang.reflect.Type parameterType = genericParameterTypes[i];
               Object obj = ImplRemoteProcedureCall.deserializeArgument(buf, parameterType);
               args.add(obj);
            }

            return new ImplRemoteProcedureCall.C2SRPCPayload(true, methodPath, method, args);
         } catch (Exception e) {
            if (ImplRemoteProcedureCall.LOGGING_LIMIT.tryDecrement()) {
               ImplRemoteProcedureCall.LOGGER.error("Failed to parse remote procedure call {}", methodPath, e);
            }

            return new ImplRemoteProcedureCall.C2SRPCPayload(false, methodPath, null, null);
         }
      }

      public void write(RegistryFriendlyByteBuf buf) {
         Validate.notNull(this.args, "args must not be null");
         Validate.notNull(this.methodPath, "methodPath must not be null");
         buf.writeUtf(this.methodPath);

         for (Object arg : this.args) {
            ImplRemoteProcedureCall.serializeArgument(buf, arg);
         }
      }

      public void handle(ServerPlayer player) {
         if (!this.deserializeSuccess) {
            if (ImplRemoteProcedureCall.ERROR_MESSAGE_LIMIT.tryDecrement()) {
               ImplRemoteProcedureCall.serverTellFailure(player);
            }
         } else {
            Validate.notNull(this.args, "args must not be null");
            Validate.notNull(this.method, "method must not be null");

            try {
               Object[] argArray = new Object[this.args.size() + 1];
               argArray[0] = player;

               for (int i = 0; i < this.args.size(); i++) {
                  argArray[i + 1] = this.args.get(i);
               }

               this.method.invoke(null, argArray);
            } catch (Exception e) {
               if (ImplRemoteProcedureCall.LOGGING_LIMIT.tryDecrement()) {
                  ImplRemoteProcedureCall.LOGGER.error("Failed to invoke remote procedure call {} {}", this.methodPath, player, e);
                  ImplRemoteProcedureCall.serverTellFailure(player);
               }
            }
         }
      }

      @NotNull
      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public record S2CRPCPayload(boolean deserializeSuccess, @Nullable String methodPath, @Nullable Method method, @Nullable List<Object> args)
      implements CustomPacketPayload {
      public static final net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<ImplRemoteProcedureCall.S2CRPCPayload> TYPE = new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(
         McHelper.newResourceLocation("iportal:remote_s2c")
      );
      public static final StreamCodec<RegistryFriendlyByteBuf, ImplRemoteProcedureCall.S2CRPCPayload> CODEC = StreamCodec.of(
         (b, p) -> p.write(b), ImplRemoteProcedureCall.S2CRPCPayload::read
      );

      public static ImplRemoteProcedureCall.S2CRPCPayload read(RegistryFriendlyByteBuf buf) {
         String methodPath = null;

         try {
            methodPath = buf.readUtf();
            Method method = ImplRemoteProcedureCall.getMethodByPath(methodPath);
            java.lang.reflect.Type[] genericParameterTypes = method.getGenericParameterTypes();
            List<Object> args = new ArrayList<>();

            for (java.lang.reflect.Type parameterType : genericParameterTypes) {
               Object obj = ImplRemoteProcedureCall.deserializeArgument(buf, parameterType);
               args.add(obj);
            }

            return new ImplRemoteProcedureCall.S2CRPCPayload(true, methodPath, method, args);
         } catch (Exception e) {
            if (ImplRemoteProcedureCall.LOGGING_LIMIT.tryDecrement()) {
               ImplRemoteProcedureCall.LOGGER.error("Failed to parse remote procedure call {}", methodPath, e);
            }

            return new ImplRemoteProcedureCall.S2CRPCPayload(false, methodPath, null, null);
         }
      }

      public void write(RegistryFriendlyByteBuf buf) {
         Validate.notNull(this.args, "args must not be null");
         Validate.notNull(this.methodPath, "methodPath must not be null");
         buf.writeUtf(this.methodPath);

         for (Object arg : this.args) {
            ImplRemoteProcedureCall.serializeArgument(buf, arg);
         }
      }

      @Environment(EnvType.CLIENT)
      public void handle(Minecraft client) {
         if (!this.deserializeSuccess) {
            if (ImplRemoteProcedureCall.ERROR_MESSAGE_LIMIT.tryDecrement()) {
               ImplRemoteProcedureCall.clientTellFailure();
            }
         } else {
            Validate.notNull(this.args, "args must not be null");
            Validate.notNull(this.method, "method must not be null");

            try {
               Object[] argArray = this.args.toArray(new Object[0]);
               this.method.invoke(null, argArray);
            } catch (Exception e) {
               if (ImplRemoteProcedureCall.LOGGING_LIMIT.tryDecrement()) {
                  ImplRemoteProcedureCall.LOGGER.error("Failed to invoke remote procedure call {}", this.methodPath, e);
                  ImplRemoteProcedureCall.clientTellFailure();
               }
            }
         }
      }

      @NotNull
      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }
}
