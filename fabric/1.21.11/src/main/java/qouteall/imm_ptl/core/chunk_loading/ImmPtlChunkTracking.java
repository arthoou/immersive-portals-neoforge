package qouteall.imm_ptl.core.chunk_loading;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.dimlib.api.DimensionAPI;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.ducks.IEChunkMap;
import qouteall.imm_ptl.core.mixin.common.chunk_sync.IEServerCommonPacketListenerImpl;
import qouteall.imm_ptl.core.network.PacketRedirection;
import qouteall.q_misc_util.my_util.IntBox;
import qouteall.q_misc_util.platform.Platform;

public class ImmPtlChunkTracking {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final int updateInterval = 13;
   public static final int defaultDelayUnloadGenerations = 4;
   private static final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<Object2ObjectOpenHashMap<ServerPlayer, ImmPtlChunkTracking.PlayerWatchRecord>>> chunkWatchRecords = new Object2ObjectOpenHashMap<>();
   private static final ArrayList<ChunkLoader> additionalChunkLoaders = new ArrayList<>();
   private static final Object2ObjectOpenHashMap<ServerPlayer, PlayerChunkLoading> playerInfoMap = new Object2ObjectOpenHashMap<>();
   private static int generationCounter = 0;

   public static void init() {
      Platform.get().onServerTickEnd(ImmPtlChunkTracking::tick);
      IPGlobal.SERVER_CLEANUP_EVENT.register(ImmPtlChunkTracking::cleanup);
      DimensionAPI.SERVER_PRE_REMOVE_DIMENSION_EVENT.register(ImmPtlChunkTracking::onDimensionRemove);
   }

   public static void onChunkProvidedDeferred(LevelChunk chunk) {
   }

   public static void removePlayerFromChunkTrackersAndEntityTrackers(ServerPlayer oldPlayer) {
      for (ServerLevel world : oldPlayer.server.getAllLevels()) {
         ServerChunkCache chunkManager = world.getChunkSource();
         IEChunkMap storage = (IEChunkMap)chunkManager.chunkMap;
         storage.ip_onPlayerUnload(oldPlayer);
      }

      forceRemovePlayer(oldPlayer);
   }

   public static void onDimensionRemove(ServerLevel world) {
      ServerChunkCache chunkManager = world.getChunkSource();
      IEChunkMap storage = (IEChunkMap)chunkManager.chunkMap;
      storage.ip_onDimensionRemove();
      forceRemoveDimension(world);
   }

   private static Long2ObjectOpenHashMap<Object2ObjectOpenHashMap<ServerPlayer, ImmPtlChunkTracking.PlayerWatchRecord>> getDimChunkWatchRecords(
      ResourceKey<Level> dimension
   ) {
      return chunkWatchRecords.computeIfAbsent(dimension, k -> new Long2ObjectOpenHashMap<>());
   }

   public static PlayerChunkLoading getPlayerInfo(ServerPlayer player) {
      return playerInfoMap.computeIfAbsent(
         player, (ServerPlayer p) -> new PlayerChunkLoading(((IEServerCommonPacketListenerImpl)p.connection).ip_getConnection().isMemoryConnection())
      );
   }

   public static void immediatelyUpdateForPlayer(ServerPlayer player) {
      updateForPlayer(player);
      getPlayerInfo(player).doChunkSending(player);
      EntitySync.update(player.server);
   }

