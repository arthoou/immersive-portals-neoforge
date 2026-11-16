package qouteall.dimlib.api;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import qouteall.dimlib.ClientDimensionInfo;
import qouteall.dimlib.DimensionImpl;
import qouteall.dimlib.DimensionTemplate;
import qouteall.dimlib.DynamicDimensionsImpl;
import qouteall.dimlib.ducks.IMinecraftServer;
import qouteall.q_misc_util.platform.event.Event;
import qouteall.q_misc_util.platform.event.EventFactory;

public class DimensionAPI {
   private static final Logger LOGGER = LogManager.getLogger();
   public static final Event<DimensionAPI.ServerDimensionsLoadCallback> SERVER_DIMENSIONS_LOAD_EVENT = EventFactory.createArrayBacked(
      DimensionAPI.ServerDimensionsLoadCallback.class, listeners -> server -> {
         for (DimensionAPI.ServerDimensionsLoadCallback listener : listeners) {
            try {
               listener.run(server);
            } catch (Exception e) {
               LOGGER.error("Error during server dimensions load event", e);
            }
         }
      }
   );
   public static final Event<DimensionAPI.ServerDynamicUpdateListener> SERVER_DIMENSION_DYNAMIC_UPDATE_EVENT = EventFactory.createArrayBacked(
      DimensionAPI.ServerDynamicUpdateListener.class, arr -> (server, dims) -> {
         for (DimensionAPI.ServerDynamicUpdateListener listener : arr) {
            try {
               listener.run(server, dims);
            } catch (Exception e) {
               LOGGER.error("Error during dimension update event", e);
            }
         }
      }
   );
   public static final Event<DimensionAPI.ClientDynamicUpdateListener> CLIENT_DIMENSION_UPDATE_EVENT = EventFactory.createArrayBacked(
      DimensionAPI.ClientDynamicUpdateListener.class, arr -> set -> {
         for (DimensionAPI.ClientDynamicUpdateListener listener : arr) {
            try {
               listener.run(set);
            } catch (Exception e) {
               LOGGER.error("Error during dimension update event", e);
            }
         }
      }
   );
   public static final Event<DimensionAPI.PreRemoveDimensionCallback> SERVER_PRE_REMOVE_DIMENSION_EVENT = EventFactory.createArrayBacked(
      DimensionAPI.PreRemoveDimensionCallback.class, listeners -> world -> {
         for (DimensionAPI.PreRemoveDimensionCallback listener : listeners) {
            try {
               listener.accept(world);
            } catch (Exception e) {
               LOGGER.error("Error during before removing dimension event", e);
            }
         }
      }
   );

   public static void addDimension(MinecraftServer server, Identifier dimensionId, LevelStem levelStem) {
      if (((IMinecraftServer)server).dimlib_getIsFinishedCreatingWorlds()) {
         addDimensionDynamically(server, dimensionId, levelStem);
      } else if (((IMinecraftServer)server).dimlib_getCanDirectlyRegisterDimensions()) {
         DimensionImpl.directlyRegisterLevelStem(server, dimensionId, levelStem);
      } else {
         LOGGER.error("Cannot add dimension at this time {}", dimensionId, new Throwable());
      }
   }

   public static boolean dimensionExistsInRegistry(MinecraftServer server, Identifier dimensionId) {
      return DimensionImpl.getDimensionRegistry(server).containsKey(dimensionId);
   }

   public static void addDimensionIfNotExists(MinecraftServer server, Identifier dimensionId, Supplier<LevelStem> levelStem) {
      if (!dimensionExistsInRegistry(server, dimensionId)) {
         addDimension(server, dimensionId, levelStem.get());
      }
   }

   public static void addDimensionDynamically(MinecraftServer server, Identifier dimensionId, LevelStem levelStem) {
      Validate.isTrue(server.isRunning(), "The server is not running");
      DynamicDimensionsImpl.addDimensionDynamically(server, dimensionId, levelStem);
   }

   public static void removeDimensionDynamically(ServerLevel world) {
      if (!world.getServer().isRunning()) {
         LOGGER.error("Cannot remove dimension at this time {}", world, new Throwable());
      } else {
         DynamicDimensionsImpl.removeDimensionDynamically(world);
      }
   }

   public static boolean isDimensionAlive(ServerLevel world) {
      return world.getServer().getLevel(world.dimension()) == world;
   }

   public static void suppressExperimentalWarning() {
      DimensionImpl.suppressExperimentalWarning = true;
   }

   public static void suppressExperimentalWarningForNamespace(String namespace) {
      DimensionImpl.STABLE_NAMESPACES.add(namespace);
   }

   @Environment(EnvType.CLIENT)
   public static Set<ResourceKey<Level>> getClientDimensionIds() {
      return ClientDimensionInfo.getDimensionIds();
   }

   @Environment(EnvType.CLIENT)
   public static Map<ResourceKey<Level>, ResourceKey<DimensionType>> getClientDimensionIdToTypeMap() {
      return ClientDimensionInfo.getDimensionIdToType();
   }

   public static void registerDimensionTemplate(String name, DimensionTemplate dimensionTemplate) {
      DimensionTemplate.registerDimensionTemplate(name, dimensionTemplate);
   }

   public interface ClientDynamicUpdateListener {
      void run(Set<ResourceKey<Level>> var1);
   }

   public interface PreRemoveDimensionCallback {
      void accept(ServerLevel var1);
   }

   public interface ServerDimensionsLoadCallback {
      void run(MinecraftServer var1);
   }

   public interface ServerDynamicUpdateListener {
      void run(MinecraftServer var1, Set<ResourceKey<Level>> var2);
   }
}
