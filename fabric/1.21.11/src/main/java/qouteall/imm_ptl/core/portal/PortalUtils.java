package qouteall.imm_ptl.core.portal;

import com.google.common.collect.Streams;
import com.mojang.datafixers.util.Pair;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.portal.global_portals.GlobalPortalStorage;
import qouteall.q_misc_util.my_util.RayTraceResult;

public class PortalUtils {
   @NotNull
   public static Optional<Pair<Portal, RayTraceResult>> raytracePortals(
      Level world, Vec3 from, Vec3 to, boolean includeGlobalPortal, Predicate<Portal> predicate
   ) {
      return lenientRayTracePortals(world, from, to, includeGlobalPortal, predicate, 0.001);
   }

   @NotNull
   public static Optional<Pair<Portal, RayTraceResult>> lenientRayTracePortals(
      Level world, Vec3 from, Vec3 to, boolean includeGlobalPortal, Predicate<Portal> predicate, double leniency
   ) {
      Stream<Portal> portalStream = McHelper.getEntitiesNearby(world, from, Portal.class, from.distanceTo(to)).stream();
      if (includeGlobalPortal) {
         List<Portal> globalPortals = GlobalPortalStorage.getGlobalPortals(world);
         portalStream = Streams.concat(portalStream, globalPortals.stream());
      }

      return portalStream.<Pair<Portal, RayTraceResult>>map(portal -> new Pair<>(portal, portal.generalRayTrace(from, to, leniency)))
         .filter(portalAndHitPos -> portalAndHitPos.getSecond() != null && predicate.test(portalAndHitPos.getFirst()))
         .min(Comparator.comparingDouble(portalAndHitPos -> portalAndHitPos.getSecond().hitPos().distanceToSqr(from)));
   }

   public static Optional<Pair<Portal, RayTraceResult>> raytracePortalFromEntityView(
      Entity player, float partialTick, double maxDistance, boolean includeGlobalPortal, Predicate<Portal> predicate
   ) {
      Vec3 from = player.getEyePosition(partialTick);
      Vec3 to = from.add(player.getViewVector(partialTick).scale(maxDistance));
      Level world = player.level();
      return raytracePortals(world, from, to, includeGlobalPortal, predicate);
   }

   @Nullable
   public static PortalUtils.PortalAwareRaytraceResult portalAwareRayTrace(Entity entity, double maxDistance) {
      return portalAwareRayTrace(entity.level(), entity.getEyePosition(), entity.getViewVector(1.0F), maxDistance, entity);
   }

   @Nullable
   public static PortalUtils.PortalAwareRaytraceResult portalAwareRayTrace(Level world, Vec3 startingPoint, Vec3 direction, double maxDistance, Entity entity) {
      return portalAwareRayTrace(world, startingPoint, direction, maxDistance, entity, List.of());
   }

   @Nullable
   public static PortalUtils.PortalAwareRaytraceResult portalAwareRayTrace(
      Level world, Vec3 startingPoint, Vec3 direction, double maxDistance, Entity entity, Block clipContextBlock
   ) {
      return portalAwareRayTrace(world, startingPoint, direction, maxDistance, entity, clipContextBlock, List.of());
   }

   @Nullable
   public static PortalUtils.PortalAwareRaytraceResult portalAwareRayTrace(
      Level world, Vec3 startingPoint, Vec3 direction, double maxDistance, Entity entity, @NotNull List<Portal> portalsPassingThrough
   ) {
      return portalAwareRayTrace(world, startingPoint, direction, maxDistance, entity, Block.OUTLINE, portalsPassingThrough);
   }

   @Nullable
   public static PortalUtils.PortalAwareRaytraceResult portalAwareRayTrace(
      Level world, Vec3 startingPoint, Vec3 direction, double maxDistance, Entity entity, Block clipContextBlock, @NotNull List<Portal> portalsPassingThrough
   ) {
      return portalAwareRayTraceFull(world, startingPoint, direction, maxDistance, entity, clipContextBlock, Fluid.NONE, portalsPassingThrough, 5);
   }

   @Nullable
   public static PortalUtils.PortalAwareRaytraceResult portalAwareRayTraceFull(
      Level world,
      Vec3 startingPoint,
      Vec3 direction,
      double maxDistance,
      Entity entity,
      Block clipContextBlock,
      Fluid clipContextFluid,
      @NotNull List<Portal> portalsPassingThrough,
      int maxPortalLayer
   ) {
      if (portalsPassingThrough.size() > maxPortalLayer) {
         return null;
      }

      Vec3 endingPoint = startingPoint.add(direction.scale(maxDistance));
      Optional<Pair<Portal, RayTraceResult>> portalHit = raytracePortals(
         world, startingPoint, endingPoint, true, p -> entity instanceof Player player ? p.isInteractableBy(player) : p.isVisible()
      );
      ClipContext context = new ClipContext(startingPoint, endingPoint, clipContextBlock, clipContextFluid, entity);
      BlockHitResult blockHitResult = world.clip(context);
      boolean portalHitFound = portalHit.isPresent();
      boolean blockHitFound = blockHitResult.getType() == Type.BLOCK;
      boolean shouldContinueRaytraceInsidePortal = false;
      if (portalHitFound && blockHitFound) {
         RayTraceResult rtResult = portalHit.get().getSecond();
         double portalDistance = rtResult.hitPos().distanceTo(startingPoint);
         double blockDistance = blockHitResult.getLocation().distanceTo(startingPoint);
         if (!(portalDistance < blockDistance + 1.0E-4)) {
            return new PortalUtils.PortalAwareRaytraceResult(world, blockHitResult, portalsPassingThrough);
         }

         shouldContinueRaytraceInsidePortal = true;
      } else {
         if (!portalHitFound && blockHitFound) {
            return new PortalUtils.PortalAwareRaytraceResult(world, blockHitResult, portalsPassingThrough);
         }

         if (portalHitFound && !blockHitFound) {
            shouldContinueRaytraceInsidePortal = true;
         }
      }

      if (shouldContinueRaytraceInsidePortal) {
         RayTraceResult rtResult = portalHit.get().getSecond();
         double portalDistance = rtResult.hitPos().distanceTo(startingPoint);
         Portal portal = portalHit.get().getFirst();
         Vec3 newStartingPoint = portal.transformPoint(rtResult.hitPos()).add(rtResult.surfaceNormal().scale(-0.001));
         Vec3 newDirection = portal.transformLocalVecNonScale(direction);
         double restDistance = maxDistance - portalDistance;
         return restDistance < 0.0
            ? null
            : portalAwareRayTraceFull(
               portal.getDestinationWorld(),
               newStartingPoint,
               newDirection,
               restDistance,
               entity,
               clipContextBlock,
               clipContextFluid,
               Stream.concat(portalsPassingThrough.stream(), Stream.of(portal)).collect(Collectors.toList()),
               maxPortalLayer
            );
      } else {
         return null;
      }
   }

   public record PortalAwareRaytraceResult(Level world, @NotNull BlockHitResult hitResult, List<Portal> portalsPassingThrough) {
   }
}