   public static void updateForPlayer(ServerPlayer player) {
      PlayerChunkLoading playerInfo = getPlayerInfo(player);
      playerInfo.visibleDimensions.clear();
      int lastLoadedChunks = playerInfo.loadedChunks;
      playerInfo.loadedChunks = 0;
      ObjectOpenHashSet<ChunkLoader> chunkLoaders = new ObjectOpenHashSet<>();
      ChunkVisibility.foreachBaseChunkLoaders(player, chunkLoaders::add);
      chunkLoaders.addAll(playerInfo.additionalChunkLoaders);
      playerInfo.currentChunkLoaders.clear();
      playerInfo.currentChunkLoaders.addAll(chunkLoaders);
      MinecraftServer server = player.server;

      for (ChunkLoader chunkLoader : chunkLoaders) {
         ResourceKey<Level> dimension = chunkLoader.dimension();
         Long2ObjectOpenHashMap<Object2ObjectOpenHashMap<ServerPlayer, ImmPtlChunkTracking.PlayerWatchRecord>> chunkRecordMap = getDimChunkWatchRecords(
            dimension
         );
         ServerLevel world = server.getLevel(dimension);
         if (world == null) {
            LOGGER.warn("Dimension not loaded {} in chunk loader {}", dimension, chunkLoader);
            return;
         }

         playerInfo.visibleDimensions.add(dimension);
         ImmPtlChunkTickets ticketInfo = ImmPtlChunkTickets.get(world);
         chunkLoader.foreachChunkPos(
            (dim, x, z, distanceToSource) -> {
               long chunkPos = ChunkPos.asLong(x, z);
               Object2ObjectOpenHashMap<ServerPlayer, ImmPtlChunkTracking.PlayerWatchRecord> records = chunkRecordMap.computeIfAbsent(
                  chunkPos, k -> new Object2ObjectOpenHashMap<>()
               );
               ticketInfo.markForLoading(chunkPos, distanceToSource, generationCounter);
               records.compute(
                  player,
                  (k, record) -> {
                     boolean isBoundary = distanceToSource == chunkLoader.radius();
                     if (record == null) {
                        ImmPtlChunkTracking.PlayerWatchRecord newRecord = new ImmPtlChunkTracking.PlayerWatchRecord(
                           player, dimension, chunkPos, generationCounter, distanceToSource, false, isBoundary
                        );
                        playerInfo.markPendingLoading(newRecord);
                        playerInfo.loadedChunks++;
                        return newRecord;
                     }

                     int oldDistance = record.distanceToSource;
                     if (record.lastWatchGeneration == generationCounter) {
                        if (distanceToSource < oldDistance) {
                           record.distanceToSource = distanceToSource;
                           playerInfo.markPendingLoading(record);
                        }

                        record.isBoundary = record.isBoundary && isBoundary;
                     } else {
                        playerInfo.loadedChunks++;
                        if (distanceToSource < oldDistance) {
                           playerInfo.markPendingLoading(record);
                        }

                        record.distanceToSource = distanceToSource;
                        record.lastWatchGeneration = generationCounter;
                        record.isBoundary = isBoundary;
                     }

                     return (ImmPtlChunkTracking.PlayerWatchRecord)record;
                  }
               );
            }
         );
      }
   }

   private static void purge(MinecraftServer server, Object2ObjectOpenHashMap<ResourceKey<Level>, LongOpenHashSet> additionalLoadedChunks) {
      Set<ImmPtlChunkTracking.PlayerWatchRecord> releasedForMemory = IPGlobal.keepChunksLoadedForever && KeepChunksMemoryValve.shouldRelease(server)
         ? selectFarthestUncoveredRecords(256)
         : Collections.emptySet();
      chunkWatchRecords.forEach(
         (dimensionx, chunkRecords) -> chunkRecords.long2ObjectEntrySet()
            .removeIf(
               entry -> {
                  long chunkPosLong = entry.getLongKey();
                  Object2ObjectOpenHashMap<ServerPlayer, ImmPtlChunkTracking.PlayerWatchRecord> dimChunkWatchRecords = entry.getValue();
                  dimChunkWatchRecords.entrySet()
                     .removeIf(
                        e -> {
                           ServerPlayer player = e.getKey();
                           if (player.isRemoved()) {
                              return true;
                           }

                           ImmPtlChunkTracking.PlayerWatchRecord record = e.getValue();
                           boolean shouldRemove;
                           if (IPGlobal.keepChunksLoadedForever) {
                              shouldRemove = releasedForMemory.contains(record);
                           } else {
                              int delayUnloadGenerations = getDelayUnloadGenerationForPlayer(player);
                              shouldRemove = generationCounter - record.lastWatchGeneration > delayUnloadGenerations;
                           }

                           if (shouldRemove) {
                              if (record.isLoadedToPlayer) {
                                 player.connection
                                    .send(
                                       PacketRedirection.createRedirectedMessage(
                                          player.level().getServer(), record.dimension, new ClientboundForgetLevelChunkPacket(new ChunkPos(record.chunkPos))
                                       )
                                    );
                              }

                              record.isValid = false;
                           }

                           return shouldRemove;
                        }
                     );
                  return dimChunkWatchRecords.isEmpty();
               }
            )
      );
      playerInfoMap.entrySet().removeIf(e -> e.getKey().isRemoved());

      for (ServerLevel world : server.getAllLevels()) {
         ResourceKey<Level> dimension = world.dimension();
         LongOpenHashSet additional = additionalLoadedChunks.get(dimension);
         Long2ObjectOpenHashMap<Object2ObjectOpenHashMap<ServerPlayer, ImmPtlChunkTracking.PlayerWatchRecord>> watchRecs = chunkWatchRecords.get(dimension);
         ImmPtlChunkTickets dimTicketManager = ImmPtlChunkTickets.get(world);
         dimTicketManager.purge(
            world, chunkPos -> watchRecs != null && watchRecs.containsKey(chunkPos) ? true : additional != null && additional.contains(chunkPos)
         );
      }
   }

