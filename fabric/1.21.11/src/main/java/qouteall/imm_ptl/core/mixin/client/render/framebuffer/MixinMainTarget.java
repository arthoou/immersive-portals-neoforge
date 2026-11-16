package qouteall.imm_ptl.core.mixin.client.render.framebuffer;

import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.textures.GpuTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.ducks.IEFrameBuffer;
import qouteall.imm_ptl.core.render.StencilDepthTextures;

@Mixin(MainTarget.class)
public abstract class MixinMainTarget {
   @Inject(method = "allocateDepthAttachment", at = @At("HEAD"))
   private void onBeginAllocateDepthAttachment(CallbackInfoReturnable<GpuTexture> cir) {
      StencilDepthTextures.creatingStencilDepth = ((IEFrameBuffer)this).ip_getIsStencilBufferEnabled();
   }

   @Inject(method = "allocateDepthAttachment", at = @At("RETURN"))
   private void onEndAllocateDepthAttachment(CallbackInfoReturnable<GpuTexture> cir) {
      StencilDepthTextures.creatingStencilDepth = false;
   }
}
