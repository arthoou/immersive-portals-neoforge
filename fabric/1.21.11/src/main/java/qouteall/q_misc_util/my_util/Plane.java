package qouteall.q_misc_util.my_util;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record Plane(Vec3 pos, Vec3 normal) {
   public Plane(Vec3 pos, Vec3 normal) {
      this.pos = pos;
      this.normal = normal.normalize();
   }

   public double getDistanceTo(Vec3 point) {
      return this.normal.dot(point.subtract(this.pos));
   }

   public double getDistanceTo(double x, double y, double z) {
      return this.normal.x() * (x - this.pos.x()) + this.normal.y() * (y - this.pos.y()) + this.normal.z() * (z - this.pos.z());
   }

   @NotNull
   public Vec3 getProjection(Vec3 point) {
      return point.subtract(this.normal.scale(this.getDistanceTo(point)));
   }

   public Vec3 getReflection(Vec3 point) {
      return point.subtract(this.normal.scale(2.0 * this.getDistanceTo(point)));
   }

   public boolean isPointOnPositiveSide(Vec3 point) {
      return this.getDistanceTo(point) > 0.0;
   }

   public Plane move(double distance) {
      return new Plane(this.pos.add(this.normal.scale(distance)), this.normal);
   }

   public Plane getOpposite() {
      return new Plane(this.pos, this.normal.scale(-1.0));
   }

   @Nullable
   public Vec3 rayTrace(Vec3 origin, Vec3 vec) {
      double t = this.rayTraceGetT(origin, vec);
      if (Double.isNaN(t)) {
         return null;
      } else {
         return t < 0.0 ? null : origin.add(vec.scale(t));
      }
   }

   public double rayTraceGetT(Vec3 lineOrigin, Vec3 lineVec) {
      double lineVecProjectToNormal = this.normal.dot(lineVec);
      return Math.abs(lineVecProjectToNormal) < 1.0E-5 ? Double.NaN : -this.getDistanceTo(lineOrigin) / lineVecProjectToNormal;
   }

   public double rayTraceGetT(double lineOriginX, double lineOriginY, double lineOriginZ, double lineVecX, double lineVecY, double lineVecZ) {
      double lineVecProjectToNormal = this.normal.x() * lineVecX + this.normal.y() * lineVecY + this.normal.z() * lineVecZ;
      return Math.abs(lineVecProjectToNormal) < 1.0E-5 ? Double.NaN : -this.getDistanceTo(lineOriginX, lineOriginY, lineOriginZ) / lineVecProjectToNormal;
   }

   @Nullable
   public Vec3 intersectionWithLineSegment(Vec3 lineP1, Vec3 lineP2) {
      Vec3 lineVec = lineP2.subtract(lineP1);
      double t = this.rayTraceGetT(lineP1, lineVec);
      if (Double.isNaN(t)) {
         return null;
      } else {
         return !(t < 0.0) && !(t > 1.0) ? lineP1.add(lineVec.scale(t)) : null;
      }
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (o != null && this.getClass() == o.getClass()) {
         Plane plane = (Plane)o;
         return this.pos.equals(plane.pos) && this.normal.equals(plane.normal);
      } else {
         return false;
      }
   }

   @Override
   public String toString() {
      return "Plane{pos=" + this.pos + ", normal=" + this.normal + "}";
   }

   public static Plane interpolate(Plane a, Plane b, double progress) {
      Vec3 pos = a.pos.lerp(b.pos, progress);
      Vec3 normal = a.normal.lerp(b.normal, progress).normalize();
      return new Plane(pos, normal);
   }

   public Plane getParallelPlane(Vec3 pos) {
      return new Plane(pos, this.normal);
   }

   public double getEquationX() {
      return this.normal.x();
   }

   public double getEquationY() {
      return this.normal.y();
   }

   public double getEquationZ() {
      return this.normal.z();
   }

   public double getEquationW() {
      return -this.normal.dot(this.pos);
   }
}
