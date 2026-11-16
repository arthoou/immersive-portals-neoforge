package qouteall.imm_ptl.core.compat.iris_compatibility;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.IPMcHelper;
import qouteall.imm_ptl.core.compat.IPPortingLibCompat;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalRenderInfo;
import qouteall.imm_ptl.core.render.MyGameRenderer;
import qouteall.imm_ptl.core.render.MyRenderHelper;
import qouteall.imm_ptl.core.render.SecondaryFrameBuffer;
import qouteall.imm_ptl.core.render.ViewAreaRenderer;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.imm_ptl.core.render.renderer.PortalRenderer;

public class IrisPortalRenderer extends PortalRenderer {
   public static final IrisPortalRenderer instance = new IrisPortalRenderer();
   private SecondaryFrameBuffer[] deferredFbs = new SecondaryFrameBuffer[0];
   private boolean portalRenderingNeeded = false;
   private boolean nextFramePortalRenderingNeeded = false;

   IrisPortalRenderer() {
      IPGlobal.PRE_GAME_RENDER_EVENT.register(() -> this.updateNeedsPortalRendering());
   }

   @Override
   public boolean replaceFrameBufferClearing() {
      return false;
   }

   @Override
   public void prepareRendering() {
      Validate.isTrue(!PortalRendering.isRendering());
      IPCGlobal.useSeparatedStencilFormat = !IPMcHelper.isNvidiaVideocard();
      if (this.deferredFbs.length != PortalRendering.getMaxPortalLayer() + 1) {
         for (SecondaryFrameBuffer fb : this.deferredFbs) {
            fb.fb.destroyBuffers();
         }

         this.deferredFbs = new SecondaryFrameBuffer[PortalRendering.getMaxPortalLayer() + 1];

         for (int i = 0; i < this.deferredFbs.length; i++) {
            this.deferredFbs[i] = new SecondaryFrameBuffer();
         }
      }

      CHelper.checkGlError();

      for (SecondaryFrameBuffer deferredFb : this.deferredFbs) {
         deferredFb.prepare();
         IPPortingLibCompat.setIsStencilEnabled(deferredFb.fb, true);
         MyRenderHelper.bindRenderTargetFbo(deferredFb.fb);
         GlStateManager._disableScissorTest();
         GL11.glClearColor(1.0F, 0.0F, 1.0F, 0.0F);
         GL11.glClearDepth(1.0);
         GL11.glClearStencil(0);
         GlStateManager._clear(17664);
         CHelper.checkGlError();
         GlStateManager._glBindFramebuffer(36160, 0);
      }

      IPPortingLibCompat.setIsStencilEnabled(client.getMainRenderTarget(), false);
   }

   private void updateNeedsPortalRendering() {
      this.portalRenderingNeeded = this.nextFramePortalRenderingNeeded;
      this.nextFramePortalRenderingNeeded = false;
   }

   @Override
   public void onBeforeHandRendering(Matrix4f modelView) {
      this.doMainRenderings(modelView);
   }

   private void doMainRenderings(Matrix4f modelView) {
      CHelper.checkGlError();
      RenderTarget mcFrameBuffer = client.getMainRenderTarget();
      int portalLayer = PortalRendering.getPortalLayer();
      if (this.portalRenderingNeeded) {
         CHelper.doCheckGlError();
         GlStateManager._glBindFramebuffer(36008, MyRenderHelper.getFbo(mcFrameBuffer));
         GlStateManager._glBindFramebuffer(36009, MyRenderHelper.getFbo(this.deferredFbs[portalLayer].fb));
         GL30.glBlitFramebuffer(0, 0, mcFrameBuffer.width, mcFrameBuffer.height, 0, 0, mcFrameBuffer.width, mcFrameBuffer.height, 256, 9728);
         int errorCode = GL11.glGetError();
         if (errorCode != 0) {
            IPGlobal.renderMode = IPGlobal.RenderMode.compatibility;
            CHelper.printChat("[Immersive Portals]Switched to compatibility portal rendering mode. Portal-in-portal wont' be rendered");
         }

         this.initStencilForLayer(portalLayer);
         GL11.glEnable(2960);
         GL11.glStencilFunc(514, portalLayer, 255);
         GL11.glStencilOp(7680, 7680, 7680);
         MyRenderHelper.drawScreenFrameBuffer(mcFrameBuffer, false, true, this.deferredFbs[portalLayer].fb);
         GL11.glDisable(2960);
      }

      this.renderPortals(modelView);
      if (portalLayer == 0) {
         this.finish();
      }
   }

