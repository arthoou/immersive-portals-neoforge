package qouteall.imm_ptl.core.portal;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.portal.nether_portal.BlockPortalShape;
import qouteall.imm_ptl.core.portal.shape.SpecialFlatPortalShape;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.IntBox;
import qouteall.q_misc_util.my_util.Mesh2D;

public class BreakableMirror extends Mirror {
   public static final EntityType<BreakableMirror> ENTITY_TYPE = createPortalEntityType(BreakableMirror::new, portalEntityTypeKey("breakable_mirror"));
   @Nullable
   public IntBox wallArea;
   @Nullable
   public BlockPortalShape blockPortalShape;
   public boolean unbreakable = false;

   public BreakableMirror(EntityType<?> entityType, Level world) {
      super(entityType, world);
   }

   @Override
   protected void readAdditionalSaveData(CompoundTag tag) {
      super.readAdditionalSaveData(tag);
      if (tag.contains("boxXL")) {
         this.wallArea = new IntBox(
            new BlockPos(tag.getIntOr("boxXL", 0), tag.getIntOr("boxYL", 0), tag.getIntOr("boxZL", 0)),
            new BlockPos(tag.getIntOr("boxXH", 0), tag.getIntOr("boxYH", 0), tag.getIntOr("boxZH", 0))
         );
      } else {
         this.wallArea = null;
      }

      if (tag.contains("blockPortalShape")) {
         this.blockPortalShape = BlockPortalShape.fromTag(tag.getCompoundOrEmpty("blockPortalShape"));
      } else {
         this.blockPortalShape = null;
      }

      if (tag.contains("unbreakable")) {
         this.unbreakable = tag.getBooleanOr("unbreakable", false);
      }
   }

   @Override
   protected void addAdditionalSaveData(CompoundTag tag) {
      super.addAdditionalSaveData(tag);
      if (this.wallArea != null) {
         tag.putInt("boxXL", this.wallArea.l.getX());
         tag.putInt("boxYL", this.wallArea.l.getY());
         tag.putInt("boxZL", this.wallArea.l.getZ());
         tag.putInt("boxXH", this.wallArea.h.getX());
         tag.putInt("boxYH", this.wallArea.h.getY());
         tag.putInt("boxZH", this.wallArea.h.getZ());
      }

      if (this.blockPortalShape != null) {
         tag.put("blockPortalShape", this.blockPortalShape.toTag());
      }

      tag.putBoolean("unbreakable", this.unbreakable);
   }

   @Override
   public void tick() {
      super.tick();
      if (!this.level().isClientSide() && !this.unbreakable && this.level().getGameTime() % 10L == this.getId() % 10) {
         this.checkWallIntegrity();
      }
   }

   @Override
   public boolean isPortalValid() {
      return super.isPortalValid() && (this.wallArea != null || this.blockPortalShape != null);
   }

   private void checkWallIntegrity() {
      boolean wallValid;
      if (this.wallArea != null) {
         wallValid = this.wallArea.fastStream().allMatch(blockPos -> isGlass(this.level(), blockPos));
      } else if (this.blockPortalShape != null) {
         wallValid = this.blockPortalShape.area.stream().allMatch(blockPos -> isGlass(this.level(), blockPos));
      } else {
         wallValid = false;
      }

      if (!wallValid) {
         this.remove(RemovalReason.KILLED);
      }
   }

   public static boolean isGlass(Level world, BlockPos blockPos) {
      Block block = world.getBlockState(blockPos).getBlock();
      return block == Blocks.GLASS || block == Blocks.GLASS_PANE || block instanceof StainedGlassBlock || block instanceof StainedGlassPaneBlock;
   }

   private static boolean isGlassPane(Level world, BlockPos blockPos) {
      Block block = world.getBlockState(blockPos).getBlock();
      return block == Blocks.GLASS_PANE || block instanceof StainedGlassPaneBlock;
   }

