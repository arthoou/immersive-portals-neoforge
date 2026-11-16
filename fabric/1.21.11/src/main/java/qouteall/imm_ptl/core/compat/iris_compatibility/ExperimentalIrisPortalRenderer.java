package qouteall.imm_ptl.core.compat.iris_compatibility;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import java.util.ArrayList;
import java.util.List;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import qouteall.imm_ptl.core.compat.IPPortingLibCompat;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalRenderInfo;
import qouteall.imm_ptl.core.render.FrontClipping;
import qouteall.imm_ptl.core.render.IPRenderPipelines;
import qouteall.imm_ptl.core.render.MyRenderHelper;
import qouteall.imm_ptl.core.render.ViewAreaRenderer;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.imm_ptl.core.render.renderer.PortalRenderer;
import qouteall.imm_ptl.core.render.renderer.RendererUsingStencil;

public class ExperimentalIrisPortalRenderer extends PortalRenderer {
   public static final ExperimentalIrisPortalRenderer instance = new ExperimentalIrisPortalRenderer();

   public static void init() {
   }

   @Override
   public boolean replaceFrameBufferClearing() {
      return PortalRendering.isRendering();
   }

   @Override
   public void onBeforeTranslucentRendering(Matrix4f modelView) {
   }

   @Override
   public void onBeginIrisTranslucentRendering(Matrix4f modelView) {
      client.renderBuffers().bufferSource().endBatch();
      this.doPortalRendering(modelView);
      ((IEIrisNewWorldRenderingPipeline)Iris.getPipelineManager().getPipeline().get()).ip_setIsRenderingWorld(true);
   }

   @Override
   public void onAfterTranslucentRendering(Matrix4f modelView) {
   }

   @Override
   public void onHandRenderingEnded() {
   }

   @Override
   public void prepareRendering() {
      if (!IPPortingLibCompat.getIsStencilEnabled(client.getMainRenderTarget())) {
         IPPortingLibCompat.setIsStencilEnabled(client.getMainRenderTarget(), true);
      }

      MyRenderHelper.bindRenderTargetFbo(client.getMainRenderTarget());
      GL11.glClearStencil(0);
      GL11.glClear(1024);
      GlStateManager._enableDepthTest();
      GL11.glEnable(2960);
   }

   @Override
   public void finishRendering() {
      this.myFinishRendering();
   }

   protected void restoreDepthOfPortalViewArea(Portal portal, Matrix4f modelView) {
      this.setStencilStateForWorldRendering();
      RenderTarget target = client.getMainRenderTarget();

      for (DepthTestFunction depthTest : new DepthTestFunction[]{DepthTestFunction.LESS_DEPTH_TEST, DepthTestFunction.GREATER_DEPTH_TEST}) {
         ViewAreaRenderer.renderPortalArea(portal, Vec3.ZERO, modelView, MyRenderHelper.getProjectionMatrix(), false, false, true, true, depthTest, target);
      }
   }

   @Override
   public void invokeWorldRendering(WorldRenderInfo worldRenderInfo) {
      WorldRenderingPipeline pipeline = (WorldRenderingPipeline)Iris.getPipelineManager().getPipeline().get();
      SystemTimeUniforms.COUNTER.beginFrame();
      super.invokeWorldRendering(worldRenderInfo);
      SystemTimeUniforms.COUNTER.beginFrame();
      if (pipeline instanceof IrisRenderingPipeline newWorldRenderingPipeline) {
         newWorldRenderingPipeline.isBeforeTranslucent = true;
      }

      ((IEIrisNewWorldRenderingPipeline)pipeline).ip_setIsRenderingWorld(false);
   }

   protected void doPortalRendering(Matrix4f modelView) {
      GlStateManager._enableDepthTest();
      GlStateManager._depthMask(true);
      Profiler.get().popPush("render_portal_total");
      this.renderPortals(modelView);
   }

   private void myFinishRendering() {
      GL11.glStencilFunc(519, 2333, 255);
      GL11.glStencilOp(7680, 7680, 7680);
      GL11.glDisable(2960);
      GlStateManager._enableDepthTest();
   }

