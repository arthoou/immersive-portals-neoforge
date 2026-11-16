package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.client.Minecraft;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.platform.ClientPlatform;

public class SecondaryFrameBuffer {
   public TextureTarget fb;

   public void prepare() {
      RenderTarget mainFrameBuffer = Minecraft.getInstance().getMainRenderTarget();
      int width = mainFrameBuffer.width;
      int height = mainFrameBuffer.height;
      this.prepare(width, height);
   }

   public void prepare(int width, int height) {
      if (this.fb == null) {
         this.fb = ClientPlatform.get().createSecondaryTextureTarget("ImmPtl secondary", width, height, true);
         Helper.log("Secondary Framebuffer init");
      }

      if (width != this.fb.width || height != this.fb.height) {
         this.fb.resize(width, height);
         Helper.log("Secondary Framebuffer resized");
      }
   }
}
