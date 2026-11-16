package qouteall.dimlib;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientCommonPacketListener;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.border.WorldBorder.Settings;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import qouteall.dimlib.api.DimensionAPI;
import qouteall.dimlib.ducks.IMappedRegistry;
import qouteall.dimlib.ducks.IMinecraftServer;
import qouteall.q_misc_util.platform.PlatformNetworking;

public class DynamicDimensionsImpl {
   private static final Logger LOGGER = LogManager.getLogger();
   public static boolean isRemovingDimension = false;

   public static void init() {
   }

   public static void addDimensionDynamically(MinecraftServer server, Identifier dimensionId, LevelStem levelStem) {
      ResourceKey<Level> dimensionResourceKey = ResourceKey.create(Registries.DIMENSION, dimensionId);
      Validate.isTrue(server.isSameThread(), "this should be called in server main thread");
      Validate.isTrue(server.isRunning(), "Server is not running");
      if (server.getLevel(dimensionResourceKey) != null) {
         throw new RuntimeException("Dimension " + dimensionId + " already exists.");
      }

      ServerLevel overworld = server.getLevel(Level.OVERWORLD);
      Validate.notNull(overworld, "Overworld is null");
      WorldBorder worldBorder = overworld.getWorldBorder();
      Validate.notNull(worldBorder, "Overworld world border is null");
      WorldData worldData = server.getWorldData();
      ServerLevelData serverLevelData = worldData.overworldData();
      long seed = worldData.worldGenOptions().seed();
      long obfuscatedSeed = BiomeManager.obfuscateSeed(seed);
      DerivedLevelData derivedLevelData = new DerivedLevelData(worldData, serverLevelData);
      ServerLevel newWorld = new ServerLevel(
         server,
         ((IMinecraftServer)server).dimlib_getExecutor(),
         ((IMinecraftServer)server).dimlib_getStorageSource(),
         derivedLevelData,
         dimensionResourceKey,
         levelStem,
         false,
         obfuscatedSeed,
         ImmutableList.of(),
         false,
         overworld.getRandomSequences()
      );
      ((IMinecraftServer)server).dimlib_addDimensionToWorldMap(dimensionResourceKey, newWorld);
      Registry<LevelStem> levelStemRegistry = server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM);
      ((IMappedRegistry)levelStemRegistry).dimlib_setIsFrozen(false);
      ((MappedRegistry)levelStemRegistry).register(ResourceKey.create(Registries.LEVEL_STEM, dimensionId), levelStem, RegistrationInfo.BUILT_IN);
      ((IMappedRegistry)levelStemRegistry).dimlib_setIsFrozen(true);
      Optional<Settings> legacyBorderSettings = serverLevelData.getLegacyWorldBorderSettings();
      if (legacyBorderSettings.isPresent()) {
         Settings legacySettings = legacyBorderSettings.get();
         DimensionDataStorage dataStorage = newWorld.getDataStorage();
         if (dataStorage.get(WorldBorder.TYPE) == null) {
            double coordinateScale = newWorld.dimensionType().coordinateScale();
            Settings scaledSettings = new Settings(
               legacySettings.centerX() / coordinateScale,
               legacySettings.centerZ() / coordinateScale,
               legacySettings.damagePerBlock(),
               legacySettings.safeZone(),
               legacySettings.warningBlocks(),
               legacySettings.warningTime(),
               legacySettings.size(),
               legacySettings.lerpTime(),
               legacySettings.lerpTarget()
            );
            WorldBorder newBorder = new WorldBorder(scaledSettings);
            newBorder.applyInitialSettings(newWorld.getGameTime());
            dataStorage.set(WorldBorder.TYPE, newBorder);
         }
      }

