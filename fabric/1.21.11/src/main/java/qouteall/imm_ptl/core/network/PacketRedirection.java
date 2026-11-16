package qouteall.imm_ptl.core.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.BundleDelimiterPacket;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.ducks.IEWorld;
import qouteall.q_misc_util.platform.PlatformNetworking;

public class PacketRedirection {
   private static final Logger LOGGER = LoggerFactory.getLogger(PacketRedirection.class);
   public static final Identifier payloadId = McHelper.newResourceLocation("i:r");
   private static final ThreadLocal<ResourceKey<Level>> serverPacketRedirection = ThreadLocal.withInitial(() -> null);
   private static final ThreadLocal<PacketRedirection.ForceBundleCallback> forceBundle = ThreadLocal.withInitial(() -> null);
   private static final ProtocolInfo<ClientGamePacketListener> PLACEHOLDER_PROTOCOL_INFO = GameProtocols.CLIENTBOUND_TEMPLATE
      .bind(argBuf -> (RegistryFriendlyByteBuf)argBuf);

   public static void init() {
      PlatformNetworking.get().registerPlayS2C(PacketRedirection.Payload.TYPE, PacketRedirection.Payload.CODEC);
   }

   public static void withForceRedirect(ServerLevel world, Runnable func) {
      withForceRedirectAndGet(world, () -> {
         func.run();
         return null;
      });
   }

   public static <T> T withForceRedirectAndGet(ServerLevel world, Supplier<T> func) {
      if (((IEWorld)world).portal_getThread() != Thread.currentThread()) {
         LOGGER.error(
            "It's possible that a mod is trying to handle packet in networking thread instead of server thread. This is not thread safe and can cause rare bugs! (ImmPtl is just doing checking, it's not an issue of iPortal)",
            new Throwable()
         );
      }

      ResourceKey<Level> redirectDim = world.dimension();
      ResourceKey<Level> oldRedirection = serverPacketRedirection.get();
      if (oldRedirection != redirectDim) {
         serverPacketRedirection.set(redirectDim);
      }

      try {
         return func.get();
      } finally {
         if (oldRedirection != redirectDim) {
            serverPacketRedirection.set(oldRedirection);
         }
      }
   }

   @Nullable
   public static ResourceKey<Level> getForceRedirectDimension() {
      return serverPacketRedirection.get();
   }

   public static void sendRedirectedPacket(
      ServerGamePacketListenerImpl serverPlayNetworkHandler, Packet<ClientGamePacketListener> packet, ResourceKey<Level> dimension
   ) {
      if (getForceRedirectDimension() == dimension) {
         serverPlayNetworkHandler.send(packet);
      } else {
         serverPlayNetworkHandler.send(createRedirectedMessage(serverPlayNetworkHandler.player.server, dimension, packet));
      }
   }

   public static void validateForceRedirecting() {
      Validate.isTrue(getForceRedirectDimension() != null);
   }

   public static Packet<ClientGamePacketListener> createRedirectedMessage(
      MinecraftServer server, ResourceKey<Level> dimension, Packet<ClientGamePacketListener> packet
   ) {
      if (isRedirectPacket(packet)) {
         return packet;
      } else {
         Validate.isTrue(!(packet instanceof BundleDelimiterPacket));
         if (!(packet instanceof ClientboundBundlePacket bundlePacket)) {
            int intDimId = PortalAPI.serverDimKeyToInt(server, dimension);
            PacketRedirection.Payload payload = new PacketRedirection.Payload(intDimId, packet);
            return (Packet<ClientGamePacketListener>)(Packet<?>)PlatformNetworking.get().createS2CPacket(payload);
         } else {
            List<Packet<ClientGamePacketListener>> newSubPackets = new ArrayList<>();

            for (Packet<? super ClientGamePacketListener> subPacket : bundlePacket.subPackets()) {
               newSubPackets.add(createRedirectedMessage(server, dimension, (Packet<ClientGamePacketListener>)subPacket));
            }

            return new ClientboundBundlePacket((List<Packet<? super ClientGamePacketListener>>)(List<?>)newSubPackets);
         }
      }
   }

   public static void sendRedirectedMessage(ServerPlayer player, ResourceKey<Level> dimension, Packet<ClientGamePacketListener> packet) {
      player.connection.send(createRedirectedMessage(player.server, dimension, packet));
   }

   public static boolean isRedirectPacket(Packet<?> packet) {
      return packet instanceof ClientboundCustomPayloadPacket customPayloadPacket && customPayloadPacket.payload() instanceof PacketRedirection.Payload;
   }

   public static <R> R withForceBundle(Supplier<R> func) {
      PacketRedirection.ForceBundleCallback forceBundleCallback = getForceBundleCallback();
      if (forceBundleCallback != null) {
         return func.get();
      }

      Map<ServerCommonPacketListenerImpl, List<Packet<ClientGamePacketListener>>> map = new HashMap<>();
      forceBundle.set((listenerx, packet) -> {
         List<Packet<ClientGamePacketListener>> packetsToBundle = map.computeIfAbsent(listenerx, k -> new ArrayList<>());
         if (packet instanceof BundlePacket<?> bundlePacket) {
            for (Packet<?> subPacket : bundlePacket.subPackets()) {
               packetsToBundle.add((Packet<ClientGamePacketListener>)subPacket);
            }
         } else {
            packetsToBundle.add(packet);
         }
      });

      try {
         return func.get();
      } finally {
         forceBundle.set(null);

         for (Entry<ServerCommonPacketListenerImpl, List<Packet<ClientGamePacketListener>>> e : map.entrySet()) {
            ServerCommonPacketListenerImpl listener = e.getKey();
            List<Packet<ClientGamePacketListener>> packets = e.getValue();
            listener.send(new ClientboundBundlePacket((List<Packet<? super ClientGamePacketListener>>)(List<?>)packets));
         }
      }
   }

   @Nullable
   public static PacketRedirection.ForceBundleCallback getForceBundleCallback() {
      return forceBundle.get();
   }

   public interface ForceBundleCallback {
      void accept(ServerCommonPacketListenerImpl var1, Packet<ClientGamePacketListener> var2);
   }

   public record Payload(int dimensionIntId, Packet<? extends ClientGamePacketListener> packet) implements CustomPacketPayload {
      public static final Type<PacketRedirection.Payload> TYPE = new Type<>(PacketRedirection.payloadId);
      public static final StreamCodec<RegistryFriendlyByteBuf, PacketRedirection.Payload> CODEC = StreamCodec.of(
         (b, p) -> p.write(b), PacketRedirection.Payload::read
      );

      public void write(RegistryFriendlyByteBuf buf) {
         Validate.notNull(this.packet, "packet is null");
         buf.writeVarInt(this.dimensionIntId);
         PacketRedirection.PLACEHOLDER_PROTOCOL_INFO.codec().encode(buf, (Packet<? super ClientGamePacketListener>)this.packet);
      }

      public static PacketRedirection.Payload read(FriendlyByteBuf buf) {
         int dimensionIntId = buf.readVarInt();
         Packet<ClientGamePacketListener> packet =
            (Packet<ClientGamePacketListener>)PacketRedirection.PLACEHOLDER_PROTOCOL_INFO.codec().decode(buf);
         return new PacketRedirection.Payload(dimensionIntId, packet);
      }

      @Environment(EnvType.CLIENT)
      public void handle(ClientGamePacketListener listener) {
         PacketRedirectionClient.handleRedirectedPacket(this.dimensionIntId, (Packet<ClientGamePacketListener>)this.packet, listener);
      }

      @NotNull
      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }
}
