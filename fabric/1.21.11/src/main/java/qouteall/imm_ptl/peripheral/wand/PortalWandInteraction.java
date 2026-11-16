package qouteall.imm_ptl.peripheral.wand;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.IPPerServerInfo;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.platform_specific.IPConfig;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalExtension;
import qouteall.imm_ptl.core.portal.PortalManipulation;
import qouteall.imm_ptl.core.portal.PortalState;
import qouteall.imm_ptl.core.portal.animation.UnilateralPortalState;
import qouteall.imm_ptl.core.portal.util.PortalLocalXYNormalized;
import qouteall.imm_ptl.peripheral.CommandStickItem;
import qouteall.q_misc_util.my_util.DQuaternion;
import qouteall.q_misc_util.my_util.Plane;
import qouteall.q_misc_util.my_util.Range;
import qouteall.q_misc_util.platform.Platform;

public class PortalWandInteraction {
   private static final double SIZE_LIMIT = 64.0;
   private static final Logger LOGGER = LogUtils.getLogger();
   private final WeakHashMap<ServerPlayer, PortalWandInteraction.DraggingSession> draggingSessionMap = new WeakHashMap<>();
   private static final WeakHashMap<ServerPlayer, PortalWandInteraction.CopyingSession> copyingSessionMap = new WeakHashMap<>();

   public static PortalWandInteraction of(MinecraftServer server) {
      return IPPerServerInfo.of(server).portalWandInteraction;
   }

