package qouteall.imm_ptl.core.portal;

import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.levelgen.feature.EndPlatformFeature;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.mixin.common.miscellaneous.IEEndDragonFight;
import qouteall.imm_ptl.core.portal.animation.NormalAnimation;
import qouteall.imm_ptl.core.portal.animation.RotationAnimation;
import qouteall.imm_ptl.core.portal.shape.BoxPortalShape;
import qouteall.q_misc_util.Helper;

public class EndPortalEntity extends Portal {
   private static final Logger LOGGER = LogManager.getLogger(EndPortalEntity.class);
   public static final EntityType<EndPortalEntity> ENTITY_TYPE = createPortalEntityType(EndPortalEntity::new, portalEntityTypeKey("end_portal"));
   public static final String PORTAL_TAG_VIEW_BOX = "view_box";
   private static final double BOX_PORTAL_SIDE_LEN = 3.0;
   private static final double BOX_PORTAL_HEIGHT = 1.5;
   private static final Vec3 NORMAL_MODE_DESTINATION = new Vec3(0.0, 120.0, 0.0);

   public EndPortalEntity(EntityType<?> entityType, Level world) {
      super(entityType, world);
      this.setCrossPortalCollisionEnabled(false);
   }

   public static void onEndPortalComplete(ServerLevel world, Vec3 portalCenter) {
      ServerLevel endDim = world.getServer().getLevel(Level.END);
      if (endDim != null) {
         IPGlobal.EndPortalMode endPortalMode = IPGlobal.endPortalMode;
         if (endPortalMode == IPGlobal.EndPortalMode.normal) {
            generateClassicalEndPortal(world, NORMAL_MODE_DESTINATION, portalCenter);
         } else if (endPortalMode == IPGlobal.EndPortalMode.toObsidianPlatform) {
            generateClassicalEndPortal(world, getObsidianPlatformDestination(), portalCenter);
         } else if (endPortalMode == IPGlobal.EndPortalMode.scaledView) {
            generateScaledViewEndPortal(world, portalCenter, false);
         } else if (endPortalMode == IPGlobal.EndPortalMode.scaledViewRotating) {
            generateScaledViewEndPortal(world, portalCenter, true);
         } else {
            LOGGER.error("End portal mode abnormal");
         }

         doCreateEndPlatform(endDim);
         EndDragonFight dragonFight = endDim.getDragonFight();
         if (dragonFight != null) {
            if (((IEEndDragonFight)dragonFight).ip_getNeedsStateScanning()) {
               ((IEEndDragonFight)dragonFight).ip_scanState();
            }
         }
      }
   }

   private static void doCreateEndPlatform(ServerLevel world) {
      EndPlatformFeature.createEndPlatform(world, BlockPos.containing(ServerLevel.END_SPAWN_POINT.getBottomCenter()).below(), true);
   }

   private static void generateClassicalEndPortal(ServerLevel world, Vec3 destination, Vec3 portalCenter) {
      Portal portal = new EndPortalEntity(ENTITY_TYPE, world);
      portal.setPos(portalCenter.x, portalCenter.y, portalCenter.z);
      portal.setDestination(destination);
      portal.setDestDim(Level.END);
      portal.setAxisW(new Vec3(0.0, 0.0, 1.0));
      portal.setAxisH(new Vec3(1.0, 0.0, 0.0));
      portal.setWidth(3.0);
      portal.setHeight(3.0);
      world.addFreshEntity(portal);
   }

