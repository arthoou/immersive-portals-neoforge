package qouteall.imm_ptl.core.chunk_loading;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData.BlockEntityTagOutput;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumInterface;
import qouteall.imm_ptl.core.ducks.IEMinecraftClient;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;
import qouteall.imm_ptl.core.platform_specific.O_O;
import qouteall.q_misc_util.my_util.SignalArged;

@Environment(EnvType.CLIENT)
@IPVanillaCopy
public class ImmPtlClientChunkMap extends ClientChunkCache {
   private static final Logger LOGGER = LogManager.getLogger();
   protected final Long2ObjectOpenHashMap<LevelChunk> chunkMapForMainThread = new Long2ObjectOpenHashMap<>();
   protected final Long2ObjectOpenHashMap<LevelChunk> chunkMapForOtherThreads = new Long2ObjectOpenHashMap<>();
   public final Thread mainThread = ((IEMinecraftClient)Minecraft.getInstance()).ip_getRunningThread();
   public static final SignalArged<LevelChunk> clientChunkLoadSignal = new SignalArged<>();
   public static final SignalArged<LevelChunk> clientChunkUnloadSignal = new SignalArged<>();

   public ImmPtlClientChunkMap(ClientLevel clientWorld, int loadDistance) {
      super(clientWorld, 1);
   }

   @Override
   public void drop(ChunkPos chunkPos) {
      Validate.isTrue(Thread.currentThread() == this.mainThread);
      LevelChunk chunk = this.chunkMapForMainThread.get(chunkPos.toLong());
      if (chunk != null) {
         this.modifyChunkMap(chunkMap -> chunkMap.remove(chunkPos.toLong()));
         O_O.postClientChunkUnloadEvent(chunk);
         this.level.unload(chunk);
         SodiumInterface.invoker.onClientChunkUnloaded(this.level, chunkPos.x, chunkPos.z);
         clientChunkUnloadSignal.emit(chunk);
      }
   }

   public <T> T readChunkMap(Function<Long2ObjectOpenHashMap<LevelChunk>, T> func) {
      if (Thread.currentThread() == this.mainThread) {
         return func.apply(this.chunkMapForMainThread);
      }

      synchronized (this.chunkMapForOtherThreads) {
         return func.apply(this.chunkMapForOtherThreads);
      }
   }

   public void modifyChunkMap(Consumer<Long2ObjectOpenHashMap<LevelChunk>> func) {
      Validate.isTrue(Thread.currentThread() == this.mainThread);
      func.accept(this.chunkMapForMainThread);
      synchronized (this.chunkMapForOtherThreads) {
         func.accept(this.chunkMapForOtherThreads);
      }
   }

   @Nullable
   public LevelChunk voxy$cheekyGetChunk(int x, int z) {
      return this.getChunk(x, z, ChunkStatus.FULL, false);
   }

   @Override
   public LevelChunk getChunk(int x, int z, ChunkStatus chunkStatus, boolean create) {
      return this.readChunkMap(chunkMap -> {
         LevelChunk chunk = chunkMap.get(ChunkPos.asLong(x, z));
         if (chunk != null) {
            return chunk;
         } else {
            return create ? this.emptyChunk : null;
         }
      });
   }

   public boolean isChunkLoaded(int x, int z) {
      return this.readChunkMap(chunkMap -> chunkMap.containsKey(ChunkPos.asLong(x, z)));
   }

   @Override
   public void replaceBiomes(int x, int z, FriendlyByteBuf friendlyByteBuf) {
      Validate.isTrue(Thread.currentThread() == this.mainThread);
      long chunkPosLong = ChunkPos.asLong(x, z);
      LevelChunk worldChunk = this.chunkMapForMainThread.get(chunkPosLong);
      new ChunkPos(x, z);
      if (worldChunk == null) {
         LOGGER.error("Trying to replace biomes for missing chunk {} {}", x, z);
      } else {
         worldChunk.replaceBiomes(friendlyByteBuf);
      }
   }

   @Override
   public LevelChunk replaceWithPacketData(int x, int z, FriendlyByteBuf buf, Map<Types, long[]> heightmaps, Consumer<BlockEntityTagOutput> consumer) {
      Validate.isTrue(Thread.currentThread() == this.mainThread);
      long chunkPosLong = ChunkPos.asLong(x, z);
      LevelChunk worldChunk = this.chunkMapForMainThread.get(chunkPosLong);
      if (worldChunk == null) {
         worldChunk = new LevelChunk(this.level, new ChunkPos(x, z));
         this.loadChunkDataFromPacket(buf, heightmaps, worldChunk, consumer);
         LevelChunk worldChunkToPut = worldChunk;
         this.modifyChunkMap(chunkMap -> chunkMap.put(chunkPosLong, worldChunkToPut));
      } else {
         this.loadChunkDataFromPacket(buf, heightmaps, worldChunk, consumer);
      }

      this.level.onChunkLoaded(new ChunkPos(x, z));
      O_O.postClientChunkLoadEvent(worldChunk);
      SodiumInterface.invoker.onClientChunkLoaded(this.level, x, z);
      clientChunkLoadSignal.emit(worldChunk);
      return worldChunk;
   }

   private void loadChunkDataFromPacket(FriendlyByteBuf buf, Map<Types, long[]> heightmaps, LevelChunk worldChunk, Consumer<BlockEntityTagOutput> consumer) {
      try {
         worldChunk.replaceWithPacketData(buf, heightmaps, consumer);
      } catch (Exception e) {
         LOGGER.error("Error deserializing chunk packet {} {}", worldChunk.getLevel().dimension().identifier(), worldChunk.getPos(), e);
         CHelper.printChat(
            Component.literal(
                  "Failed to deserialize chunk packet. %s %s %s"
                     .formatted(worldChunk.getLevel().dimension().identifier(), worldChunk.getPos().x, worldChunk.getPos().z)
               )
               .append(Component.literal(" Report issue:"))
               .append(McHelper.getLinkText(O_O.getIssueLink()))
               .withStyle(ChatFormatting.RED)
         );
         throw new RuntimeException(e);
      }
   }

   public List<LevelChunk> getCopiedChunkList() {
      return this.readChunkMap(chunkMap -> Arrays.asList(chunkMap.values().toArray(new LevelChunk[0])));
   }

   @Override
   public void updateViewCenter(int x, int z) {
   }

   @Override
   public void updateViewRadius(int r) {
   }

   @Override
   public String gatherStats() {
      return "Client Chunks (ImmPtl) " + this.getLoadedChunksCount();
   }

   @Override
   public int getLoadedChunksCount() {
      return this.readChunkMap(chunkMap -> chunkMap.size());
   }

   @Override
   public void onLightUpdate(LightLayer lightType, SectionPos chunkSectionPos) {
      ClientWorldLoader.getWorldRenderer(this.level.dimension()).setSectionDirty(chunkSectionPos.x(), chunkSectionPos.y(), chunkSectionPos.z());
   }
}
