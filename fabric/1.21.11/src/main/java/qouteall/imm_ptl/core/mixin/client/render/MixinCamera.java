package qouteall.imm_ptl.core.mixin.client.render;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.ducks.IECamera;
import qouteall.imm_ptl.core.render.CrossPortalEntityRenderer;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;

@Mixin(Camera.class)
public abstract class MixinCamera implements IECamera {
   @Shadow
   private Vec3 position;
   @Shadow
   private Level level;
   @Shadow
   private Entity entity;
   @Shadow
   private float eyeHeight;
   @Shadow
   private float eyeHeightOld;
   @Shadow
   @Final
   private EnvironmentAttributeProbe attributeProbe;

   @Shadow
   protected abstract void setPosition(Vec3 var1);

   @Shadow
   public abstract Entity entity();

   @Inject(method = "Lnet/minecraft/client/Camera;setup(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;ZZF)V", at = @At("RETURN"))
   private void onUpdateFinished(Level area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float partialTick, CallbackInfo ci) {
      Camera this_ = (Camera)(Object)this;
      WorldRenderInfo.adjustCameraPos(this_);
   }

   @Inject(method = "Lnet/minecraft/client/Camera;getFluidInCamera()Lnet/minecraft/world/level/material/FogType;", at = @At("HEAD"), cancellable = true)
   private void getSubmergedFluidState(CallbackInfoReturnable<FogType> cir) {
      if (PortalRendering.isRendering()) {
         cir.setReturnValue(FogType.NONE);
         cir.cancel();
      }
   }

   @Inject(method = "Lnet/minecraft/client/Camera;isDetached()Z", at = @At("HEAD"), cancellable = true)
   private void onIsThirdPerson(CallbackInfoReturnable<Boolean> cir) {
      if (CrossPortalEntityRenderer.shouldRenderPlayerDefault()) {
         cir.setReturnValue(true);
      }
   }

   @Override
   public void ip_resetState(Vec3 pos, ClientLevel currWorld) {
      this.setPosition(pos);
      this.level = currWorld;
   }

   @Override
   public float ip_getCameraY() {
      return this.eyeHeight;
   }

   @Override
   public float ip_getLastCameraY() {
      return this.eyeHeightOld;
   }

   @Override
   public void ip_setCameraY(float cameraY_, float lastCameraY_) {
      this.eyeHeight = cameraY_;
      this.eyeHeightOld = lastCameraY_;
   }

   @Override
   public void portal_setPos(Vec3 pos) {
      this.setPosition(pos);
   }

   @Override
   public void portal_setFocusedEntity(Entity arg) {
      this.entity = arg;
   }

   @Override
   public void portal_tickAttributeProbe() {
      if (this.level != null && this.position != null) {
         this.attributeProbe.tick(this.level, this.position);
      }
   }
}
