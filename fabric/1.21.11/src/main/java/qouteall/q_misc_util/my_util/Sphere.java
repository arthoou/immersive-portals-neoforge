package qouteall.q_misc_util.my_util;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public record Sphere(Vec3 center, double radius) {
   @Nullable
   public Vec3 projectToSphere(Vec3 pos) {
      Vec3 delta = pos.subtract(this.center());
      return delta.lengthSqr() < 0.001 ? null : this.center().add(delta.normalize().scale(this.radius()));
   }

   @Nullable
   public Circle getIntersectionWithPlane(Plane plane) {
      double distance = plane.getDistanceTo(this.center());
      if (distance > this.radius()) {
         return null;
      }

      Vec3 circleCenter = plane.getProjection(this.center());
      double newRadius = Math.sqrt(this.radius() * this.radius() - distance * distance);
      return new Circle(plane, circleCenter, newRadius);
   }

   @Nullable
   public Vec3 rayTrace(Vec3 lineOrigin, Vec3 lineVec) {
      Vec3 delta = lineOrigin.subtract(this.center());
      double a = lineVec.lengthSqr();
      double b = delta.dot(lineVec) * 2.0;
      double c = delta.lengthSqr() - this.radius() * this.radius();
      double discriminant = b * b - 4.0 * a * c;
      if (discriminant < 0.0) {
         return null;
      } else {
         double sqrtDiscriminant = Math.sqrt(discriminant);
         double t1 = (-b + sqrtDiscriminant) / (2.0 * a);
         double t2 = (-b - sqrtDiscriminant) / (2.0 * a);
         if (t1 < 0.0 && t2 < 0.0) {
            return null;
         } else if (t1 < 0.0) {
            return lineOrigin.add(lineVec.scale(t2));
         } else {
            return t2 < 0.0 ? lineOrigin.add(lineVec.scale(t1)) : lineOrigin.add(lineVec.scale(Math.min(t1, t2)));
         }
      }
   }

   public static Sphere interpolate(Sphere a, Sphere b, double progress) {
      return new Sphere(a.center().lerp(b.center(), progress), Mth.lerp(progress, a.radius(), b.radius()));
   }
}
