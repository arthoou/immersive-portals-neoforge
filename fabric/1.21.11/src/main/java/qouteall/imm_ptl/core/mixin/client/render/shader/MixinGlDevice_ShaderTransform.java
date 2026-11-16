package qouteall.imm_ptl.core.mixin.client.render.shader;

import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import qouteall.imm_ptl.core.render.ShaderCodeTransformation;

@Mixin(GlDevice.class)
public class MixinGlDevice_ShaderTransform {
   @Redirect(
      method = "compileShader",
      at = @At(
         value = "INVOKE",
         target = "Lcom/mojang/blaze3d/shaders/ShaderSource;get(Lnet/minecraft/resources/Identifier;Lcom/mojang/blaze3d/shaders/ShaderType;)Ljava/lang/String;"
      )
   )
   private String redirectGetShaderSource(ShaderSource shaderSource, Identifier id, ShaderType type) {
      String shaderCode = shaderSource.get(id, type);
      return shaderCode == null ? null : ShaderCodeTransformation.transform(type, ShaderCodeTransformation.shaderIdToName(id), shaderCode);
   }
}
