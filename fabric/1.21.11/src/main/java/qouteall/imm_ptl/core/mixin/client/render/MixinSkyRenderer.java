package qouteall.imm_ptl.core.mixin.client.render;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;

@Mixin(SkyRenderer.class)
public class MixinSkyRenderer {
   @Redirect(
      method = "shouldRenderDarkDisc",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getEyePosition(F)Lnet/minecraft/world/phys/Vec3;")
   )
   private Vec3 redirectGetEyePositionInSkyRendering(LocalPlayer player, float partialTicks) {
      return WorldRenderInfo.isRendering() ? WorldRenderInfo.getCameraPos() : player.getEyePosition(partialTicks);
   }
}