      newWorld.getWorldBorder().setAbsoluteMaxSize(server.getAbsoluteMaxWorldSize());
      server.getPlayerList().addWorldborderListener(newWorld);
      LOGGER.info("Added Dimension {}", dimensionId);
      Packet<ClientCommonPacketListener> dimSyncPacket = PlatformNetworking.get().createS2CPacket(DimLibNetworking.DimSyncPacket.createPacket(server));

      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
         player.connection.send(dimSyncPacket);
      }

      DimensionAPI.SERVER_DIMENSION_DYNAMIC_UPDATE_EVENT.invoker().run(server, server.levelKeys());
   }

   public static void removeDimensionDynamically(ServerLevel world) {
      MinecraftServer server = world.getServer();
      Validate.isTrue(server.isSameThread());
      ResourceKey<Level> dimension = world.dimension();
      if (dimension != Level.OVERWORLD && dimension != Level.NETHER && dimension != Level.END) {
         Validate.isTrue(server.isRunning(), "Server is not running");
         LOGGER.info("Started Removing Dimension {}", dimension.identifier());
         ((IMinecraftServer)server).dimlib_addTask(() -> {
            DimensionAPI.SERVER_PRE_REMOVE_DIMENSION_EVENT.invoker().accept(world);
            evacuatePlayersFromDimension(world);
            long startTime = System.nanoTime();
            long lastLogTime = System.nanoTime();
            isRemovingDimension = true;
            ((IMinecraftServer)server).dimlib_removeDimensionFromWorldMap(dimension);

            try {
               while (world.getChunkSource().chunkMap.hasWork()) {
                  world.getChunkSource().deactivateTicketsOnClosing();
                  world.getChunkSource().tick(() -> true, false);
                  world.getChunkSource().pollTask();
                  server.pollTask();
                  if (System.nanoTime() - lastLogTime > DimLibUtil.secondToNano(1.0)) {
                     lastLogTime = System.nanoTime();
                     LOGGER.info("waiting for chunk tasks to finish");
                  }

                  if (System.nanoTime() - startTime > DimLibUtil.secondToNano(15.0)) {
                     LOGGER.error("Waited too long for chunk tasks");
                     break;
                  }

                  ((IMinecraftServer)server).dimlib_waitUntilNextTick();
               }
            } catch (Throwable e) {
               LOGGER.error("Error when waiting for chunk tasks", e);
            }

            isRemovingDimension = false;
            LOGGER.info("Finished chunk tasks in {} seconds", DimLibUtil.nanoToSecond(System.nanoTime() - startTime));
            LOGGER.info("Chunk num: {}     Has entities: {}", world.getChunkSource().chunkMap.size(), world.getAllEntities().iterator().hasNext());
            server.saveAllChunks(false, true, false);

            try {
               world.close();
            } catch (IOException e) {
               LOGGER.error("Error when closing world", e);
            }

            Registry<LevelStem> levelStemRegistry = server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM);
            ((IMappedRegistry)levelStemRegistry).dimlib_forceRemove(dimension.identifier());
            LOGGER.info("Removed Dimension {}", dimension.identifier());
            Packet<ClientCommonPacketListener> dimSyncPacket = PlatformNetworking.get().createS2CPacket(DimLibNetworking.DimSyncPacket.createPacket(server));

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
               player.connection.send(dimSyncPacket);
            }

            DimensionAPI.SERVER_DIMENSION_DYNAMIC_UPDATE_EVENT.invoker().run(server, server.levelKeys());
         });
      } else {
         throw new RuntimeException("Cannot remove vanilla dimension");
      }
   }

   private static void evacuatePlayersFromDimension(ServerLevel world) {
      MinecraftServer server = world.getServer();
      ServerLevel overworld = server.getLevel(Level.OVERWORLD);
      Validate.notNull(overworld, "Overworld is null");
      List<ServerPlayer> players = world.getPlayers(p -> true);
      BlockPos sharedSpawnPos = overworld.getRespawnData().pos();

      for (ServerPlayer player : players) {
         player.teleportTo(overworld, sharedSpawnPos.getX(), sharedSpawnPos.getY(), sharedSpawnPos.getZ(), Set.of(), 0.0F, 0.0F, true);
         player.sendSystemMessage(Component.literal("Teleported to spawn pos because dimension %s had been removed".formatted(world.dimension().identifier())));
      }
   }
}
