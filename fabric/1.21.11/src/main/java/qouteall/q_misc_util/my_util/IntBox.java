package qouteall.q_misc_util.my_util;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import qouteall.q_misc_util.Helper;

public class IntBox {
   public final BlockPos l;
   public final BlockPos h;

   public IntBox(BlockPos l, BlockPos h) {
      this.l = Helper.min(l, h);
      this.h = Helper.max(l, h);
   }

   public static IntBox fromBasePointAndSize(BlockPos basePoint, BlockPos areaSize) {
      return new IntBox(basePoint, basePoint.offset(areaSize).offset(-1, -1, -1));
   }

   public static IntBox fromPosAndOffset(BlockPos pos, BlockPos offset) {
      return new IntBox(pos, pos.offset(offset));
   }

   public IntBox expandOrShrink(Vec3i offset) {
      return new IntBox(this.l.subtract(offset), this.h.offset(offset));
   }

   public IntBox getExpanded(Axis axis, int n) {
      return this.expandOrShrink(Helper.scale(Direction.get(AxisDirection.POSITIVE, axis).getUnitVec3i(), n));
   }

   public IntBox getExpanded(Direction direction, int n) {
      return direction.getAxisDirection() == AxisDirection.POSITIVE
         ? new IntBox(this.l, this.h.offset(Helper.scale(direction.getUnitVec3i(), n)))
         : new IntBox(this.l.offset(Helper.scale(direction.getUnitVec3i(), n)), this.h);
   }

   public Stream<BlockPos> stream() {
      return IntStream.range(this.l.getX(), this.h.getX() + 1)
         .boxed()
         .flatMap(
            x -> IntStream.range(this.l.getY(), this.h.getY() + 1)
               .boxed()
               .flatMap(y -> IntStream.range(this.l.getZ(), this.h.getZ() + 1).boxed().map(z -> new BlockPos(x, y, z)))
         );
   }

   public Stream<BlockPos> fastStream() {
      return BlockPos.betweenClosedStream(this.l, this.h);
   }

   public BlockPos getSize() {
      return this.h.offset(1, 1, 1).subtract(this.l);
   }

   public BlockPos getDiagonalOffset() {
      return this.h.subtract(this.l);
   }

   public IntBox getSurfaceLayer(Axis axis, AxisDirection axisDirection) {
      if (axisDirection == AxisDirection.NEGATIVE) {
         IntBox result = new IntBox(
            this.l,
            new BlockPos((axis == Axis.X ? this.l : this.h).getX(), (axis == Axis.Y ? this.l : this.h).getY(), (axis == Axis.Z ? this.l : this.h).getZ())
         );
         return result;
      } else {
         return new IntBox(
            new BlockPos((axis == Axis.X ? this.h : this.l).getX(), (axis == Axis.Y ? this.h : this.l).getY(), (axis == Axis.Z ? this.h : this.l).getZ()),
            this.h
         );
      }
   }

   public IntBox getSurfaceLayer(Direction facing) {
      return this.getSurfaceLayer(facing.getAxis(), facing.getAxisDirection());
   }

   @Nullable
   public static IntBox getIntersect(IntBox a, IntBox b) {
      BlockPos l = Helper.max(a.l, b.l);
      BlockPos h = Helper.min(a.h, b.h);
      if (l.getX() > h.getX()) {
         return null;
      } else if (l.getY() > h.getY()) {
         return null;
      } else {
         return l.getZ() > h.getZ() ? null : new IntBox(l, h);
      }
   }

   public IntBox map(Function<BlockPos, BlockPos> func1, Function<BlockPos, BlockPos> func2) {
      return new IntBox(func1.apply(this.l), func2.apply(this.h));
   }

   public BlockPos getCenter() {
      return Helper.divide(this.l.offset(this.h), 2);
   }

   public Vec3 getCenterVec() {
      return new Vec3((this.l.getX() + this.h.getX() + 1) / 2.0, (this.l.getY() + this.h.getY() + 1) / 2.0, (this.l.getZ() + this.h.getZ() + 1) / 2.0);
   }

   public IntBox getAdjusted(int dxa, int dya, int dza, int dxb, int dyb, int dzb) {
      return new IntBox(this.l.offset(dxa, dya, dza), this.h.offset(dxb, dyb, dzb));
   }

   public Stream<BlockPos> forSixSurfaces(Function<Stream<IntBox>, Stream<IntBox>> mapper) {
      IntBox[] array = new IntBox[]{
         this.getSurfaceLayer(Direction.DOWN),
         this.getSurfaceLayer(Direction.NORTH).getAdjusted(0, 1, 0, 0, 0, 0),
         this.getSurfaceLayer(Direction.SOUTH).getAdjusted(0, 1, 0, 0, 0, 0),
         this.getSurfaceLayer(Direction.WEST).getAdjusted(0, 1, 1, 0, 0, -1),
         this.getSurfaceLayer(Direction.EAST).getAdjusted(0, 1, 1, 0, 0, -1),
         this.getSurfaceLayer(Direction.UP).getAdjusted(1, 0, 1, -1, 0, -1)
      };
      return mapper.apply(Arrays.stream(array).filter(intBox -> true)).flatMap(IntBox::stream);
   }