   protected void renderPortals(Matrix4f modelView) {
      List<Portal> portalsToRender = this.getPortalsToRender(modelView);
      List<Portal> reallyRenderedPortals = new ArrayList<>();

      for (Portal portal : portalsToRender) {
         boolean reallyRendered = this.doRenderPortal(portal, modelView);
         if (reallyRendered) {
            reallyRenderedPortals.add(portal);
         }
      }

      this.setStencilStateForWorldRendering();

      for (Portal reallyRenderedPortal : reallyRenderedPortals) {
         if (!reallyRenderedPortal.isFuseView()) {
            this.renderPortalViewAreaToStencil(reallyRenderedPortal, modelView);
         }
      }

      this.setStencilStateForWorldRendering();
   }

   private boolean doRenderPortal(Portal portal, Matrix4f modelView) {
      if (RendererUsingStencil.shouldSkipRenderingInsideFuseViewPortal(portal)) {
         return false;
      }

      int outerPortalStencilValue = PortalRendering.getPortalLayer();
      Profiler.get().push("render_view_area");
      boolean anySamplePassed = PortalRenderInfo.renderAndDecideVisibility(portal, () -> this.renderPortalViewAreaToStencil(portal, modelView));
      Profiler.get().pop();
      if (!anySamplePassed) {
         this.setStencilStateForWorldRendering();
         return false;
      }

      PortalRendering.pushPortalLayer(portal);
      int thisPortalStencilValue = outerPortalStencilValue + 1;
      if (!portal.isFuseView()) {
         Profiler.get().push("clear_depth_of_view_area");
         this.clearDepthOfThePortalViewArea(portal);
         Profiler.get().pop();
      }

      this.setStencilStateForWorldRendering();
      this.renderPortalContent(portal);
      if (!portal.isFuseView()) {
         this.restoreDepthOfPortalViewArea(portal, modelView);
      }

      clampStencilValue(outerPortalStencilValue);
      PortalRendering.popPortalLayer();
      return true;
   }

   public void onAfterIrisDeferredCompositeRendering() {
      int outerPortalStencilValue = PortalRendering.getPortalLayer();
      clampStencilValue(outerPortalStencilValue);
      this.setStencilStateForWorldRendering();
   }

   @Override
   public void renderPortalInEntityRenderer(Portal portal) {
   }

   private void renderPortalViewAreaToStencil(Portal portal, Matrix4f modelView) {
      int outerPortalStencilValue = PortalRendering.getPortalLayer();
      GL11.glStencilFunc(514, outerPortalStencilValue, 255);
      GL11.glStencilOp(7680, 7680, 7682);
      GL11.glStencilMask(255);
      FrontClipping.updateInnerClipping(modelView);
      ViewAreaRenderer.renderPortalArea(
         portal, Vec3.ZERO, modelView, MyRenderHelper.getProjectionMatrix(), true, false, true, true, client.getMainRenderTarget()
      );
   }

   private void clearDepthOfThePortalViewArea(Portal portal) {
      GlStateManager._enableDepthTest();
      GlStateManager._depthMask(true);
      this.setStencilStateForWorldRendering();
      MyRenderHelper.renderScreenTriangle(IPRenderPipelines.SCREEN_TRIANGLE_DEPTH_CLEAR, 1.0F, 255, 255, 255, 255, client.getMainRenderTarget());
   }

   public static void clampStencilValue(int maximumValue) {
      GlStateManager._depthMask(true);
      GL11.glStencilFunc(513, maximumValue, 255);
      GL11.glStencilOp(7680, 7681, 7681);
      MyRenderHelper.renderScreenTriangle(IPRenderPipelines.SCREEN_TRIANGLE_STENCIL_ONLY, client.getMainRenderTarget());
      GlStateManager._enableDepthTest();
   }

   private void setStencilStateForWorldRendering() {
      int thisPortalStencilValue = PortalRendering.getPortalLayer();
      GL11.glStencilFunc(514, thisPortalStencilValue, 255);
      GL11.glStencilOp(7680, 7680, 7680);
   }
}
