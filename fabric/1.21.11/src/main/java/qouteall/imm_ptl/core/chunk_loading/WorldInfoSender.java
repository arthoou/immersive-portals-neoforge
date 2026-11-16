package qouteall.imm_ptl.core.chunk_loading;

import java.util.Set;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import org.apache.commons.lang3.Validate;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.network.PacketRedirection;
import qouteall.q_misc_util.platform.Platform;

public class WorldInfoSender {
   public static void init() {
      Platform.get().onServerTickEnd(server -> {
         Profiler.get().push("portal_send_world_info");
         if (McHelper.getServerGameTime() % 100L == 42L) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
               Set<ResourceKey<Level>> visibleDimensions = ImmPtlChunkTracking.getVisibleDimensions(player);
               if (player.level().dimension() != Level.OVERWORLD) {
                  ServerLevel overworld = server.getLevel(Level.OVERWORLD);
                  Validate.notNull(overworld, "missing overworld");
                  sendWorldInfo(player, overworld);
               }

               server.getAllLevels().forEach(thisWorld -> {
                  if (isNonOverworldSurfaceDimension(thisWorld) && visibleDimensions.contains(thisWorld.dimension())) {
                     sendWorldInfo(player, thisWorld);
                  }
               });
            }
         }

         Profiler.get().pop();
      });
   }

   public static void sendWorldInfo(ServerPlayer player, ServerLevel world) {
      ResourceKey<Level> remoteDimension = world.dimension();
      PacketRedirection.sendRedirectedMessage(
         player, remoteDimension, new ClientboundSetTimePacket(world.getGameTime(), world.getDayTime(), world.getGameRules().get(GameRules.ADVANCE_TIME))
      );
      if (world.isRaining()) {
         PacketRedirection.sendRedirectedMessage(player, world.dimension(), new ClientboundGameEventPacket(ClientboundGameEventPacket.START_RAINING, 0.0F));
      }

      PacketRedirection.sendRedirectedMessage(
         player, world.dimension(), new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, world.getRainLevel(1.0F))
      );
      PacketRedirection.sendRedirectedMessage(
         player, world.dimension(), new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, world.getThunderLevel(1.0F))
      );
   }

   public static boolean isNonOverworldSurfaceDimension(Level world) {
      return world.dimensionType().hasSkyLight() && world.dimension() != Level.OVERWORLD;
   }
}
