package qouteall.imm_ptl.core.chunk_loading;

import java.util.Objects;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

public class DimensionalChunkPos {
   public final ResourceKey<Level> dimension;
   public final int x;
   public final int z;

   public DimensionalChunkPos(ResourceKey<Level> dimension, int x, int z) {
      this.dimension = dimension;
      this.x = x;
      this.z = z;
   }

   public DimensionalChunkPos(ResourceKey<Level> dimension, ChunkPos chunkPos) {
      this(dimension, chunkPos.x, chunkPos.z);
   }

   public ChunkPos getChunkPos() {
      return new ChunkPos(this.x, this.z);
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (o != null && this.getClass() == o.getClass()) {
         DimensionalChunkPos that = (DimensionalChunkPos)o;
         return this.x == that.x && this.z == that.z && this.dimension.equals(that.dimension);
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.dimension, this.x, this.z);
   }

   @Override
   public String toString() {
      return "(%s %d %d)".formatted(this.dimension, this.x, this.z);
   }
}
