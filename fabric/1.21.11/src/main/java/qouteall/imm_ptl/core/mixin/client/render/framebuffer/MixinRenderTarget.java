package qouteall.imm_ptl.core.mixin.client.render.framebuffer;

import com.mojang.blaze3d.pipeline.RenderTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.ducks.IEFrameBuffer;
import qouteall.imm_ptl.core.render.StencilDepthTextures;
import qouteall.q_misc_util.platform.ClientPlatform;

@Mixin(RenderTarget.class)
public abstract class MixinRenderTarget implements IEFrameBuffer {
   @Unique
   private boolean isStencilBufferEnabled;
   @Shadow
   public int width;
   @Shadow
   public int height;

   @Shadow
   public abstract void resize(int var1, int var2);

   @Inject(method = {"<init>(Ljava/lang/String;Z)V", "<init>(Ljava/lang/String;ZZ)V"}, at = @At("RETURN"), require = 1)
   private void onInit(CallbackInfo ci) {
      this.isStencilBufferEnabled = false;
   }

   @Inject(method = "createBuffers", at = @At("HEAD"))
   private void onBeginCreateBuffers(int width, int height, CallbackInfo ci) {
      StencilDepthTextures.creatingStencilDepth = this.isStencilBufferEnabled;
   }

   @Inject(method = "createBuffers", at = @At("RETURN"))
   private void onEndCreateBuffers(int width, int height, CallbackInfo ci) {
      StencilDepthTextures.creatingStencilDepth = false;
      ClientPlatform.get().onRenderTargetBuffersCreated((RenderTarget)(Object)this);
   }

   @Inject(method = "Lcom/mojang/blaze3d/pipeline/RenderTarget;copyDepthFrom(Lcom/mojang/blaze3d/pipeline/RenderTarget;)V", at = @At("RETURN"))
   private void onCopiedDepthFrom(RenderTarget framebuffer, CallbackInfo ci) {
      CHelper.checkGlError();
   }

   @Override
   public boolean ip_getIsStencilBufferEnabled() {
      return this.isStencilBufferEnabled;
   }

   @Override
   public void ip_setIsStencilBufferEnabledAndReload(boolean cond) {
      if (this.isStencilBufferEnabled != cond) {
         this.isStencilBufferEnabled = cond;
         this.resize(this.width, this.height);
      }
   }
}