   private static void handleFinishPortalCreation(ServerPlayer player, ProtoPortal protoPortal) {
      Validate.isTrue(protoPortal.firstSide != null);
      Validate.isTrue(protoPortal.secondSide != null);
      Vec3 firstSideLeftBottom = protoPortal.firstSide.leftBottom;
      Vec3 firstSideRightBottom = protoPortal.firstSide.rightBottom;
      Vec3 firstSideLeftUp = protoPortal.firstSide.leftTop;
      Vec3 secondSideLeftBottom = protoPortal.secondSide.leftBottom;
      Vec3 secondSideRightBottom = protoPortal.secondSide.rightBottom;
      Vec3 secondSideLeftUp = protoPortal.secondSide.leftTop;
      Validate.notNull(firstSideLeftBottom);
      Validate.notNull(firstSideRightBottom);
      Validate.notNull(firstSideLeftUp);
      Validate.notNull(secondSideLeftBottom);
      Validate.notNull(secondSideRightBottom);
      Validate.notNull(secondSideLeftUp);
      ResourceKey<Level> firstSideDimension = protoPortal.firstSide.dimension;
      ResourceKey<Level> secondSideDimension = protoPortal.secondSide.dimension;
      Vec3 firstSideHorizontalAxis = firstSideRightBottom.subtract(firstSideLeftBottom);
      Vec3 firstSideVerticalAxis = firstSideLeftUp.subtract(firstSideLeftBottom);
      double firstSideWidth = firstSideHorizontalAxis.length();
      double firstSideHeight = firstSideVerticalAxis.length();
      Vec3 firstSideHorizontalUnitAxis = firstSideHorizontalAxis.normalize();
      Vec3 firstSideVerticalUnitAxis = firstSideVerticalAxis.normalize();
      if (Math.abs(firstSideWidth) < 0.001 || Math.abs(firstSideHeight) < 0.001) {
         player.sendSystemMessage(Component.literal("The first side is too small"));
         LOGGER.error("The first side is too small");
      } else if (firstSideHorizontalUnitAxis.dot(firstSideVerticalUnitAxis) > 0.001) {
         player.sendSystemMessage(Component.literal("The horizontal and vertical axis are not perpendicular in first side"));
         LOGGER.error("The horizontal and vertical axis are not perpendicular in first side");
      } else if (!(firstSideWidth > 64.0) && !(firstSideHeight > 64.0)) {
         Vec3 secondSideHorizontalAxis = secondSideRightBottom.subtract(secondSideLeftBottom);
         Vec3 secondSideVerticalAxis = secondSideLeftUp.subtract(secondSideLeftBottom);
         double secondSideWidth = secondSideHorizontalAxis.length();
         double secondSideHeight = secondSideVerticalAxis.length();
         Vec3 secondSideHorizontalUnitAxis = secondSideHorizontalAxis.normalize();
         Vec3 secondSideVerticalUnitAxis = secondSideVerticalAxis.normalize();
         if (Math.abs(secondSideWidth) < 0.001 || Math.abs(secondSideHeight) < 0.001) {
            player.sendSystemMessage(Component.literal("The second side is too small"));
            LOGGER.error("The second side is too small");
         } else if (secondSideHorizontalUnitAxis.dot(secondSideVerticalUnitAxis) > 0.001) {
            player.sendSystemMessage(Component.literal("The horizontal and vertical axis are not perpendicular in second side"));
            LOGGER.error("The horizontal and vertical axis are not perpendicular in second side");
         } else if (secondSideWidth > 64.0 || secondSideHeight > 64.0) {
            player.sendSystemMessage(Component.literal("The second side is too large"));
            LOGGER.error("The second side is too large");
         } else if (Math.abs(firstSideHeight / firstSideWidth - secondSideHeight / secondSideWidth) > 0.001) {
            player.sendSystemMessage(Component.literal("The two sides have different aspect ratio"));
            LOGGER.error("The two sides have different aspect ratio");
         } else {
            boolean overlaps = false;
            if (firstSideDimension == secondSideDimension) {
               Vec3 firstSideNormal = firstSideHorizontalUnitAxis.cross(firstSideVerticalUnitAxis);
               Vec3 secondSideNormal = secondSideHorizontalUnitAxis.cross(secondSideVerticalUnitAxis);
               if (Math.abs(firstSideNormal.dot(secondSideNormal)) > 0.99
                  && Math.abs(firstSideLeftBottom.subtract(secondSideLeftBottom).dot(firstSideNormal)) < 0.001) {
                  Vec3 coordCenter = firstSideLeftBottom;
                  Vec3 coordX = firstSideHorizontalAxis;
                  Vec3 coordY = firstSideVerticalAxis;
                  Range firstSideXRange = Range.createUnordered(
                     firstSideLeftBottom.subtract(coordCenter).dot(coordX), firstSideRightBottom.subtract(coordCenter).dot(coordX)
                  );
                  Range firstSideYRange = Range.createUnordered(
                     firstSideLeftBottom.subtract(coordCenter).dot(coordY), firstSideLeftUp.subtract(coordCenter).dot(coordY)
                  );
                  Range secondSideXRange = Range.createUnordered(
                     secondSideLeftBottom.subtract(coordCenter).dot(coordX), secondSideRightBottom.subtract(coordCenter).dot(coordX)
                  );
                  Range secondSideYRange = Range.createUnordered(
                     secondSideLeftBottom.subtract(coordCenter).dot(coordY), secondSideLeftUp.subtract(coordCenter).dot(coordY)
                  );
                  if (firstSideXRange.intersect(secondSideXRange) != null && firstSideYRange.intersect(secondSideYRange) != null) {
                     overlaps = true;
                  }
               }
            }

            Portal portal = Portal.ENTITY_TYPE.create(McHelper.getServerWorld(firstSideDimension), EntitySpawnReason.TRIGGERED);
            Validate.notNull(portal);
            portal.setOriginPos(firstSideLeftBottom.add(firstSideHorizontalAxis.scale(0.5)).add(firstSideVerticalAxis.scale(0.5)));
            portal.setWidth(firstSideWidth);
            portal.setHeight(firstSideHeight);
            portal.setAxisW(firstSideHorizontalUnitAxis);
            portal.setAxisH(firstSideVerticalUnitAxis);
            portal.setDestDim(secondSideDimension);
            portal.setDestination(secondSideLeftBottom.add(secondSideHorizontalAxis.scale(0.5)).add(secondSideVerticalAxis.scale(0.5)));
            portal.setScaling(secondSideWidth / firstSideWidth);
            DQuaternion secondSideOrientation = DQuaternion.matrixToQuaternion(
               secondSideHorizontalUnitAxis, secondSideVerticalUnitAxis, secondSideHorizontalUnitAxis.cross(secondSideVerticalUnitAxis)
            );
            portal.setOtherSideOrientation(secondSideOrientation);
            Portal flippedPortal = PortalManipulation.createFlippedPortal(portal, Portal.ENTITY_TYPE);
            Portal reversePortal = PortalManipulation.createReversePortal(portal, Portal.ENTITY_TYPE);
            Portal parallelPortal = PortalManipulation.createFlippedPortal(reversePortal, Portal.ENTITY_TYPE);
            McHelper.spawnServerEntity(portal);
            if (overlaps) {
               player.sendSystemMessage(Component.translatable("imm_ptl.wand.overlap"));
            } else {
               McHelper.spawnServerEntity(flippedPortal);
               McHelper.spawnServerEntity(reversePortal);
               McHelper.spawnServerEntity(parallelPortal);
            }

            player.sendSystemMessage(Component.translatable("imm_ptl.wand.finished"));
            giveCommandStick(player, "/portal eradicate_portal_cluster");
         }
      } else {
         player.sendSystemMessage(Component.literal("The first side is too large"));
         LOGGER.error("The first side is too large");
      }
   }