   @Override
   public void onHandRenderingEnded() {
   }

   private void initStencilForLayer(int portalLayer) {
      if (portalLayer == 0) {
         MyRenderHelper.bindRenderTargetFbo(this.deferredFbs[portalLayer].fb);
         GlStateManager._disableScissorTest();
         GL11.glClearStencil(0);
         GlStateManager._clear(1024);
      } else {
         CHelper.checkGlError();
         GlStateManager._glBindFramebuffer(36008, MyRenderHelper.getFbo(this.deferredFbs[portalLayer - 1].fb));
         GlStateManager._glBindFramebuffer(36009, MyRenderHelper.getFbo(this.deferredFbs[portalLayer].fb));
         GL30.glBlitFramebuffer(
            0, 0, this.deferredFbs[0].fb.width, this.deferredFbs[0].fb.height, 0, 0, this.deferredFbs[0].fb.width, this.deferredFbs[0].fb.height, 1024, 9728
         );
         CHelper.checkGlError();
      }
   }

   @Override
   public void onBeforeTranslucentRendering(Matrix4f modelView) {
   }

   @Override
   public void onAfterTranslucentRendering(Matrix4f modelView) {
   }

   @Override
   public void finishRendering() {
   }

   private void finish() {
      GlStateManager._colorMask(true, true, true, true);
      if (RenderStates.getRenderedPortalNum() != 0) {
         if (this.portalRenderingNeeded) {
            RenderTarget mainFrameBuffer = client.getMainRenderTarget();
            MyRenderHelper.drawScreenFrameBuffer(this.deferredFbs[0].fb, false, false, mainFrameBuffer);
            CHelper.checkGlError();
         }
      }
   }

   protected void doRenderPortal(Portal portal, Matrix4f modelView) {
      this.nextFramePortalRenderingNeeded = true;
      if (this.portalRenderingNeeded) {
         if (this.tryRenderViewAreaInDeferredBufferAndIncreaseStencil(portal, modelView)) {
            PortalRendering.pushPortalLayer(portal);
            this.renderPortalContent(portal);
            int innerLayer = PortalRendering.getPortalLayer();
            PortalRendering.popPortalLayer();
            int outerLayer = PortalRendering.getPortalLayer();
            if (innerLayer <= PortalRendering.getMaxPortalLayer()) {
               GL11.glEnable(2960);
               GL11.glStencilFunc(514, innerLayer, 255);
               GL11.glStencilOp(7680, 7680, 7680);
               MyRenderHelper.drawScreenFrameBuffer(this.deferredFbs[innerLayer].fb, true, false, this.deferredFbs[outerLayer].fb);
               GL11.glDisable(2960);
            }
         }
      }
   }

   private boolean tryRenderViewAreaInDeferredBufferAndIncreaseStencil(Portal portal, Matrix4f modelView) {
      int portalLayer = PortalRendering.getPortalLayer();
      this.initStencilForLayer(portalLayer);
      GL11.glEnable(2960);
      GL11.glStencilFunc(514, portalLayer, 255);
      GL11.glStencilOp(7680, 7680, 7682);
      GlStateManager._enableDepthTest();
      boolean result = PortalRenderInfo.renderAndDecideVisibility(
         portal,
         () -> ViewAreaRenderer.renderPortalArea(
            portal, Vec3.ZERO, modelView, MyRenderHelper.getProjectionMatrix(), true, true, true, true, this.deferredFbs[portalLayer].fb
         )
      );
      GL11.glDisable(2960);
      return result;
   }

   @Override
   public void invokeWorldRendering(WorldRenderInfo worldRenderInfo) {
      MyGameRenderer.renderWorldNew(worldRenderInfo, Runnable::run);
   }

   @Override
   public void renderPortalInEntityRenderer(Portal portal) {
   }

   protected void renderPortals(Matrix4f modelView) {
      for (Portal portal : this.getPortalsToRender(modelView)) {
         this.doRenderPortal(portal, modelView);
      }
   }
}
