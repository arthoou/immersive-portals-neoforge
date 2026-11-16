package qouteall.imm_ptl.core.chunk_loading;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.Set;
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchStartPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.mutable.MutableInt;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.ducks.IEChunkMap;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;
import qouteall.imm_ptl.core.network.PacketRedirection;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.platform.PlatformNetworking;

public class PlayerChunkLoading {
   private static final Logger LOGGER = LogUtils.getLogger();
   public final Set<ResourceKey<Level>> visibleDimensions = new ObjectOpenHashSet<>();
   public final ArrayList<ChunkLoader> additionalChunkLoaders = new ArrayList<>();
   public final ArrayList<ChunkLoader> currentChunkLoaders = new ArrayList<>();
   public final ArrayList<ObjectArrayList<ImmPtlChunkTracking.PlayerWatchRecord>> distanceToPendingChunks = new ArrayList<>();
   public int loadedChunks = 0;
   public boolean shouldUpdateImmediately = false;
   public PerformanceLevel performanceLevel = PerformanceLevel.bad;
   public final boolean isMemoryConnection;
   private float desiredChunksPerTick = 9.0F;
   private float batchQuota;
   private int unacknowledgedBatches;
   private int maxUnacknowledgedBatches = 1;

   public PlayerChunkLoading(boolean isMemoryConnection) {
      this.isMemoryConnection = isMemoryConnection;
   }

   public void markPendingLoading(ImmPtlChunkTracking.PlayerWatchRecord record) {
      Helper.arrayListComputeIfAbsent(this.distanceToPendingChunks, record.distanceToSource, ObjectArrayList::new).add(record);
   }

   @IPVanillaCopy
   public void doChunkSending(ServerPlayer serverPlayer) {
      if (this.unacknowledgedBatches < this.maxUnacknowledgedBatches) {
         if (this.isMemoryConnection) {
            this.batchQuota = 256.0F;
         } else {
            this.batchQuota = Math.min(this.batchQuota + this.desiredChunksPerTick, Math.max(1.0F, this.desiredChunksPerTick));
            if (this.batchQuota < 1.0F) {
               return;
            }
         }

         ServerGamePacketListenerImpl connection = serverPlayer.connection;
         MinecraftServer server = serverPlayer.server;
         int maxSendNum = (int)Math.floor(this.batchQuota);
         Validate.isTrue(maxSendNum != 0);
         MutableInt sentNum = new MutableInt(0);

         for (ObjectArrayList<ImmPtlChunkTracking.PlayerWatchRecord> recs : this.distanceToPendingChunks) {
            if (recs != null && !recs.isEmpty()) {
               if (sentNum.getValue() >= maxSendNum) {
                  break;
               }

               Helper.removeIfWithEarlyExit(recs, (record, shouldStop) -> {
                  if (!record.isValid) {
                     return true;
                  }

                  if (record.isLoadedToPlayer) {
                     return true;
                  }

                  ServerLevel world = server.getLevel(record.dimension);
                  if (world == null) {
                     LOGGER.error("Missing dimension when flushing pending loading {}", record.dimension.identifier());
                     return true;
                  }

                  ChunkMap chunkMap = world.getChunkSource().chunkMap;
                  ChunkHolder chunkHolder = ((IEChunkMap)chunkMap).ip_getChunkHolder(record.chunkPos);
                  if (chunkHolder == null) {
                     return false;
                  }

                  LevelChunk tickingChunk = chunkHolder.getTickingChunk();
                  if (tickingChunk == null) {
                     return false;
                  }

                  record.isLoadedToPlayer = true;
                  if (sentNum.getValue() == 0) {
                     this.unacknowledgedBatches++;
                     connection.send(ClientboundChunkBatchStartPacket.INSTANCE);
                  }

                  sentNum.increment();
                  sendChunkPacket(connection, world, tickingChunk);
                  if (sentNum.getValue() >= maxSendNum) {
                     shouldStop.setValue(true);
                  }

                  return true;
               });
            }
         }

         if (sentNum.getValue() != 0) {
            connection.send(new ClientboundChunkBatchFinishedPacket(sentNum.getValue()));
         }

         this.batchQuota = this.batchQuota - sentNum.getValue().intValue();
      }
   }

   @IPVanillaCopy
   private static void sendChunkPacket(ServerGamePacketListenerImpl serverGamePacketListenerImpl, ServerLevel serverLevel, LevelChunk levelChunk) {
      PacketRedirection.withForceRedirect(
         serverLevel,
         () -> {
            serverGamePacketListenerImpl.send(
               PlatformNetworking.get()
                  .decorateChunkPacket(levelChunk, new ClientboundLevelChunkWithLightPacket(levelChunk, serverLevel.getLightEngine(), null, null))
            );
            onSendPacket(serverGamePacketListenerImpl, serverLevel, levelChunk);
         }
      );
   }

   @IPVanillaCopy
   private static void onSendPacket(ServerGamePacketListenerImpl listener, ServerLevel level, LevelChunk chunk) {
      PlatformNetworking.get().onChunkSentToPlayer(listener, level, chunk);
   }

   @IPVanillaCopy
   public void onChunkBatchReceivedByClient(float clientDesiredChunkPerTick) {
      this.unacknowledgedBatches--;
      this.desiredChunksPerTick = Double.isNaN(clientDesiredChunkPerTick) ? 0.01F : Mth.clamp(clientDesiredChunkPerTick, 0.01F, 64.0F);
      if (this.unacknowledgedBatches == 0) {
         this.batchQuota = 1.0F;
      }

      this.maxUnacknowledgedBatches = 10;
   }
}
