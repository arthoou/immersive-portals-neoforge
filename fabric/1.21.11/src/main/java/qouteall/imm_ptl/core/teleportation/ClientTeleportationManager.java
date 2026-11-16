package qouteall.imm_ptl.core.teleportation;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.IPMcHelper;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.ScaleUtils;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.collision.CollisionHelper;
import qouteall.imm_ptl.core.collision.PortalCollisionHandler;
import qouteall.imm_ptl.core.compat.GravityChangerInterface;
import qouteall.imm_ptl.core.compat.voxy_compatibility.VoxyInterface;
import qouteall.imm_ptl.core.ducks.IEClientPlayNetworkHandler;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.ducks.IEGameRenderer;
import qouteall.imm_ptl.core.ducks.IEMinecraftClient;
import qouteall.imm_ptl.core.ducks.IEParticleManager;
import qouteall.imm_ptl.core.network.ImmPtlNetworking;
import qouteall.imm_ptl.core.network.PacketRedirectionClient;
import qouteall.imm_ptl.core.platform_specific.O_O;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalExtension;
import qouteall.imm_ptl.core.portal.animation.ClientPortalAnimationManagement;
import qouteall.imm_ptl.core.portal.animation.StableClientTimer;
import qouteall.imm_ptl.core.render.FrontClipping;
import qouteall.imm_ptl.core.render.MyGameRenderer;
import qouteall.imm_ptl.core.render.TransformationManager;
import qouteall.imm_ptl.core.render.context_management.FogRendererContext;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.Plane;
import qouteall.q_misc_util.my_util.Vec2d;
import qouteall.q_misc_util.platform.PlatformNetworking;

@Environment(EnvType.CLIENT)
public class ClientTeleportationManager {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final Minecraft client = Minecraft.getInstance();
   public static long tickTimeForTeleportation = 0L;
   private static long lastTeleportGameTime = 0L;
   private static long teleportTickTimeLimit = 0L;
   private static Vec3 lastPlayerEyePos = null;
   private static long lastRecordStableTickTime = 0L;
   private static float lastRecordStablePartialTicks = 0.0F;
   public static boolean isTeleportingTick = false;
   public static boolean isTeleportingFrame = false;
   public static boolean isTicking = false;
   private static final int teleportLimitPerFrame = 3;
   private static long teleportationCounter = 0L;

   public static void init() {
      IPGlobal.POST_CLIENT_TICK_EVENT.register(ClientTeleportationManager::tick);
      IPCGlobal.CLIENT_CLEANUP_EVENT.register(() -> lastPlayerEyePos = null);
   }

   private static void tick() {
      tickTimeForTeleportation++;
      changePlayerMotionIfCollidingWithPortal();
      isTeleportingTick = false;
   }

   public static void acceptSynchronizationDataFromServer(ResourceKey<Level> dimension, Vec3 pos, boolean forceAccept) {
      if (!forceAccept) {
         if (isTeleportingFrequently()) {
            return;
         }

         if (client.player.tickCount < 200) {
            return;
         }
      }

      if (client.player.level().dimension() != dimension) {
         forceTeleportPlayer(dimension, pos);
      }
   }

   public static void manageTeleportation(boolean isTicking_) {
      if (!IPGlobal.disableTeleportation) {
         isTicking = isTicking_;
         teleportationCounter++;
         isTeleportingFrame = false;
         if (client.level == null || client.player == null) {
            lastPlayerEyePos = null;
         } else if (client.player.xo != 0.0 || client.player.yo != 0.0 || client.player.zo != 0.0) {
            Profiler.get().push("ip_teleport");
            ClientPortalAnimationManagement.foreachCustomAnimatedPortals(
               portal -> PortalExtension.forClusterPortals(portal, p -> p.animation.updateClientState(p, teleportationCounter))
            );
            float realPartialTicks = RenderStates.getPartialTick();
            TeleportationUtil.Teleportation lastTeleportation = null;
            ResourceKey<Level> originalDim = client.player.level().dimension();
            if (lastPlayerEyePos != null) {
               for (int i = 0; i <= 3; i++) {
                  TeleportationUtil.Teleportation teleportation = tryTeleport(realPartialTicks);
                  if (teleportation == null) {
                     break;
                  }

                  lastTeleportation = teleportation;
                  if (i != 0) {
                     LOGGER.info("The client player made a combo-teleport");
                     if (i == 3) {
                        LOGGER.info("Combo teleport out of limit. Reject teleportation!");
                        Vec3 oldPos = lastPlayerEyePos.subtract(McHelper.getEyeOffset(client.player));
                        forceTeleportPlayer(originalDim, oldPos.add(teleportation.worldSurfaceNormal().scale(-0.001)));
                        client.player.setDeltaMovement(teleportation.worldSurfaceNormal().scale(-0.1));
                        lastTeleportation = null;
                        break;
                     }
                  }
               }
            }

            if (lastTeleportation != null && PortalExtension.get(lastTeleportation.portal()).adjustPositionAfterTeleport) {
               adjustPlayerPosition(client.player);
            }

            lastPlayerEyePos = getPlayerEyePos(realPartialTicks);
            lastRecordStableTickTime = StableClientTimer.getStableTickTime();
            lastRecordStablePartialTicks = StableClientTimer.getStablePartialTicks();
            Profiler.get().pop();
         }
      }
   }

