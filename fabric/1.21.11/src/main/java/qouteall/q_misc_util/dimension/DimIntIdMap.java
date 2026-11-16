package qouteall.q_misc_util.dimension;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import qouteall.q_misc_util.Helper;

public class DimIntIdMap {
   public static final int MISSING_ID = Integer.MIN_VALUE;
   final Object2IntOpenHashMap<ResourceKey<Level>> toIntegerId;
   final Int2ObjectOpenHashMap<ResourceKey<Level>> fromIntegerId;
   private int maxId;

   public DimIntIdMap(Object2IntOpenHashMap<ResourceKey<Level>> toIntegerId, Int2ObjectOpenHashMap<ResourceKey<Level>> fromIntegerId) {
      this.toIntegerId = toIntegerId;
      this.fromIntegerId = fromIntegerId;
      toIntegerId.defaultReturnValue(Integer.MIN_VALUE);
      this.maxId = toIntegerId.values().intStream().max().orElse(0);
   }

   public DimIntIdMap() {
      this(new Object2IntOpenHashMap<>(), new Int2ObjectOpenHashMap<>());
   }

   public ResourceKey<Level> fromIntegerId(int integerId) {
      ResourceKey<Level> result = this.fromIntegerId.get(integerId);
      if (result == null) {
         throw new RuntimeException("Missing Dimension " + integerId);
      } else {
         return result;
      }
   }

   @Nullable
   public ResourceKey<Level> fromIntegerIdNullable(int integerId) {
      return this.fromIntegerId.get(integerId);
   }

   public int toIntegerId(ResourceKey<Level> dim) {
      int result = this.toIntegerId.getInt(dim);
      if (result == Integer.MIN_VALUE) {
         throw new RuntimeException("Missing Dimension " + dim.identifier());
      } else {
         return result;
      }
   }

   public void add(ResourceKey<Level> dimId, int intId) {
      if (this.toIntegerId.containsKey(dimId)) {
         throw new RuntimeException("Dimension Id Record already contains " + dimId.identifier() + " " + this);
      }

      if (this.fromIntegerId.containsKey(intId)) {
         throw new RuntimeException("Dimension Id Record already contains " + intId + " " + this);
      }

      this.toIntegerId.put(dimId, intId);
      this.fromIntegerId.put(intId, dimId);
      this.maxId = Math.max(this.maxId, intId);
   }

   public boolean remove(ResourceKey<Level> dimId) {
      int intId = this.toIntegerId.removeInt(dimId);
      if (intId == Integer.MIN_VALUE) {
         return false;
      }

      this.fromIntegerId.remove(intId);
      return true;
   }

   public boolean removeUnused(Set<ResourceKey<Level>> currentDimIds) {
      boolean changed = false;

      for (ResourceKey<Level> dimId : this.toIntegerId.keySet()) {
         if (!currentDimIds.contains(dimId)) {
            this.remove(dimId);
            changed = true;
         }
      }

      return changed;
   }

   public boolean containsDimId(ResourceKey<Level> dimId) {
      return this.toIntegerId.containsKey(dimId);
   }

   public boolean containsIntId(int intId) {
      return this.fromIntegerId.containsKey(intId);
   }

   public static DimIntIdMap fromTag(CompoundTag tag) {
      CompoundTag intids = tag.getCompoundOrEmpty("intids");
      Object2IntOpenHashMap<ResourceKey<Level>> toIntegerId = new Object2IntOpenHashMap<>();
      Int2ObjectOpenHashMap<ResourceKey<Level>> fromIntegerId = new Int2ObjectOpenHashMap<>();
      intids.keySet().forEach(dim -> {
         if (intids.contains(dim)) {
            int intid = intids.getIntOr(dim, 0);
            ResourceKey<Level> dimId = Helper.dimIdToKey(dim);
            toIntegerId.put(dimId, intid);
            fromIntegerId.put(intid, dimId);
         }
      });
      return new DimIntIdMap(toIntegerId, fromIntegerId);
   }

   public CompoundTag toTag(Predicate<ResourceKey<Level>> filter) {
      CompoundTag intids = new CompoundTag();
      this.toIntegerId.forEach((key, intid) -> {
         if (filter.test((ResourceKey<Level>)key)) {
            intids.put(key.identifier().toString(), IntTag.valueOf(intid));
         }
      });
      CompoundTag result = new CompoundTag();
      result.put("intids", intids);
      return result;
   }

   public Set<ResourceKey<Level>> getDimIdSet() {
      return Collections.unmodifiableSet(this.toIntegerId.keySet());
   }

   public int getNextIntegerId() {
      return this.maxId + 1;
   }

   @Override
   public String toString() {
      return this.toIntegerId
         .object2IntEntrySet()
         .stream()
         .sorted(Comparator.comparingInt(e -> e.getIntValue()))
         .map(e -> e.getKey().identifier().toString() + " -> " + e.getIntValue())
         .collect(Collectors.joining("\n"));
   }
}
