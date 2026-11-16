package qouteall.imm_ptl.core.compat.mixin.voxy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.compat.voxy_compatibility.VoxyInterface;

@Pseudo
@Mixin(targets = "me/cortex/voxy/client/core/VoxyRenderSystem", remap = false)
public class MixinVoxyRenderSystem {
   @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Ljava/lang/System;gc()V"), remap = false, require = 0)
   private static void ip_voxySkipGcWhileSwitching() {
      if (!VoxyInterface.skipGcNow) {
         System.gc();
      }
   }

   @Inject(method = "renderOpaque", at = @At("RETURN"), remap = false, require = 0)
   private void ip_voxyAfterLodDraw(CallbackInfo ci) {
      VoxyInterface.afterLodDraw();
   }
}
