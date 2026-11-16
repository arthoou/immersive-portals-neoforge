package qouteall.dimlib;

import java.util.HashSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.RegistryAccess.Frozen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.WorldData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qouteall.dimlib.ducks.IMappedRegistry;

public class DimensionImpl {
   public static final Logger LOGGER = LoggerFactory.getLogger(DimensionImpl.class);
   public static final HashSet<String> STABLE_NAMESPACES = new HashSet<>();
   public static boolean suppressExperimentalWarning = false;

   public static void directlyRegisterLevelStem(MinecraftServer server, Identifier dimensionId, LevelStem levelStem) {
      Frozen registryAccess = server.registryAccess();
      WorldData worldData = server.getWorldData();
      WorldOptions worldOptions = worldData.worldGenOptions();
      MappedRegistry<LevelStem> levelStems = (MappedRegistry<LevelStem>)registryAccess.<LevelStem>lookupOrThrow(Registries.LEVEL_STEM);
      if (!levelStems.containsKey(dimensionId)) {
         boolean oldIsFrozen = ((IMappedRegistry)levelStems).dimlib_getIsFrozen();
         ((IMappedRegistry)levelStems).dimlib_setIsFrozen(false);

         try {
            levelStems.register(ResourceKey.create(Registries.LEVEL_STEM, dimensionId), levelStem, RegistrationInfo.BUILT_IN);
         } finally {
            ((IMappedRegistry)levelStems).dimlib_setIsFrozen(oldIsFrozen);
         }
      } else {
         LOGGER.error("The dimension {} already exists", dimensionId, new Throwable());
      }
   }

   public static MappedRegistry<LevelStem> getDimensionRegistry(MinecraftServer server) {
      return (MappedRegistry<LevelStem>)server.registryAccess().<LevelStem>lookupOrThrow(Registries.LEVEL_STEM);
   }
}
