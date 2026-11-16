package qouteall.imm_ptl.core.mixin.client.render.optimization;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface;
import qouteall.imm_ptl.core.ducks.IEFrustum;
import qouteall.imm_ptl.core.render.FrustumCuller;

@Mixin(Frustum.class)
public class MixinFrustum implements IEFrustum {
   @Shadow
   private double camX;
   @Shadow
   private double camY;
   @Shadow
   private double camZ;
   @Shadow
   private Vector4f viewVector;
   private double portal_camX;
   private double portal_camY;
   private double portal_camZ;
   private FrustumCuller portal_frustumCuller;

   @Inject(method = "<init>(Lnet/minecraft/client/renderer/culling/Frustum;)V", at = @At("RETURN"))
   private void onFrustumCopy(Frustum other, CallbackInfo ci) {
      if (other instanceof IEFrustum) {
         MixinFrustum otherFrustum = (MixinFrustum)(Object)other;
         this.portal_camX = otherFrustum.portal_camX;
         this.portal_camY = otherFrustum.portal_camY;
         this.portal_camZ = otherFrustum.portal_camZ;
         this.portal_frustumCuller = otherFrustum.portal_frustumCuller;
      }
   }

   @Inject(method = "Lnet/minecraft/client/renderer/culling/Frustum;prepare(DDD)V", at = @At("TAIL"))
   private void onSetOrigin(double double_1, double double_2, double double_3, CallbackInfo ci) {
      if (!IrisInterface.invoker.isRenderingShadowMap()) {
         if (this.portal_frustumCuller == null) {
            this.portal_frustumCuller = new FrustumCuller();
         }

         this.portal_frustumCuller.update(this.camX, this.camY, this.camZ);
         this.portal_camX = this.camX;
         this.portal_camY = this.camY;
         this.portal_camZ = this.camZ;
      }
   }

   @Inject(method = "cubeInFrustum(DDDDDD)I", at = @At("HEAD"), cancellable = true)
   private void onCubeInFrustum(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, CallbackInfoReturnable<Integer> cir) {
      if (this.ip_canDetermineInvisibleWithCamCoord(
         (float)(minX - this.portal_camX),
         (float)(minY - this.portal_camY),
         (float)(minZ - this.portal_camZ),
         (float)(maxX - this.portal_camX),
         (float)(maxY - this.portal_camY),
         (float)(maxZ - this.portal_camZ)
      )) {
         cir.setReturnValue(-3);
      }
   }

   @Inject(method = "calculateFrustum", at = @At("RETURN"))
   private void onCalculateFrustumReturn(Matrix4f matrix4f, Matrix4f matrix4f2, CallbackInfo ci) {
      this.viewVector.normalize();
   }

   @Override
   public boolean ip_canDetermineInvisibleWithCamCoord(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
      return this.portal_frustumCuller.canDetermineInvisibleWithCameraCoord(minX, minY, minZ, maxX, maxY, maxZ);
   }

   @Override
   public Vec3 ip_getViewVec3() {
      return new Vec3(this.viewVector.x(), this.viewVector.y(), this.viewVector.z());
   }
}
