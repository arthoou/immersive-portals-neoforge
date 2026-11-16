package qouteall.imm_ptl.peripheral.alternate_dimension;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.HolderSet.Named;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.synth.NormalNoise.NoiseParameters;
import org.jetbrains.annotations.NotNull;
import qouteall.imm_ptl.peripheral.ducks.IEChunkGenerator_AlternateDim;
import qouteall.imm_ptl.peripheral.mixin.common.alternate_dimension.IEChunkAccess_AlternateDim;
import qouteall.imm_ptl.peripheral.mixin.common.alternate_dimension.IENoiseRouterData;

public class NormalSkylandGenerator extends NoiseBasedChunkGenerator {
   public static final MapCodec<NormalSkylandGenerator> MAP_CODEC = RecordCodecBuilder.mapCodec(
      instance -> instance.group(
            RegistryOps.retrieveGetter(Registries.BIOME),
            RegistryOps.retrieveGetter(Registries.DENSITY_FUNCTION),
            RegistryOps.retrieveGetter(Registries.NOISE),
            RegistryOps.retrieveGetter(Registries.NOISE_SETTINGS),
            RegistryOps.retrieveGetter(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST),
            Codec.LONG.optionalFieldOf("seed", 0L).forGetter(g -> g.seed)
         )
         .apply(instance, NormalSkylandGenerator::create)
   );
   private RandomState delegatedRandomState;
   private final HolderGetter<Biome> biomeHolderGetter;
   private final HolderGetter<DensityFunction> densityFunctionHolderGetter;
   private final HolderGetter<NoiseParameters> noiseParametersHolderGetter;
   private final long seed;
   private final NoiseBasedChunkGenerator delegate;

   public NormalSkylandGenerator(
      BiomeSource biomeSource,
      Holder<NoiseGeneratorSettings> noiseGeneratorSettings,
      NoiseBasedChunkGenerator delegate,
      HolderGetter<Biome> biomeHolderGetter,
      HolderGetter<DensityFunction> densityFunctionHolderGetter,
      HolderGetter<NoiseParameters> noiseParametersHolderGetter,
      long seed
   ) {
      super(biomeSource, noiseGeneratorSettings);
      this.delegate = delegate;
      this.biomeHolderGetter = biomeHolderGetter;
      this.densityFunctionHolderGetter = densityFunctionHolderGetter;
      this.noiseParametersHolderGetter = noiseParametersHolderGetter;
      this.seed = seed;
      this.delegatedRandomState = RandomState.create(delegate.generatorSettings().value(), noiseParametersHolderGetter, seed);
   }

   public static NormalSkylandGenerator create(
      HolderGetter<Biome> biomeHolderGetter,
      HolderGetter<DensityFunction> densityFunctionHolderGetter,
      HolderGetter<NoiseParameters> noiseParametersHolderGetter,
      HolderGetter<NoiseGeneratorSettings> noiseGeneratorSettingsHolderGetter,
      HolderGetter<MultiNoiseBiomeSourceParameterList> biomeParamListLookup,
      long seed
   ) {
      Reference<MultiNoiseBiomeSourceParameterList> overworldBiomeParamList = biomeParamListLookup.getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
      MultiNoiseBiomeSource overworldBiomeSource = MultiNoiseBiomeSource.createFromPreset(overworldBiomeParamList);
      NoiseGeneratorSettings overworldNGS = noiseGeneratorSettingsHolderGetter.getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
      NoiseGeneratorSettings intrinsicSkylandNGS = noiseGeneratorSettingsHolderGetter.getOrThrow(NoiseGeneratorSettings.FLOATING_ISLANDS).value();
      NoiseGeneratorSettings usedSkylandNGS = new NoiseGeneratorSettings(
         intrinsicSkylandNGS.noiseSettings(),
         intrinsicSkylandNGS.defaultBlock(),
         intrinsicSkylandNGS.defaultFluid(),
         IENoiseRouterData.ip_noNewCaves(
            densityFunctionHolderGetter,
            noiseParametersHolderGetter,
            IENoiseRouterData.ip_slideEndLike(IENoiseRouterData.ip_getFunction(densityFunctionHolderGetter, IENoiseRouterData.get_BASE_3D_NOISE_END()), 0, 128)
         ),
         intrinsicSkylandNGS.surfaceRule(),
         intrinsicSkylandNGS.spawnTarget(),
         0,
         intrinsicSkylandNGS.disableMobGeneration(),
         intrinsicSkylandNGS.aquifersEnabled(),
         intrinsicSkylandNGS.oreVeinsEnabled(),
         intrinsicSkylandNGS.useLegacyRandomSource()
      );
      NoiseBasedChunkGenerator skylandGenerator = new NoiseBasedChunkGenerator(overworldBiomeSource, Holder.direct(usedSkylandNGS));
      NormalSkylandGenerator result = new NormalSkylandGenerator(
         overworldBiomeSource, Holder.direct(overworldNGS), skylandGenerator, biomeHolderGetter, densityFunctionHolderGetter, noiseParametersHolderGetter, seed
      );
      ((IEChunkGenerator_AlternateDim)result)
         .ip_setFeaturesPerStep(Suppliers.memoize(() -> FeatureSorter.buildFeaturesPerStep(List.copyOf(overworldBiomeSource.possibleBiomes()), holder -> {
            Biome biome = holder.value();
            BiomeGenerationSettings bgs = biome.getGenerationSettings();
            return bgs.features();
         }, true)));
      return result;
   }

   @NotNull
   @Override
   protected MapCodec<? extends ChunkGenerator> codec() {
      return MAP_CODEC;
   }

   @Override
   public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState pRandomState, StructureManager structureManager, ChunkAccess chunkAccess) {
      ((IEChunkAccess_AlternateDim)chunkAccess).ip_setNoiseChunk(null);
      return this.delegate.fillFromNoise(blender, this.delegatedRandomState, structureManager, chunkAccess).thenApply(c -> {
         ((IEChunkAccess_AlternateDim)c).ip_setNoiseChunk(null);
         return (ChunkAccess)c;
      });
   }

   @Override
   public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSetLookup, RandomState randomState, long seed) {
      HolderLookup<StructureSet> structureSetLookupDelegate = new HolderLookup<StructureSet>() {
         @Override
         public Stream<Reference<StructureSet>> listElements() {
            return structureSetLookup.listElements().filter(holder -> !holder.key().identifier().getPath().equals("mineshafts"));
         }

         @Override
         public Stream<Named<StructureSet>> listTags() {
            return structureSetLookup.listTags();
         }

         @Override
         public Optional<Reference<StructureSet>> get(ResourceKey<StructureSet> resourceKey) {
            return structureSetLookup.get(resourceKey);
         }

         @Override
         public Optional<Named<StructureSet>> get(TagKey<StructureSet> tagKey) {
            return structureSetLookup.get(tagKey);
         }
      };
      return super.createState(structureSetLookupDelegate, randomState, seed);
   }
}