   private static int getDelayUnloadGenerationForPlayer(ServerPlayer player) {
      PlayerChunkLoading playerInfo = getPlayerInfo(player);
      if (playerInfo == null) {
         return 4;
      } else {
         int loadedChunks = playerInfo.loadedChunks;
         if (loadedChunks > 2000) {
            return 1;
         } else {
            return loadedChunks > 1200 ? 2 : 4;
         }
      }
   }

   private static Set<ImmPtlChunkTracking.PlayerWatchRecord> selectFarthestUncoveredRecords(int batch) {
      record Candidate(ImmPtlChunkTracking.PlayerWatchRecord record, int distance) {
      }

      List<Candidate> candidates = new ArrayList<>();
      chunkWatchRecords.forEach((dimension, chunkRecords) -> {
         for (Entry<Object2ObjectOpenHashMap<ServerPlayer, ImmPtlChunkTracking.PlayerWatchRecord>> entry : chunkRecords.long2ObjectEntrySet()) {
            int x = ChunkPos.getX(entry.getLongKey());
            int z = ChunkPos.getZ(entry.getLongKey());

            for (ImmPtlChunkTracking.PlayerWatchRecord record : entry.getValue().values()) {
               if (record.lastWatchGeneration != generationCounter && !record.player.isRemoved()) {
                  candidates.add(new Candidate(record, distanceBeyondLoaders(record.player, (ResourceKey<Level>)dimension, x, z)));
               }
            }
         }
      });
      candidates.sort(Comparator.comparingInt(Candidate::distance).reversed());
      Set<ImmPtlChunkTracking.PlayerWatchRecord> picked = Collections.newSetFromMap(new IdentityHashMap<>());

      for (int i = 0; i < Math.min(batch, candidates.size()); i++) {
         picked.add(candidates.get(i).record());
      }

      KeepChunksMemoryValve.onReleased(picked.size());
      return picked;
   }

   private static int distanceBeyondLoaders(ServerPlayer player, ResourceKey<Level> dimension, int x, int z) {
      PlayerChunkLoading info = playerInfoMap.get(player);
      int nearest = Integer.MAX_VALUE;
      if (info != null) {
         for (ChunkLoader loader : info.currentChunkLoaders) {
            if (loader.dimension() == dimension) {
               int beyond = Math.max(Math.abs(x - loader.x()), Math.abs(z - loader.z())) - loader.radius();
               nearest = Math.min(nearest, Math.max(beyond, 0));
            }
         }
      }

      return nearest;
   }

   private static Object2ObjectOpenHashMap<ResourceKey<Level>, LongOpenHashSet> refreshAdditionalChunkLoaders(MinecraftServer server) {
      Object2ObjectOpenHashMap<ResourceKey<Level>, LongOpenHashSet> additionalLoadedChunks = new Object2ObjectOpenHashMap<>();
      additionalChunkLoaders.removeIf(chunkLoader -> {
         ResourceKey<Level> dimension = chunkLoader.dimension();
         ServerLevel world = server.getLevel(dimension);
         if (world == null) {
            LOGGER.error("Missing dimension in chunk loader {}", dimension.identifier());
            return true;
         } else {
            final ImmPtlChunkTickets dimTicketManager = ImmPtlChunkTickets.get(world);
            final LongOpenHashSet set = additionalLoadedChunks.computeIfAbsent(dimension, k -> new LongOpenHashSet());
            chunkLoader.foreachChunkPos(new ChunkLoader.ChunkPosConsumer() {
               @Override
               public void consume(ResourceKey<Level> dimension, int x, int z, int distanceToSource) {
                  long chunkPos = ChunkPos.asLong(x, z);
                  dimTicketManager.markForLoading(chunkPos, distanceToSource, ImmPtlChunkTracking.generationCounter);
                  set.add(chunkPos);
               }
            });
            return false;
         }
      });
      return additionalLoadedChunks;
   }

