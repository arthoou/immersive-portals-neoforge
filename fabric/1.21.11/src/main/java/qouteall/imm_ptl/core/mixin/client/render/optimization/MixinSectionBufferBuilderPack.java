package qouteall.imm_ptl.core.mixin.client.render.optimization;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import qouteall.imm_ptl.core.IPGlobal;

@Mixin(SectionBufferBuilderPack.class)
public class MixinSectionBufferBuilderPack {
   @Redirect(
      method = "<init>",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;makeEnumMap(Ljava/lang/Class;Ljava/util/function/Function;)Ljava/util/Map;")
   )
   private Map<ChunkSectionLayer, ByteBufferBuilder> redirectMakeBuffers(
      Class<ChunkSectionLayer> layerClass, Function<ChunkSectionLayer, ByteBufferBuilder> original
   ) {
      return !IPGlobal.saveMemoryInBufferPack
         ? Util.makeEnumMap(layerClass, original)
         : Util.makeEnumMap(layerClass, layer -> new ByteBufferBuilder(Math.min(128, layer.bufferSize())));
   }
}