   private static void generateScaledViewEndPortal(ServerLevel world, Vec3 frameCenter, boolean hasAnimation) {
      ServerLevel endWorld = world.getServer().getLevel(Level.END);
      Validate.notNull(endWorld, "End dimension is not loaded");
      double sideLen = 3.0;
      double otherSideSideLen = 288.0;
      double height = 1.5;
      Vec3 viewBoxSize = new Vec3(sideLen, height, sideLen);
      double scale = otherSideSideLen / sideLen;
      AABB thisSideBox = Helper.getBoxByBottomPosAndSize(frameCenter.add(0.0, 0.2, 0.0), viewBoxSize);
      AABB otherSideBox = Helper.getBoxByBottomPosAndSize(new Vec3(0.0, 0.0, 0.0), viewBoxSize.scale(scale));
      Vec3 portalCenter = thisSideBox.getCenter();
      EndPortalEntity portal = ENTITY_TYPE.create(world, EntitySpawnReason.TRIGGERED);
      assert portal != null;
      portal.setOriginPos(portalCenter);
      portal.setDestDim(endWorld.dimension());
      portal.setDestination(otherSideBox.getCenter());
      portal.setAxisW(new Vec3(1.0, 0.0, 0.0));
      portal.setAxisH(new Vec3(0.0, 1.0, 0.0));
      portal.setWidth(sideLen);
      portal.setHeight(height);
      portal.setThickness(sideLen);
      portal.setPortalShape(BoxPortalShape.FACING_OUTWARDS);
      portal.setScaling(scale);
      portal.setTeleportChangesScale(false);
      portal.portalTag = "view_box";
      portal.setInteractable(false);
      portal.setCrossPortalCollisionEnabled(false);
      portal.setFuseView(true);
      if (hasAnimation) {
         portal.addThisSideAnimationDriver(
            new RotationAnimation.Builder()
               .setDegreesPerTick(0.5)
               .setInitialOffset(Vec3.ZERO)
               .setRotationAxis(new Vec3(0.0, 1.0, 0.0))
               .setStartGameTime(world.getGameTime())
               .setEndGameTime(Long.MAX_VALUE)
               .build()
         );
         portal.addThisSideAnimationDriver(NormalAnimation.createOscillationAnimation(new Vec3(0.0, 0.3, 0.0), 100, world.getGameTime()));
      }

      McHelper.spawnServerEntity(portal);
   }

   @Override
   public void tick() {
      super.tick();
      if (this.level().isClientSide()) {
         this.tickClient();
      } else if (!this.isRemoved() && this.tickCount % 20 == 0) {
         this.followEndPortalMode((ServerLevel)this.level());
      }
   }

   private void followEndPortalMode(ServerLevel world) {
      IPGlobal.EndPortalMode currentMode = IPGlobal.endPortalMode;
      IPGlobal.EndPortalMode madeInMode = this.getModeItWasMadeIn();
      if (madeInMode != null && madeInMode != currentMode) {
         BlockPos interiorCorner = this.findFrameInterior(world);
         if (interiorCorner != null) {
            LOGGER.info("End portal at {} made in mode {} follows the End portal mode {}", interiorCorner, madeInMode, currentMode);
            this.remove(RemovalReason.KILLED);
            if (currentMode == IPGlobal.EndPortalMode.vanilla) {
               for (int dx = 0; dx < 3; dx++) {
                  for (int dz = 0; dz < 3; dz++) {
                     world.setBlock(interiorCorner.offset(dx, 0, dz), Blocks.END_PORTAL.defaultBlockState(), 2);
                  }
               }
            } else {
               onEndPortalComplete(world, Vec3.atLowerCornerOf(interiorCorner).add(1.5, 0.5, 1.5));
            }
         }
      }
   }

   @Nullable
   private IPGlobal.EndPortalMode getModeItWasMadeIn() {
      if (this.isViewBoxPortal()) {
         return this.animation.thisSideAnimations.isEmpty() ? IPGlobal.EndPortalMode.scaledView : IPGlobal.EndPortalMode.scaledViewRotating;
      } else if (this.getDestDim() != Level.END) {
         return null;
      } else if (this.getDestPos().distanceToSqr(NORMAL_MODE_DESTINATION) < 1.0E-4) {
         return IPGlobal.EndPortalMode.normal;
      } else {
         return this.getDestPos().distanceToSqr(getObsidianPlatformDestination()) < 1.0E-4 ? IPGlobal.EndPortalMode.toObsidianPlatform : null;
      }
   }

   @Nullable
   private BlockPos findFrameInterior(ServerLevel world) {
      BlockPos near = BlockPos.containing(this.getOriginPos());
      int minX = Integer.MAX_VALUE;
      int minY = Integer.MAX_VALUE;
      int minZ = Integer.MAX_VALUE;
      int maxX = Integer.MIN_VALUE;
      int maxY = Integer.MIN_VALUE;
      int maxZ = Integer.MIN_VALUE;
      int count = 0;

      for (BlockPos pos : BlockPos.betweenClosed(near.offset(-2, -3, -2), near.offset(2, 1, 2))) {
         BlockState state = world.getBlockState(pos);
         if (state.getBlock() == PortalPlaceholderBlock.instance && state.getValue(PortalPlaceholderBlock.AXIS) == Axis.Y) {
            count++;
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
         }
      }

      return count == 9 && maxX - minX == 2 && maxZ - minZ == 2 && maxY == minY ? new BlockPos(minX, minY, minZ) : null;
   }

