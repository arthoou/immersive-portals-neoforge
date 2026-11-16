package qouteall.imm_ptl.core.collision;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.ScaleUtils;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;
import qouteall.imm_ptl.core.mixin.common.collision.IEEntity_Collision;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.global_portals.GlobalPortalStorage;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.MiscHelper;
import qouteall.q_misc_util.my_util.LimitedLogger;
import qouteall.q_misc_util.platform.Platform;

public class CollisionHelper {
   private static final LimitedLogger limitedLogger = new LimitedLogger(20);
   private static boolean thisTickStagnate = false;
   private static boolean lastTickStagnate = false;

   @Nullable
   public static AABB clipBox(AABB box, Vec3 planePos, Vec3 planeNormal) {
      boolean xForward = planeNormal.x > 0.0;
      boolean yForward = planeNormal.y > 0.0;
      boolean zForward = planeNormal.z > 0.0;
      Vec3 pushedPos = new Vec3(xForward ? box.minX : box.maxX, yForward ? box.minY : box.maxY, zForward ? box.minZ : box.maxZ);
      Vec3 staticPos = new Vec3(xForward ? box.maxX : box.minX, yForward ? box.maxY : box.minY, zForward ? box.maxZ : box.minZ);
      double tOfPushedPos = Helper.getCollidingT(planePos, planeNormal, pushedPos, planeNormal);
      boolean isPushedPosInFrontOfPlane = tOfPushedPos < 0.0;
      if (isPushedPosInFrontOfPlane) {
         return box;
      }

      boolean isStaticPosInFrontOfPlane = Helper.isInFrontOfPlane(staticPos, planePos, planeNormal);
      if (!isStaticPosInFrontOfPlane) {
         return null;
      }

      Vec3 afterBeingPushed = pushedPos.add(planeNormal.scale(tOfPushedPos));
      return new AABB(afterBeingPushed, staticPos);
   }

   public static boolean isBoxFullyBehindPlane(Vec3 planePos, Vec3 planeNormal, AABB box) {
      boolean xForward = planeNormal.x > 0.0;
      boolean yForward = planeNormal.y > 0.0;
      boolean zForward = planeNormal.z > 0.0;
      Vec3 testingPos = new Vec3(xForward ? box.maxX : box.minX, yForward ? box.maxY : box.minY, zForward ? box.maxZ : box.minZ);
      return testingPos.subtract(planePos).dot(planeNormal) < 0.0;
   }

   public static boolean canCollideWithPortal(Entity entity, Portal portal, float partialTick) {
      return mayEntityCollideWithPortal(entity, portal, entity.getEyePosition(partialTick), entity.getBoundingBox());
   }

   public static boolean mayEntityCollideWithPortal(Entity entity, Portal portal, Vec3 entityEyePos, AABB entityBoundingBox) {
      return !portal.canCollideWithEntity(entity)
         ? false
         : portal.getPortalShape().canCollideWith(portal, portal.getThisSideState(), entityEyePos, entityBoundingBox);
   }

   public static double absMin(double a, double b) {
      return Math.abs(a) < Math.abs(b) ? a : b;
   }

   public static double fixCoordinateFloatingPointError(double attemptedMove, double result) {
      if (Math.abs(attemptedMove - result) < 0.001) {
         return attemptedMove;
      } else {
         return Math.abs(result) < 1.0E-4 ? 0.0 : result;
      }
   }

   @Nullable
   public static VoxelShape clipVoxelShape(VoxelShape shape, Vec3 clippingPlanePos, Vec3 clippingPlaneNormal) {
      if (shape.isEmpty()) {
         return null;
      }

      AABB shapeBoundingBox = shape.bounds();
      boolean boxBehindPlane = isBoxFullyBehindPlane(clippingPlanePos, clippingPlaneNormal, shapeBoundingBox);
      if (boxBehindPlane) {
         return null;
      }

      boolean isFullyInFrontOfPlane = isBoxFullyBehindPlane(clippingPlanePos, clippingPlaneNormal.scale(-1.0), shapeBoundingBox);
      if (isFullyInFrontOfPlane) {
         return shape;
      }

      AABB clippedBoundingBox = clipBox(shapeBoundingBox, clippingPlanePos, clippingPlaneNormal);
      return clippedBoundingBox == null ? null : Shapes.joinUnoptimized(shape, Shapes.create(clippedBoundingBox), BooleanOp.AND);
   }

