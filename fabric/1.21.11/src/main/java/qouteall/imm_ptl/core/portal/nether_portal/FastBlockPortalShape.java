package qouteall.imm_ptl.core.portal.nether_portal;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.ChunkPos;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.q_misc_util.my_util.IntBox;
import qouteall.q_misc_util.my_util.TriIntPredicate;

public record FastBlockPortalShape(
   int basePosX,
   int basePosY,
   int basePosZ,
   Axis axis,
   int[] localAreaBlockCoords,
   int[] localFrameWithoutCornerBlockCoords,
   int[] localFrameCornerBlockCoords,
   IntBox innerAreaBox,
   IntBox totalAreaBox
) {
   private static final Logger LOGGER = LogUtils.getLogger();

   public static int toLocalA(Axis axis, int x, int y, int z) {
      return switch (axis) {
         case X -> y;
         case Y -> x;
         case Z -> x;
      };
   }

   public static int toLocalB(Axis axis, int x, int y, int z) {
      return switch (axis) {
         case X -> z;
         case Y -> z;
         case Z -> y;
      };
   }

   public static int toWorldX(Axis axis, int coordOnAxis, int a, int b) {
      return switch (axis) {
         case X -> coordOnAxis;
         case Y -> a;
         case Z -> a;
      };
   }

   public static int toWorldY(Axis axis, int coordOnAxis, int a, int b) {
      return switch (axis) {
         case X -> a;
         case Y -> coordOnAxis;
         case Z -> b;
      };
   }

   public static int toWorldZ(Axis axis, int coordOnAxis, int a, int b) {
      return switch (axis) {
         case X -> b;
         case Y -> b;
         case Z -> coordOnAxis;
      };
   }

   public static BlockPos toWorldBlockPos(Axis axis, int coordOnAxis, int a, int b) {
      return new BlockPos(toWorldX(axis, coordOnAxis, a, b), toWorldY(axis, coordOnAxis, a, b), toWorldZ(axis, coordOnAxis, a, b));
   }

   public static long assemble(int a, int b) {
      return ChunkPos.asLong(a, b);
   }

   public static int getAFromAssemble(long l) {
      return ChunkPos.getX(l);
   }

   public static int getBFromAssemble(long l) {
      return ChunkPos.getZ(l);
   }

   public static FastBlockPortalShape create(Axis axis, int[] areaBlockCoords, int coordOnAxis) {
      Validate.isTrue(areaBlockCoords.length % 2 == 0);
      Validate.isTrue(areaBlockCoords.length != 0);
      int areaBlockNum = areaBlockCoords.length / 2;
      LongOpenHashSet area = new LongOpenHashSet();

      for (int i = 0; i < areaBlockNum; i++) {
         int a = areaBlockCoords[i * 2];
         int b = areaBlockCoords[i * 2 + 1];
         area.add(assemble(a, b));
      }

      LongOpenHashSet frame = new LongOpenHashSet();
      LongOpenHashSet frameCorner = new LongOpenHashSet();

      for (int i = 0; i < areaBlockNum; i++) {
         int a = areaBlockCoords[i * 2];
         int b = areaBlockCoords[i * 2 + 1];
         if (!area.contains(assemble(a + 1, b))) {
            frame.add(assemble(a + 1, b));
         }

         if (!area.contains(assemble(a - 1, b))) {
            frame.add(assemble(a - 1, b));
         }

         if (!area.contains(assemble(a, b + 1))) {
            frame.add(assemble(a, b + 1));
         }

         if (!area.contains(assemble(a, b - 1))) {
            frame.add(assemble(a, b - 1));
         }
      }

      for (int i = 0; i < areaBlockNum; i++) {
         int a = areaBlockCoords[i * 2];
         int b = areaBlockCoords[i * 2 + 1];
         if (!area.contains(assemble(a + 1, b + 1)) && !frame.contains(assemble(a + 1, b + 1))) {
            frameCorner.add(assemble(a + 1, b + 1));
         }

         if (!area.contains(assemble(a + 1, b - 1)) && !frame.contains(assemble(a + 1, b - 1))) {
            frameCorner.add(assemble(a + 1, b - 1));
         }

         if (!area.contains(assemble(a - 1, b + 1)) && !frame.contains(assemble(a - 1, b + 1))) {
            frameCorner.add(assemble(a - 1, b + 1));
         }

         if (!area.contains(assemble(a - 1, b - 1)) && !frame.contains(assemble(a - 1, b - 1))) {
            frameCorner.add(assemble(a - 1, b - 1));
         }
      }

      int minA = areaBlockCoords[0];
      int maxA = areaBlockCoords[0];
      int minB = areaBlockCoords[1];
      int maxB = areaBlockCoords[1];

      for (int i = 1; i < areaBlockNum; i++) {
         int a = areaBlockCoords[i * 2];
         int b = areaBlockCoords[i * 2 + 1];
         if (a < minA) {
            minA = a;
         }

         if (a > maxA) {
            maxA = a;
         }

         if (b < minB) {
            minB = b;
         }

         if (b > maxB) {
            maxB = b;
         }
      }

      IntBox innerAreaBox = new IntBox(toWorldBlockPos(axis, coordOnAxis, minA, minB), toWorldBlockPos(axis, coordOnAxis, maxA, maxB));
      IntBox totalAreaBox = new IntBox(toWorldBlockPos(axis, coordOnAxis, minA - 1, minB - 1), toWorldBlockPos(axis, coordOnAxis, maxA + 1, maxB + 1));
      long firstFramePosAssemble = frame.iterator().nextLong();
      int baseA = getAFromAssemble(firstFramePosAssemble);
      int baseB = getBFromAssemble(firstFramePosAssemble);
      BlockPos firstFramePos = toWorldBlockPos(axis, coordOnAxis, baseA, baseB);
      int[] localAreaBlockCoords = new int[areaBlockNum * 2];

      for (int i = 0; i < areaBlockNum; i++) {
         int a = areaBlockCoords[i * 2];
         int b = areaBlockCoords[i * 2 + 1];
         localAreaBlockCoords[i * 2] = a - baseA;
         localAreaBlockCoords[i * 2 + 1] = b - baseB;
      }

      int[] localFrameWithoutCornerBlockCoords = new int[frame.size() * 2];
      LongIterator framePosIter = frame.longIterator();

      for (int i = 0; i < frame.size(); i++) {
         long l = framePosIter.nextLong();
         int a = getAFromAssemble(l);
         int b = getBFromAssemble(l);
         localFrameWithoutCornerBlockCoords[i * 2] = a - baseA;
         localFrameWithoutCornerBlockCoords[i * 2 + 1] = b - baseB;
      }

      int[] localFrameCornerBlockCoords = new int[frameCorner.size() * 2];
      LongIterator frameCornerPosIter = frameCorner.longIterator();

      for (int i = 0; i < frameCorner.size(); i++) {
         long l = frameCornerPosIter.nextLong();
         int a = getAFromAssemble(l);
         int b = getBFromAssemble(l);
         localFrameCornerBlockCoords[i * 2] = a - baseA;
         localFrameCornerBlockCoords[i * 2 + 1] = b - baseB;
      }

      return new FastBlockPortalShape(
         firstFramePos.getX(),
         firstFramePos.getY(),
         firstFramePos.getZ(),
         axis,
         localAreaBlockCoords,
         localFrameWithoutCornerBlockCoords,
         localFrameCornerBlockCoords,
         innerAreaBox,
         totalAreaBox
      );
   }

   @Nullable
   public static FastBlockPortalShape fromTag(CompoundTag tag) {
      int axisInt = tag.getIntOr("axis", 0);
      if (axisInt >= 0 && axisInt <= 2) {
         Axis axis = Axis.values()[axisInt];
         ListTag positions = tag.getListOrEmpty("poses");
         int numNum = positions.size();
         if (numNum != 0 && numNum % 3 == 0) {
            int positionNum = numNum / 3;
            int firstPointX = positions.getIntOr(0, 0);
            int firstPointY = positions.getIntOr(1, 0);
            int firstPointZ = positions.getIntOr(2, 0);
            IntArrayList areaBlockCoords = new IntArrayList();
            if (axis != Axis.X) {
               areaBlockCoords.add(firstPointX);
            }

            if (axis != Axis.Y) {
               areaBlockCoords.add(firstPointY);
            }

            if (axis != Axis.Z) {
               areaBlockCoords.add(firstPointZ);
            }

            int coordOnAxis = axis.choose(firstPointX, firstPointY, firstPointZ);

            for (int i = 1; i < positionNum; i++) {
               int x = positions.getIntOr(i * 3, 0);
               int y = positions.getIntOr(i * 3 + 1, 0);
               int z = positions.getIntOr(i * 3 + 2, 0);
               if (axis.choose(x, y, z) != coordOnAxis) {
                  LOGGER.error("invalid block position in {} at {}", tag, i);
                  return null;
               }

               if (axis != Axis.X) {
                  areaBlockCoords.add(x);
               }

               if (axis != Axis.Y) {
                  areaBlockCoords.add(y);
               }

               if (axis != Axis.Z) {
                  areaBlockCoords.add(z);
               }
            }

            return create(axis, areaBlockCoords.toIntArray(), coordOnAxis);
         } else {
            LOGGER.error("invalid poses tag {}", tag);
            return null;
         }
      } else {
         LOGGER.error("invalid axis {}", tag);
         return null;
      }
   }

   public CompoundTag toTag() {
      int coordOnAxis = this.axis.choose(this.basePosX, this.basePosY, this.basePosZ);
      CompoundTag tag = new CompoundTag();
      tag.putInt("axis", this.axis.ordinal());
      ListTag positions = new ListTag();

      for (int i = 0; i < this.localAreaBlockCoords.length / 2; i++) {
         int a = this.localAreaBlockCoords[i * 2];
         int b = this.localAreaBlockCoords[i * 2 + 1];
         positions.add(IntTag.valueOf(toWorldX(this.axis, 0, a, b) + this.basePosX));
         positions.add(IntTag.valueOf(toWorldY(this.axis, 0, a, b) + this.basePosY));
         positions.add(IntTag.valueOf(toWorldZ(this.axis, 0, a, b) + this.basePosZ));
      }

      tag.put("poses", positions);
      return tag;
   }

   public static FastBlockPortalShape fromBlockPortalShape(BlockPortalShape blockPortalShape) {
      return fromTag(blockPortalShape.toTag());
   }

   public BlockPortalShape toBlockPortalShape() {
      return BlockPortalShape.fromTag(this.toTag());
   }

   public boolean matchShape(int newBaseX, int newBaseY, int newBaseZ, TriIntPredicate framePredicate, TriIntPredicate areaPredicate) {
      for (int frameI = 0; frameI < this.localFrameWithoutCornerBlockCoords.length / 2; frameI++) {
         int a = this.localFrameWithoutCornerBlockCoords[frameI * 2];
         int b = this.localFrameWithoutCornerBlockCoords[frameI * 2 + 1];
         int x = toWorldX(this.axis, 0, a, b) + newBaseX;
         int y = toWorldY(this.axis, 0, a, b) + newBaseY;
         int z = toWorldZ(this.axis, 0, a, b) + newBaseZ;
         if (!framePredicate.test(x, y, z)) {
            return false;
         }
      }

      for (int areaI = 0; areaI < this.localAreaBlockCoords.length / 2; areaI++) {
         int a = this.localAreaBlockCoords[areaI * 2];
         int b = this.localAreaBlockCoords[areaI * 2 + 1];
         int x = toWorldX(this.axis, 0, a, b) + newBaseX;
         int y = toWorldY(this.axis, 0, a, b) + newBaseY;
         int z = toWorldZ(this.axis, 0, a, b) + newBaseZ;
         if (!areaPredicate.test(x, y, z)) {
            return false;
         }
      }

      return true;
   }

   public FastBlockPortalShape withNewBase(int newBaseX, int newBaseY, int newBaseZ) {
      return new FastBlockPortalShape(
         newBaseX,
         newBaseY,
         newBaseZ,
         this.axis,
         this.localAreaBlockCoords,
         this.localFrameWithoutCornerBlockCoords,
         this.localFrameCornerBlockCoords,
         this.innerAreaBox,
         this.totalAreaBox
      );
   }
}