   public static void init() {
      Platform.get().onServerTickEnd(server -> {
         of(server).draggingSessionMap.entrySet().removeIf(e -> {
            ServerPlayer player = e.getKey();
            return player.isRemoved() ? true : player.getMainHandItem().getItem() != PortalWandItem.instance;
         });
         copyingSessionMap.entrySet().removeIf(e -> {
            ServerPlayer player = e.getKey();
            return player.isRemoved();
         });
      });
      IPGlobal.SERVER_CLEANUP_EVENT.register(s -> of(s).draggingSessionMap.clear());
      IPGlobal.SERVER_CLEANUP_EVENT.register(s -> copyingSessionMap.clear());
   }

   @Nullable
   public static UnilateralPortalState applyDrag(
      UnilateralPortalState originalState, Vec3 cursorPos, PortalWandInteraction.DraggingInfo info, boolean updateInternalState
   ) {
      if (info.lockedAnchor == null) {
         Vec3 offset = info.draggingAnchor.getOffset(originalState);
         Vec3 newPos = cursorPos.subtract(offset);
         return new UnilateralPortalState.Builder().from(originalState).position(newPos).build();
      }

      PortalWandInteraction.OneLockDraggingResult r = performDragWithOneLockedAnchor(
         originalState, info.lockedAnchor, info.draggingAnchor, cursorPos, info.previousRotationAxis, info.lockWidth, info.lockHeight
      );
      if (r == null) {
         return null;
      }

      if (updateInternalState) {
         info.previousRotationAxis = r.rotationAxis();
      }

      return r.newState();
   }

   private static void handleFinishDrag(ServerPlayer player) {
      PortalWandInteraction.DraggingSession session = of(player.server).draggingSessionMap.remove(player);
      if (session != null) {
         Portal portal = session.getPortal();
         if (portal != null) {
            portal.reloadAndSyncToClientNextTick();
         }
      }
   }

   private static void handleUndoDrag(ServerPlayer player) {
      PortalWandInteraction portalWandInteraction = of(player.server);
      PortalWandInteraction.DraggingSession session = portalWandInteraction.draggingSessionMap.get(player);
      if (session != null) {
         Portal portal = session.getPortal();
         if (portal == null) {
            LOGGER.error("Cannot find portal {}", session.portalId);
         } else {
            portal.setPortalState(session.originalState);
            portal.reloadAndSyncToClientNextTick();
            portal.rectifyClusterPortals(true);
            portalWandInteraction.draggingSessionMap.remove(player);
         }
      }
   }

   private static void handleDraggingRequest(ServerPlayer player, UUID portalId, Vec3 cursorPos, PortalWandInteraction.DraggingInfo draggingInfo, Portal portal) {
      PortalWandInteraction portalWandInteraction = of(player.server);
      PortalWandInteraction.DraggingSession session = portalWandInteraction.draggingSessionMap.get(player);
      if (session == null || !session.portalId.equals(portalId)) {
         session = new PortalWandInteraction.DraggingSession(player.level().dimension(), portalId, portal.getPortalState(), draggingInfo);
         portalWandInteraction.draggingSessionMap.put(player, session);
      }

      UnilateralPortalState newThisSideState = applyDrag(session.originalState.getThisSideState(), cursorPos, draggingInfo, true);
      if (validateDraggedPortalState(session.originalState, newThisSideState, player)) {
         portal.setThisSideState(newThisSideState, draggingInfo.shouldLockScale());
         portal.reloadAndSyncToClientNextTick();
         portal.rectifyClusterPortals(true);
      } else {
         player.sendSystemMessage(Component.literal("Invalid dragging"));
      }
   }

