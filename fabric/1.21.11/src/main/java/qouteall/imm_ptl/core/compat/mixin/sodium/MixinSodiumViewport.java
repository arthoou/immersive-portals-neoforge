// Reconstructed from the published Apache-2.0 MC 1.21.11 port; see SOURCE-PROVENANCE.md.
package qouteall.imm_ptl.core.compat.mixin.sodium;

import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.render.viewport.frustum.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumInterface;

@Mixin(value = Viewport.class, remap = false)
public class MixinSodiumViewport {
   @Redirect(
      method = "isBoxVisible(III)Z",
      at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/viewport/frustum/Frustum;testSection(FFF)Z")
   )
   private boolean redirectTestSection(Frustum instance, float centerX, float centerY, float centerZ) {
      boolean inFrustum = instance.testSection(centerX, centerY, centerZ);
      if (inFrustum && SodiumInterface.frustumCuller != null) {
         float r = 9.125F;
         boolean canDetermineInvisible = SodiumInterface.frustumCuller
            .canDetermineInvisibleWithCameraCoord(centerX - r, centerY - r, centerZ - r, centerX + r, centerY + r, centerZ + r);
         return !canDetermineInvisible;
      } else {
         return inFrustum;
      }
   }
}
