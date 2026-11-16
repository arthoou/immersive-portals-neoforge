package qouteall.imm_ptl.core.mixin.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer {
   @Redirect(
      method = "extractRenderState",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;shouldEntityAppearGlowing(Lnet/minecraft/world/entity/Entity;)Z")
   )
   private boolean redirectGlowing(Minecraft client, Entity entity) {
      return WorldRenderInfo.isRendering() ? false : client.shouldEntityAppearGlowing(entity);
   }
}
