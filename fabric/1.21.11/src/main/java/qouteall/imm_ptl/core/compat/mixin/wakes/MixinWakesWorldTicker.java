package qouteall.imm_ptl.core.compat.mixin.wakes;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.ClientWorldLoader;

@Pseudo
@Mixin(targets = "com/goby56/wakes/event/WakeWorldTicker", remap = false)
public class MixinWakesWorldTicker {
   @Inject(method = "onEndTick", at = @At("HEAD"), cancellable = true, remap = false)
   private void ip_onEndTick(CallbackInfo ci) {
      if (ClientWorldLoader.isClientRemoteTicking) {
         ci.cancel();
      }
   }
}
