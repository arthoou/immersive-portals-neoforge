package qouteall.imm_ptl.core.mixin.client.particle;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Queue;
import java.util.stream.Stream;
import net.minecraft.client.particle.ItemPickupParticle;
import net.minecraft.client.particle.ItemPickupParticleGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import qouteall.imm_ptl.core.render.context_management.RenderStates;

@Mixin(ItemPickupParticleGroup.class)
public class MixinItemPickupParticleGroup {
   @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Ljava/util/Queue;stream()Ljava/util/stream/Stream;", remap = false))
   private Stream<ItemPickupParticle> wrapParticleStream(Queue<ItemPickupParticle> particles, Operation<Stream<ItemPickupParticle>> original) {
      return original.call(particles).filter(RenderStates::shouldRenderParticle);
   }
}
