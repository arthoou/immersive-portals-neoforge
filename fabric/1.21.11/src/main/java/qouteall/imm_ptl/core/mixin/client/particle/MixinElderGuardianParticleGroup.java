package qouteall.imm_ptl.core.mixin.client.particle;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Queue;
import java.util.stream.Stream;
import net.minecraft.client.particle.ElderGuardianParticle;
import net.minecraft.client.particle.ElderGuardianParticleGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import qouteall.imm_ptl.core.render.context_management.RenderStates;

@Mixin(ElderGuardianParticleGroup.class)
public class MixinElderGuardianParticleGroup {
   @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Ljava/util/Queue;stream()Ljava/util/stream/Stream;", remap = false))
   private Stream<ElderGuardianParticle> wrapParticleStream(Queue<ElderGuardianParticle> particles, Operation<Stream<ElderGuardianParticle>> original) {
      return original.call(particles).filter(RenderStates::shouldRenderParticle);
   }
}