   private static Vec3 getObsidianPlatformDestination() {
      return Vec3.atCenterOf(ServerLevel.END_SPAWN_POINT).add(0.0, 1.0, 0.0);
   }

   @Environment(EnvType.CLIENT)
   private void tickClient() {
      if (this.isViewBoxPortal()) {
         LocalPlayer player = Minecraft.getInstance().player;
         if (player == null) {
            return;
         }

         if (this.getPortalShape() instanceof BoxPortalShape) {
            Vec3 cameraPosVec = player.getEyePosition(1.0F);
            double dist = this.getDistanceToNearestPointInPortal(cameraPosVec);
            if (dist < 1.0 && this.isInBoxPortalTeleportataionRange(cameraPosVec)) {
               double mul = 0.5;
               player.setDeltaMovement(player.getDeltaMovement().x * mul, player.getDeltaMovement().y * mul, player.getDeltaMovement().z * mul);
            }
         } else if (this.getNormal().y > 0.5 && ((IEEntity)player).ip_getCollidingPortal() == this) {
            Vec3 cameraPosVec = player.getEyePosition(1.0F);
            double dist = this.getDistanceToNearestPointInPortal(cameraPosVec);
            if (dist < 1.0) {
               double mul = dist / 2.0 + 0.1;
               player.setDeltaMovement(player.getDeltaMovement().x * mul, player.getDeltaMovement().y * mul, player.getDeltaMovement().z * mul);
            }
         }

         this.setFuseView(true);
      }
   }

   @Override
   public void onEntityTeleportedOnServer(Entity entity) {
      super.onEntityTeleportedOnServer(entity);
      if (this.shouldAddSlowFalling(entity)) {
         int duration = 200;
         if (this.isViewBoxPortal()) {
            duration = 400;
         }

         LivingEntity livingEntity = (LivingEntity)entity;
         livingEntity.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, duration, 1));
      }

      MinecraftServer server = this.level().getServer();
      assert server != null;
      ServerLevel endWorld = server.getLevel(Level.END);
      if (endWorld != null) {
         doCreateEndPlatform(endWorld);
      }
   }

   private boolean isViewBoxPortal() {
      return Objects.equals(this.portalTag, "view_box");
   }

   @Override
   public Vec3 transformVelocityRelativeToPortal(Vec3 originalVelocityRelativeToPortal, Entity entity, Vec3 oldEntityPos) {
      if (this.isViewBoxPortal()) {
         Vec3 offset = oldEntityPos.subtract(this.getOriginPos());
         double threshold = 1.0;
         double horizontalDistanceSq = offset.x * offset.x + offset.z * offset.z;
         if (horizontalDistanceSq > threshold * threshold) {
            return Vec3.ZERO;
         }
      }

      return super.transformVelocityRelativeToPortal(originalVelocityRelativeToPortal, entity, oldEntityPos);
   }

   @Override
   public boolean canTeleportEntity(Entity entity) {
      if (entity instanceof Arrow) {
         return false;
      } else {
         return this.getPortalShape() instanceof BoxPortalShape && !this.isInBoxPortalTeleportataionRange(entity.getEyePosition(1.0F))
            ? false
            : super.canTeleportEntity(entity);
      }
   }

   private boolean shouldAddSlowFalling(Entity entity) {
      if (entity instanceof LivingEntity) {
         if (entity instanceof ServerPlayer player) {
            if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) {
               return false;
            }

            if (player.getItemBySlot(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean shouldLimitBoundingBox() {
      return false;
   }

   @Override
   public void onCollidingWithEntity(Entity entity) {
      if (!this.level().isClientSide() && entity instanceof ServerPlayer && IPGlobal.endPortalMode == IPGlobal.EndPortalMode.toObsidianPlatform) {
         MinecraftServer server = this.level().getServer();
         assert server != null;
         ServerLevel endWorld = server.getLevel(Level.END);
         if (endWorld != null) {
            doCreateEndPlatform(endWorld);
         }
      }
   }

   public boolean isInBoxPortalTeleportataionRange(Vec3 pos) {
      Vec3 offset = pos.subtract(this.getOriginPos());
      double threshold = 1.5;
      double horizontalDistanceSq = offset.x * offset.x + offset.z * offset.z;
      return horizontalDistanceSq <= threshold * threshold;
   }
}
