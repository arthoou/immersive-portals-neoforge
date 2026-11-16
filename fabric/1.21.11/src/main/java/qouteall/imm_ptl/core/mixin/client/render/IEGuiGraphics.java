package qouteall.imm_ptl.core.mixin.client.render;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.state.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GuiGraphics.class)
public interface IEGuiGraphics {
   @Accessor("guiRenderState")
   GuiRenderState ip_getGuiRenderState();
}
