package qouteall.imm_ptl.core.render;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.LongConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlClientChunkMap;
import qouteall.imm_ptl.core.ducks.IERenderSection;
import qouteall.imm_ptl.core.ducks.IEWorldRenderer;
import qouteall.imm_ptl.core.miscellaneous.GcMonitor;
import qouteall.imm_ptl.core.mixin.client.render.IEViewArea;
import qouteall.q_misc_util.Helper;

@Environment(EnvType.CLIENT)
public class ImmPtlViewArea extends ViewArea {
   private final SectionRenderDispatcher factory;
   private final Long2ObjectOpenHashMap<ImmPtlViewArea.Column> columnMap = new Long2ObjectOpenHashMap<>();
   private final Long2ObjectOpenHashMap<ImmPtlViewArea.Preset> presets = new Long2ObjectOpenHashMap<>();
   private ImmPtlViewArea.Preset currentPreset = null;
   public final int minSectionY;
   public final int endSectionY;
   private boolean isAlive = true;

   public static void init() {
      ImmPtlClientChunkMap.clientChunkUnloadSignal.connect(section -> {
         ResourceKey<Level> dimension = section.getLevel().dimension();
         LevelRenderer worldRenderer = ClientWorldLoader.WORLD_RENDERER_MAP.get(dimension);
         if (worldRenderer != null && ((IEWorldRenderer)worldRenderer).ip_getBuiltChunkStorage() instanceof ImmPtlViewArea immPtlViewArea) {
            immPtlViewArea.onChunkUnload(section.getPos().x, section.getPos().z);
         }
      });
      IPGlobal.POST_CLIENT_TICK_EVENT.register(() -> {
         if (ClientWorldLoader.getIsInitialized()) {
            for (ClientLevel world : ClientWorldLoader.getClientWorlds()) {
               LevelRenderer worldRenderer = ClientWorldLoader.getWorldRenderer(world.dimension());
               if (((IEWorldRenderer)worldRenderer).ip_getBuiltChunkStorage() instanceof ImmPtlViewArea immPtlViewArea) {
                  immPtlViewArea.tick();
               }
            }
         }
      });
   }

   public ImmPtlViewArea(SectionRenderDispatcher sectionBuilder, Level world, int r, LevelRenderer worldRenderer) {
      super(sectionBuilder, world, r, worldRenderer);
      this.factory = sectionBuilder;
      int cacheSize = this.sectionGridSizeX * this.sectionGridSizeY * this.sectionGridSizeZ;
      if (IPGlobal.cacheGlBuffer) {
         cacheSize /= 10;
      }

      this.minSectionY = McHelper.getMinSectionY(world);
      this.endSectionY = McHelper.getMaxSectionYExclusive(world);
   }

   @Override
   protected void createSections(SectionRenderDispatcher sectionBuilder_1) {
      int num = this.sectionGridSizeX * this.sectionGridSizeY * this.sectionGridSizeZ;
      this.sections = new RenderSection[num];
   }

   @Override
   public void releaseAllBuffers() {
      Set<RenderSection> allActiveBuiltChunks = this.getAllActiveBuiltChunks();
      allActiveBuiltChunks.forEach(RenderSection::reset);
      this.columnMap.clear();
      this.presets.clear();
      this.isAlive = false;
   }

   @Override
   public void repositionCamera(SectionPos cameraSectionPos) {
      Profiler.get().push("built_section_storage");
      int cameraChunkX = cameraSectionPos.x();
      int cameraChunkZ = cameraSectionPos.z();
      ChunkPos cameraChunkPos = new ChunkPos(cameraChunkX, cameraChunkZ);
      ImmPtlViewArea.Preset preset = this.presets.computeIfAbsent(cameraChunkPos.toLong(), whatever -> this.createPresetByChunkPos(cameraChunkX, cameraChunkZ));
      preset.lastActiveTime = System.nanoTime();
      this.sections = preset.data;
      this.currentPreset = preset;
      ((IEViewArea)this).ip_setCameraSectionPos(cameraSectionPos);
      this.levelRenderer.getSectionOcclusionGraph().invalidate();
      Profiler.get().pop();
   }

