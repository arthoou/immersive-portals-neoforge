package qouteall.imm_ptl.core.portal.nether_portal;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.Tuple;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.shape.SpecialFlatPortalShape;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.IntBox;
import qouteall.q_misc_util.my_util.Mesh2D;

public class BlockPortalShape {
   public static int defaultLengthLimit = 64;
   public BlockPos anchor;
   public Set<BlockPos> area;
   public IntBox innerAreaBox;
   public IntBox totalAreaBox;
   public Axis axis;
   public Set<BlockPos> frameAreaWithoutCorner;
   public Set<BlockPos> frameAreaWithCorner;
   public BlockPos firstFramePos;

   public BlockPortalShape(Set<BlockPos> area, Axis axis) {
      this.area = area;
      this.axis = axis;
      this.calcAnchor();
      this.calcFrameArea();
      this.calcAreaBox();
   }

   public BlockPortalShape(CompoundTag tag) {
      this(readArea(tag.getListOrEmpty("poses")), Axis.values()[tag.getIntOr("axis", 0)]);
   }

   private static Set<BlockPos> readArea(ListTag list) {
      int size = list.size();
      Validate.isTrue(size % 3 == 0);
      Set<BlockPos> result = new HashSet<>();

      for (int i = 0; i < size / 3; i++) {
         result.add(new BlockPos(list.getIntOr(i * 3 + 0, 0), list.getIntOr(i * 3 + 1, 0), list.getIntOr(i * 3 + 2, 0)));
      }

      return result;
   }

   public static BlockPortalShape fromTag(CompoundTag tag) {
      return new BlockPortalShape(tag);
   }

   public CompoundTag toTag() {
      CompoundTag data = new CompoundTag();
      ListTag list = new ListTag();
      this.area.forEach(blockPos -> {
         list.add(list.size(), IntTag.valueOf(blockPos.getX()));
         list.add(list.size(), IntTag.valueOf(blockPos.getY()));
         list.add(list.size(), IntTag.valueOf(blockPos.getZ()));
      });
      data.put("poses", list);
      data.putInt("axis", this.axis.ordinal());
      return data;
   }

   public void calcAnchor() {
      this.anchor = this.area.stream().min(Comparator.comparingInt(Vec3i::getX).thenComparingInt(Vec3i::getY).thenComparingInt(Vec3i::getZ)).get();
      Validate.notNull(this.anchor);
   }

   public void calcAreaBox() {
      this.innerAreaBox = Helper.reduce(new IntBox(this.anchor, this.anchor), this.area.stream(), IntBox::getExpanded);
      this.totalAreaBox = Helper.reduce(new IntBox(this.anchor, this.anchor), this.frameAreaWithoutCorner.stream(), IntBox::getExpanded);
   }

   public void calcFrameArea() {
      Direction[] directions = Helper.getAnotherFourDirections(this.axis);
      this.frameAreaWithoutCorner = this.area
         .stream()
         .flatMap(
            blockPos -> Stream.of(
               blockPos.offset(directions[0].getUnitVec3i()),
               blockPos.offset(directions[1].getUnitVec3i()),
               blockPos.offset(directions[2].getUnitVec3i()),
               blockPos.offset(directions[3].getUnitVec3i())
            )
         )
         .filter(blockPos -> !this.area.contains(blockPos))
         .collect(Collectors.toSet());
      BlockPos[] cornerOffsets = new BlockPos[]{
         new BlockPos(directions[0].getUnitVec3i()).offset(directions[1].getUnitVec3i()),
         new BlockPos(directions[1].getUnitVec3i()).offset(directions[2].getUnitVec3i()),
         new BlockPos(directions[2].getUnitVec3i()).offset(directions[3].getUnitVec3i()),
         new BlockPos(directions[3].getUnitVec3i()).offset(directions[0].getUnitVec3i())
      };
      this.frameAreaWithCorner = this.area
         .stream()
         .flatMap(
            blockPos -> Stream.of(
               blockPos.offset(cornerOffsets[0]), blockPos.offset(cornerOffsets[1]), blockPos.offset(cornerOffsets[2]), blockPos.offset(cornerOffsets[3])
            )
         )
         .filter(blockPos -> !this.area.contains(blockPos))
         .collect(Collectors.toSet());
      this.frameAreaWithCorner.addAll(this.frameAreaWithoutCorner);
      this.firstFramePos = this.frameAreaWithoutCorner.iterator().next();
   }

