package qouteall.q_misc_util.api;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientCommonPacketListener;
import net.minecraft.network.protocol.common.ServerCommonPacketListener;
import net.minecraft.server.level.ServerPlayer;
import qouteall.q_misc_util.ImplRemoteProcedureCall;

public class McRemoteProcedureCall {
   public static void tellClientToInvoke(ServerPlayer player, String methodPath, Object... arguments) {
      Packet<ClientCommonPacketListener> packet = createPacketToSendToClient(methodPath, arguments);
      player.connection.send(packet);
   }

   public static Packet<ClientCommonPacketListener> createPacketToSendToClient(String methodPath, Object... arguments) {
      return ImplRemoteProcedureCall.createS2CPacket(methodPath, arguments);
   }

   @Environment(EnvType.CLIENT)
   public static void tellServerToInvoke(String methodPath, Object... arguments) {
      Packet<ServerCommonPacketListener> packet = createPacketToSendToServer(methodPath, arguments);
      Minecraft.getInstance().getConnection().send(packet);
   }

   public static Packet<ServerCommonPacketListener> createPacketToSendToServer(String methodPath, Object... arguments) {
      return ImplRemoteProcedureCall.createC2SPacket(methodPath, arguments);
   }
}
