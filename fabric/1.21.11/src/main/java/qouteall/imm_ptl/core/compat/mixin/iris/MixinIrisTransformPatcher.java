package qouteall.imm_ptl.core.compat.mixin.iris;

import com.mojang.blaze3d.shaders.ShaderType;
import java.util.Map;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.pipeline.transform.TransformPatcher;
import net.irisshaders.iris.pipeline.transform.parameter.Parameters;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.render.ShaderCodeTransformation;

@Pseudo
@Mixin(value = TransformPatcher.class, remap = false)
public class MixinIrisTransformPatcher {
   @Inject(method = "transformInternal", at = @At("RETURN"), cancellable = true)
   private static void onTransformInternal(
      String name, Map<PatchShaderType, String> inputs, Parameters parameters, CallbackInfoReturnable<Map<PatchShaderType, String>> cir
   ) {
      Map<PatchShaderType, String> map = cir.getReturnValue();
      String code = map.get(PatchShaderType.VERTEX);
      if (code != null) {
         String transformed = ShaderCodeTransformation.transform(ShaderType.VERTEX, "iris_" + name, code);
         map.put(PatchShaderType.VERTEX, transformed);
      }
   }
}
