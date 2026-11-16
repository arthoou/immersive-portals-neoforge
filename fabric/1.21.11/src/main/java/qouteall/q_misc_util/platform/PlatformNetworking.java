package qouteall.q_misc_util.platform;

import com.mojang.authlib.GameProfile;
import java.util.ServiceLoader;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientCommonPacketListener;
import net.minecraft.network.protocol.common.ServerCommonPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.chunk.LevelChunk;

public interface PlatformNetworking {
   static PlatformNetworking get() {
      return PlatformNetworking.Holder.INSTANCE;
   }

   <T extends CustomPacketPayload> void registerPlayS2C(Type<T> var1, StreamCodec<? super RegistryFriendlyByteBuf, T> var2);

   <T extends CustomPacketPayload> void registerPlayC2S(Type<T> var1, StreamCodec<? super RegistryFriendlyByteBuf, T> var2);

   <T extends CustomPacketPayload> void registerServerReceiver(Type<T> var1, PlatformNetworking.ServerPlayReceiver<T> var2);

   <T extends CustomPacketPayload> void registerClientReceiver(Type<T> var1, PlatformNetworking.ClientPlayReceiver<T> var2);

   Packet<ClientCommonPacketListener> createS2CPacket(CustomPacketPayload var1);

   Packet<ServerCommonPacketListener> createC2SPacket(CustomPacketPayload var1);

   <T extends CustomPacketPayload> void registerConfigurationS2C(Type<T> var1, StreamCodec<? super FriendlyByteBuf, T> var2);

   <T extends CustomPacketPayload> void registerConfigurationC2S(Type<T> var1, StreamCodec<? super FriendlyByteBuf, T> var2);

   <T extends CustomPacketPayload> void registerServerConfigurationReceiver(Type<T> var1, PlatformNetworking.ServerConfigurationReceiver<T> var2);

   <T extends CustomPacketPayload> void registerClientConfigurationReceiver(Type<T> var1, PlatformNetworking.ClientConfigurationReceiver<T> var2);

   void onServerConfigure(PlatformNetworking.ServerConfigureHandler var1);

   Packet<ClientCommonPacketListener> createConfigurationS2CPacket(CustomPacketPayload var1);

   default Packet<?> decorateChunkPacket(LevelChunk chunk, ClientboundLevelChunkWithLightPacket packet) {
      return packet;
   }

   void onChunkSentToPlayer(ServerGamePacketListenerImpl var1, ServerLevel var2, LevelChunk var3);

   @FunctionalInterface
   interface ClientConfigurationReceiver<T extends CustomPacketPayload> {
      void receive(T var1, Consumer<CustomPacketPayload> var2);
   }

   @FunctionalInterface
   interface ClientPlayReceiver<T extends CustomPacketPayload> {
      void receive(T var1, Minecraft var2);
   }

   class Holder {
      private static final PlatformNetworking INSTANCE = ServiceLoader.load(PlatformNetworking.class)
         .findFirst()
         .orElseThrow(() -> new IllegalStateException("No PlatformNetworking implementation found"));
   }

   interface ServerConfigurationContext {
      GameProfile gameProfile();

      void completeTask(net.minecraft.server.network.ConfigurationTask.Type var1);

      void disconnect(Component var1);
   }

   interface ServerConfigurationControl {
      boolean canSend(Type<?> var1);

      void addTask(ConfigurationTask var1);

      void disconnect(Component var1);

      GameProfile gameProfile();
   }

   @FunctionalInterface
   interface ServerConfigurationReceiver<T extends CustomPacketPayload> {
      void receive(T var1, PlatformNetworking.ServerConfigurationContext var2);
   }

   @FunctionalInterface
   interface ServerConfigureHandler {
      void onConfigure(PlatformNetworking.ServerConfigurationControl var1, MinecraftServer var2);
   }

   @FunctionalInterface
   interface ServerPlayReceiver<T extends CustomPacketPayload> {
      void receive(T var1, ServerPlayer var2);
   }
}
