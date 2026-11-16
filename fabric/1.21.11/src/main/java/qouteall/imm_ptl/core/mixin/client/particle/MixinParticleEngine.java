package qouteall.imm_ptl.core.mixin.client.particle;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.ParticlesRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.ducks.IEParticleManager;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.RenderStates;

@Mixin(ParticleEngine.class)
public class MixinParticleEngine implements IEParticleManager {
   @Shadow
   protected ClientLevel level;

   @Inject(method = "extract", at = @At("HEAD"), cancellable = true)
   private void onBeginExtractingParticles(ParticlesRenderState particlesRenderState, Frustum frustum, Camera camera, float partialTick, CallbackInfo ci) {
      if (PortalRendering.isRendering() && RenderStates.getRenderedPortalNum() > 4) {
         ci.cancel();
      }
   }

   @Override
   public void ip_setWorld(ClientLevel world_) {
      this.level = world_;
   }
}