   private static boolean checkPermission(ServerPlayer player) {
      if (!canPlayerUsePortalWand(player)) {
         player.sendSystemMessage(Component.literal("You cannot use portal wand"));
         LOGGER.error("Player cannot use portal wand {}", player);
         return false;
      } else {
         return true;
      }
   }

   public static boolean validateDraggedPortalState(PortalState originalState, UnilateralPortalState newThisSideState, Player player) {
      if (newThisSideState == null) {
         return false;
      } else if (newThisSideState.width() > 64.1) {
         return false;
      } else if (newThisSideState.height() > 64.1) {
         return false;
      } else if (newThisSideState.width() < 0.05) {
         return false;
      } else if (newThisSideState.height() < 0.05) {
         return false;
      } else {
         return originalState.fromWorld != newThisSideState.dimension() ? false : !(newThisSideState.position().distanceTo(player.position()) > 64.0);
      }
   }

   private static boolean canPlayerUsePortalWand(ServerPlayer player) {
      return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
         || IPGlobal.easeCreativePermission && player.isCreative()
         || IPConfig.getConfig().portalWandUsableOnSurvivalMode && player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL;
   }

   private static void giveCommandStick(ServerPlayer player, String command) {
      CommandStickItem.Data data = CommandStickItem.BUILT_IN_COMMAND_STICK_TYPES.get(command);
      if (data == null) {
         data = new CommandStickItem.Data(command, command, List.of());
      }

      ItemStack stack = new ItemStack(CommandStickItem.instance);
      stack.set(CommandStickItem.COMPONENT_TYPE, data);
      if (!player.getInventory().contains(stack)) {
         player.getInventory().add(stack);
      }
   }

   public static boolean isDragging(ServerPlayer player) {
      return of(player.server).draggingSessionMap.containsKey(player);
   }

