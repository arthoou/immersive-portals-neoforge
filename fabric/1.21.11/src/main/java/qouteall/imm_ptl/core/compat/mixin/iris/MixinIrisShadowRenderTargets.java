package qouteall.imm_ptl.core.compat.mixin.iris;

import net.irisshaders.iris.shadows.ShadowRenderTargets;
import org.spongepowered.asm.mixin.Mixin;
import qouteall.imm_ptl.core.compat.iris_compatibility.IEIrisShadowRenderTargets;

@Mixin(value = ShadowRenderTargets.class, remap = false)
public class MixinIrisShadowRenderTargets implements IEIrisShadowRenderTargets {
}