   private static void tick(MinecraftServer server) {
      Profiler.get().push("portal_chunk_tracking");
      boolean updates = false;
      long gameTime = server.overworld().getGameTime();

      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
         PlayerChunkLoading playerInfo = getPlayerInfo(player);
         if (playerInfo.shouldUpdateImmediately || player.getId() % 13 == gameTime % 13L) {
            playerInfo.shouldUpdateImmediately = false;
            updateForPlayer(player);
            updates = true;
         }
      }

      if (gameTime % 13L == 0L) {
         Object2ObjectOpenHashMap<ResourceKey<Level>, LongOpenHashSet> additionalLoadedChunks = refreshAdditionalChunkLoaders(server);
         purge(server, additionalLoadedChunks);
         generationCounter++;
         updates = true;
      }

      for (ServerLevel world : server.getAllLevels()) {
         ImmPtlChunkTickets dimTicketManager = ImmPtlChunkTickets.get(world);
         dimTicketManager.tick(world);
      }

      Profiler.get().pop();
      if (updates) {
         EntitySync.update(server);
      }

      EntitySync.tick(server);
   }

   public static boolean isPlayerWatchingChunk(
      ServerPlayer player, ResourceKey<Level> dimension, int x, int z, Predicate<ImmPtlChunkTracking.PlayerWatchRecord> predicate
   ) {
      long chunkPos = ChunkPos.asLong(x, z);
      Object2ObjectOpenHashMap<ServerPlayer, ImmPtlChunkTracking.PlayerWatchRecord> recordMap = getDimChunkWatchRecords(dimension).get(chunkPos);
      if (recordMap == null) {
         return false;
      } else {
         ImmPtlChunkTracking.PlayerWatchRecord record = recordMap.get(player);
         if (record == null) {
            return false;
         } else {
            return !record.isLoadedToPlayer ? false : predicate.test(record);
         }
      }
   }

   public static boolean isPlayerWatchingChunk(ServerPlayer player, ResourceKey<Level> dimension, int x, int z) {
      return isPlayerWatchingChunk(player, dimension, x, z, r -> true);
   }

   public static boolean isPlayerWatchingChunkWithinRadius(ServerPlayer player, ResourceKey<Level> dimension, int x, int z, int radiusBlocks) {
      return isPlayerWatchingChunk(player, dimension, x, z, r -> r.distanceToSource * 16 <= radiusBlocks);
   }

   public static boolean isChunkPendingToPlayer(ServerPlayer player, ResourceKey<Level> dimension, long chunkPos) {
      Long2ObjectOpenHashMap<Object2ObjectOpenHashMap<ServerPlayer, ImmPtlChunkTracking.PlayerWatchRecord>> dimRecords = chunkWatchRecords.get(dimension);
      if (dimRecords == null) {
         return false;
      }

      Object2ObjectOpenHashMap<ServerPlayer, ImmPtlChunkTracking.PlayerWatchRecord> records = dimRecords.get(chunkPos);
      if (records == null) {
         return false;
      }

      ImmPtlChunkTracking.PlayerWatchRecord record = records.get(player);
      return record != null && record.isValid && !record.isLoadedToPlayer;
   }

   private static void cleanup(MinecraftServer server) {
      chunkWatchRecords.clear();
      additionalChunkLoaders.clear();
      playerInfoMap.clear();
   }

   public static Stream<ServerPlayer> getPlayersViewingChunk(ResourceKey<Level> dimension, int x, int z) {
      Object2ObjectOpenHashMap<ServerPlayer, ImmPtlChunkTracking.PlayerWatchRecord> records = getWatchRecordForChunk(dimension, x, z);
      return records == null ? Stream.empty() : records.values().stream().filter(e -> e.isLoadedToPlayer).map(e -> e.player);
   }

   public static List<ServerPlayer> getPlayersViewingChunk(ResourceKey<Level> dimension, int x, int z, boolean boundaryOnly) {
      Object2ObjectOpenHashMap<ServerPlayer, ImmPtlChunkTracking.PlayerWatchRecord> recs = getWatchRecordForChunk(dimension, x, z);
      if (recs == null) {
         return Collections.emptyList();
      }

      ArrayList<ServerPlayer> result = new ArrayList<>();

      for (ImmPtlChunkTracking.PlayerWatchRecord rec : recs.values()) {
         if (rec.isLoadedToPlayer && (!boundaryOnly || rec.isBoundary)) {
            result.add(rec.player);
         }
      }

      return result;
   }

   @Nullable
   public static Object2ObjectOpenHashMap<ServerPlayer, ImmPtlChunkTracking.PlayerWatchRecord> getWatchRecordForChunk(
      ResourceKey<Level> dimension, int x, int z
   ) {
      return getDimChunkWatchRecords(dimension).get(ChunkPos.asLong(x, z));
   }

   public static void forceRemovePlayer(ServerPlayer oldPlayer) {
      playerInfoMap.remove(oldPlayer);
      chunkWatchRecords.forEach((dim, dimMap) -> dimMap.long2ObjectEntrySet().removeIf(e -> {
         long chunkPos = e.getLongKey();
         Object2ObjectOpenHashMap<ServerPlayer, ImmPtlChunkTracking.PlayerWatchRecord> records = e.getValue();
         ImmPtlChunkTracking.PlayerWatchRecord rec = records.remove(oldPlayer);
         if (rec != null) {
            PacketRedirection.sendRedirectedMessage(oldPlayer, (ResourceKey<Level>)dim, new ClientboundForgetLevelChunkPacket(new ChunkPos(chunkPos)));
         }

         return records.isEmpty();
      }));
   }

   public static void forceRemoveDimension(ServerLevel world) {
      ResourceKey<Level> dim = world.dimension();
      MinecraftServer server = world.getServer();
      Long2ObjectOpenHashMap<Object2ObjectOpenHashMap<ServerPlayer, ImmPtlChunkTracking.PlayerWatchRecord>> map = chunkWatchRecords.get(dim);
      if (map != null) {
         map.forEach(
            (chunkPos, records) -> {
               Packet<ClientGamePacketListener> unloadPacket = PacketRedirection.createRedirectedMessage(
                  server, dim, new ClientboundForgetLevelChunkPacket(new ChunkPos(chunkPos))
               );

               for (ImmPtlChunkTracking.PlayerWatchRecord record : records.values()) {
                  if (record.isValid && record.isLoadedToPlayer) {
                     record.player.connection.send(unloadPacket);
                  }

                  record.isValid = false;
               }
            }
         );
         chunkWatchRecords.remove(dim);
         additionalChunkLoaders.removeIf(chunkLoader -> chunkLoader.dimension() == dim);

         for (PlayerChunkLoading playerInfo : playerInfoMap.values()) {
            playerInfo.additionalChunkLoaders.removeIf(l -> l.dimension() == dim);
         }
      }
   }

   public static boolean shouldLoadDimension(ResourceKey<Level> dimension) {
      if (!chunkWatchRecords.containsKey(dimension)) {
         return false;
      }

      Long2ObjectOpenHashMap<Object2ObjectOpenHashMap<ServerPlayer, ImmPtlChunkTracking.PlayerWatchRecord>> map = chunkWatchRecords.get(dimension);
      return !map.isEmpty();
   }

   public static void addGlobalAdditionalChunkLoader(MinecraftServer server, ChunkLoader chunkLoader) {
      additionalChunkLoaders.add(chunkLoader);
      ResourceKey<Level> dimension = chunkLoader.dimension();
      ServerLevel world = server.getLevel(dimension);
      if (world == null) {
         LOGGER.error("Missing dimension in chunk loader {}", dimension.identifier());
      } else {
         ImmPtlChunkTickets dimTicketManager = ImmPtlChunkTickets.get(world);
         chunkLoader.foreachChunkPos(
            (dim, x, z, distanceToSource) -> dimTicketManager.markForLoading(ChunkPos.asLong(x, z), distanceToSource, generationCounter)
         );
      }
   }

   public static void removeGlobalAdditionalChunkLoader(MinecraftServer server, ChunkLoader chunkLoader) {
      additionalChunkLoaders.removeIf(c -> c == chunkLoader);
   }

   public static int getLoadedChunkNum(ResourceKey<Level> dimension) {
      return getDimChunkWatchRecords(dimension).size();
   }

   public static void addPerPlayerAdditionalChunkLoader(ServerPlayer player, ChunkLoader chunkLoader) {
      PlayerChunkLoading playerInfo = getPlayerInfo(player);
      playerInfo.additionalChunkLoaders.add(chunkLoader);
      playerInfo.shouldUpdateImmediately = true;
   }

   public static void removePerPlayerAdditionalChunkLoader(ServerPlayer player, ChunkLoader chunkLoader) {
      ArrayList<ChunkLoader> chunkLoaderList = getPlayerInfo(player).additionalChunkLoaders;
      chunkLoaderList.removeIf(c -> c == chunkLoader);
   }

   public static Set<ResourceKey<Level>> getVisibleDimensions(ServerPlayer player) {
      return getPlayerInfo(player).visibleDimensions;
   }

   public static void syncBlockUpdateToClientImmediately(ServerLevel world, IntBox box) {
      ChunkPos lowPos = new ChunkPos(box.l);
      ChunkPos highPos = new ChunkPos(box.h);
      Set<ServerPlayer> playersViewingRegion = new HashSet<>();
      ResourceKey<Level> dimension = world.dimension();

      for (int x = lowPos.x; x <= highPos.x; x++) {
         for (int z = lowPos.z; z <= highPos.z; z++) {
            Object2ObjectOpenHashMap<ServerPlayer, ImmPtlChunkTracking.PlayerWatchRecord> rec = getWatchRecordForChunk(dimension, x, z);
            if (rec != null) {
               for (ImmPtlChunkTracking.PlayerWatchRecord r : rec.values()) {
                  if (r.isValid && !r.isLoadedToPlayer) {
                     playersViewingRegion.add(r.player);
                  }
               }
            }
         }
      }

      for (ServerPlayer player : playersViewingRegion) {
         getPlayerInfo(player).doChunkSending(player);
      }

      IEChunkMap chunkMap = (IEChunkMap)world.getChunkSource().chunkMap;

      for (int x = lowPos.x; x <= highPos.x; x++) {
         for (int z = lowPos.z; z <= highPos.z; z++) {
            long chunkPosLong = ChunkPos.asLong(x, z);
            ChunkHolder chunkHolder = chunkMap.ip_getChunkHolder(chunkPosLong);
            if (chunkHolder != null) {
               LevelChunk tickingChunk = chunkHolder.getTickingChunk();
               if (tickingChunk != null) {
                  chunkHolder.broadcastChanges(tickingChunk);
               }
            }
         }
      }
   }

   public static class PlayerWatchRecord {
      public final ServerPlayer player;
      public final ResourceKey<Level> dimension;
      public final long chunkPos;
      public int lastWatchGeneration;
      public int distanceToSource;
      public boolean isLoadedToPlayer;
      public boolean isValid = true;
      public boolean isBoundary = false;

      public PlayerWatchRecord(
         ServerPlayer player,
         ResourceKey<Level> dimension,
         long chunkPos,
         int lastWatchGeneration,
         int distanceToSource,
         boolean isLoadedToPlayer,
         boolean isBoundary
      ) {
         this.player = player;
         this.dimension = dimension;
         this.chunkPos = chunkPos;
         this.lastWatchGeneration = lastWatchGeneration;
         this.distanceToSource = distanceToSource;
         this.isLoadedToPlayer = isLoadedToPlayer;
         this.isBoundary = isBoundary;
      }

      @Override
      public String toString() {
         return String.format(
            "%s (%d,%d) distance:%d valid:%s loaded:%s",
            this.dimension.identifier(),
            ChunkPos.getX(this.chunkPos),
            ChunkPos.getZ(this.chunkPos),
            this.distanceToSource,
            this.isValid,
            this.isLoadedToPlayer
         );
      }
   }

   public static class RemoteCallables {
      public static void acceptClientPerformanceInfo(ServerPlayer player, PerformanceLevel performanceLevel) {
         PlayerChunkLoading playerInfo = ImmPtlChunkTracking.getPlayerInfo(player);
         playerInfo.performanceLevel = performanceLevel;
      }
   }
}
