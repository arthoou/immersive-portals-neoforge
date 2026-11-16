package qouteall.imm_ptl.core.compat.mixin.axiom;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.compat.axiom_compatibility.AxiomInterface;

@Pseudo
@Mixin(targets = "com/moulberry/axiom/AxiomClient", remap = false)
public class MixinAxiomClient {
   @Inject(method = "isAxiomActive", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
   private static void ip_onIsAxiomActive(CallbackInfoReturnable<Boolean> cir) {
      if (AxiomInterface.shouldBeInactive()) {
         cir.setReturnValue(false);
      }
   }
}
