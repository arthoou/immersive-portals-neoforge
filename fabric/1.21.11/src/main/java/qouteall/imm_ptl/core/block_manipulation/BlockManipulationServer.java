package qouteall.imm_ptl.core.block_manipulation;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Tuple;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult.Success;
import net.minecraft.world.InteractionResult.SwingSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.IPMcHelper;
import qouteall.imm_ptl.core.ScaleUtils;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;
import qouteall.imm_ptl.core.network.PacketRedirection;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalUtils;
import qouteall.imm_ptl.core.portal.global_portals.GlobalPortalStorage;
import qouteall.q_misc_util.platform.event.Event;
import qouteall.q_misc_util.platform.event.EventFactory;

public class BlockManipulationServer {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final ThreadLocal<BlockManipulationServer.Context> REDIRECT_CONTEXT = ThreadLocal.withInitial(() -> null);
   public static final Event<Predicate<Player>> canDoCrossPortalInteractionEvent = EventFactory.createArrayBacked(Predicate.class, handlers -> player -> {
      for (Predicate<Player> handler : handlers) {
         if (!handler.test(player)) {
            return false;
         }
      }

      return true;
   });

   private static boolean canPlayerReach(ResourceKey<Level> dimension, ServerPlayer player, BlockPos requestPos) {
      if (!canDoCrossPortalInteractionEvent.invoker().test(player)) {
         return false;
      }

      double playerScale = ScaleUtils.computeBlockReachScale(player);
      Vec3 pos = Vec3.atCenterOf(requestPos);
      Vec3 playerPos = player.position();
      double distanceSquare = 576.0 * playerScale * playerScale;
      return player.level().dimension() == dimension && playerPos.distanceToSqr(pos) < distanceSquare
         ? true
         : IPMcHelper.getNearbyPortals(player, IPGlobal.maxNormalPortalRadius)
            .anyMatch(
               portal -> portal.getDestDim() == dimension
                  && portal.isInteractableBy(player)
                  && portal.transformPoint(playerPos).distanceToSqr(pos) < distanceSquare * portal.getScale() * portal.getScale()
            );
   }

   public static Tuple<BlockHitResult, ResourceKey<Level>> getHitResultForPlacing(Level world, BlockHitResult blockHitResult) {
      Direction side = blockHitResult.getDirection();
      Vec3 sideVec = Vec3.atLowerCornerOf(side.getUnitVec3i());
      BlockPos hitPos = blockHitResult.getBlockPos();
      Vec3 hitCenter = Vec3.atCenterOf(hitPos);
      List<Portal> globalPortals = GlobalPortalStorage.getGlobalPortals(world);
      Portal portal = globalPortals.stream()
         .filter(
            p -> p.getNormal().dot(sideVec) < -0.9
               && p.getPortalShape().isBoxInPortalProjection(p.getThisSideState(), new AABB(hitPos))
               && p.getDistanceToPlane(hitCenter) < 0.6
         )
         .findFirst()
         .orElse(null);
      if (portal == null) {
         return new Tuple<>(blockHitResult, world.dimension());
      }

      Vec3 newCenter = portal.transformPoint(hitCenter.add(sideVec.scale(0.501)));
      BlockPos placingBlockPos = BlockPos.containing(newCenter);
      BlockHitResult newHitResult = new BlockHitResult(Vec3.ZERO, side.getOpposite(), placingBlockPos, blockHitResult.isInside());
      return new Tuple<>(newHitResult, portal.getDestDim());
   }

   public static void init() {
   }

   private static void withRedirect(BlockManipulationServer.Context context, Runnable runnable) {
      BlockManipulationServer.Context original = REDIRECT_CONTEXT.get();
      REDIRECT_CONTEXT.set(context);

      try {
         PacketRedirection.withForceRedirect(context.world(), runnable);
      } finally {
         REDIRECT_CONTEXT.set(original);
      }
   }