   public static BreakableMirror createMirror(ServerLevel world, BlockPos glassPos, Direction facing) {
      if (!isGlass(world, glassPos)) {
         return null;
      }

      boolean isPane = isGlassPane(world, glassPos);
      if (facing.getAxis() == Axis.Y && isPane) {
         return null;
      }

      Predicate<BlockPos> glassWallPredicate = blockPos -> isGlass(world, blockPos)
         && isPane == isGlassPane(world, blockPos)
         && world.getBlockState(blockPos.relative(facing)).isAir();
      BlockPortalShape shape = BlockPortalShape.findArea(glassPos, facing.getAxis(), glassWallPredicate, blockPos -> !glassWallPredicate.test(blockPos));
      if (shape == null) {
         return null;
      }

      BreakableMirror breakableMirror = ENTITY_TYPE.create(world, EntitySpawnReason.TRIGGERED);
      assert breakableMirror != null;
      double distanceToCenter = isPane ? 0.0625 : 0.5;
      breakableMirror.blockPortalShape = shape;
      AABB wallBox = McHelper.getWallBox(world, shape.area.stream());
      if (wallBox == null) {
         return null;
      }

      Vec3 pos = Helper.getBoxSurfaceInversed(wallBox, facing.getOpposite()).getCenter();
      pos = Helper.putCoordinate(
         pos,
         facing.getAxis(),
         Helper.getCoordinate(shape.innerAreaBox.getCenterVec().add(Vec3.atLowerCornerOf(facing.getUnitVec3i()).scale(distanceToCenter)), facing.getAxis())
      );
      breakableMirror.setPos(pos.x, pos.y, pos.z);
      breakableMirror.setDestination(pos);
      breakableMirror.setDestDim(world.dimension());
      Tuple<Direction, Direction> perpendicularDirections = Helper.getPerpendicularDirections(facing);
      Direction wDirection = perpendicularDirections.getA();
      Direction hDirection = perpendicularDirections.getB();
      breakableMirror.setWidth(Helper.getCoordinate(Helper.getBoxSize(wallBox), wDirection.getAxis()));
      breakableMirror.setHeight(Helper.getCoordinate(Helper.getBoxSize(wallBox), hDirection.getAxis()));
      breakableMirror.setAxisW(Vec3.atLowerCornerOf(wDirection.getUnitVec3i()));
      breakableMirror.setAxisH(Vec3.atLowerCornerOf(hDirection.getUnitVec3i()));
      initializeMirrorGeometryShape(breakableMirror, facing, shape);
      breakIntersectedMirror(breakableMirror);
      world.addFreshEntity(breakableMirror);
      return breakableMirror;
   }

   private static void initializeMirrorGeometryShape(BreakableMirror breakableMirror, Direction facing, BlockPortalShape shape) {
      if (shape.isRectangle()) {
         breakableMirror.setPortalShapeToDefault();
      } else {
         Vec3 center = breakableMirror.getOriginPos();
         Level world = breakableMirror.level();
         Vec3 axisW = breakableMirror.getAxisW();
         Vec3 axisH = breakableMirror.getAxisH();
         Mesh2D mesh2D = new Mesh2D();

         for (BlockPos blockPos : shape.area) {
            VoxelShape collisionShape = world.getBlockState(blockPos).getCollisionShape(world, blockPos);
            if (!collisionShape.isEmpty()) {
               AABB bounds = collisionShape.bounds().move(Vec3.atLowerCornerOf(blockPos));
               Vec3 p1 = new Vec3(bounds.minX, bounds.minY, bounds.minZ);
               Vec3 p2 = new Vec3(bounds.maxX, bounds.maxY, bounds.maxZ);
               double p1LocalX = p1.subtract(center).dot(axisW);
               double p1LocalY = p1.subtract(center).dot(axisH);
               double p2LocalX = p2.subtract(center).dot(axisW);
               double p2LocalY = p2.subtract(center).dot(axisH);
               mesh2D.addQuad(p1LocalX, p1LocalY, p2LocalX, p2LocalY);
            }
         }

         breakableMirror.setPortalShape(new SpecialFlatPortalShape(mesh2D));
      }
   }

   public IntBox getAreaBox() {
      if (this.wallArea != null) {
         return this.wallArea;
      } else if (this.blockPortalShape != null) {
         return this.blockPortalShape.innerAreaBox;
      } else {
         throw new RuntimeException();
      }
   }

   private static void breakIntersectedMirror(BreakableMirror newMirror) {
      McHelper.getEntitiesNearby(newMirror, BreakableMirror.class, IPGlobal.maxNormalPortalRadius)
         .stream()
         .filter(mirror1 -> mirror1.getNormal().dot(newMirror.getNormal()) > 0.5)
         .filter(mirror1 -> IntBox.getIntersect(mirror1.getAreaBox(), newMirror.getAreaBox()) != null)
         .filter(mirror -> mirror != newMirror)
         .forEach(e -> e.remove(RemovalReason.KILLED));
   }
}
