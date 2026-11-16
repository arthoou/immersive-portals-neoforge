package qouteall.imm_ptl.core.portal.nether_portal;

import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record FastBlockAccess(LevelChunkSection[] sections, int lowerCX, int lowerCY, int lowerCZ, int lX, int lY, int lZ, Level world) {
   public static FastBlockAccess from(Level world, ChunkPos centerChunkPos, int radiusChunks) {
      int lowerCX = centerChunkPos.x - radiusChunks;
      int lowerCY = world.getMinSectionY();
      int lowerCZ = centerChunkPos.z - radiusChunks;
      int upperCX = centerChunkPos.x + radiusChunks;
      int upperCY = world.getMaxSectionY() + 1;
      int upperCZ = centerChunkPos.z + radiusChunks;
      return from(world, lowerCX, upperCX, lowerCY, upperCY, lowerCZ, upperCZ);
   }

   @NotNull
   public static FastBlockAccess from(Level world, int lowerCX, int upperCXExclusive, int lowerCY, int upperCYExclusive, int lowerCZ, int upperCZExclusive) {
      int lX = upperCXExclusive - lowerCX;
      int lY = upperCYExclusive - lowerCY;
      int lZ = upperCZExclusive - lowerCZ;
      int minSectionY = world.getMinSectionY();
      int maxSectionYExclusive = world.getMaxSectionY() + 1;
      Validate.isTrue(lowerCY >= minSectionY, "Min section Y out of range");
      Validate.isTrue(upperCYExclusive <= maxSectionYExclusive, "Max section Y out of range");
      ChunkSource chunkSource = world.getChunkSource();
      LevelChunkSection[] sections = new LevelChunkSection[lX * lY * lZ];

      for (int cx = lowerCX; cx < upperCXExclusive; cx++) {
         for (int cz = lowerCZ; cz < upperCZExclusive; cz++) {
            LevelChunk chunk = chunkSource.getChunk(cx, cz, false);
            if (chunk != null && !(chunk instanceof EmptyLevelChunk)) {
               LevelChunkSection[] column = chunk.getSections();

               for (int cy = lowerCY; cy < upperCYExclusive; cy++) {
                  LevelChunkSection section = column[cy - minSectionY];
                  if (section != null && !section.hasOnlyAir()) {
                     int index = cx - lowerCX + (cy - lowerCY) * lX + (cz - lowerCZ) * lX * lY;
                     sections[index] = section;
                  }
               }
            }
         }
      }

      return new FastBlockAccess(sections, lowerCX, lowerCY, lowerCZ, lX, lY, lZ, world);
   }

   @NotNull
   public BlockState getBlockState(int x, int y, int z) {
      int cx = x >> 4;
      int cy = y >> 4;
      int cz = z >> 4;
      LevelChunkSection section = this.getSection(cx, cy, cz);
      return section == null ? Blocks.AIR.defaultBlockState() : section.getBlockState(x & 15, y & 15, z & 15);
   }

   @Nullable
   public LevelChunkSection getSection(int cx, int cy, int cz) {
      if (cx >= this.lowerCX
         && cx < this.lowerCX + this.lX
         && cy >= this.lowerCY
         && cy < this.lowerCY + this.lY
         && cz >= this.lowerCZ
         && cz < this.lowerCZ + this.lZ) {
         int index = cx - this.lowerCX + (cy - this.lowerCY) * this.lX + (cz - this.lowerCZ) * this.lX * this.lY;
         return this.sections[index];
      } else {
         return null;
      }
   }

   public Stream<SectionPos> sectionPoses() {
      return IntStream.range(0, this.lY)
         .boxed()
         .flatMap(
            y -> IntStream.range(0, this.lZ)
               .boxed()
               .flatMap(z -> IntStream.range(0, this.lX).mapToObj(x -> SectionPos.of(x + this.lowerCX, y + this.lowerCY, z + this.lowerCZ)))
         );
   }

   public Stream<ChunkPos> chunkPoses() {
      return IntStream.range(0, this.lZ).boxed().flatMap(z -> IntStream.range(0, this.lX).mapToObj(x -> new ChunkPos(x + this.lowerCX, z + this.lowerCZ)));
   }

   public int minSectionX() {
      return this.lowerCX;
   }

   public int minSectionY() {
      return this.lowerCY;
   }

   public int minSectionZ() {
      return this.lowerCZ;
   }

   public int maxSectionXInclusive() {
      return this.lowerCX + this.lX - 1;
   }

   public int maxSectionYInclusive() {
      return this.lowerCY + this.lY - 1;
   }

   public int maxSectionZInclusive() {
      return this.lowerCZ + this.lZ - 1;
   }

   public int maxSectionXExclusive() {
      return this.lowerCX + this.lX;
   }

   public int maxSectionYExclusive() {
      return this.lowerCY + this.lY;
   }

   public int maxSectionZExclusive() {
      return this.lowerCZ + this.lZ;
   }
}