   private static Vec3 refHandleCollisionWithShapeProcessor(Entity entity, Vec3 attemptedMove, Function<VoxelShape, VoxelShape> filter) {
      AABB boundingBox = entity.getBoundingBox();
      List<VoxelShape> entityCollisions = entity.level().getEntityCollisions(entity, boundingBox.expandTowards(attemptedMove));
      BiFunction<Vec3, AABB, Vec3> collisionFunc = (attempt, bb) -> collideBoundingBox(entity, attempt, bb, entity.level(), entityCollisions, filter);
      Vec3 collidedMovement = attemptedMove.lengthSqr() == 0.0 ? attemptedMove : collisionFunc.apply(attemptedMove, boundingBox);
      boolean collideX = attemptedMove.x != collidedMovement.x;
      boolean collideY = attemptedMove.y != collidedMovement.y;
      boolean collideZ = attemptedMove.z != collidedMovement.z;
      boolean collidesWithFloor = collideY && attemptedMove.y < 0.0;
      boolean touchGround = entity.onGround() || collidesWithFloor;
      boolean collidesHorizontally = collideX || collideZ;
      float maxUpStep = entity.maxUpStep();
      if (maxUpStep > 0.0F && touchGround && collidesHorizontally) {
         Vec3 stepping = collisionFunc.apply(new Vec3(attemptedMove.x, maxUpStep, attemptedMove.z), boundingBox);
         Vec3 verticalStep = collisionFunc.apply(new Vec3(0.0, maxUpStep, 0.0), boundingBox.expandTowards(attemptedMove.x, 0.0, attemptedMove.z));
         if (verticalStep.y < maxUpStep) {
            Vec3 horizontalMoveAfterVerticalStepping = collisionFunc.apply(new Vec3(attemptedMove.x, 0.0, attemptedMove.z), boundingBox.move(verticalStep))
               .add(verticalStep);
            if (horizontalMoveAfterVerticalStepping.horizontalDistanceSqr() > stepping.horizontalDistanceSqr()) {
               stepping = horizontalMoveAfterVerticalStepping;
            }
         }

         if (stepping.horizontalDistanceSqr() > collidedMovement.horizontalDistanceSqr()) {
            Vec3 movingDown = collisionFunc.apply(new Vec3(0.0, -stepping.y + attemptedMove.y, 0.0), boundingBox.move(stepping));
            return stepping.add(movingDown);
         }
      }

      return collidedMovement;
   }

   @IPVanillaCopy
   public static Vec3 handleCollisionWithShapeProcessor(
      Entity entity, AABB boundingBox, Level world, Vec3 attemptedMove, Function<VoxelShape, VoxelShape> filter, Direction gravity, double steppingScale
   ) {
      Direction jumpDirection = gravity.getOpposite();
      Axis gravityAxis = gravity.getAxis();
      List<VoxelShape> entityCollisions = world.getEntityCollisions(entity, boundingBox.expandTowards(attemptedMove));
      BiFunction<Vec3, AABB, Vec3> collisionFunc = (attempt, bb) -> collideBoundingBox(entity, attempt, bb, world, entityCollisions, filter);
      Vec3 collidedMovement = attemptedMove.lengthSqr() == 0.0 ? attemptedMove : collisionFunc.apply(attemptedMove, boundingBox);
      Vec3 collisionDelta = attemptedMove.subtract(collidedMovement);
      boolean collidesOnGravityAxis = Helper.getCoordinate(collisionDelta, gravityAxis) != 0.0;
      boolean attemptToMoveAlongGravity = Helper.getSignedCoordinate(attemptedMove, gravity) > 0.0;
      boolean collidesWithFloor = collidesOnGravityAxis && attemptToMoveAlongGravity;
      boolean touchGround = entity.onGround() || collidesWithFloor;
      boolean collidesHorizontally = movesOnNonGravityAxis(collisionDelta, gravityAxis);
      double maxUpStep = entity.maxUpStep();
      if (steppingScale > 1.0) {
         maxUpStep *= steppingScale;
      }

      if (maxUpStep > 0.0 && touchGround && collidesHorizontally) {
         Vec3 stepping = collisionFunc.apply(Helper.putSignedCoordinate(attemptedMove, jumpDirection, maxUpStep), boundingBox);
         Vec3 expandVec = Helper.putSignedCoordinate(attemptedMove, gravity, 0.0);
         Vec3 verticalStep = collisionFunc.apply(Helper.putSignedCoordinate(Vec3.ZERO, jumpDirection, maxUpStep), boundingBox.expandTowards(expandVec));
         if (Helper.getSignedCoordinate(verticalStep, jumpDirection) < maxUpStep + 0.001) {
            Vec3 horizontalMoveAfterVerticalStepping = collisionFunc.apply(expandVec, boundingBox.move(verticalStep)).add(verticalStep);
            if (Helper.getDistanceSqrOnAxisPlane(horizontalMoveAfterVerticalStepping, gravityAxis) > Helper.getDistanceSqrOnAxisPlane(stepping, gravityAxis)) {
               stepping = horizontalMoveAfterVerticalStepping;
            }
         }

         if (Helper.getDistanceSqrOnAxisPlane(stepping, gravityAxis) > Helper.getDistanceSqrOnAxisPlane(collidedMovement, gravityAxis)) {
            double steppingVerticalLen = Helper.getSignedCoordinate(stepping, jumpDirection);
            double attemptMoveVerticalLen = Helper.getSignedCoordinate(attemptedMove, jumpDirection);
            Vec3 movingDown = collisionFunc.apply(
               Helper.putSignedCoordinate(Vec3.ZERO, jumpDirection, -steppingVerticalLen + attemptMoveVerticalLen), boundingBox.move(stepping)
            );
            return stepping.add(movingDown);
         }
      }

      return collidedMovement;
   }

