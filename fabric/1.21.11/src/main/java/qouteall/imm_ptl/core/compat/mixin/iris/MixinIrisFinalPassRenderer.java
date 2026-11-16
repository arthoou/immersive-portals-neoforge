package qouteall.imm_ptl.core.compat.mixin.iris;

import net.irisshaders.iris.pipeline.FinalPassRenderer;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.IPCGlobal;

@Mixin(value = FinalPassRenderer.class, remap = false)
public class MixinIrisFinalPassRenderer {
   @Inject(method = "renderFinalPass", at = @At("HEAD"))
   void onRenderFinalPass(CallbackInfo ci) {
      if (IPCGlobal.debugEnableStencilWithIris) {
         GL11.glDisable(2960);
      }
   }
}
