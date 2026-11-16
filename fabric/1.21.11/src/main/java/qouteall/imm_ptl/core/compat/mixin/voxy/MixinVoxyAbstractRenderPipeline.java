package qouteall.imm_ptl.core.compat.mixin.voxy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import qouteall.imm_ptl.core.compat.voxy_compatibility.VoxyInterface;

@Pseudo
@Mixin(targets = "me/cortex/voxy/client/core/AbstractRenderPipeline", remap = false)
public class MixinVoxyAbstractRenderPipeline {
   @Redirect(method = "transformBlitDepth", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11C;glDisable(I)V"), remap = false, require = 0)
   private static void ip_voxyKeepPortalStencil(int cap) {
      VoxyInterface.onVoxyGlDisable(cap);
   }
}
