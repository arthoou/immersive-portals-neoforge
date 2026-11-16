package qouteall.imm_ptl.core.mixin.client.render.framebuffer;

import com.mojang.blaze3d.opengl.GlTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.render.StencilDepthTextures;

@Mixin(GlTexture.class)
public class MixinGlTexture {
   @Inject(method = "destroyImmediately", at = @At("HEAD"))
   private void onDestroyImmediately(CallbackInfo ci) {
      StencilDepthTextures.unregister(((GlTexture)(Object)this).glId());
   }
}
