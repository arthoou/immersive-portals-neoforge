package qouteall.imm_ptl.core.chunk_loading;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongPredicate;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;
import java.util.concurrent.Executor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.chunk.LevelChunk;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import qouteall.dimlib.api.DimensionAPI;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.ducks.IEChunkMap;
import qouteall.imm_ptl.core.ducks.IEServerChunkCache;
import qouteall.imm_ptl.core.ducks.IEWorld;
import qouteall.imm_ptl.core.mixin.common.chunk_sync.IEDistanceManager;
import qouteall.imm_ptl.core.platform_specific.IPConfig;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.RateStat;

public class ImmPtlChunkTickets {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final TicketType TICKET_TYPE = new TicketType(0L, 6);
   private static boolean enableDebugRateStat = false;
   private static final RateStat debugRateStat = new RateStat("imm_ptl_chunk_ticket");
   public static final WeakHashMap<ServerLevel, ImmPtlChunkTickets> BY_DIMENSION = new WeakHashMap<>();
   private final Long2ObjectOpenHashMap<ImmPtlChunkTickets.ChunkTicketInfo> chunkPosToTicketInfo = new Long2ObjectOpenHashMap<>();
   private final ArrayList<LongLinkedOpenHashSet> chunksToAddTicketByDistance = new ArrayList<>();
   private final LongOpenHashSet waitingForLoading = new LongOpenHashSet();
   private boolean isValid = true;
   public final int throttlingLimit = 4;

   public static void init() {
      DimensionAPI.SERVER_PRE_REMOVE_DIMENSION_EVENT.register(ImmPtlChunkTickets::onDimensionRemove);
      IPGlobal.SERVER_CLEANUP_EVENT.register(ImmPtlChunkTickets::cleanup);
   }

   private ImmPtlChunkTickets() {
   }

   public static ImmPtlChunkTickets get(ServerLevel world) {
      return BY_DIMENSION.computeIfAbsent(world, k -> new ImmPtlChunkTickets());
   }

   public void markForLoading(long chunkPos, int distanceToSource, int generation) {
      Validate.isTrue(distanceToSource >= 0);
      ImmPtlChunkTickets.ChunkTicketInfo info = this.chunkPosToTicketInfo.get(chunkPos);
      if (info == null) {
         info = new ImmPtlChunkTickets.ChunkTicketInfo(generation, distanceToSource);
         this.chunkPosToTicketInfo.put(chunkPos, info);
         this.getQueueByDistance(distanceToSource).add(chunkPos);
      } else if (generation != info.lastUpdateGeneration) {
         info.lastUpdateGeneration = generation;
         int oldDistanceToSource = info.distanceToSource;
         info.distanceToSource = distanceToSource;
         if (this.getQueueByDistance(oldDistanceToSource).remove(chunkPos)) {
            this.getQueueByDistance(distanceToSource).add(chunkPos);
         }
      } else if (distanceToSource < info.distanceToSource) {
         int oldDistanceToSource = info.distanceToSource;
         info.distanceToSource = distanceToSource;
         if (this.getQueueByDistance(oldDistanceToSource).remove(chunkPos)) {
            this.getQueueByDistance(distanceToSource).add(chunkPos);
         }
      }
   }

   private LongLinkedOpenHashSet getQueueByDistance(int distanceToSource) {
      return Helper.arrayListComputeIfAbsent(this.chunksToAddTicketByDistance, distanceToSource, LongLinkedOpenHashSet::new);
   }

   public void tick(ServerLevel world) {
      this.flushThrottling(world);
   }

