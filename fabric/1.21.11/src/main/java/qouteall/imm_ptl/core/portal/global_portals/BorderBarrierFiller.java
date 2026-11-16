package qouteall.imm_ptl.core.portal.global_portals;

import com.google.common.base.Supplier;
import com.google.common.collect.Streams;
import java.util.List;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.McHelper;
import qouteall.q_misc_util.my_util.IntBox;

public class BorderBarrierFiller {
   private static final WeakHashMap<ServerPlayer, Object> warnedPlayers = new WeakHashMap<>();

   public static void onCommandExecuted(ServerPlayer player) {
      ServerLevel world = player.level();
      Vec3 playerPos = player.position();
      List<WorldWrappingPortal.WrappingZone> wrappingZones = WorldWrappingPortal.getWrappingZones(world);
      WorldWrappingPortal.WrappingZone zone = wrappingZones.stream()
         .filter(wrappingZone -> wrappingZone.getArea().contains(playerPos))
         .findFirst()
         .orElse(null);
      if (zone == null) {
         player.displayClientMessage(Component.translatable("imm_ptl.cannot_find_zone"), false);
      } else {
         doInvoke(player, world, zone);
      }
   }

   public static void onCommandExecuted(ServerPlayer player, int zoneId) {
      ServerLevel world = player.level();
      List<WorldWrappingPortal.WrappingZone> wrappingZones = WorldWrappingPortal.getWrappingZones(world);
      WorldWrappingPortal.WrappingZone zone = wrappingZones.stream().filter(wrappingZone -> wrappingZone.id == zoneId).findFirst().orElse(null);
      if (zone == null) {
         player.displayClientMessage(Component.translatable("imm_ptl.cannot_find_zone"), false);
      } else {
         doInvoke(player, world, zone);
      }
   }

   private static void doInvoke(ServerPlayer player, ServerLevel world, WorldWrappingPortal.WrappingZone zone) {
      IntBox borderBox = zone.getBorderBox();
      boolean warned = warnedPlayers.containsKey(player);
      if (!warned) {
         warnedPlayers.put(player, null);
         BlockPos size = borderBox.getSize();
         int totalColumns = size.getX() * 2 + size.getZ() * 2;
         double sizeEstimationGB = totalColumns / 80000.0 * 0.5;
         player.displayClientMessage(Component.translatable("imm_ptl.clear_border_warning", sizeEstimationGB < 0.01 ? 0.0 : sizeEstimationGB), false);
      } else {
         warnedPlayers.remove(player);
         player.displayClientMessage(Component.translatable("imm_ptl.start_clearing_border"), false);
         startFillingBorder(world, borderBox, l -> player.displayClientMessage(l, false));
      }
   }

   private static void startFillingBorder(ServerLevel world, IntBox borderBox, Consumer<Component> informer) {
      Supplier<IntStream> xStream = () -> IntStream.range(borderBox.l.getX(), borderBox.h.getX() + 1);
      Supplier<IntStream> zStream = () -> IntStream.range(borderBox.l.getZ(), borderBox.h.getZ() + 1);
      MutableBlockPos temp = new MutableBlockPos();
      MutableBlockPos temp1 = new MutableBlockPos();
      Stream<MutableBlockPos> stream = Streams.concat(
         xStream.get().mapToObj(x -> temp.set(x, 0, borderBox.l.getZ())),
         xStream.get().mapToObj(x -> temp.set(x, 0, borderBox.h.getZ())),
         zStream.get().mapToObj(z -> temp.set(borderBox.l.getX(), 0, z)),
         zStream.get().mapToObj(z -> temp.set(borderBox.h.getX(), 0, z))
      );
      BlockPos size = borderBox.getSize();
      int totalColumns = size.getX() * 2 + size.getZ() * 2;
      int minY = McHelper.getMinY(world);
      int maxYEx = McHelper.getMaxYExclusive(world);
      ThreadedLevelLightEngine lightingProvider = world.getChunkSource().getLightEngine();
      McHelper.performMultiThreadedFindingTaskOnServer(world.getServer(), stream, columnPos -> {
         ChunkAccess chunk = world.getChunk(columnPos);

         for (int y = minY; y < maxYEx; y++) {
            temp1.set(columnPos.getX(), y, columnPos.getZ());
            chunk.setBlockState(temp1, Blocks.AIR.defaultBlockState(), 1);
            lightingProvider.checkBlock(temp1);
         }

         return false;
      }, columns -> {
         if (McHelper.getServerGameTime() % 20L == 0L) {
            informer.accept(Component.literal(String.format("Progress: %d / %d", columns, totalColumns)));
         }

         return true;
      }, e -> {}, () -> informer.accept(Component.translatable("imm_ptl.finished_clearing_border")), () -> {});
   }
}