   @Override
   public void setDirty(int cx, int cy, int cz, boolean isImportant) {
      RenderSection builtChunk = this.provideBuiltChunkByChunkPos(cx, cy, cz);
      builtChunk.setDirty(isImportant);
   }

   public RenderSection provideBuiltChunkByChunkPos(int cx, int cy, int cz) {
      ImmPtlViewArea.Column column = this.provideColumn(ChunkPos.asLong(cx, cz));
      int offsetChunkY = Mth.clamp(cy - McHelper.getMinSectionY(this.level), 0, McHelper.getYSectionNumber(this.level) - 1);
      return column.sections[offsetChunkY];
   }

   private ImmPtlViewArea.Preset createPresetByChunkPos(int sectionX, int sectionZ) {
      RenderSection[] sections1 = new RenderSection[this.sectionGridSizeX * this.sectionGridSizeY * this.sectionGridSizeZ];

      for (int cx = 0; cx < this.sectionGridSizeX; cx++) {
         int xBlockSize = this.sectionGridSizeX * 16;
         int xStart = (sectionX << 4) - xBlockSize / 2;
         int px = xStart + Math.floorMod(cx * 16 - xStart, xBlockSize);

         for (int cz = 0; cz < this.sectionGridSizeZ; cz++) {
            int zBlockSize = this.sectionGridSizeZ * 16;
            int zStart = (sectionZ << 4) - zBlockSize / 2;
            int pz = zStart + Math.floorMod(cz * 16 - zStart, zBlockSize);
            Validate.isTrue(px % 16 == 0);
            Validate.isTrue(pz % 16 == 0);
            ImmPtlViewArea.Column column = this.provideColumn(ChunkPos.asLong(px >> 4, pz >> 4));

            for (int offsetCy = 0; offsetCy < this.sectionGridSizeY; offsetCy++) {
               int index = this.getChunkIndex(cx, offsetCy, cz);
               sections1[index] = column.sections[offsetCy];
            }
         }
      }

      return new ImmPtlViewArea.Preset(sections1);
   }

   private void foreachPresetCoveredChunkPoses(int centerChunkX, int centerChunkZ, LongConsumer func) {
      RenderSection[] sections1 = new RenderSection[this.sectionGridSizeX * this.sectionGridSizeY * this.sectionGridSizeZ];

      for (int cx = 0; cx < this.sectionGridSizeX; cx++) {
         int xBlockSize = this.sectionGridSizeX * 16;
         int xStart = (centerChunkX << 4) - xBlockSize / 2;
         int px = xStart + Math.floorMod(cx * 16 - xStart, xBlockSize);

         for (int cz = 0; cz < this.sectionGridSizeZ; cz++) {
            int zBlockSize = this.sectionGridSizeZ * 16;
            int zStart = (centerChunkZ << 4) - zBlockSize / 2;
            int pz = zStart + Math.floorMod(cz * 16 - zStart, zBlockSize);
            Validate.isTrue(px % 16 == 0);
            Validate.isTrue(pz % 16 == 0);
            long sectionPos = ChunkPos.asLong(px >> 4, pz >> 4);
            func.accept(sectionPos);
         }
      }
   }

   private int getChunkIndex(int x, int y, int z) {
      return (z * this.sectionGridSizeY + y) * this.sectionGridSizeX + x;
   }

   public ImmPtlViewArea.Column provideColumn(long sectionPos) {
      return this.columnMap.computeIfAbsent(sectionPos, this::createColumn);
   }

