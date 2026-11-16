package qouteall.imm_ptl.core.mixin.client.render;

import com.mojang.blaze3d.opengl.GlCommandEncoder;
import com.mojang.blaze3d.opengl.GlRenderPass;
import com.mojang.blaze3d.opengl.GlRenderPipeline;
import java.util.Collection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.render.FrontClipping;

@Mixin(GlCommandEncoder.class)
public class MixinGlCommandEncoder {
   @Inject(method = "trySetup", at = @At("RETURN"))
   private void onTrySetupReturn(GlRenderPass renderPass, Collection<String> dynamicUniforms, CallbackInfoReturnable<Boolean> cir) {
      if (cir.getReturnValueZ()) {
         GlRenderPipeline pipeline = ((IEGlRenderPass)renderPass).ip_getPipeline();
         if (pipeline != null) {
            FrontClipping.onProgramBound(pipeline.program());
         }
      }
   }
}