   @Nullable
   public static PortalWandInteraction.OneLockDraggingResult performDragWithOneLockedAnchor(
      UnilateralPortalState originalState,
      PortalLocalXYNormalized lockedLocalPos,
      PortalLocalXYNormalized draggingLocalPos,
      Vec3 draggedPos,
      @Nullable Vec3 previousRotationAxis,
      boolean lockWidth,
      boolean lockHeight
   ) {
      Vec3 draggedPosOriginalPos = draggingLocalPos.getPos(originalState);
      Vec3 lockedPos = lockedLocalPos.getPos(originalState);
      Vec3 originalOffset = draggedPosOriginalPos.subtract(lockedPos);
      Vec3 newOffset = draggedPos.subtract(lockedPos);
      double newOffsetLen = newOffset.length();
      double originalOffsetLen = originalOffset.length();
      if (!(newOffsetLen < 1.0E-5) && !(originalOffsetLen < 1.0E-5)) {
         Vec3 originalOffsetN = originalOffset.normalize();
         Vec3 newOffsetN = newOffset.normalize();
         double dot = originalOffsetN.dot(newOffsetN);
         DQuaternion rotation;
         if (Math.abs(dot) < 0.99999) {
            rotation = DQuaternion.getRotationBetween(originalOffset, newOffset).fixFloatingPointErrorAccumulation();
         } else if (dot > 0.0) {
            rotation = DQuaternion.identity;
         } else {
            Plane planeOfPossibleAxis = new Plane(Vec3.ZERO, originalOffsetN);
            if (previousRotationAxis != null) {
               Vec3 projected = planeOfPossibleAxis.getProjection(previousRotationAxis);
               if (projected.lengthSqr() < 1.0E-5) {
                  return null;
               }

               Vec3 axis = projected.normalize();
               rotation = DQuaternion.rotationByDegrees(axis, 180.0).fixFloatingPointErrorAccumulation();
            } else {
               rotation = DQuaternion.identity;
            }
         }

         DQuaternion newOrientation = rotation.hamiltonProduct(originalState.orientation()).fixFloatingPointErrorAccumulation();
         PortalLocalXYNormalized deltaLocalXY = draggingLocalPos.subtract(lockedLocalPos);
         double newHeight;
         double newWidth;
         if (lockWidth && lockHeight) {
            newWidth = originalState.width();
            newHeight = originalState.height();
         } else {
            Vec3 newNormal = rotation.rotate(originalState.getNormal());
            if (lockWidth) {
               assert !lockHeight;
               newWidth = originalState.width();
               if (Math.abs(deltaLocalXY.ny()) < 0.001) {
                  newHeight = originalState.height();
               } else {
                  double subWidth = Math.abs(deltaLocalXY.nx()) * newWidth;
                  double diff = newOffsetLen * newOffsetLen - subWidth * subWidth;
                  if (diff < 1.0E-6) {
                     return null;
                  }

                  double subHeight = Math.sqrt(diff);
                  newHeight = subHeight / Math.abs(deltaLocalXY.ny());
                  if (Math.abs(subWidth) > 0.001) {
                     newOrientation = getOrientationByNormalDiagonalWidthHeight(
                        newNormal, newOffset, subWidth, subHeight, Math.signum(deltaLocalXY.nx()), Math.signum(deltaLocalXY.ny())
                     );
                  }
               }
            } else if (lockHeight) {
               assert !lockWidth;
               newHeight = originalState.height();
               if (Math.abs(deltaLocalXY.nx()) < 0.001) {
                  newWidth = originalState.width();
               } else {
                  double subHeight = Math.abs(deltaLocalXY.ny()) * newHeight;
                  double diff = newOffsetLen * newOffsetLen - subHeight * subHeight;
                  if (diff < 1.0E-6) {
                     return null;
                  }

                  double subWidth = Math.sqrt(diff);
                  newWidth = subWidth / Math.abs(deltaLocalXY.nx());
                  if (Math.abs(subHeight) > 0.001) {
                     newOrientation = getOrientationByNormalDiagonalWidthHeight(
                        newNormal, newOffset, subWidth, subHeight, Math.signum(deltaLocalXY.nx()), Math.signum(deltaLocalXY.ny())
                     );
                  }
               }
            } else {
               double scaling = newOffsetLen / originalOffsetLen;
               newWidth = originalState.width() * scaling;
               newHeight = originalState.height() * scaling;
            }
         }

         Vec3 newLockedPosOffset = newOrientation.rotate(new Vec3((lockedLocalPos.nx() - 0.5) * newWidth, (lockedLocalPos.ny() - 0.5) * newHeight, 0.0));
         Vec3 newOrigin = lockedPos.subtract(newLockedPosOffset);
         UnilateralPortalState newPortalState = new UnilateralPortalState(originalState.dimension(), newOrigin, newOrientation, newWidth, newHeight);
         return new PortalWandInteraction.OneLockDraggingResult(newPortalState, rotation.getRotatingAxis());
      } else {
         return null;
      }
   }

   private static DQuaternion getOrientationByNormalDiagonalWidthHeight(Vec3 normal, Vec3 diagonal, double width, double height, double sigX, double sigY) {
      Vec3 newOffsetN = diagonal.normalize();
      double newOffsetLen = diagonal.length();
      Vec3 sideVecN = normal.cross(newOffsetN).normalize();
      double sideVecLen = width * height / newOffsetLen;
      double wFront = sideVecLen * width / height;
      double hFront = sideVecLen * height / width;
      Vec3 sideVecW = sideVecN.scale(-sideVecLen * sigX * sigY);
      Vec3 sideVecH = sideVecW.scale(-1.0);
      Vec3 newAxisW = newOffsetN.scale(wFront).add(sideVecW).normalize().scale(sigX);
      Vec3 newAxisH = newOffsetN.scale(hFront).add(sideVecH).normalize().scale(sigY);
      return DQuaternion.fromFacingVecs(newAxisW, newAxisH).fixFloatingPointErrorAccumulation();
   }