   @IPVanillaCopy
   private static void doProcessPlayerAction(ServerLevel world, ServerPlayer player, ServerboundPlayerActionPacket packet) {
      player.resetLastActionTime();
      BlockPos blockPos = packet.getPos();
      Action action = packet.getAction();
      if (!canPlayerReach(world.dimension(), player, blockPos)) {
         LOGGER.error("Reject cross-portal action {} {} {}", player, world, blockPos);
      } else {
         if (isAttackingAction(action)) {
            player.gameMode.handleBlockBreakAction(blockPos, action, packet.getDirection(), world.getMaxY() + 1, packet.getSequence());
            player.connection.ackBlockChangesUpTo(packet.getSequence());
         }
      }
   }

   public static boolean isAttackingAction(Action action) {
      return action == Action.START_DESTROY_BLOCK || action == Action.ABORT_DESTROY_BLOCK || action == Action.STOP_DESTROY_BLOCK;
   }

   @IPVanillaCopy
   private static void doProcessUseItemOn(ServerLevel world, ServerPlayer player, ServerboundUseItemOnPacket packet) {
      player.connection.ackBlockChangesUpTo(packet.getSequence());
      InteractionHand hand = packet.getHand();
      BlockHitResult blockHitResult = packet.getHitResult();
      ResourceKey<Level> dimension = world.dimension();
      ItemStack itemStack = player.getItemInHand(hand);
      if (itemStack.isItemEnabled(world.enabledFeatures())) {
         BlockPos blockPos = blockHitResult.getBlockPos();
         Direction direction = blockHitResult.getDirection();
         player.resetLastActionTime();
         if (world.mayInteract(player, blockPos)) {
            if (!canPlayerReach(dimension, player, blockPos)) {
               LOGGER.error("Reject cross-portal action {} {} {}", player, world, blockPos);
               return;
            }

            if (player.gameMode.useItemOn(player, world, itemStack, hand, blockHitResult) instanceof Success success
               && success.swingSource() == SwingSource.SERVER) {
               player.swing(hand, true);
            }
         }

         PacketRedirection.sendRedirectedMessage(player, dimension, new ClientboundBlockUpdatePacket(world, blockPos));
         BlockPos offseted = blockPos.relative(direction);
         if (offseted.getY() >= world.getMinY() && offseted.getY() < world.getMaxY() + 1) {
            PacketRedirection.sendRedirectedMessage(player, dimension, new ClientboundBlockUpdatePacket(world, offseted));
         }
      }
   }

   public static boolean validateReach(Player player, Level targetWorld, BlockPos targetPos) {
      PortalUtils.PortalAwareRaytraceResult result = PortalUtils.portalAwareRayTrace(
         player.level(), player.getEyePosition(), player.getViewVector(1.0F), 32.0, player, Block.COLLIDER
      );
      return result != null && result.world() == targetWorld && result.hitResult().getBlockPos().distManhattan(targetPos) < 8;
   }

   public record Context(ServerLevel world, @Nullable BlockHitResult blockHitResult) {
   }

   public static class RemoteCallables {
      public static void processPlayerActionPacket(ServerPlayer player, ResourceKey<Level> dimension, byte[] packetBytes) {
         FriendlyByteBuf buf = IPMcHelper.bytesToBuf(packetBytes);
         ServerboundPlayerActionPacket packet = ServerboundPlayerActionPacket.STREAM_CODEC.decode(buf);
         ServerLevel world = player.server.getLevel(dimension);
         Validate.notNull(world, "missing %s", dimension.identifier());
         BlockManipulationServer.withRedirect(
            new BlockManipulationServer.Context(world, null), () -> BlockManipulationServer.doProcessPlayerAction(world, player, packet)
         );
      }

      public static void processUseItemOnPacket(ServerPlayer player, ResourceKey<Level> dimension, byte[] packetBytes) {
         FriendlyByteBuf buf = IPMcHelper.bytesToBuf(packetBytes);
         ServerboundUseItemOnPacket packet = ServerboundUseItemOnPacket.STREAM_CODEC.decode(buf);
         ServerLevel world = player.server.getLevel(dimension);
         Validate.notNull(world, "missing %s", dimension.identifier());
         BlockManipulationServer.withRedirect(
            new BlockManipulationServer.Context(world, packet.getHitResult()), () -> BlockManipulationServer.doProcessUseItemOn(world, player, packet)
         );
      }
   }
}