   private ImmPtlViewArea.Column createColumn(long sectionPos) {
      RenderSection[] array = new RenderSection[this.sectionGridSizeY];
      int sectionX = ChunkPos.getX(sectionPos);
      int sectionZ = ChunkPos.getZ(sectionPos);
      int minSectionY = McHelper.getMinSectionY(this.level);

      for (int offsetCY = 0; offsetCY < this.sectionGridSizeY; offsetCY++) {
         RenderSection builtChunk = this.factory.new RenderSection(0, SectionPos.asLong(sectionX, offsetCY + minSectionY, sectionZ));
         array[offsetCY] = builtChunk;
      }

      return new ImmPtlViewArea.Column(array);
   }

   private void tick() {
      if (this.isAlive) {
         ClientLevel worldClient = Minecraft.getInstance().level;
         if (worldClient != null) {
            if (GcMonitor.isMemoryNotEnough()) {
               if (worldClient.getGameTime() % 3L == 0L) {
                  this.purge();
               }
            } else if (worldClient.getGameTime() % 213L == 66L) {
               this.purge();
            }
         }
      }
   }

   private void purge() {
      Profiler.get().push("my_built_section_storage_purge");
      long dropTime = Helper.secondToNano(GcMonitor.isMemoryNotEnough() ? 3.0 : 20.0);
      long currentTime = System.nanoTime();
      this.presets.long2ObjectEntrySet().removeIf(entry -> {
         ImmPtlViewArea.Preset preset = entry.getValue();
         long centerChunkPos = entry.getLongKey();
         boolean shouldDropPreset = this.shouldDropPreset(dropTime, currentTime, preset);
         if (!shouldDropPreset) {
            this.foreachPresetCoveredChunkPoses(ChunkPos.getX(centerChunkPos), ChunkPos.getZ(centerChunkPos), columnChunkPos -> {
               ImmPtlViewArea.Column column = this.columnMap.get(columnChunkPos);
               column.mark = currentTime;
            });
         }

         return shouldDropPreset;
      });
      long timeThreshold = Helper.secondToNano(5.0);
      ArrayDeque<RenderSection> toDelete = new ArrayDeque<>();
      this.columnMap.long2ObjectEntrySet().removeIf(entry -> {
         ImmPtlViewArea.Column column = entry.getValue();
         boolean shouldRemove = currentTime - column.mark > timeThreshold;
         if (shouldRemove) {
            toDelete.addAll(Arrays.asList(column.sections));
         }

         return shouldRemove;
      });
      if (!toDelete.isEmpty()) {
         IPGlobal.PRE_GAME_RENDER_TASK_LIST.addTask(() -> {
            if (toDelete.isEmpty()) {
               return true;
            }

            for (int num = 0; !toDelete.isEmpty() && num < 100; num++) {
               RenderSection builtChunk = toDelete.poll();
               builtChunk.reset();
            }

            return false;
         });
      }

      Profiler.get().pop();
   }

   private boolean shouldDropPreset(long dropTime, long currentTime, ImmPtlViewArea.Preset preset) {
      return preset.data == this.sections ? false : currentTime - preset.lastActiveTime > dropTime;
   }

   private Set<RenderSection> getAllActiveBuiltChunks() {
      HashSet<RenderSection> result = new HashSet<>();
      this.presets.forEach((key, preset) -> result.addAll(Arrays.asList(preset.data)));
      if (this.sections != null) {
         result.addAll(Arrays.asList(this.sections));
      }

      result.remove(null);
      return result;
   }

   public int getManagedSectionNum() {
      return this.columnMap.size() * this.sectionGridSizeY;
   }

   public String getDebugString() {
      return String.format("Built Section Storage Columns:%s", this.columnMap.size());
   }

   public int getRadius() {
      return (this.sectionGridSizeX - 1) / 2;
   }

   public boolean isRegionActive(int cxStart, int czStart, int cxEnd, int czEnd) {
      for (int cx = cxStart; cx <= cxEnd; cx++) {
         for (int cz = czStart; cz <= czEnd; cz++) {
            if (this.columnMap.containsKey(ChunkPos.asLong(cx, cz))) {
               return true;
            }
         }
      }

      return false;
   }