   public IntBox getMoved(Vec3i offset) {
      return new IntBox(this.l.offset(offset), this.h.offset(offset));
   }

   public static IntBox getContainingBox(IntBox box1, IntBox box2) {
      return new IntBox(Helper.min(box1.l, box2.l), Helper.max(box1.h, box2.h));
   }

   public IntBox getSubBoxInCenter(BlockPos subBoxSize) {
      BlockPos thisSize = this.getSize();
      Validate.isTrue(thisSize.getX() >= subBoxSize.getX());
      Validate.isTrue(thisSize.getY() >= subBoxSize.getY());
      Validate.isTrue(thisSize.getZ() >= subBoxSize.getZ());
      return fromBasePointAndSize(Helper.divide(thisSize.subtract(subBoxSize), 2).offset(this.l), subBoxSize);
   }

   public BlockPos[] getEightVertices() {
      return new BlockPos[]{
         new BlockPos(this.l.getX(), this.l.getY(), this.l.getZ()),
         new BlockPos(this.l.getX(), this.l.getY(), this.h.getZ()),
         new BlockPos(this.l.getX(), this.h.getY(), this.l.getZ()),
         new BlockPos(this.l.getX(), this.h.getY(), this.h.getZ()),
         new BlockPos(this.h.getX(), this.l.getY(), this.l.getZ()),
         new BlockPos(this.h.getX(), this.l.getY(), this.h.getZ()),
         new BlockPos(this.h.getX(), this.h.getY(), this.l.getZ()),
         new BlockPos(this.h.getX(), this.h.getY(), this.h.getZ())
      };
   }

   public AABB toRealNumberBox() {
      return new AABB(this.l.getX(), this.l.getY(), this.l.getZ(), this.h.getX() + 1, this.h.getY() + 1, this.h.getZ() + 1);
   }

   public static IntBox fromRealNumberBox(AABB aabb) {
      int minX = (int)Math.floor(aabb.minX + 1.0E-5);
      int minY = (int)Math.floor(aabb.minY + 1.0E-5);
      int minZ = (int)Math.floor(aabb.minZ + 1.0E-5);
      int maxX = (int)Math.ceil(aabb.maxX - 1.0E-5);
      int maxY = (int)Math.ceil(aabb.maxY - 1.0E-5);
      int maxZ = (int)Math.ceil(aabb.maxZ - 1.0E-5);
      return new IntBox(new BlockPos(minX, minY, minZ), new BlockPos(maxX - 1, maxY - 1, maxZ - 1));
   }

   public IntBox getExpanded(BlockPos newPoint) {
      return new IntBox(Helper.min(this.l, newPoint), Helper.max(this.h, newPoint));
   }

   public boolean contains(BlockPos pos) {
      return pos.getX() >= this.l.getX()
         && pos.getX() <= this.h.getX()
         && pos.getY() >= this.l.getY()
         && pos.getY() <= this.h.getY()
         && pos.getZ() >= this.l.getZ()
         && pos.getZ() <= this.h.getZ();
   }

   public boolean contains(IntBox box) {
      return this.contains(box.l) && this.contains(box.h);
   }

   public BlockPos selectCoordinateFromBox(boolean high) {
      return high ? this.h : this.l;
   }

   public IntBox[] get12Edges() {
      return new IntBox[]{
         new IntBox(this.selectCoordinateFromBox(false, false, false), this.selectCoordinateFromBox(false, false, true)),
         new IntBox(this.selectCoordinateFromBox(false, true, false), this.selectCoordinateFromBox(false, true, true)),
         new IntBox(this.selectCoordinateFromBox(true, false, false), this.selectCoordinateFromBox(true, false, true)),
         new IntBox(this.selectCoordinateFromBox(true, true, false), this.selectCoordinateFromBox(true, true, true)),
         new IntBox(this.selectCoordinateFromBox(false, false, false), this.selectCoordinateFromBox(false, true, false)),
         new IntBox(this.selectCoordinateFromBox(false, false, true), this.selectCoordinateFromBox(false, true, true)),
         new IntBox(this.selectCoordinateFromBox(true, false, false), this.selectCoordinateFromBox(true, true, false)),
         new IntBox(this.selectCoordinateFromBox(true, false, true), this.selectCoordinateFromBox(true, true, true)),
         new IntBox(this.selectCoordinateFromBox(false, false, false), this.selectCoordinateFromBox(true, false, false)),
         new IntBox(this.selectCoordinateFromBox(false, false, true), this.selectCoordinateFromBox(true, false, true)),
         new IntBox(this.selectCoordinateFromBox(false, true, false), this.selectCoordinateFromBox(true, true, false)),
         new IntBox(this.selectCoordinateFromBox(false, true, true), this.selectCoordinateFromBox(true, true, true))
      };
   }

   public BlockPos selectCoordinateFromBox(boolean xUp, boolean yUp, boolean zUp) {
      return new BlockPos(this.selectCoordinateFromBox(xUp).getX(), this.selectCoordinateFromBox(yUp).getY(), this.selectCoordinateFromBox(zUp).getZ());
   }

