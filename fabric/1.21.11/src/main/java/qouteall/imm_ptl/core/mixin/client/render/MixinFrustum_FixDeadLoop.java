package qouteall.imm_ptl.core.mixin.client.render;

import net.minecraft.client.renderer.culling.Frustum;
import org.joml.FrustumIntersection;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;
import qouteall.imm_ptl.core.render.TransformationManager;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.LimitedLogger;

@Mixin(Frustum.class)
public abstract class MixinFrustum_FixDeadLoop {
   @Shadow
   private double camX;
   @Shadow
   private double camY;
   @Shadow
   private double camZ;
   @Shadow
   private Vector4f viewVector;
   @Shadow
   @Final
   private FrustumIntersection intersection;
   private static LimitedLogger limitedLogger = new LimitedLogger(10);

   @Overwrite
   @IPVanillaCopy
   public Frustum offsetToFullyIncludeCameraCube(int gridSize) {
      if (TransformationManager.isIsometricView) {
         return (Frustum)(Object)this;
      }

      double minX = Math.floor(this.camX / gridSize) * gridSize;
      double minY = Math.floor(this.camY / gridSize) * gridSize;
      double minZ = Math.floor(this.camZ / gridSize) * gridSize;
      double maxX = Math.ceil(this.camX / gridSize) * gridSize;
      double maxY = Math.ceil(this.camY / gridSize) * gridSize;
      double maxZ = Math.ceil(this.camZ / gridSize) * gridSize;
      int countLimit = 10;

      while (
         this.intersection
               .intersectAab(
                  (float)(minX - this.camX),
                  (float)(minY - this.camY),
                  (float)(minZ - this.camZ),
                  (float)(maxX - this.camX),
                  (float)(maxY - this.camY),
                  (float)(maxZ - this.camZ)
               )
            != -2
      ) {
         this.camX = this.camX - this.viewVector.x() * 4.0F;
         this.camY = this.camY - this.viewVector.y() * 4.0F;
         this.camZ = this.camZ - this.viewVector.z() * 4.0F;
         if (--countLimit <= 0) {
            limitedLogger.invoke(() -> {
               Helper.err("the projection matrix and the frustum are abnormal");
               new Throwable().printStackTrace();
            });
            break;
         }
      }

      return (Frustum)(Object)this;
   }
}
