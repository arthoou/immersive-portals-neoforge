package qouteall.dimlib;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.RegistryAccess.Frozen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;

public record DimensionTemplate(ResourceKey<DimensionType> dimensionTypeId, DimensionTemplate.DimensionFactory dimensionFactory) {
   static final Map<String, DimensionTemplate> DIMENSION_TEMPLATES = new LinkedHashMap<>();
   public static final DimensionTemplate VOID_TEMPLATE = new DimensionTemplate(BuiltinDimensionTypes.OVERWORLD, (server, dimTypeHolder) -> {
      Frozen registryAccess = server.registryAccess();
      Registry<Biome> biomeRegistry = registryAccess.lookupOrThrow(Registries.BIOME);
      Reference<Biome> plainsHolder = biomeRegistry.getOrThrow(Biomes.PLAINS);
      FlatLevelGeneratorSettings flatChunkGeneratorConfig = new FlatLevelGeneratorSettings(Optional.of(HolderSet.direct()), plainsHolder, List.of());
      flatChunkGeneratorConfig.getLayersInfo().add(new FlatLayerInfo(1, Blocks.AIR));
      flatChunkGeneratorConfig.updateLayers();
      FlatLevelSource chunkGenerator = new FlatLevelSource(flatChunkGeneratorConfig);
      return new LevelStem(dimTypeHolder, chunkGenerator);
   });

   public static void registerDimensionTemplate(String name, DimensionTemplate dimensionTemplate) {
      DIMENSION_TEMPLATES.put(name, dimensionTemplate);
   }

   public LevelStem createLevelStem(MinecraftServer server) {
      Registry<DimensionType> dimensionTypes = server.registryAccess().lookupOrThrow(Registries.DIMENSION_TYPE);
      Reference<DimensionType> holder = dimensionTypes.getOrThrow(this.dimensionTypeId);
      return this.dimensionFactory.createLevelStem(server, holder);
   }

   public static void init() {
      registerDimensionTemplate("void", VOID_TEMPLATE);
   }

   public interface DimensionFactory {
      LevelStem createLevelStem(MinecraftServer var1, Holder<DimensionType> var2);
   }
}
