package qouteall.imm_ptl.core.ducks;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.server.level.ChunkMap.TrackedEntity;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

public interface IEChunkMap {
   int ip_getPlayerViewDistance(ServerPlayer var1);

   ServerLevel ip_getWorld();

   ThreadedLevelLightEngine ip_getLightingProvider();

   ChunkHolder ip_getChunkHolder(long var1);

   void ip_onPlayerUnload(ServerPlayer var1);

   void ip_onDimensionRemove();

   void ip_resendSpawnPacketToTrackers(Entity var1);

   Int2ObjectMap<TrackedEntity> ip_getEntityTrackerMap();

   @Nullable
   ChunkHolder ip_getUpdatingChunkIfPresent(long var1);
}
