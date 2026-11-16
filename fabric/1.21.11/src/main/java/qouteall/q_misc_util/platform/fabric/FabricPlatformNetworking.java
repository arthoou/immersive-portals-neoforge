package qouteall.q_misc_util.platform.fabric;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents.Configure;
import net.fabricmc.fabric.impl.attachment.AttachmentTargetImpl;
import net.fabricmc.fabric.impl.attachment.sync.AttachmentChange;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientCommonPacketListener;
import net.minecraft.network.protocol.common.ServerCommonPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.chunk.LevelChunk;
import qouteall.imm_ptl.core.mixin.common.other_sync.IEServerConfigurationPacketListenerImpl;
import qouteall.q_misc_util.platform.PlatformNetworking;

public class FabricPlatformNetworking implements PlatformNetworking {
   @Override
   public <T extends CustomPacketPayload> void registerPlayS2C(Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
      PayloadTypeRegistry.playS2C().register(type, codec);
   }

   @Override
   public <T extends CustomPacketPayload> void registerPlayC2S(Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
      PayloadTypeRegistry.playC2S().register(type, codec);
   }

   @Override
   public <T extends CustomPacketPayload> void registerServerReceiver(Type<T> type, PlatformNetworking.ServerPlayReceiver<T> handler) {
      ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) -> handler.receive((T)payload, context.player()));
   }

   @Environment(EnvType.CLIENT)
   @Override
   public <T extends CustomPacketPayload> void registerClientReceiver(Type<T> type, PlatformNetworking.ClientPlayReceiver<T> handler) {
      ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) -> handler.receive((T)payload, context.client()));
   }

   @Override
   public Packet<ClientCommonPacketListener> createS2CPacket(CustomPacketPayload payload) {
      return ServerPlayNetworking.createS2CPacket(payload);
   }

   @Environment(EnvType.CLIENT)
   @Override
   public Packet<ServerCommonPacketListener> createC2SPacket(CustomPacketPayload payload) {
      return ClientPlayNetworking.createC2SPacket(payload);
   }

   @Override
   public <T extends CustomPacketPayload> void registerConfigurationS2C(Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec) {
      PayloadTypeRegistry.configurationS2C().register(type, codec);
   }

   @Override
   public <T extends CustomPacketPayload> void registerConfigurationC2S(Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec) {
      PayloadTypeRegistry.configurationC2S().register(type, codec);
   }

   @Override
   public <T extends CustomPacketPayload> void registerServerConfigurationReceiver(Type<T> type, PlatformNetworking.ServerConfigurationReceiver<T> handler) {
      ServerConfigurationNetworking.registerGlobalReceiver(
         type, (payload, context) -> handler.receive((T)payload, new FabricPlatformNetworking.FabricServerConfigurationContext(context.networkHandler()))
      );
   }

   @Environment(EnvType.CLIENT)
   @Override
   public <T extends CustomPacketPayload> void registerClientConfigurationReceiver(Type<T> type, PlatformNetworking.ClientConfigurationReceiver<T> handler) {
      ClientConfigurationNetworking.registerGlobalReceiver(type, (payload, context) -> handler.receive((T)payload, context.responseSender()::sendPacket));
   }

   @Override
   public void onServerConfigure(PlatformNetworking.ServerConfigureHandler handler) {
      ServerConfigurationConnectionEvents.CONFIGURE
         .register(
            (Configure)(networkHandler, server) -> handler.onConfigure(new FabricPlatformNetworking.FabricServerConfigurationControl(networkHandler), server)
         );
   }

   @Override
   public Packet<ClientCommonPacketListener> createConfigurationS2CPacket(CustomPacketPayload payload) {
      return ServerConfigurationNetworking.createS2CPacket(payload);
   }

   @Override
   public void onChunkSentToPlayer(ServerGamePacketListenerImpl listener, ServerLevel level, LevelChunk chunk) {
      ServerPlayer player = listener.player;
      List<AttachmentChange> changes = new ArrayList<>();
      ((AttachmentTargetImpl)chunk).fabric_computeInitialSyncChanges(player, changes::add);
      if (!changes.isEmpty()) {
         AttachmentChange.partitionAndSendPackets(changes, player);
      }
   }

   private record FabricServerConfigurationContext(ServerConfigurationPacketListenerImpl networkHandler)
      implements PlatformNetworking.ServerConfigurationContext {
      @Override
      public GameProfile gameProfile() {
         return ((IEServerConfigurationPacketListenerImpl)this.networkHandler).ip_getGameProfile();
      }

      @Override
      public void completeTask(net.minecraft.server.network.ConfigurationTask.Type type) {
         this.networkHandler.completeTask(type);
      }

      @Override
      public void disconnect(Component reason) {
         this.networkHandler.disconnect(reason);
      }
   }

   private record FabricServerConfigurationControl(ServerConfigurationPacketListenerImpl handler) implements PlatformNetworking.ServerConfigurationControl {
      @Override
      public boolean canSend(Type<?> type) {
         return ServerConfigurationNetworking.canSend(this.handler, type);
      }

      @Override
      public void addTask(ConfigurationTask task) {
         this.handler.addTask(task);
      }

      @Override
      public void disconnect(Component reason) {
         this.handler.disconnect(reason);
      }

      @Override
      public GameProfile gameProfile() {
         return ((IEServerConfigurationPacketListenerImpl)this.handler).ip_getGameProfile();
      }
   }
}
