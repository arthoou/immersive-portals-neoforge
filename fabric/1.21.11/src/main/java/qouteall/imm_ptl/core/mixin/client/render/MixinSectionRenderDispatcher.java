package qouteall.imm_ptl.core.mixin.client.render;

import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SectionBufferBuilderPool;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumInterface;

@Mixin(SectionRenderDispatcher.class)
public class MixinSectionRenderDispatcher {
   @Shadow
   @Final
   private SectionBufferBuilderPool bufferPool;

   @Redirect(
      method = "<init>",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/RenderBuffers;sectionBufferPool()Lnet/minecraft/client/renderer/SectionBufferBuilderPool;"
      )
   )
   private SectionBufferBuilderPool redirectSectionBufferBuilderPool(RenderBuffers instance) {
      if (ClientWorldLoader.getIsCreatingClientWorld() && !SodiumInterface.invoker.isSodiumPresent()) {
         int processors = Runtime.getRuntime().availableProcessors();
         return SectionBufferBuilderPool.allocate(processors);
      } else {
         return instance.sectionBufferPool();
      }
   }
}
