package qouteall.imm_ptl.core.compat.iris_compatibility;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.compat.IPPortingLibCompat;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalRenderInfo;
import qouteall.imm_ptl.core.render.MyGameRenderer;
import qouteall.imm_ptl.core.render.MyRenderHelper;
import qouteall.imm_ptl.core.render.SecondaryFrameBuffer;
import qouteall.imm_ptl.core.render.ViewAreaRenderer;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.imm_ptl.core.render.renderer.PortalRenderer;

public class IrisCompatibilityPortalRenderer extends PortalRenderer {
   public static final IrisCompatibilityPortalRenderer instance = new IrisCompatibilityPortalRenderer(false);
   public static final IrisCompatibilityPortalRenderer debugModeInstance = new IrisCompatibilityPortalRenderer(true);
   private SecondaryFrameBuffer deferredBuffer = new SecondaryFrameBuffer();
   private Matrix4f passingModelView = new Matrix4f();
   public boolean isDebugMode;

   public IrisCompatibilityPortalRenderer(boolean isDebugMode) {
      this.isDebugMode = isDebugMode;
   }

   @Override
   public boolean replaceFrameBufferClearing() {
      return false;
   }

   @Override
   public void onBeforeTranslucentRendering(Matrix4f modelView) {
      if (!PortalRendering.isRendering()) {
         this.passingModelView = modelView;
         GL11.glDisable(2960);
      }
   }

   @Override
   public void onAfterTranslucentRendering(Matrix4f modelView) {
   }

   @Override
   public void finishRendering() {
      GL11.glDisable(2960);
   }

   @Override
   public void prepareRendering() {
      this.deferredBuffer.prepare();
      RenderSystem.getDevice()
         .createCommandEncoder()
         .clearColorAndDepthTextures(this.deferredBuffer.fb.getColorTexture(), 16711680, this.deferredBuffer.fb.getDepthTexture(), 1.0);
      IPPortingLibCompat.setIsStencilEnabled(client.getMainRenderTarget(), false);
   }

   protected void doRenderPortal(Portal portal, Matrix4f modelView) {
      if (!PortalRendering.isRendering()) {
         if (this.testShouldRenderPortal(portal, modelView)) {
            PortalRendering.pushPortalLayer(portal);
            this.renderPortalContent(portal);
            PortalRendering.popPortalLayer();
            CHelper.enableDepthClamp();
            if (!this.isDebugMode) {
               MyRenderHelper.drawPortalAreaWithFramebuffer(
                  portal, client.getMainRenderTarget(), modelView, MyRenderHelper.getProjectionMatrix(), this.deferredBuffer.fb
               );
            } else {
               MyRenderHelper.drawScreenFrameBuffer(client.getMainRenderTarget(), true, true, this.deferredBuffer.fb);
            }

            CHelper.disableDepthClamp();
            GlStateManager._colorMask(true, true, true, true);
         }
      }
   }

   @Override
   public void invokeWorldRendering(WorldRenderInfo worldRenderInfo) {
      MyGameRenderer.renderWorldNew(worldRenderInfo, Runnable::run);
   }

   @Override
   public void renderPortalInEntityRenderer(Portal portal) {
   }

   private boolean testShouldRenderPortal(Portal portal, Matrix4f modelView) {
      return PortalRenderInfo.renderAndDecideVisibility(
         portal,
         () -> ViewAreaRenderer.renderPortalArea(
            portal, Vec3.ZERO, modelView, MyRenderHelper.getProjectionMatrix(), true, false, false, true, this.deferredBuffer.fb
         )
      );
   }

   @Override
   public void onBeforeHandRendering(Matrix4f modelView) {
      if (!PortalRendering.isRendering()) {
         CHelper.checkGlError();
         IPIrisHelper.newCopyDepthStencil(client.getMainRenderTarget(), this.deferredBuffer.fb);
         IPIrisHelper.copyColor(client.getMainRenderTarget(), this.deferredBuffer.fb);
         CHelper.checkGlError();
         this.renderPortals(this.passingModelView);
         RenderTarget mainFrameBuffer = client.getMainRenderTarget();
         MyRenderHelper.drawScreenFrameBuffer(this.deferredBuffer.fb, false, false, mainFrameBuffer);
      }
   }

   @Override
   public void onHandRenderingEnded() {
   }

   protected void renderPortals(Matrix4f modelView) {
      for (Portal portal : this.getPortalsToRender(modelView)) {
         this.doRenderPortal(portal, modelView);
      }
   }
}
