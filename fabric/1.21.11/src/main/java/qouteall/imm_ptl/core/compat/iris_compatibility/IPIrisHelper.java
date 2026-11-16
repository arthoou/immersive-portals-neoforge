package qouteall.imm_ptl.core.compat.iris_compatibility;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuTexture;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43C;
import qouteall.imm_ptl.core.render.MyRenderHelper;

public class IPIrisHelper {
   public static int getTextureId(GpuTexture texture) {
      return ((GlTexture)texture).glId();
   }

   public static void copyDepthStencil(RenderTarget from, RenderTarget to, boolean copyDepth, boolean copyStencil) {
      GlStateManager._glBindFramebuffer(36160, 0);
      int mask = 0;
      short var5;
      if (copyDepth) {
         if (copyStencil) {
            var5 = 1280;
         } else {
            var5 = 256;
         }
      } else {
         if (!copyStencil) {
            throw new RuntimeException();
         }

         var5 = 1024;
      }

      GlStateManager._glBindFramebuffer(36008, MyRenderHelper.getFbo(from));
      GlStateManager._glBindFramebuffer(36009, MyRenderHelper.getFbo(to));
      GL30.glBlitFramebuffer(0, 0, from.width, from.height, 0, 0, to.width, to.height, var5, 9728);
      GlStateManager._glBindFramebuffer(36160, 0);
   }

   private static boolean isCopyImageSubDataSupported() {
      return GL.getCapabilities().glCopyImageSubData != 0L;
   }

   public static void newCopyDepthStencil(RenderTarget from, RenderTarget to) {
      GL43C.glCopyImageSubData(
         getTextureId(from.getDepthTexture()), 3553, 0, 0, 0, 0, getTextureId(to.getDepthTexture()), 3553, 0, 0, 0, 0, from.width, from.height, 1
      );
   }

   public static void copyColor(RenderTarget from, RenderTarget to) {
      GL43C.glCopyImageSubData(
         getTextureId(from.getColorTexture()), 3553, 0, 0, 0, 0, getTextureId(to.getColorTexture()), 3553, 0, 0, 0, 0, from.width, from.height, 1
      );
   }
}