   public static void handleCopyCutPortal(ServerPlayer player, UUID portalId, boolean isCut) {
      Portal portal = WandUtil.getPortalByUUID(player.level(), portalId);
      if (portal == null) {
         player.sendSystemMessage(Component.literal("Cannot find portal " + portalId));
      } else {
         PortalState portalState = portal.getPortalState();
         CompoundTag portalData = portal.writePortalDataToNbt();
         Portal flipped = null;
         Portal reverse = null;
         Portal parallel = null;
         PortalExtension ext = PortalExtension.get(portal);
         if (ext.flippedPortal != null) {
            flipped = ext.flippedPortal;
         }

         if (ext.reversePortal != null) {
            reverse = ext.reversePortal;
         }

         if (ext.parallelPortal != null) {
            parallel = ext.parallelPortal;
         }

         PortalWandInteraction.CopyingSession copyingSession = new PortalWandInteraction.CopyingSession(
            portalState, portalData, isCut, flipped != null, reverse != null, parallel != null
         );
         copyingSessionMap.put(player, copyingSession);
         if (isCut) {
            portal.remove(RemovalReason.KILLED);
            if (flipped != null) {
               flipped.remove(RemovalReason.KILLED);
            }

            if (reverse != null) {
               reverse.remove(RemovalReason.KILLED);
            }

            if (parallel != null) {
               parallel.remove(RemovalReason.KILLED);
            }
         }
      }
   }

   private static void handleConfirmCopyCut(ServerPlayer player, Vec3 origin, DQuaternion rawOrientation) {
      PortalWandInteraction.CopyingSession copyingSession = copyingSessionMap.remove(player);
      if (copyingSession == null) {
         player.sendSystemMessage(Component.literal("Missing copying session"));
      } else {
         DQuaternion orientation = rawOrientation.fixFloatingPointErrorAccumulation();
         if (player.position().distanceToSqr(origin) > 4096.0) {
            player.sendSystemMessage(Component.literal("Too far away from the portal"));
         } else {
            Portal portal = Portal.ENTITY_TYPE.create(player.level(), EntitySpawnReason.TRIGGERED);
            assert portal != null;
            portal.readPortalDataFromNbt(copyingSession.portalData);
            PortalState originalPortalState = copyingSession.portalState;
            UnilateralPortalState originalThisSide = originalPortalState.getThisSideState();
            UnilateralPortalState originalOtherSide = originalPortalState.getOtherSideState();
            UnilateralPortalState newThisSide = new UnilateralPortalState(
               player.level().dimension(), origin, orientation, originalThisSide.width(), originalThisSide.height()
            );
            portal.setPortalState(UnilateralPortalState.combine(newThisSide, originalOtherSide));
            portal.resetAnimationReferenceState(true, false);
            if (copyingSession.isCut()) {
               McHelper.spawnServerEntity(portal);
               if (copyingSession.hasFlipped) {
                  Portal flippedPortal = PortalManipulation.createFlippedPortal(portal, Portal.ENTITY_TYPE);
                  flippedPortal.resetAnimationReferenceState(true, false);
                  McHelper.spawnServerEntity(flippedPortal);
               }

               if (copyingSession.hasReverse) {
                  Portal reversePortal = PortalManipulation.createReversePortal(portal, Portal.ENTITY_TYPE);
                  reversePortal.resetAnimationReferenceState(false, true);
                  McHelper.spawnServerEntity(reversePortal);
                  if (copyingSession.hasParallel) {
                     Portal parallelPortal = PortalManipulation.createFlippedPortal(reversePortal, Portal.ENTITY_TYPE);
                     parallelPortal.resetAnimationReferenceState(false, true);
                     McHelper.spawnServerEntity(parallelPortal);
                  }
               }
            } else {
               PortalExtension.get(portal).bindCluster = false;
               McHelper.spawnServerEntity(portal);
               if (copyingSession.hasFlipped || copyingSession.hasReverse || copyingSession.hasParallel) {
                  player.sendSystemMessage(Component.translatable("imm_ptl.wand.copy.not_copying_cluster"));
                  giveCommandStick(player, "/portal complete_bi_way_bi_faced_portal");
               }
            }
         }
      }
   }

   private static void handleClearPortalClipboard(ServerPlayer player) {
      copyingSessionMap.remove(player);
   }

