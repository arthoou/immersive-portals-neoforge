package qouteall.imm_ptl.core.compat.mixin.voxy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.compat.voxy_compatibility.VoxyInterface;

@Pseudo
@Mixin(targets = "net/irisshaders/iris/pipeline/PipelineManager", remap = false)
public class MixinVoxyIrisPipelineManager {
   @Inject(method = "preparePipeline", at = @At("RETURN"), remap = false, require = 0)
   private void ip_voxyAfterPipelinePrepared(CallbackInfoReturnable<?> cir) {
      LevelRenderer renderer = Minecraft.getInstance().levelRenderer;
      if (renderer != null) {
         VoxyInterface.invoker.onRendererAboutToRender(renderer);
      }
   }
}
