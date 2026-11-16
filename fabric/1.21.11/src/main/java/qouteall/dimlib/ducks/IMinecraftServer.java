package qouteall.dimlib.ducks;

import java.util.concurrent.Executor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;

public interface IMinecraftServer {
   LevelStorageAccess dimlib_getStorageSource();

   Executor dimlib_getExecutor();

   void dimlib_addDimensionToWorldMap(ResourceKey<Level> var1, ServerLevel var2);

   void dimlib_removeDimensionFromWorldMap(ResourceKey<Level> var1);

   void dimlib_waitUntilNextTick();

   boolean dimlib_getCanDirectlyRegisterDimensions();

   boolean dimlib_getIsFinishedCreatingWorlds();

   void dimlib_addTask(Runnable var1);

   void dimlib_processTasks();
}