   @Nullable
   public static BlockPortalShape findArea(BlockPos startingPos, Axis axis, Predicate<BlockPos> isAir, Predicate<BlockPos> isObsidian) {
      return findArea(startingPos, axis, isAir, isObsidian, defaultLengthLimit);
   }

   @Nullable
   public static BlockPortalShape findArea(BlockPos startingPos, Axis axis, Predicate<BlockPos> isAir, Predicate<BlockPos> isObsidian, int lengthLimit) {
      return !isAir.test(startingPos) ? null : findShapeWithoutRegardingStartingPos(startingPos, axis, isAir, isObsidian, lengthLimit);
   }

   @Nullable
   public static BlockPortalShape findShapeWithoutRegardingStartingPos(
      BlockPos startingPos, Axis axis, Predicate<BlockPos> isAir, Predicate<BlockPos> isObsidian
   ) {
      return findShapeWithoutRegardingStartingPos(startingPos, axis, isAir, isObsidian, defaultLengthLimit);
   }

   @Nullable
   private static BlockPortalShape findShapeWithoutRegardingStartingPos(
      BlockPos startingPos, Axis axis, Predicate<BlockPos> isAir, Predicate<BlockPos> isObsidian, int lengthLimit
   ) {
      startingPos = startingPos.immutable();
      Set<BlockPos> area = new HashSet<>();
      area.add(startingPos);
      Direction[] directions = Helper.getAnotherFourDirections(axis);
      boolean isNormalFrame = findAreaBreadthFirst(startingPos, isAir, isObsidian, directions, area, startingPos, lengthLimit);
      if (!isNormalFrame) {
         return null;
      }

      BlockPortalShape result = new BlockPortalShape(area, axis);
      BlockPos innerSize = result.innerAreaBox.getSize();
      return innerSize.getX() <= lengthLimit && innerSize.getY() <= lengthLimit && innerSize.getZ() <= lengthLimit ? result : null;
   }

   private static boolean findAreaBreadthFirst(
      BlockPos startingPos,
      Predicate<BlockPos> isAir,
      Predicate<BlockPos> isObsidian,
      Direction[] directions,
      Set<BlockPos> foundArea,
      BlockPos initialPos,
      int lengthLimit
   ) {
      ArrayDeque<BlockPos> newlyAdded = new ArrayDeque<>();
      newlyAdded.addLast(startingPos);

      while (!newlyAdded.isEmpty()) {
         if (foundArea.size() > lengthLimit * lengthLimit) {
            return false;
         }

         BlockPos last = newlyAdded.pollFirst();

         for (Direction direction : directions) {
            BlockPos curr = last.relative(direction).immutable();
            if (!foundArea.contains(curr)) {
               if (isAir.test(curr)) {
                  newlyAdded.addLast(curr);
                  foundArea.add(curr);
               } else if (!isObsidian.test(curr)) {
                  return false;
               }
            }
         }

         BlockPos delta = initialPos.subtract(startingPos);
         if (Math.abs(delta.getX()) > lengthLimit || Math.abs(delta.getY()) > lengthLimit || Math.abs(delta.getZ()) > lengthLimit) {
            return false;
         }
      }

      return true;
   }

