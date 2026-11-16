package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.mc_utils.WireRenderingHelper;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;

@Environment(EnvType.CLIENT)
public class PortalEntityRenderer extends EntityRenderer<Portal, PortalEntityRenderer.PortalRenderState> {
   public PortalEntityRenderer(Context context) {
      super(context);
   }

   public PortalEntityRenderer.PortalRenderState createRenderState() {
      return new PortalEntityRenderer.PortalRenderState();
   }

   public void extractRenderState(Portal portal, PortalEntityRenderer.PortalRenderState state, float partialTick) {
      super.extractRenderState(portal, state, partialTick);
      state.portal = portal;
   }

   public void submit(
      PortalEntityRenderer.PortalRenderState state, PoseStack matrixStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState
   ) {
      Portal portal = state.portal;
      IPCGlobal.renderer.renderPortalInEntityRenderer(portal);
      if (OverlayRendering.shouldRenderOverlay(portal)) {
         OverlayRendering.onRenderPortalEntity(portal, matrixStack, submitNodeCollector);
      }

      if (IPGlobal.debugRenderPortalShapeMesh && !PortalRendering.isRendering()) {
         submitNodeCollector.submitCustomGeometry(matrixStack, RenderTypes.lines(), (pose, lineVertexConsumer) -> {
            PoseStack poseStack = new PoseStack();
            poseStack.last().set(pose);
            WireRenderingHelper.renderPortalShapeMeshDebug(poseStack, lineVertexConsumer, portal);
         });
      }

      super.submit(state, matrixStack, submitNodeCollector, cameraRenderState);
   }

   public static class PortalRenderState extends EntityRenderState {
      public Portal portal;
   }
}
