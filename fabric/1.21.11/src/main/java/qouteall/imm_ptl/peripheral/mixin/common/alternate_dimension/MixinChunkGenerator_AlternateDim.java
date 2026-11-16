package qouteall.imm_ptl.peripheral.mixin.common.alternate_dimension;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.level.biome.FeatureSorter.StepFeatureData;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import qouteall.imm_ptl.peripheral.ducks.IEChunkGenerator_AlternateDim;

@Mixin(ChunkGenerator.class)
public abstract class MixinChunkGenerator_AlternateDim implements IEChunkGenerator_AlternateDim {
   @Shadow
   @Final
   @Mutable
   private Supplier<List<StepFeatureData>> featuresPerStep;

   @Override
   public void ip_setFeaturesPerStep(Supplier<List<StepFeatureData>> arg) {
      this.featuresPerStep = arg;
   }
}