   @Nullable
   public BlockPortalShape matchShape(Predicate<BlockPos> isAir, Predicate<BlockPos> isObsidian, BlockPos newAnchor, MutableBlockPos temp) {
      if (!isAir.test(newAnchor)) {
         return null;
      }

      boolean testFrame = this.testFrameWithoutCorner(isObsidian, newAnchor, temp);
      if (!testFrame) {
         return null;
      }

      boolean testAir = this.area
         .stream()
         .map(
            blockPos -> temp.set(
               blockPos.getX() - this.anchor.getX() + newAnchor.getX(),
               blockPos.getY() - this.anchor.getY() + newAnchor.getY(),
               blockPos.getZ() - this.anchor.getZ() + newAnchor.getZ()
            )
         )
         .allMatch(isAir);
      return !testAir ? null : this.getShapeWithMovedAnchor(newAnchor);
   }

   private boolean testFrameWithoutCorner(Predicate<BlockPos> isObsidian, BlockPos newAnchor, MutableBlockPos temp) {
      Function<BlockPos, MutableBlockPos> mapper = blockPos -> temp.set(
         blockPos.getX() - this.anchor.getX() + newAnchor.getX(),
         blockPos.getY() - this.anchor.getY() + newAnchor.getY(),
         blockPos.getZ() - this.anchor.getZ() + newAnchor.getZ()
      );
      return !isObsidian.test(mapper.apply(this.firstFramePos)) ? false : this.frameAreaWithoutCorner.stream().map(mapper).allMatch(isObsidian);
   }

   public BlockPortalShape getShapeWithMovedAnchor(BlockPos newAnchor) {
      BlockPos offset = newAnchor.subtract(this.anchor);
      return new BlockPortalShape(this.area.stream().map(blockPos -> blockPos.offset(offset)).collect(Collectors.toSet()), this.axis);
   }

   public boolean isFrameIntact(Predicate<BlockPos> isObsidian) {
      return this.frameAreaWithoutCorner.stream().allMatch(isObsidian);
   }

   public boolean isPortalIntact(Predicate<BlockPos> isPortalBlock, Predicate<BlockPos> isObsidian) {
      return this.isFrameIntact(isObsidian) && this.area.stream().allMatch(isPortalBlock);
   }

   public void initPortalPosAxisShape(Portal portal, AxisDirection axisDirection) {
      Vec3 center = this.innerAreaBox.getCenterVec();
      portal.setPos(center.x, center.y, center.z);
      this.initPortalAxisShape(portal, center, Direction.fromAxisAndDirection(this.axis, axisDirection));
   }

   public void initPortalAxisShape(Portal portal, Vec3 center, Direction facing) {
      Validate.isTrue(facing.getAxis() == this.axis);
      Tuple<Direction, Direction> perpendicularDirections = Helper.getPerpendicularDirections(facing);
      Direction wDirection = perpendicularDirections.getA();
      Direction hDirection = perpendicularDirections.getB();
      portal.setAxisW(Vec3.atLowerCornerOf(wDirection.getUnitVec3i()));
      portal.setAxisH(Vec3.atLowerCornerOf(hDirection.getUnitVec3i()));
      portal.setWidth(Helper.getCoordinate(this.innerAreaBox.getSize(), wDirection.getAxis()));
      portal.setHeight(Helper.getCoordinate(this.innerAreaBox.getSize(), hDirection.getAxis()));
      Vec3 offset = Vec3.atLowerCornerOf(Direction.get(AxisDirection.POSITIVE, this.axis).getUnitVec3i()).scale(0.5);
      if (this.isRectangle()) {
         portal.setPortalShapeToDefault();
      } else {
         Mesh2D mesh2D = new Mesh2D();
         double halfWidth = portal.getWidth() / 2.0;
         double halfHeight = portal.getHeight() / 2.0;
         Vec3 axisW = portal.getAxisW();
         Vec3 axisH = portal.getAxisH();
         this.area.forEach(part -> {
            Vec3 p1 = Vec3.atLowerCornerOf(part).add(offset);
            Vec3 p2 = Vec3.atLowerCornerOf(part).add(1.0, 1.0, 1.0).add(offset);
            double p1LocalX = p1.subtract(center).dot(axisW);
            double p1LocalY = p1.subtract(center).dot(axisH);
            double p2LocalX = p2.subtract(center).dot(axisW);
            double p2LocalY = p2.subtract(center).dot(axisH);
            mesh2D.addQuad(p1LocalX / halfWidth, p1LocalY / halfHeight, p2LocalX / halfWidth, p2LocalY / halfHeight);
         });
         mesh2D.simplify();
         portal.setPortalShape(new SpecialFlatPortalShape(mesh2D));
      }
   }