   public static boolean movesOnNonGravityAxis(Vec3 vec, Axis gravityAxis) {
      return switch (gravityAxis) {
         case X -> vec.y != 0.0 || vec.z != 0.0;
         case Y -> vec.x != 0.0 || vec.z != 0.0;
         case Z -> vec.x != 0.0 || vec.y != 0.0;
      };
   }

   @IPVanillaCopy
   public static Vec3 collideBoundingBox(
      Entity entity, Vec3 vec, AABB collisionBox, Level level, List<VoxelShape> potentialHits, Function<VoxelShape, VoxelShape> shapeProcessor
   ) {
      Builder<VoxelShape> builder = ImmutableList.builderWithExpectedSize(potentialHits.size() + 1);

      for (VoxelShape potentialHit : potentialHits) {
         VoxelShape processed = shapeProcessor.apply(potentialHit);
         if (processed != null) {
            builder.add(processed);
         }
      }

      WorldBorder worldBorder = level.getWorldBorder();
      Vec3 boundingBoxCenter = collisionBox.getCenter();
      boolean addWorldBorderCollision = worldBorder.isWithinBounds(boundingBoxCenter.x, boundingBoxCenter.z)
         && worldBorder.getDistanceToBorder(boundingBoxCenter.x, boundingBoxCenter.z) < 32.0;
      if (addWorldBorderCollision) {
         builder.add(worldBorder.getCollisionShape());
      }

      for (VoxelShape blockCollision : level.getBlockCollisions(entity, collisionBox.expandTowards(vec))) {
         VoxelShape processed = shapeProcessor.apply(blockCollision);
         if (processed != null) {
            builder.add(processed);
         }
      }

      return IEEntity_Collision.ip_CollideWithShapes(vec, collisionBox, builder.build());
   }

   public static AABB transformBox(Portal portal, AABB originalBox) {
      return portal.getRotation() == null && portal.getScale() == 1.0
         ? originalBox.move(portal.getDestPos().subtract(portal.getOriginPos()))
         : Helper.transformBox(originalBox, portal::transformPoint);
   }

   @Deprecated
   public static Level getWorld(boolean isClient, ResourceKey<Level> dimension) {
      return isClient ? CHelper.getClientWorld(dimension) : MiscHelper.getServer().getLevel(dimension);
   }

   public static boolean isCollidingWithAnyPortal(Entity entity) {
      return ((IEEntity)entity).ip_getCollidingPortal() != null;
   }

