package qouteall.imm_ptl.core.compat.mixin.voxy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface;
import qouteall.imm_ptl.core.compat.voxy_compatibility.IEVoxyLevelRenderer;
import qouteall.imm_ptl.core.compat.voxy_compatibility.VoxyInterface;

@Mixin(value = LevelRenderer.class, priority = 1500)
public abstract class MixinVoxyLevelRenderer_PassGuard implements IEVoxyLevelRenderer {
   @Unique
   private boolean ip_voxyPendingCreate = false;
   @Unique
   @Nullable
   private Object ip_voxyBoundIrisPipeline = null;

   @Override
   public boolean ip_voxyIsCreationPending() {
      return this.ip_voxyPendingCreate;
   }

   @Override
   public void ip_voxySetCreationPending(boolean pending) {
      this.ip_voxyPendingCreate = pending;
   }

   @Nullable
   @Override
   public Object ip_voxyBoundIrisPipeline() {
      return this.ip_voxyBoundIrisPipeline;
   }

   @Override
   public void ip_voxySetBoundIrisPipeline(@Nullable Object pipeline) {
      this.ip_voxyBoundIrisPipeline = pipeline;
   }

   @Inject(method = "voxy$getRenderSystem", at = @At("HEAD"), cancellable = true, remap = false)
   private void ip_voxyGateRenderSystem(CallbackInfoReturnable<?> cir) {
      if (VoxyInterface.shouldHideRenderSystem((LevelRenderer)(Object)this)) {
         cir.setReturnValue(null);
      }
   }

   @Inject(method = "voxy$createRenderer", at = @At("HEAD"), cancellable = true, remap = false)
   private void ip_voxyDeferCreation(CallbackInfo ci) {
      LevelRenderer self = (LevelRenderer)(Object)this;
      if (VoxyInterface.creatingNow) {
         this.ip_voxyPendingCreate = false;
      } else if (self == Minecraft.getInstance().levelRenderer && !VoxyInterface.isSecondaryContext() && !IrisInterface.invoker.isShaders()) {
         this.ip_voxyPendingCreate = false;
         this.ip_voxyBoundIrisPipeline = null;
      } else {
         this.ip_voxyPendingCreate = true;
         ci.cancel();
      }
   }

   @Inject(method = "renderLevel", at = @At("HEAD"))
   private void ip_voxyBeforeRender(CallbackInfo ci) {
      if (!IrisInterface.invoker.isIrisPresent()) {
         VoxyInterface.invoker.onRendererAboutToRender((LevelRenderer)(Object)this);
      }
   }
}
