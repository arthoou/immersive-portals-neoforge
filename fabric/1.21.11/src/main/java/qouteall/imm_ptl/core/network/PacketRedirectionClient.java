package qouteall.imm_ptl.core.network;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.q_misc_util.dimension.DimensionIntId;
import qouteall.q_misc_util.my_util.LimitedLogger;

@Environment(EnvType.CLIENT)
public class PacketRedirectionClient {
   public static final Minecraft client = Minecraft.getInstance();
   private static final LimitedLogger limitedLogger = new LimitedLogger(100);
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final ThreadLocal<ResourceKey<Level>> clientTaskRedirection = ThreadLocal.withInitial(() -> null);

   public static boolean getIsProcessingRedirectedMessage() {
      return clientTaskRedirection.get() != null;
   }

   public static void handleRedirectedPacket(int dimensionIntId, Packet<ClientGamePacketListener> packet, ClientGamePacketListener handler) {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.isSameThread()) {
         ResourceKey<Level> dimension = DimensionIntId.getClientMap().fromIntegerId(dimensionIntId);
         ResourceKey<Level> oldTaskRedirection = clientTaskRedirection.get();
         clientTaskRedirection.set(dimension);

         try {
            ClientWorldLoader.withSwitchedWorldFailSoft(dimension, () -> packet.handle(handler));
         } finally {
            clientTaskRedirection.set(oldTaskRedirection);
         }
      } else {
         minecraft.packetProcessor().scheduleIfPossible(handler, new PacketRedirectionClient.QueuedRedirectedPacket(dimensionIntId, packet));
      }
   }

   @Deprecated
   public static void old_handleRedirectedPacket(ResourceKey<Level> dimension, Packet<ClientGamePacketListener> packet, ClientGamePacketListener handler) {
      ResourceKey<Level> oldTaskRedirection = clientTaskRedirection.get();
      clientTaskRedirection.set(dimension);

      try {
         if (Minecraft.getInstance().isSameThread()) {
            ClientWorldLoader.withSwitchedWorldFailSoft(dimension, () -> packet.handle(handler));
         } else {
            packet.handle(handler);
         }
      } finally {
         clientTaskRedirection.set(oldTaskRedirection);
      }
   }

   private record QueuedRedirectedPacket(int dimensionIntId, Packet<ClientGamePacketListener> packet) implements Packet<ClientGamePacketListener> {
      @Override
      public PacketType<? extends Packet<ClientGamePacketListener>> type() {
         return this.packet.type();
      }

      public void handle(ClientGamePacketListener listener) {
         try {
            PacketRedirectionClient.handleRedirectedPacket(this.dimensionIntId, this.packet, listener);
         } catch (Exception e) {
            if (BlockableEventLoop.isNonRecoverable(e)) {
               throw e;
            }

            PacketRedirectionClient.LOGGER.error("Error handling redirected packet {} of dimension id {}", this.packet, this.dimensionIntId, e);
         }
      }
   }
}
