package qouteall.imm_ptl.core.compat.mixin.euphoria;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com/euphoriapatches/euphoria_patcher/integration/iris/DimensionShaderRefresh", remap = false)
public class MixinEuphoriaDimensionShaderRefresh {
   @Unique
   private static final Logger ip_LOGGER = LogManager.getLogger("ImmersivePortals/Euphoria");
   @Unique
   private static boolean ip_reported;

   @Inject(method = "beforePreparePipeline", at = @At("HEAD"), cancellable = true, remap = false)
   private static void ip_onBeforePreparePipeline(CallbackInfo ci) {
      ci.cancel();
      if (!ip_reported) {
         ip_reported = true;
         ip_LOGGER.info(
            "Euphoria Patches' per-dimension pack swap is off while Immersive Portals renders other dimensions (it rebuilt every Iris pipeline at each portal view and crossing); the joined dimension's copy serves all"
         );
      }
   }
}
