package qouteall.imm_ptl.core.mixin.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.RenderStates;

@Mixin(value = LevelRenderer.class, priority = 1100)
public class MixinLevelRenderer_Optional {
   @Shadow
   private ViewArea viewArea;
   @Shadow
   @Final
   private Minecraft minecraft;

   @Inject(method = "scheduleTranslucentSectionResort", at = @At("HEAD"), cancellable = true, require = 0)
   private void onScheduleTranslucentSectionResort(Vec3 cameraPos, CallbackInfo ci) {
      if (PortalRendering.isRendering()) {
         ci.cancel();
      }
   }

   @Redirect(
      method = "cullTerrain",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;setCameraPosition(Lnet/minecraft/world/phys/Vec3;)V"),
      require = 0
   )
   private void onSetChunkBuilderCameraPosition(SectionRenderDispatcher chunkBuilder, Vec3 cameraPosition) {
      if (!PortalRendering.isRendering() || this.minecraft.level.dimension() != RenderStates.originalPlayerDimension) {
         chunkBuilder.setCameraPosition(cameraPosition);
      }
   }
}