   @Nullable
   private static TeleportationUtil.Teleportation tryTeleport(float partialTicks) {
      LocalPlayer player = client.player;
      assert player != null;
      Vec3 thisFrameEyePos = getPlayerEyePos(partialTicks);
      if (lastPlayerEyePos.distanceToSqr(thisFrameEyePos) > 1600.0) {
         return null;
      } else {
         assert client.level != null;
         long currentGameTime = client.level.getGameTime();
         Vec3 lastTickEyePos = McHelper.getLastTickEyePos(player);
         Vec3 thisTickEyePos = McHelper.getEyePos(player);
         ArrayList<TeleportationUtil.Teleportation> teleportationCandidates = new ArrayList<>();
         IPMcHelper.traverseNearbyPortals(
            player.level(),
            thisFrameEyePos,
            IPGlobal.maxNormalPortalRadius + 1,
            portalx -> {
               if (portalx.canTeleportEntity(player)) {
                  if (portalx.animation.clientLastFramePortalStateCounter == teleportationCounter - 1L
                     && portalx.animation.clientLastFramePortalState != null
                     && portalx.animation.lastTickAnimatedState != null
                     && portalx.animation.thisTickAnimatedState != null) {
                     assert portalx.animation.clientCurrentFramePortalState != null;
                     TeleportationUtil.Teleportation teleportationx = TeleportationUtil.checkDynamicTeleportation(
                        portalx,
                        portalx.animation.clientLastFramePortalState,
                        portalx.animation.clientCurrentFramePortalState,
                        lastPlayerEyePos,
                        thisFrameEyePos,
                        portalx.animation.lastTickAnimatedState,
                        portalx.animation.thisTickAnimatedState,
                        lastTickEyePos,
                        thisTickEyePos,
                        partialTicks
                     );
                     if (teleportationx != null) {
                        teleportationCandidates.add(teleportationx);
                     }
                  } else {
                     TeleportationUtil.Teleportation teleportationxx = TeleportationUtil.checkStaticTeleportation(
                        portalx, lastPlayerEyePos, thisFrameEyePos, lastTickEyePos, thisTickEyePos
                     );
                     if (teleportationxx != null) {
                        teleportationCandidates.add(teleportationxx);
                     }
                  }
               }
            }
         );
         TeleportationUtil.Teleportation teleportation = teleportationCandidates.stream()
            .min(Comparator.comparingDouble(p -> p.worldCollisionPoint().distanceToSqr(lastPlayerEyePos)))
            .orElse(null);
         if (teleportation != null) {
            Portal portal = teleportation.portal();
            Vec3 collidingPos = teleportation.worldCollisionPoint();
            Profiler.get().push("portal_teleport");
            teleportPlayer(teleportation, partialTicks);
            Profiler.get().pop();
            boolean allowOverlappedTeleport = portal.respectParallelOrientedPortal();
            double adjustment = allowOverlappedTeleport ? -0.001 : 0.001;
            Vec3 newDelta = teleportation.newThisTickEyePos().subtract(teleportation.newLastTickEyePos());
            lastPlayerEyePos = teleportation.teleportationCheckpoint().add(newDelta.scale(adjustment));
            return teleportation;
         } else {
            return null;
         }
      }
   }

   public static Vec3 getPlayerEyePos(float partialTick) {
      return client.player.getEyePosition(partialTick);
   }

