package qouteall.imm_ptl.core.compat.mixin.voxy;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import me.cortex.voxy.client.core.model.ColourDepthTextureData;
import me.cortex.voxy.common.util.MemoryBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

@Pseudo
@Mixin(targets = "me/cortex/voxy/client/core/model/MipGen", remap = false)
public class MixinVoxyMipGen {
   @Unique
   private static final Object ip_BAKE_LOCK = new Object();

   @WrapMethod(method = "putTextures", remap = false, require = 0)
   private static void ip_voxySerialiseTextureBake(boolean darkened, ColourDepthTextureData[] textures, MemoryBuffer into, Operation<Void> original) {
      synchronized (ip_BAKE_LOCK) {
         original.call(new Object[]{darkened, textures, into});
      }
   }
}