   public void flushThrottling(ServerLevel world) {
      if (Thread.currentThread() != ((IEWorld)world).portal_getThread()) {
         LOGGER.error("Called in a non-server-main (or server-world) thread.", new Throwable());
      } else {
         if (enableDebugRateStat) {
            debugRateStat.update();
         }

         if (!this.isValid) {
            LOGGER.error("flushing when invalid {}", world);
         } else if (world.getServer().isRunning()) {
            DistanceManager distanceManager = getDistanceManager(world);
            Executor mainThreadExecutor = ((IEDistanceManager)distanceManager).ip_getMainThreadExecutor();
            this.waitingForLoading.removeIf(chunkPosx -> {
               ChunkHolder chunkHolder = getChunkHolder(world, chunkPosx);
               if (chunkHolder == null) {
                  return true;
               }

               ChunkResult<LevelChunk> resultNow = chunkHolder.getEntityTickingChunkFuture().getNow(null);
               if (resultNow == null) {
                  return false;
               }

               if (!resultNow.isSuccess()) {
                  LOGGER.error("Chunk loading failure {} {} {}", world, new ChunkPos(chunkPosx));
               }

               return true;
            });

            for (LongLinkedOpenHashSet queue : this.chunksToAddTicketByDistance) {
               if (queue != null) {
                  while (!queue.isEmpty()) {
                     if (this.waitingForLoading.size() >= 4) {
                        return;
                     }

                     long chunkPos = queue.removeFirstLong();
                     if (this.chunkPosToTicketInfo.containsKey(chunkPos)) {
                        addTicket(world, chunkPos);
                        this.waitingForLoading.add(chunkPos);
                     } else {
                        LOGGER.warn("Chunk {} is not in the queue", new ChunkPos(chunkPos));
                     }
                  }
               }
            }
         }
      }
   }

   private static void addTicket(ServerLevel world, long chunkPos) {
      if (IPConfig.getConfig().enableImmPtlChunkLoading) {
         ChunkPos chunkPosObj = new ChunkPos(chunkPos);
         world.getChunkSource().addTicketWithRadius(TICKET_TYPE, chunkPosObj, getLoadingRadius());
         if (enableDebugRateStat) {
            debugRateStat.hit();
         }
      }
   }

   public void purge(ServerLevel world, LongPredicate shouldKeepLoadingFunc) {
      this.chunkPosToTicketInfo.long2ObjectEntrySet().removeIf(e -> {
         long chunkPos = e.getLongKey();
         ImmPtlChunkTickets.ChunkTicketInfo ticketInfo = e.getValue();
         boolean keepLoading = shouldKeepLoadingFunc.test(chunkPos);
         if (!keepLoading) {
            this.waitingForLoading.remove(chunkPos);
            boolean pendingTicketAdding = this.getQueueByDistance(ticketInfo.distanceToSource).remove(chunkPos);
            if (!pendingTicketAdding) {
               ChunkPos chunkPosObj = new ChunkPos(chunkPos);
               world.getChunkSource().removeTicketWithRadius(TICKET_TYPE, chunkPosObj, getLoadingRadius());
            }

            return true;
         } else {
            return false;
         }
      });
   }

   public int getLoadedChunkNum() {
      return this.chunkPosToTicketInfo.size();
   }

   public static void onDimensionRemove(ServerLevel world) {
      ImmPtlChunkTickets dimTicketManager = BY_DIMENSION.remove(world);
      if (dimTicketManager != null) {
         removeAllTicketsInWorld(world, dimTicketManager);
      }
   }

   private static void removeAllTicketsInWorld(ServerLevel world, ImmPtlChunkTickets dimTicketManager) {
      TicketStorage ticketStorage = ((IEDistanceManager)getDistanceManager(world)).ip_getTicketStorage();
      dimTicketManager.chunkPosToTicketInfo.keySet().forEach(pos -> {
         List<Ticket> tickets = ((qouteall.imm_ptl.core.ducks.IEDistanceManager)getDistanceManager(world)).portal_getTicketSet(pos);

         for (Ticket ticket : tickets.stream().filter(t -> t.getType() == TICKET_TYPE).toList()) {
            ticketStorage.removeTicket(pos, ticket);
         }
      });
      dimTicketManager.isValid = false;
   }

   public static int getLoadingRadius() {
      return IPGlobal.activeLoading ? 2 : 1;
   }

   public static ChunkHolder getChunkHolder(ServerLevel world, long chunkPos) {
      return ((IEChunkMap)world.getChunkSource().chunkMap).ip_getChunkHolder(chunkPos);
   }

   public static DistanceManager getDistanceManager(ServerLevel world) {
      return ((IEServerChunkCache)world.getChunkSource()).ip_getDistanceManager();
   }

   private static void cleanup(MinecraftServer server) {
      for (ImmPtlChunkTickets immPtlChunkTickets : BY_DIMENSION.values()) {
         immPtlChunkTickets.isValid = false;
      }

      BY_DIMENSION.clear();
   }

   public static class ChunkTicketInfo {
      public int lastUpdateGeneration;
      public int distanceToSource;

      public ChunkTicketInfo(int lastUpdateGeneration, int distanceToSource) {
         this.lastUpdateGeneration = lastUpdateGeneration;
         this.distanceToSource = distanceToSource;
      }
   }
}