   public void onChunkUnload(int sectionX, int sectionZ) {
      long sectionPos = ChunkPos.asLong(sectionX, sectionZ);
      ImmPtlViewArea.Column column = this.columnMap.get(sectionPos);
      if (column != null) {
         for (RenderSection builtChunk : column.sections) {
            ((IERenderSection)builtChunk).portal_fullyReset();
         }
      }
   }

   public RenderSection getSectionFromRawArray(BlockPos sectionOrigin, RenderSection[] sections) {
      int i = Mth.floorDiv(sectionOrigin.getX(), 16);
      int j = Mth.floorDiv(sectionOrigin.getY() - McHelper.getMinY(this.level), 16);
      int k = Mth.floorDiv(sectionOrigin.getZ(), 16);
      if (j >= 0 && j < this.sectionGridSizeY) {
         i = Mth.positiveModulo(i, this.sectionGridSizeX);
         k = Mth.positiveModulo(k, this.sectionGridSizeZ);
         return sections[this.getChunkIndex(i, j, k)];
      } else {
         return null;
      }
   }

   @Nullable
   @Override
   protected RenderSection/* @Nullable */ getRenderSectionAt(BlockPos pos) {
      int i = Mth.floorDiv(pos.getX(), 16);
      int j = Mth.floorDiv(pos.getY() - McHelper.getMinY(this.level), 16);
      int k = Mth.floorDiv(pos.getZ(), 16);
      return this.getRenderSectionByGridCoords(i, j, k, pos);
   }

   @Nullable
   @Override
   protected RenderSection/* @Nullable */ getRenderSection(long sectionNode) {
      int i = SectionPos.x(sectionNode);
      int j = SectionPos.y(sectionNode) - McHelper.getMinSectionY(this.level);
      int k = SectionPos.z(sectionNode);
      return this.getRenderSectionByGridCoords(i, j, k, SectionPos.of(sectionNode).origin());
   }

   @Nullable
   private RenderSection/* @Nullable */ getRenderSectionByGridCoords(int i, int j, int k, BlockPos pos) {
      if (j >= 0 && j < this.sectionGridSizeY) {
         i = Mth.positiveModulo(i, this.sectionGridSizeX);
         k = Mth.positiveModulo(k, this.sectionGridSizeZ);
         int sectionIndex = this.getChunkIndex(i, j, k);
         RenderSection result = this.sections[sectionIndex];
         if (result == null) {
            Helper.err("Null RenderChunk " + pos);
            return null;
         } else {
            ((IERenderSection)result).portal_setIndex(sectionIndex);
            return result;
         }
      } else {
         return null;
      }
   }

   @Nullable
   public RenderSection/* @Nullable */ rawFetch(int cx, int cy, int cz, long timeMark) {
      if (cy >= this.minSectionY && cy < this.endSectionY) {
         long l = ChunkPos.asLong(cx, cz);
         ImmPtlViewArea.Column column = this.provideColumn(l);
         column.mark = timeMark;
         int yOffset = cy - this.minSectionY;
         return column.sections[yOffset];
      } else {
         return null;
      }
   }

   @Nullable
   public RenderSection/* @Nullable */ rawGet(int cx, int cy, int cz) {
      if (cy >= this.minSectionY && cy < this.endSectionY) {
         long l = ChunkPos.asLong(cx, cz);
         ImmPtlViewArea.Column column = this.columnMap.get(l);
         if (column == null) {
            return null;
         }

         int yOffset = cy - this.minSectionY;
         return column.sections[yOffset];
      } else {
         return null;
      }
   }

   public static class Column {
      public long mark = 0L;
      public RenderSection[] sections;

      public Column(RenderSection[] sections) {
         this.sections = sections;
      }
   }

   public static class Preset {
      public final RenderSection[] data;
      public long lastActiveTime = 0L;

      public Preset(RenderSection[] data) {
         this.data = data;
      }
   }
}