   public BlockPortalShape matchShapeWithMovedFirstFramePos(
      Predicate<BlockPos> isAir, Predicate<BlockPos> isObsidian, BlockPos newFirstObsidianPos, MutableBlockPos temp
   ) {
      boolean testFrame = this.frameAreaWithoutCorner
         .stream()
         .map(
            blockPos1 -> temp.set(
               blockPos1.getX() - this.firstFramePos.getX() + newFirstObsidianPos.getX(),
               blockPos1.getY() - this.firstFramePos.getY() + newFirstObsidianPos.getY(),
               blockPos1.getZ() - this.firstFramePos.getZ() + newFirstObsidianPos.getZ()
            )
         )
         .allMatch(isObsidian);
      if (!testFrame) {
         return null;
      }

      boolean testAir = this.area
         .stream()
         .map(
            blockPos -> temp.set(
               blockPos.getX() - this.firstFramePos.getX() + newFirstObsidianPos.getX(),
               blockPos.getY() - this.firstFramePos.getY() + newFirstObsidianPos.getY(),
               blockPos.getZ() - this.firstFramePos.getZ() + newFirstObsidianPos.getZ()
            )
         )
         .allMatch(isAir);
      if (!testAir) {
         return null;
      }

      BlockPos offset = newFirstObsidianPos.subtract(this.firstFramePos);
      return new BlockPortalShape(this.area.stream().map(blockPos -> blockPos.offset(offset)).collect(Collectors.toSet()), this.axis);
   }

   public static boolean isSquareShape(BlockPortalShape shape, int length) {
      BlockPos areaSize = shape.innerAreaBox.getSize();
      Tuple<Axis, Axis> xs = Helper.getAnotherTwoAxis(shape.axis);
      return Helper.getCoordinate(areaSize, xs.getA()) == length && Helper.getCoordinate(areaSize, xs.getB()) == length && shape.area.size() == length * length;
   }

   public static BlockPortalShape getSquareShapeTemplate(Axis axis, int length) {
      Tuple<Direction, Direction> perpendicularDirections = Helper.getPerpendicularDirections(Direction.fromAxisAndDirection(axis, AxisDirection.POSITIVE));
      Set<BlockPos> area = new HashSet<>();

      for (int i = 0; i < length; i++) {
         for (int j = 0; j < length; j++) {
            area.add(BlockPos.ZERO.relative(perpendicularDirections.getA(), i).relative(perpendicularDirections.getB(), j));
         }
      }

      return new BlockPortalShape(area, axis);
   }

   public BlockPortalShape getShapeWithMovedTotalAreaBox(IntBox newTotalAreaBox) {
      Validate.isTrue(this.totalAreaBox.getSize().equals(newTotalAreaBox.getSize()));
      return this.getShapeWithMovedAnchor(newTotalAreaBox.l.subtract(this.totalAreaBox.l).offset(this.anchor));
   }

   public int getShapeInnerLength() {
      BlockPos size = this.innerAreaBox.getSize();
      return Math.max(size.getX(), Math.max(size.getY(), size.getZ()));
   }

   public boolean isRectangle() {
      BlockPos size = this.innerAreaBox.getSize();
      return size.getX() * size.getY() * size.getZ() == this.area.size();
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (o != null && this.getClass() == o.getClass()) {
         BlockPortalShape that = (BlockPortalShape)o;
         return this.area.equals(that.area) && this.axis == that.axis;
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.area, this.axis);
   }
}