   private static void teleportPlayer(TeleportationUtil.Teleportation teleportation, float partialTicks) {
      Portal portal = teleportation.portal();
      if (tickTimeForTeleportation <= teleportTickTimeLimit) {
         Helper.log("Client player teleportation rejected");
      } else {
         lastTeleportGameTime = tickTimeForTeleportation;
         LocalPlayer player = client.player;
         Validate.isTrue(player != null);
         ResourceKey<Level> toDimension = portal.getDestDim();
         float partialTick = RenderStates.getPartialTick();
         Entity vehicle = player.getVehicle();
         Vec3 oldVehiclePos = vehicle != null ? vehicle.position() : null;
         Vec3 thisTickEyePos = McHelper.getEyePos(player);
         Vec3 lastTickEyePos = McHelper.getLastTickEyePos(player);
         Vec3 newThisTickEyePos = teleportation.newThisTickEyePos();
         Vec3 newLastTickEyePos = teleportation.newLastTickEyePos();
         ClientLevel fromWorld = client.level;
         ResourceKey<Level> fromDimension = fromWorld.dimension();
         if (fromDimension != toDimension) {
            ClientLevel toWorld = ClientWorldLoader.getWorld(toDimension);
            changePlayerDimension(player, fromWorld, toWorld, newThisTickEyePos);
         }

         McHelper.setEyePos(player, newThisTickEyePos, newLastTickEyePos);
         McHelper.updateBoundingBox(player);
         Vec3 oldRealVelocity = McHelper.getWorldVelocity(player);
         TransformationManager.managePlayerRotationAndChangeGravity(portal);
         McHelper.setWorldVelocity(player, oldRealVelocity);
         TeleportationUtil.PortalPointVelocity portalPointVelocity = teleportation.portalPointVelocity();
         TeleportationUtil.transformEntityVelocity(portal, player, portalPointVelocity, thisTickEyePos);
         if (vehicle != null) {
            TeleportationUtil.transformEntityVelocity(portal, vehicle, portalPointVelocity, oldVehiclePos);
         }

         ScaleUtils.onClientPlayerTeleported(portal);
         player.connection
            .send(
               PlatformNetworking.get()
                  .createC2SPacket(new ImmPtlNetworking.TeleportPacket(PortalAPI.clientDimKeyToInt(fromDimension), thisTickEyePos, portal.getUUID()))
            );
         PortalCollisionHandler.updateCollidingPortalAfterTeleportation(player, newThisTickEyePos, newLastTickEyePos, RenderStates.getPartialTick());
         McHelper.adjustVehicle(player);
         RenderStates.updatePreRenderInfo(partialTick);
         if (teleportation.isDynamic()) {
            LOGGER.info(
               "Client Teleported Dynamically\nportal: {}\ntickTime: {}\nduring ticking: {}\ncounter: {}\neye pos (by frame): {} -> {}\npartial ticks: {}\nnew immediate eye pos: {}\nportal origin/normal: {} {}\nportal dest/content dir: {} {}",
               portal,
               tickTimeForTeleportation,
               isTicking,
               teleportationCounter,
               teleportation.lastWorldEyePos(),
               teleportation.currentWorldEyePos(),
               partialTicks,
               teleportation.newLastTickEyePos().lerp(teleportation.newThisTickEyePos(), partialTick),
               portal.getOriginPos(),
               portal.getNormal(),
               portal.getDestPos(),
               portal.getContentDirection()
            );
         } else {
            LOGGER.info(
               "Client Teleported Statically\nportal: {}\neye pos: {} -> {}", portal, teleportation.lastWorldEyePos(), teleportation.currentWorldEyePos()
            );
         }

         isTeleportingTick = true;
         isTeleportingFrame = true;
         MyGameRenderer.vanillaTerrainSetupOverride = 1;
      }
   }

   public static boolean isTeleportingFrequently() {
      return tickTimeForTeleportation - lastTeleportGameTime <= 100L || tickTimeForTeleportation <= teleportTickTimeLimit;
   }

   public static void forceTeleportPlayer(ResourceKey<Level> toDimension, Vec3 destination) {
      LOGGER.info("client player force teleported {} {}", toDimension.identifier(), destination);
      ClientLevel fromWorld = client.level;
      assert fromWorld != null;
      ResourceKey<Level> fromDimension = fromWorld.dimension();
      LocalPlayer player = client.player;
      assert player != null;
      if (fromDimension != toDimension) {
         ClientLevel toWorld = ClientWorldLoader.getWorld(toDimension);
         Vec3 eyeOffset = McHelper.getEyeOffset(player);
         changePlayerDimension(player, fromWorld, toWorld, destination.add(eyeOffset));
      }

      player.setPos(destination.x, destination.y, destination.z);
      McHelper.adjustVehicle(player);
      lastPlayerEyePos = null;
      RenderStates.updatePreRenderInfo(RenderStates.getPartialTick());
      MyGameRenderer.vanillaTerrainSetupOverride = 1;
   }

