package qouteall.imm_ptl.core.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.render.context_management.RenderStates;

@Mixin(DebugScreenOverlay.class)
public class MixinDebugScreenOverlay {
   @WrapOperation(
      method = "render",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/components/DebugScreenOverlay;renderLines(Lnet/minecraft/client/gui/GuiGraphics;Ljava/util/List;Z)V"
      )
   )
   private void onRenderLines(DebugScreenOverlay instance, GuiGraphics guiGraphics, List<String> lines, boolean leftAlign, Operation<Void> original) {
      if (!leftAlign && Minecraft.getInstance().debugEntries.isOverlayVisible()) {
         List<String> debugText = RenderStates.collectDebugText();
         if (IPGlobal.moveDebugTextToTop) {
            lines.addAll(0, debugText);
         } else {
            lines.addAll(debugText);
         }
      }

      original.call(new Object[]{instance, guiGraphics, lines, leftAlign});
   }
}
