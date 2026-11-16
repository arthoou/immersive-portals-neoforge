package qouteall.imm_ptl.core;

import com.mojang.blaze3d.systems.RenderSystem;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.ClickEvent.RunCommand;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import qouteall.imm_ptl.core.ducks.IERayTraceContext;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.global_portals.GlobalPortalStorage;
import qouteall.imm_ptl.core.render.CrossPortalEntityRenderer;
import qouteall.q_misc_util.my_util.LimitedLogger;

public class IPMcHelper {
   public static final LimitedLogger limitedLogger = new LimitedLogger(20);

   public static void foreachNearbyPortals(Level world, Vec3 pos, int range, Consumer<Portal> func) {
      for (Portal globalPortal : GlobalPortalStorage.getGlobalPortals(world)) {
         if (globalPortal.getDistanceToNearestPointInPortal(pos) < range * 2) {
            func.accept(globalPortal);
         }
      }

      McHelper.foreachEntitiesByPointAndRoughRadius(Portal.class, world, pos, range, func);
   }

   public static List<Portal> getNearbyPortalList(Entity center, double range, Predicate<Portal> predicate) {
      return getNearbyPortalList(center.level(), center.position(), range, predicate);
   }

   public static List<Portal> getNearbyPortalList(Level world, Vec3 pos, double range, Predicate<Portal> predicate) {
      List<Portal> result = new ArrayList<>();
      foreachNearbyPortals(world, pos, (int)range, portal -> {
         if (predicate.test(portal)) {
            result.add(portal);
         }
      });
      return result;
   }

   public static Stream<Portal> getNearbyPortals(Entity center, double range) {
      return getNearbyPortals(center.level(), center.position(), range);
   }

   public static Stream<Portal> getNearbyPortals(Level world, Vec3 pos, double range) {
      return getNearbyPortalList(world, pos, range, e -> true).stream();
   }

   public static void traverseNearbyPortals(Level world, Vec3 pos, int range, Consumer<Portal> func) {
      for (Portal globalPortal : GlobalPortalStorage.getGlobalPortals(world)) {
         if (globalPortal.getDistanceToNearestPointInPortal(pos) < range * 2) {
            func.accept(globalPortal);
         }
      }

      McHelper.traverseEntitiesByPointAndRoughRadius(Portal.class, world, pos, range, portal -> {
         func.accept(portal);
         return null;
      });
   }

   public static void onClientEntityTick(Entity entity) {
      CrossPortalEntityRenderer.onEntityTickClient(entity);
   }

   public static List<Tuple<Portal, Vec3>> rayTracePortals(Level world, Vec3 start, Vec3 end, boolean includeGlobalPortals, Predicate<Portal> filter) {
      Vec3 middle = start.scale(0.5).add(end.scale(0.5));
      int chunkRadius = (int)Math.ceil(Math.abs(start.distanceTo(end) / 2.0) / 16.0);
      List<Portal> nearby = McHelper.getEntitiesNearby(world, middle, Portal.class, chunkRadius * 16);
      if (includeGlobalPortals) {
         nearby.addAll(GlobalPortalStorage.getGlobalPortals(world));
      }

      List<Tuple<Portal, Vec3>> hits = new ArrayList<>();
      nearby.forEach(portal -> {
         if (filter == null || filter.test(portal)) {
            Vec3 intersection = portal.rayTrace(start, end);
            if (intersection != null) {
               hits.add(new Tuple<>(portal, intersection));
            }
         }
      });
      hits.sort((pair1, pair2) -> {
         Vec3 intersection1 = pair1.getB();
         Vec3 intersection2 = pair2.getB();
         return (int)Math.signum(intersection1.distanceToSqr(start) - intersection2.distanceToSqr(start));
      });
      return hits;
   }

   public static <T> T withSwitchedContext(Level world, Supplier<T> func) {
      return world.isClientSide() ? ClientWorldLoader.withSwitchedWorld((ClientLevel)world, func) : func.get();
   }

   private static Tuple<BlockHitResult, List<Portal>> rayTrace(Level world, ClipContext context, boolean includeGlobalPortals, List<Portal> portals) {
      Vec3 start = context.getFrom();
      Vec3 end = context.getTo();
      if (portals.size() > IPGlobal.maxPortalLayer) {
         Vec3 diff = end.subtract(start);
         return new Tuple<>(BlockHitResult.miss(end, Direction.getApproximateNearest(diff.x, diff.y, diff.z), BlockPos.containing(end)), portals);
      }

      BlockHitResult hitResult = world.clip(context);
      List<Tuple<Portal, Vec3>> rayTracedPortals = rayTracePortals(world, start, end, includeGlobalPortals, Portal::isInteractable);
      if (rayTracedPortals.isEmpty()) {
         return new Tuple<>(hitResult, portals);
      }

      Tuple<Portal, Vec3> portalHit = rayTracedPortals.get(0);
      Portal portal = portalHit.getA();
      Vec3 intersection = portalHit.getB();
      if (hitResult.getLocation().distanceToSqr(start) < intersection.distanceToSqr(start)) {
         return new Tuple<>(hitResult, portals);
      }

      IERayTraceContext betterContext = (IERayTraceContext)context;
      betterContext.ip_setStart(portal.transformPoint(intersection)).ip_setEnd(portal.transformPoint(end));
      portals.add(portal);
      Level destWorld = portal.getDestinationWorld();
      Tuple<BlockHitResult, List<Portal>> recursion = withSwitchedContext(destWorld, () -> rayTrace(destWorld, context, includeGlobalPortals, portals));
      betterContext.ip_setStart(start).ip_setEnd(end);
      return recursion;
   }

   public static Tuple<BlockHitResult, List<Portal>> rayTrace(Level world, ClipContext context, boolean includeGlobalPortals) {
      return rayTrace(world, context, includeGlobalPortals, new ArrayList<>());
   }

   public static boolean hitResultIsMissedOrNull(HitResult hitResult) {
      return hitResult == null || hitResult.getType() == Type.MISS;
   }

   public static MutableComponent getTextWithCommand(MutableComponent component, String command) {
      return component.withStyle(style -> style.withClickEvent(new RunCommand(command)).withUnderlined(true));
   }

   public static Component getDisableWarningText(String warningKey) {
      return Component.literal(" ")
         .append(getTextWithCommand(Component.translatable("imm_ptl.disable_warning"), "/imm_ptl_client_debug disable_warning_for \"" + warningKey + "\""))
         .append(" ");
   }

   public static Component getDisableUpdateCheckText() {
      return Component.literal(" ")
         .append(getTextWithCommand(Component.translatable("imm_ptl.disable_update_check"), "/imm_ptl_client_debug disable_update_check"));
   }

   @Environment(EnvType.CLIENT)
   public static boolean isNvidiaVideocard() {
      return RenderSystem.getDevice().getVendor().toLowerCase().contains("nvidia");
   }

   public static FriendlyByteBuf bytesToBuf(byte[] packetBytes) {
      return new FriendlyByteBuf(Unpooled.wrappedBuffer(packetBytes));
   }

   public static byte[] bufToBytes(FriendlyByteBuf buf) {
      byte[] packetBytes = new byte[buf.readableBytes()];
      buf.readBytes(packetBytes);
      return packetBytes;
   }
}
