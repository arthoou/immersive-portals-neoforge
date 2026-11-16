package qouteall.imm_ptl.core.compat.mixin.axiom;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.compat.axiom_compatibility.AxiomInterface;

@Pseudo
@Mixin(targets = {"com/moulberry/axiom/render/BiomeOverlayRenderer", "com/moulberry/axiom/render/CollisionMeshOverlayRenderer"}, remap = false)
public class MixinAxiomOverlayRenderers {
   @Inject(method = {"markDirty", "forgetChunk"}, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
   private void ip_onChunkChange(CallbackInfo ci) {
      if (AxiomInterface.isHandlingAnotherLevel()) {
         ci.cancel();
      }
   }
}
