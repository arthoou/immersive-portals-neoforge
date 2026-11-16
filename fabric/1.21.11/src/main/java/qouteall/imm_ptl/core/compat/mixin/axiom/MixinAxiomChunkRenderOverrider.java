package qouteall.imm_ptl.core.compat.mixin.axiom;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.compat.axiom_compatibility.AxiomInterface;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;

@Pseudo
@Mixin(targets = "com/moulberry/axiom/render/ChunkRenderOverrider", remap = false)
public class MixinAxiomChunkRenderOverrider {
   @Inject(method = "isOverridingSection", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
   private static void ip_onIsOverridingSection(CallbackInfoReturnable<Boolean> cir) {
      if (PortalRendering.isRendering()) {
         cir.setReturnValue(false);
      }
   }

   @Inject(method = {"render", "uploadDirty", "afterRenderLevel"}, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
   private static void ip_onPreviewPass(CallbackInfo ci) {
      if (PortalRendering.isRendering()) {
         ci.cancel();
      }
   }

   @Inject(method = "invalidateChunkSection", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
   private static void ip_onInvalidateChunkSection(CallbackInfo ci) {
      if (AxiomInterface.isHandlingAnotherLevel()) {
         ci.cancel();
      }
   }
}
