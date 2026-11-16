package qouteall.imm_ptl.peripheral.ducks;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.level.biome.FeatureSorter.StepFeatureData;

public interface IEChunkGenerator_AlternateDim {
   void ip_setFeaturesPerStep(Supplier<List<StepFeatureData>> var1);
}
