package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface;
import qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumInterface;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.nether_portal.BlockPortalShape;
import qouteall.imm_ptl.core.portal.nether_portal.BreakablePortalEntity;
import qouteall.imm_ptl.core.render.context_management.RenderStates;

@Environment(EnvType.CLIENT)
public class OverlayRendering {
   private static final RandomSource random = RandomSource.create();
   private static boolean shaderOverlayWarned = false;

   public static boolean shouldRenderOverlay(Portal portal) {
      return portal instanceof BreakablePortalEntity breakablePortalEntity && breakablePortalEntity.getActualOverlay() != null
         ? breakablePortalEntity.isInFrontOfPortal(CHelper.getCurrentCameraPos())
         : false;
   }

   public static void onRenderPortalEntity(Portal portal, PoseStack matrixStack, SubmitNodeCollector submitNodeCollector) {
      if (IrisInterface.invoker.isShaders()) {
         if (!shaderOverlayWarned) {
            shaderOverlayWarned = true;
            CHelper.printChat("[Immersive Portals] Portal overlay cannot be rendered with shaders");
         }
      } else {
         if (portal instanceof BreakablePortalEntity) {
            renderBreakablePortalOverlay((BreakablePortalEntity)portal, RenderStates.getPartialTick(), matrixStack, submitNodeCollector);
         }
      }
   }

   public static List<BakedQuad> getQuads(BlockStateModel model, BlockState blockState, Vec3 portalNormal) {
      Direction facing = Direction.getApproximateNearest(portalNormal.x, portalNormal.y, portalNormal.z);
      List<BakedQuad> result = new ArrayList<>();
      List<BlockModelPart> parts = model.collectParts(random);

      for (BlockModelPart part : parts) {
         result.addAll(part.getQuads(facing));
      }

      for (BlockModelPart part : parts) {
         result.addAll(part.getQuads(null));
      }

      if (result.isEmpty()) {
         for (Direction direction : Direction.values()) {
            for (BlockModelPart part : parts) {
               result.addAll(part.getQuads(direction));
            }
         }
      }

      return result;
   }

   private static void renderBreakablePortalOverlay(
      BreakablePortalEntity portal, float partialTick, PoseStack matrixStack, SubmitNodeCollector submitNodeCollector
   ) {
      BreakablePortalEntity.OverlayInfo overlay = portal.getActualOverlay();
      if (overlay != null) {
         BlockState blockState = overlay.blockState();
         Vec3 cameraPos = CHelper.getCurrentCameraPos();
         if (blockState != null) {
            BlockRenderDispatcher blockRenderManager = Minecraft.getInstance().getBlockRenderer();
            BlockPortalShape blockPortalShape = portal.blockPortalShape;
            if (blockPortalShape != null) {
               matrixStack.pushPose();
               Vec3 offset = portal.getNormal().scale(overlay.offset());
               Vec3 pos = portal.position();
               matrixStack.translate(offset.x, offset.y, offset.z);
               BlockStateModel model = blockRenderManager.getBlockModel(blockState);
               RenderType renderLayer = Sheets.translucentBlockItemSheet();
               List<BakedQuad> quads = getQuads(model, blockState, portal.getNormal());
               random.setSeed(0L);
               float opacity = (float)overlay.opacity();

               for (BlockPos blockPos : blockPortalShape.area) {
                  matrixStack.pushPose();
                  matrixStack.translate(blockPos.getX() - pos.x, blockPos.getY() - pos.y, blockPos.getZ() - pos.z);
                  if (overlay.rotation() != null) {
                     matrixStack.mulPose(overlay.rotation().toMcQuaternion());
                  }

                  submitNodeCollector.submitCustomGeometry(
                     matrixStack,
                     renderLayer,
                     (blockPose, buffer) -> {
                        for (BakedQuad quad : quads) {
                           SodiumInterface.invoker.markSpriteActive(quad.sprite());
                           buffer.putBulkData(
                              blockPose,
                              quad,
                              new float[]{1.0F, 1.0F, 1.0F, 1.0F},
                              1.0F,
                              1.0F,
                              1.0F,
                              opacity,
                              new int[]{14680304, 14680304, 14680304, 14680304},
                              OverlayTexture.NO_OVERLAY
                           );
                        }
                     }
                  );
                  matrixStack.popPose();
               }

               matrixStack.popPose();
            }
         }
      }
   }
}