   public static void changePlayerDimension(LocalPlayer player, ClientLevel fromWorld, ClientLevel toWorld, Vec3 newEyePos) {
      Validate.isTrue(!WorldRenderInfo.isRendering());
      Validate.isTrue(!FrontClipping.isClippingEnabled);
      Validate.isTrue(!PacketRedirectionClient.getIsProcessingRedirectedMessage());
      Entity vehicle = player.getVehicle();
      player.unRide();
      ResourceKey<Level> toDimension = toWorld.dimension();
      ResourceKey<Level> fromDimension = fromWorld.dimension();
      ((IEClientPlayNetworkHandler)client.getConnection()).ip_setWorld(toWorld);
      fromWorld.removeEntity(player.getId(), RemovalReason.CHANGED_DIMENSION);
      ((IEEntity)player).ip_setWorld(toWorld);
      McHelper.setEyePos(player, newEyePos, newEyePos);
      McHelper.updateBoundingBox(player);
      ((IEEntity)player).ip_unsetRemoved();
      toWorld.addEntity(player);
      IEGameRenderer gameRenderer = (IEGameRenderer)Minecraft.getInstance().gameRenderer;
      gameRenderer.ip_setLightmapTextureManager(ClientWorldLoader.getDimensionRenderHelper(toDimension).lightmapTexture);
      client.level = toWorld;
      ((IEMinecraftClient)client).ip_setWorldRenderer(ClientWorldLoader.getWorldRenderer(toDimension));
      VoxyInterface.invoker
         .onPlayerDimensionChangePromoted(ClientWorldLoader.getWorldRenderer(toDimension), toWorld, ClientWorldLoader.getWorldRenderer(fromDimension));
      if (client.particleEngine != null) {
         ((IEParticleManager)client.particleEngine).ip_setWorld(toWorld);
      }

      if (vehicle != null) {
         Vec3 offset = McHelper.getVehicleOffsetFromPassenger(vehicle, player);
         Vec3 vehiclePos = player.position().add(offset);
         moveClientEntityAcrossDimension(vehicle, toWorld, vehiclePos);
         McHelper.setPosAndLastTickPos(vehicle, player.position().add(offset), McHelper.lastTickPosOf(player).add(offset));
         player.startRiding(vehicle, true, false);
      }

      Helper.log(
         String.format(
            "Client Changed Dimension from %s to %s time: %s age: %s",
            fromDimension.identifier(),
            toDimension.identifier(),
            tickTimeForTeleportation,
            player.tickCount
         )
      );
      FogRendererContext.onPlayerTeleport(fromDimension, toDimension);
      O_O.onPlayerChangeDimensionClient(fromDimension, toDimension);
   }

   private static void changePlayerMotionIfCollidingWithPortal() {
      LocalPlayer player = client.player;
      Portal portal = ((IEEntity)player).ip_getCollidingPortal();
      if (portal != null) {
         if (PortalExtension.get(portal).motionAffinity > 0.0) {
            changeMotion(player, portal);
         } else if (PortalExtension.get(portal).motionAffinity < 0.0 && player.getDeltaMovement().length() > 0.7) {
            changeMotion(player, portal);
         }
      }
   }

   private static void changeMotion(Entity player, Portal portal) {
      Vec3 velocity = player.getDeltaMovement();
      player.setDeltaMovement(velocity.scale(1.0 + PortalExtension.get(portal).motionAffinity));
   }

   public static void moveClientEntityAcrossDimension(Entity entity, ClientLevel newWorld, Vec3 newPos) {
      ClientLevel oldWorld = (ClientLevel)entity.level();
      oldWorld.removeEntity(entity.getId(), RemovalReason.CHANGED_DIMENSION);
      ((IEEntity)entity).ip_setWorld(newWorld);
      entity.setPos(newPos.x, newPos.y, newPos.z);
      ((IEEntity)entity).ip_unsetRemoved();
      newWorld.addEntity(entity);
      Validate.isTrue(!entity.isRemoved());
   }

   public static void disableTeleportFor(int ticks) {
      teleportTickTimeLimit = tickTimeForTeleportation + ticks;
   }

