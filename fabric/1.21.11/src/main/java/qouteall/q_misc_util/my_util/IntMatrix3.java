package qouteall.q_misc_util.my_util;

import com.mojang.math.OctahedralGroup;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import qouteall.q_misc_util.Helper;

public class IntMatrix3 {
   public final Vec3i x;
   public final Vec3i y;
   public final Vec3i z;

   public IntMatrix3(Vec3i x, Vec3i y, Vec3i z) {
      this.x = x;
      this.y = y;
      this.z = z;
   }

   public IntMatrix3(OctahedralGroup t) {
      Direction d1 = t.rotate(Direction.fromAxisAndDirection(Axis.X, AxisDirection.POSITIVE));
      Direction d2 = t.rotate(Direction.fromAxisAndDirection(Axis.Y, AxisDirection.POSITIVE));
      Direction d3 = t.rotate(Direction.fromAxisAndDirection(Axis.Z, AxisDirection.POSITIVE));
      this.x = d1.getUnitVec3i();
      this.y = d2.getUnitVec3i();
      this.z = d3.getUnitVec3i();
   }

   public BlockPos transform(Vec3i p) {
      return Helper.scale(this.x, p.getX()).offset(Helper.scale(this.y, p.getY())).offset(Helper.scale(this.z, p.getZ()));
   }

   public IntMatrix3 multiply(IntMatrix3 m) {
      return new IntMatrix3(m.transform(this.x), m.transform(this.y), m.transform(this.z));
   }

   public Direction transformDirection(Direction direction) {
      BlockPos vec = this.transform(direction.getUnitVec3i());
      return Helper.directionFromDelta(vec.getX(), vec.getY(), vec.getZ());
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (o != null && this.getClass() == o.getClass()) {
         IntMatrix3 that = (IntMatrix3)o;
         return this.x.equals(that.x) && this.y.equals(that.y) && this.z.equals(that.z);
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.x, this.y, this.z);
   }

   public static IntMatrix3 getIdentity() {
      return new IntMatrix3(new BlockPos(1, 0, 0), new BlockPos(0, 1, 0), new BlockPos(0, 0, 1));
   }

   public Matrix3f toMatrix() {
      Matrix3f matrix = new Matrix3f();
      matrix.set(0, 0, this.x.getX());
      matrix.set(0, 1, this.x.getY());
      matrix.set(0, 2, this.x.getZ());
      matrix.set(1, 0, this.y.getX());
      matrix.set(1, 1, this.y.getY());
      matrix.set(1, 2, this.y.getZ());
      matrix.set(2, 0, this.z.getX());
      matrix.set(2, 1, this.z.getY());
      matrix.set(2, 2, this.z.getZ());
      return matrix;
   }

   public DQuaternion toQuaternion() {
      return DQuaternion.matrixToQuaternion(Vec3.atLowerCornerOf(this.x), Vec3.atLowerCornerOf(this.y), Vec3.atLowerCornerOf(this.z));
   }
}