   public static void updateCollidingPortalForWorld(Level world, float partialTick) {
      Profiler.get().push("update_colliding_portal");
      List<Portal> globalPortals = GlobalPortalStorage.getGlobalPortals(world);

      for (Entity entity : McHelper.getWorldEntityList(world)) {
         if (entity instanceof Portal portal) {
            notifyCollidingPortals(portal, partialTick);
         } else {
            AABB entityBoundingBoxStretched = getStretchedBoundingBox(entity);

            for (Portal globalPortal : globalPortals) {
               AABB globalPortalBoundingBox = globalPortal.getBoundingBox();
               if (entityBoundingBoxStretched.intersects(globalPortalBoundingBox) && canCollideWithPortal(entity, globalPortal, partialTick)) {
                  ((IEEntity)entity).ip_notifyCollidingWithPortal(globalPortal);
               }
            }
         }
      }

      Profiler.get().pop();
   }

   public static void init() {
      Platform.get().onServerTickEnd(server -> {
         for (ServerLevel world : server.getAllLevels()) {
            updateCollidingPortalForWorld(world, 0.0F);
         }
      });
   }

   @Environment(EnvType.CLIENT)
   public static void initClient() {
      IPGlobal.POST_CLIENT_TICK_EVENT.register(CollisionHelper::tickClient);
   }

   @Environment(EnvType.CLIENT)
   public static void tickClient() {
      updateClientCollidingStatus();
      updateClientStagnateStatus();
   }

   @Environment(EnvType.CLIENT)
   private static void updateClientCollidingStatus() {
      if (ClientWorldLoader.getIsInitialized()) {
         for (ClientLevel world : ClientWorldLoader.getClientWorlds()) {
            updateCollidingPortalForWorld(world, 0.0F);
         }
      }
   }

   public static void notifyCollidingPortals(Portal portal, float partialTick) {
      if (portal.isTeleportable()) {
         AABB portalBoundingBox = portal.getBoundingBox();
         McHelper.foreachEntitiesByBoxApproximateRegions(Entity.class, portal.level(), portalBoundingBox, 8.0, entity -> {
            if (!(entity instanceof Portal)) {
               AABB entityBoxStretched = getStretchedBoundingBox(entity);
               if (entityBoxStretched.intersects(portalBoundingBox)) {
                  boolean canCollideWithPortal = canCollideWithPortal(entity, portal, partialTick);
                  if (canCollideWithPortal) {
                     ((IEEntity)entity).ip_notifyCollidingWithPortal(portal);
                  }
               }
            }
         });
      }
   }

   public static AABB getStretchedBoundingBox(Entity entity) {
      Vec3 backwardExpand = McHelper.lastTickPosOf(entity).subtract(entity.position());
      Vec3 forwardExpand = McHelper.getWorldVelocity(entity);
      AABB box = entity.getBoundingBox().expandTowards(forwardExpand.scale(1.2)).expandTowards(backwardExpand);
      double scale = ScaleUtils.getScale(entity);
      if (scale > 4.0) {
         box = box.inflate(scale);
      }

      return box;
   }

   @Environment(EnvType.CLIENT)
   public static void informClientStagnant() {
      thisTickStagnate = true;
      limitedLogger.log("client movement stagnated");
   }

   @Environment(EnvType.CLIENT)
   private static void updateClientStagnateStatus() {
      if (thisTickStagnate && lastTickStagnate) {
         Minecraft.getInstance().gui.setOverlayMessage(Component.translatable("imm_ptl.stagnate_movement"), false);
      } else if (!thisTickStagnate && lastTickStagnate) {
         Minecraft.getInstance().gui.setOverlayMessage(Component.literal(""), false);
      }

      lastTickStagnate = thisTickStagnate;
      thisTickStagnate = false;
   }

   @Nullable
   public static AABB getTotalBlockCollisionBox(Entity entity, AABB box, Function<VoxelShape, VoxelShape> shapeFilter) {
      Iterable<VoxelShape> collisions = entity.level().getBlockCollisions(entity, box);
      AABB collisionUnion = null;

      for (VoxelShape c : collisions) {
         VoxelShape currCollision = shapeFilter.apply(c);
         if (currCollision != null && !currCollision.isEmpty()) {
            AABB collisionBoundingBox = currCollision.bounds();
            if (collisionUnion == null) {
               collisionUnion = collisionBoundingBox;
            } else {
               collisionUnion = collisionUnion.minmax(collisionBoundingBox);
            }
         }
      }

      return collisionUnion;
   }
}
