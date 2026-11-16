package qouteall.imm_ptl.core.mixin.common.interaction;

import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClipContext.class)
public interface IEClipContext {
   @Accessor("block")
   Block ip_getBlock();

   @Accessor("fluid")
   Fluid ip_getFluid();
}