   public static IntBox getBoxByPosAndSignedSize(BlockPos basePos, BlockPos signedSize) {
      return new IntBox(
         basePos,
         new BlockPos(
            getEndCoordWithSignedSize(basePos.getX(), signedSize.getX()),
            getEndCoordWithSignedSize(basePos.getY(), signedSize.getY()),
            getEndCoordWithSignedSize(basePos.getZ(), signedSize.getZ())
         )
      );
   }

   private static int getEndCoordWithSignedSize(int base, int signedSize) {
      if (signedSize > 0) {
         return base + signedSize - 1;
      } else if (signedSize < 0) {
         return base + signedSize + 1;
      } else {
         throw new IllegalArgumentException("Signed size cannot be zero");
      }
   }

   public boolean isOnSurface(BlockPos pos) {
      boolean xOnEnd = pos.getX() == this.l.getX() || pos.getX() == this.h.getX();
      boolean yOnEnd = pos.getY() == this.l.getY() || pos.getY() == this.h.getY();
      boolean zOnEnd = pos.getZ() == this.l.getZ() || pos.getZ() == this.h.getZ();
      return xOnEnd || yOnEnd || zOnEnd;
   }

   public boolean isOnEdge(BlockPos pos) {
      boolean xOnEnd = pos.getX() == this.l.getX() || pos.getX() == this.h.getX();
      boolean yOnEnd = pos.getY() == this.l.getY() || pos.getY() == this.h.getY();
      boolean zOnEnd = pos.getZ() == this.l.getZ() || pos.getZ() == this.h.getZ();
      return xOnEnd && yOnEnd || yOnEnd && zOnEnd || zOnEnd && xOnEnd;
   }

   public boolean isOnVertex(BlockPos pos) {
      boolean xOnEnd = pos.getX() == this.l.getX() || pos.getX() == this.h.getX();
      boolean yOnEnd = pos.getY() == this.l.getY() || pos.getY() == this.h.getY();
      boolean zOnEnd = pos.getZ() == this.l.getZ() || pos.getZ() == this.h.getZ();
      return xOnEnd && yOnEnd && zOnEnd;
   }

   public IntBox confineInnerBox(IntBox innerBox) {
      int offsetX = getOffsetForConfiningIntegerRange(this.l.getX(), this.h.getX(), innerBox.l.getX(), innerBox.h.getX());
      int offsetY = getOffsetForConfiningIntegerRange(this.l.getY(), this.h.getY(), innerBox.l.getY(), innerBox.h.getY());
      int offsetZ = getOffsetForConfiningIntegerRange(this.l.getZ(), this.h.getZ(), innerBox.l.getZ(), innerBox.h.getZ());
      return innerBox.getMoved(new BlockPos(offsetX, offsetY, offsetZ));
   }

   public static int getOffsetForConfiningIntegerRange(int outerRangeStart, int outerRangeEnd, int innerRangeStart, int innerRangeEnd) {
      int outerRangeSize = outerRangeEnd - outerRangeStart + 1;
      int innerRangeSize = innerRangeEnd - innerRangeStart + 1;
      Validate.isTrue(
         outerRangeSize >= innerRangeSize, "cannot confine range (%d %d) to (%d %d)", innerRangeStart, innerRangeEnd, outerRangeStart, outerRangeEnd
      );
      if (innerRangeStart >= outerRangeStart && innerRangeEnd <= outerRangeEnd) {
         return 0;
      } else {
         return innerRangeStart < outerRangeStart ? outerRangeStart - innerRangeStart : outerRangeEnd - innerRangeEnd;
      }
   }

   public BlockPos getVertex(boolean xPosi, boolean yPosi, boolean zPosi) {
      return new BlockPos(xPosi ? this.h.getX() : this.l.getX(), yPosi ? this.h.getY() : this.l.getY(), zPosi ? this.h.getZ() : this.l.getZ());
   }

   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putInt("lX", this.l.getX());
      tag.putInt("lY", this.l.getY());
      tag.putInt("lZ", this.l.getZ());
      tag.putInt("hX", this.h.getX());
      tag.putInt("hY", this.h.getY());
      tag.putInt("hZ", this.h.getZ());
      return tag;
   }

   public static IntBox fromTag(CompoundTag tag) {
      return new IntBox(
         new BlockPos(tag.getIntOr("lX", 0), tag.getIntOr("lY", 0), tag.getIntOr("lZ", 0)),
         new BlockPos(tag.getIntOr("hX", 0), tag.getIntOr("hY", 0), tag.getIntOr("hZ", 0))
      );
   }

   @Override
   public String toString() {
      return String.format("(%d %d %d)-(%d %d %d)", this.l.getX(), this.l.getY(), this.l.getZ(), this.h.getX(), this.h.getY(), this.h.getZ());
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (o != null && this.getClass() == o.getClass()) {
         IntBox intBox = (IntBox)o;
         return this.l.equals(intBox.l) && this.h.equals(intBox.h);
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.l, this.h);
   }
}
