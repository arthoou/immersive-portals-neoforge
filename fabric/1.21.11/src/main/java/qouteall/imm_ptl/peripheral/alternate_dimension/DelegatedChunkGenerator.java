package qouteall.imm_ptl.peripheral.alternate_dimension;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MobSpawnSettings.SpawnerData;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;

@IPVanillaCopy
public abstract class DelegatedChunkGenerator extends ChunkGenerator {
   protected ChunkGenerator delegate;
   protected BiomeSource biomeSource_;

   public DelegatedChunkGenerator(BiomeSource biomeSource, ChunkGenerator delegate) {
      super(biomeSource);
      this.delegate = delegate;
      this.biomeSource_ = biomeSource;
   }

   @Override
   public void applyCarvers(
      WorldGenRegion worldGenRegion, long l, RandomState randomState, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunkAccess
   ) {
      this.delegate.applyCarvers(worldGenRegion, l, randomState, biomeManager, structureManager, chunkAccess);
   }

   @Override
   public void buildSurface(WorldGenRegion worldGenRegion, StructureManager structureManager, RandomState randomState, ChunkAccess chunkAccess) {
      this.delegate.buildSurface(worldGenRegion, structureManager, randomState, chunkAccess);
   }

   @Override
   public void spawnOriginalMobs(WorldGenRegion region) {
      this.delegate.spawnOriginalMobs(region);
   }

   @Override
   public int getGenDepth() {
      return this.delegate.getGenDepth();
   }

   @NotNull
   @Override
   public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunkAccess) {
      return this.delegate.fillFromNoise(blender, randomState, structureManager, chunkAccess);
   }

   @Override
   public int getSeaLevel() {
      return this.delegate.getSeaLevel();
   }

   @Override
   public int getMinY() {
      return this.delegate.getMinY();
   }

   @Override
   public int getBaseHeight(int i, int j, Types types, LevelHeightAccessor levelHeightAccessor, RandomState randomState) {
      return this.delegate.getBaseHeight(i, j, types, levelHeightAccessor, randomState);
   }

   @Override
   public NoiseColumn getBaseColumn(int i, int j, LevelHeightAccessor levelHeightAccessor, RandomState randomState) {
      return this.delegate.getBaseColumn(i, j, levelHeightAccessor, randomState);
   }

   @Override
   public void addDebugScreenInfo(List<String> list, RandomState randomState, BlockPos blockPos) {
      this.delegate.addDebugScreenInfo(list, randomState, blockPos);
   }

   @Override
   public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> holderLookup, RandomState randomState, long l) {
      return super.createState(holderLookup, randomState, l);
   }

   @Override
   public CompletableFuture<ChunkAccess> createBiomes(RandomState randomState, Blender blender, StructureManager structureManager, ChunkAccess chunkAccess) {
      return super.createBiomes(randomState, blender, structureManager, chunkAccess);
   }

   @Override
   public void createStructures(
      RegistryAccess registryAccess,
      ChunkGeneratorStructureState chunkGeneratorStructureState,
      StructureManager structureManager,
      ChunkAccess chunkAccess,
      StructureTemplateManager structureTemplateManager,
      ResourceKey<Level> dimension
   ) {
      super.createStructures(registryAccess, chunkGeneratorStructureState, structureManager, chunkAccess, structureTemplateManager, dimension);
   }

   @Override
   public void applyBiomeDecoration(WorldGenLevel worldGenLevel, ChunkAccess chunkAccess, StructureManager structureManager) {
      this.delegate.applyBiomeDecoration(worldGenLevel, chunkAccess, structureManager);
   }

   @Override
   public Optional<ResourceKey<MapCodec<? extends ChunkGenerator>>> getTypeNameForDataFixer() {
      return this.delegate.getTypeNameForDataFixer();
   }

   @Nullable
   @Override
   public Pair<BlockPos, Holder<Structure>> findNearestMapStructure(
      ServerLevel serverLevel, HolderSet<Structure> holderSet, BlockPos blockPos, int i, boolean bl
   ) {
      return this.delegate.findNearestMapStructure(serverLevel, holderSet, blockPos, i, bl);
   }

   @Override
   public int getSpawnHeight(LevelHeightAccessor level) {
      return this.delegate.getSpawnHeight(level);
   }

   @Override
   public BiomeSource getBiomeSource() {
      return this.delegate.getBiomeSource();
   }

   @Override
   public WeightedList<SpawnerData> getMobsAt(Holder<Biome> holder, StructureManager structureManager, MobCategory mobCategory, BlockPos blockPos) {
      return this.delegate.getMobsAt(holder, structureManager, mobCategory, blockPos);
   }

   @Override
   public void createReferences(WorldGenLevel worldGenLevel, StructureManager structureManager, ChunkAccess chunkAccess) {
      this.delegate.createReferences(worldGenLevel, structureManager, chunkAccess);
   }

   @Override
   public int getFirstFreeHeight(int i, int j, Types types, LevelHeightAccessor levelHeightAccessor, RandomState randomState) {
      return this.delegate.getFirstFreeHeight(i, j, types, levelHeightAccessor, randomState);
   }

   @Override
   public int getFirstOccupiedHeight(int i, int j, Types types, LevelHeightAccessor levelHeightAccessor, RandomState randomState) {
      return this.delegate.getFirstOccupiedHeight(i, j, types, levelHeightAccessor, randomState);
   }

   @Override
   public BiomeGenerationSettings getBiomeGenerationSettings(Holder<Biome> holder) {
      return this.delegate.getBiomeGenerationSettings(holder);
   }
}
