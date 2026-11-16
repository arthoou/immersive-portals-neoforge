package qouteall.imm_ptl.core.mixin.client.render;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.render.MyRenderHelper;

@Mixin(FogRenderer.class)
public class MixinFogRenderer_Distance {
   @Inject(
      method = "setupFog",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/fog/FogRenderer;updateBuffer(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V")
   )
   private void onBeforeWritingFogBuffer(CallbackInfoReturnable<Vector4f> cir, @Local FogData fogData) {
      fogData.environmentalStart = MyRenderHelper.transformFogDistance(fogData.environmentalStart);
      fogData.environmentalEnd = MyRenderHelper.transformFogDistance(fogData.environmentalEnd);
      fogData.renderDistanceStart = MyRenderHelper.transformFogDistance(fogData.renderDistanceStart);
      fogData.renderDistanceEnd = MyRenderHelper.transformFogDistance(fogData.renderDistanceEnd);
      fogData.skyEnd = MyRenderHelper.transformFogDistance(fogData.skyEnd);
      fogData.cloudEnd = MyRenderHelper.transformFogDistance(fogData.cloudEnd);
   }
}
