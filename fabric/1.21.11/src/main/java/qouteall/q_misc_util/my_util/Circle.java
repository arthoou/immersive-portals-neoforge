package qouteall.q_misc_util.my_util;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public record Circle(Plane plane, Vec3 circleCenter, double radius) {
   @Nullable
   public Vec3 projectToCircle(Vec3 pos) {
      Vec3 projection = this.plane.getProjection(pos);
      Vec3 offset = projection.subtract(this.circleCenter);
      return offset.lengthSqr() < 0.001 ? null : offset.normalize().scale(this.radius).add(this.circleCenter);
   }
}
