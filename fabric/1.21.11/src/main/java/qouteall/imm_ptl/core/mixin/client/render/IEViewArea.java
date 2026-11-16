package qouteall.imm_ptl.core.mixin.client.render;

import net.minecraft.client.renderer.ViewArea;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ViewArea.class)
public interface IEViewArea {
   @Accessor("cameraSectionPos")
   void ip_setCameraSectionPos(SectionPos var1);
}
