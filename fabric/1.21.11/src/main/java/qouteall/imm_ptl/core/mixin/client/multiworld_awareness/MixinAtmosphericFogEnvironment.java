package qouteall.imm_ptl.core.mixin.client.multiworld_awareness;

import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.render.context_management.FogRendererContext;

@Mixin(value = AtmosphericFogEnvironment.class, priority = 1100)
public class MixinAtmosphericFogEnvironment {
   @Shadow
   private float rainFogMultiplier;
   @Unique
   @Nullable
   private static AtmosphericFogEnvironment ip_instance;

   @Inject(method = "<init>", at = @At("RETURN"))
   private void onInit(CallbackInfo ci) {
      ip_instance = (AtmosphericFogEnvironment)(Object)this;
   }

   static {
      FogRendererContext.copyContextFromObject = context -> {
         if (ip_instance != null) {
            ((MixinAtmosphericFogEnvironment)(Object)ip_instance).rainFogMultiplier = context.rainFogMultiplier;
         }
      };
      FogRendererContext.copyContextToObject = context -> {
         if (ip_instance != null) {
            context.rainFogMultiplier = ((MixinAtmosphericFogEnvironment)(Object)ip_instance).rainFogMultiplier;
         }
      };
      FogRendererContext.init();
   }
}