   private static void adjustPlayerPosition(LocalPlayer player) {
      if (!player.isSpectator()) {
         if (player.getVehicle() == null) {
            AABB playerBoundingBox = player.getBoundingBox();
            PortalCollisionHandler portalCollisionHandler = ((IEEntity)player).ip_getPortalCollisionHandler();
            List<Portal> collidingPortals = portalCollisionHandler == null ? Collections.emptyList() : portalCollisionHandler.getCollidingPortals();
            Direction gravityDir = GravityChangerInterface.invoker.getGravityDirection(player);
            Direction levitationDir = gravityDir.getOpposite();
            Vec3 eyeOffset = GravityChangerInterface.invoker.getEyeOffset(player);
            AABB bottomHalfBox = playerBoundingBox.contract(eyeOffset.x / 2.0, eyeOffset.y / 2.0, eyeOffset.z / 2.0);
            Function<VoxelShape, VoxelShape> shapeFilter = c -> {
               VoxelShape curr = c;

               for (Portal collidingPortal : collidingPortals) {
                  Plane outerClipping = collidingPortal.getPortalShape().getOuterClipping(collidingPortal.getThisSideState());
                  if (outerClipping != null) {
                     curr = CollisionHelper.clipVoxelShape(curr, outerClipping.pos(), outerClipping.normal());
                     if (curr == null) {
                        return null;
                     }
                  }
               }

               return curr;
            };
            AABB collisionUnion = CollisionHelper.getTotalBlockCollisionBox(player, bottomHalfBox, shapeFilter);
            if (collisionUnion != null) {
               Vec3 anchor = player.position();
               AABB collisionUnionLocal = Helper.transformBox(
                  collisionUnion, v -> GravityChangerInterface.invoker.transformWorldToPlayer(gravityDir, v.subtract(anchor))
               );
               AABB playerBoxLocal = Helper.transformBox(
                  playerBoundingBox, v -> GravityChangerInterface.invoker.transformWorldToPlayer(gravityDir, v.subtract(anchor))
               );
               double targetLocalY = collisionUnionLocal.maxY + 0.01;
               double originalLocalY = playerBoxLocal.minY;
               double delta = targetLocalY - originalLocalY;
               if (!(delta <= 0.0)) {
                  Vec3 levitationVec = Vec3.atLowerCornerOf(levitationDir.getUnitVec3i());
                  Vec3 offset = levitationVec.scale(delta);
                  int ticks = 5;
                  Helper.log("Adjusting Client Player Position");
                  int[] counter = new int[]{0};
                  IPGlobal.CLIENT_TASK_LIST
                     .addTask(
                        () -> {
                           if (player.isRemoved()) {
                              return true;
                           }

                           if (GravityChangerInterface.invoker.getGravityDirection(player) != gravityDir) {
                              return true;
                           }

                           if (counter[0] >= 5) {
                              return true;
                           }

                           counter[0]++;
                           double len = player.position().subtract(anchor).dot(levitationVec);
                           if (!(len < -1.0) && !(len > 2.0)) {
                              double progress = counter[0] / 5.0;
                              progress = TransformationManager.mapProgress(progress);
                              Vec3 expectedPos = anchor.add(offset.scale(progress));
                              Vec3 newPos = Helper.putCoordinate(
                                 player.position(), levitationDir.getAxis(), Helper.getCoordinate(expectedPos, levitationDir.getAxis())
                              );
                              Portal currentCollidingPortal = ((IEEntity)player).ip_getCollidingPortal();
                              if (currentCollidingPortal != null) {
                                 Vec3 eyePos = McHelper.getEyePos(player);
                                 Vec3 newEyePos = newPos.add(McHelper.getEyeOffset(player));
                                 if (currentCollidingPortal.rayTrace(eyePos, newEyePos) != null) {
                                    return true;
                                 }
                              }

                              player.setPosRaw(newPos.x, newPos.y, newPos.z);
                              McHelper.updateBoundingBox(player);
                              return false;
                           } else {
                              return true;
                           }
                        }
                     );
               }
            }
         }
      }
   }

   public static class RemoteCallables {
      public static void updateEntityPos(ResourceKey<Level> dim, int entityId, Vec3 pos) {
         ClientLevel world = ClientWorldLoader.getWorld(dim);
         Entity entity = world.getEntity(entityId);
         if (entity == null) {
            Helper.err("cannot find entity to update position");
         } else {
            entity.setPos(pos);
            entity.moveOrInterpolateTo(pos, entity.getYRot(), entity.getXRot());
            entity.setPos(pos);
         }
      }
   }

   private record TeleportationRec(Portal portal, Vec2d portalLocalXY, Vec3 collisionPos) {
   }
}