   private record CopyingSession(PortalState portalState, CompoundTag portalData, boolean isCut, boolean hasFlipped, boolean hasReverse, boolean hasParallel) {
   }

   public static final class DraggingInfo {
      @Nullable
      public final PortalLocalXYNormalized lockedAnchor;
      public final PortalLocalXYNormalized draggingAnchor;
      @Nullable
      public Vec3 previousRotationAxis;
      public final boolean lockWidth;
      public final boolean lockHeight;

      public DraggingInfo(
         @Nullable PortalLocalXYNormalized lockedAnchor,
         PortalLocalXYNormalized draggingAnchor,
         @Nullable Vec3 previousRotationAxis,
         boolean lockWidth,
         boolean lockHeight
      ) {
         this.lockedAnchor = lockedAnchor;
         this.draggingAnchor = draggingAnchor;
         this.previousRotationAxis = previousRotationAxis;
         this.lockWidth = lockWidth;
         this.lockHeight = lockHeight;
      }

      public boolean shouldLockScale() {
         return this.lockWidth || this.lockHeight;
      }

      public boolean isValid() {
         return this.lockedAnchor != null && !this.lockedAnchor.isValid() ? false : this.draggingAnchor.isValid();
      }
   }

   private static class DraggingSession {
      private final ResourceKey<Level> dimension;
      private final UUID portalId;
      private final PortalState originalState;
      private final PortalWandInteraction.DraggingInfo lastDraggingInfo;

      public DraggingSession(ResourceKey<Level> dimension, UUID portalId, PortalState originalState, PortalWandInteraction.DraggingInfo lastDraggingInfo) {
         this.dimension = dimension;
         this.portalId = portalId;
         this.originalState = originalState;
         this.lastDraggingInfo = lastDraggingInfo;
      }

      @Nullable
      public Portal getPortal() {
         Entity entity = McHelper.getServerWorld(this.dimension).getEntity(this.portalId);
         return entity instanceof Portal ? (Portal)entity : null;
      }
   }

   public record OneLockDraggingResult(UnilateralPortalState newState, Vec3 rotationAxis) {
   }

   public static class RemoteCallables {
      public static void finishPortalCreation(ServerPlayer player, ProtoPortal protoPortal) {
         if (PortalWandInteraction.checkPermission(player)) {
            PortalWandInteraction.handleFinishPortalCreation(player, protoPortal);
         }
      }

      public static void requestApplyDrag(ServerPlayer player, UUID portalId, Vec3 cursorPos, PortalWandInteraction.DraggingInfo draggingInfo) {
         if (PortalWandInteraction.checkPermission(player)) {
            if (player.level().getEntity(portalId) instanceof Portal portal) {
               if (!draggingInfo.isValid()) {
                  player.sendSystemMessage(Component.literal("Invalid dragging info"));
                  PortalWandInteraction.LOGGER.error("Invalid dragging info {}", draggingInfo);
               } else {
                  PortalWandInteraction.handleDraggingRequest(player, portalId, cursorPos, draggingInfo, portal);
               }
            } else {
               PortalWandInteraction.LOGGER.error("Cannot find portal {}", portalId);
            }
         }
      }

      public static void undoDrag(ServerPlayer player) {
         if (PortalWandInteraction.checkPermission(player)) {
            PortalWandInteraction.handleUndoDrag(player);
         }
      }

      public static void finishDragging(ServerPlayer player) {
         if (PortalWandInteraction.checkPermission(player)) {
            PortalWandInteraction.handleFinishDrag(player);
         }
      }

      public static void copyCutPortal(ServerPlayer player, UUID portalId, boolean isCut) {
         if (PortalWandInteraction.checkPermission(player)) {
            PortalWandInteraction.handleCopyCutPortal(player, portalId, isCut);
         }
      }

      public static void confirmCopyCut(ServerPlayer player, Vec3 origin, DQuaternion orientation) {
         if (PortalWandInteraction.checkPermission(player)) {
            PortalWandInteraction.handleConfirmCopyCut(player, origin, orientation);
         }
      }

      public static void clearPortalClipboard(ServerPlayer player) {
         if (PortalWandInteraction.checkPermission(player)) {
            PortalWandInteraction.handleClearPortalClipboard(player);
         }
      }
   }
}
