package qouteall.imm_ptl.core.compat.mixin.wakes;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;

@Pseudo
@Mixin(targets = {"com/goby56/wakes/render/WakeRenderer", "com/goby56/wakes/render/SplashPlaneRenderer"}, remap = false)
public class MixinWakesRenderers {
   @Inject(method = "endMain", at = @At("HEAD"), cancellable = true, remap = false)
   private void ip_onEndMain(CallbackInfo ci) {
      if (PortalRendering.isRendering()) {
         ci.cancel();
      }
   }
}
