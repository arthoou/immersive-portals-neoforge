package qouteall.imm_ptl.core.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

@Environment(EnvType.CLIENT)
public class IPRenderTypes {
   private static final RenderType DEBUG_LINE_STRIP = RenderType.create(
      "imm_ptl_debug_line_strip", RenderSetup.builder(IPRenderPipelines.DEBUG_LINE_STRIP).createRenderSetup()
   );

   public static RenderType debugLineStrip() {
      return DEBUG_LINE_STRIP;
   }
}
