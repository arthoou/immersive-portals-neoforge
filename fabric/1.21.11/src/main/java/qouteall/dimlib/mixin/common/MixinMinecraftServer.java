package qouteall.dimlib.mixin.common;

import com.google.common.collect.Maps;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.thread.ReentrantBlockableEventLoop;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;
import org.apache.commons.lang3.Validate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.dimlib.api.DimensionAPI;
import qouteall.dimlib.ducks.IMinecraftServer;

@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServer extends ReentrantBlockableEventLoop<TickTask> implements IMinecraftServer {
   @Mutable
   @Shadow
   @Final
   private Map<ResourceKey<Level>, ServerLevel> levels;
   @Shadow
   @Final
   protected LevelStorageAccess storageSource;
   @Shadow
   @Final
   private Executor executor;
   @Unique
   private boolean ip_canDirectlyRegisterDimension = false;
   @Unique
   private boolean ip_finishedCreatingWorlds = false;
   @Unique
   private List<Runnable> dimlib_taskList;

   public MixinMinecraftServer(String name) {
      super(name);
      throw new RuntimeException();
   }

   @Shadow
   public abstract boolean isStopped();

   @Shadow
   public abstract boolean isDedicatedServer();

   @Shadow
   protected abstract void waitUntilNextTick();

   @Inject(method = "Lnet/minecraft/server/MinecraftServer;createLevels()V", at = @At("HEAD"))
   private void onBeforeCreateWorlds(CallbackInfo ci) {
      Validate.isTrue(!this.ip_canDirectlyRegisterDimension, "invalid server initialization status");
      this.ip_canDirectlyRegisterDimension = true;
      DimensionAPI.SERVER_DIMENSIONS_LOAD_EVENT.invoker().run((MinecraftServer)(Object)this);
      this.ip_canDirectlyRegisterDimension = false;
   }

   @Inject(method = "Lnet/minecraft/server/MinecraftServer;createLevels()V", at = @At("RETURN"))
   private void onFinishedLoadingAllWorlds(CallbackInfo ci) {
      this.ip_finishedCreatingWorlds = true;
   }

   @Override
   public void dimlib_addDimensionToWorldMap(ResourceKey<Level> dim, ServerLevel world) {
      LinkedHashMap<ResourceKey<Level>, ServerLevel> newMap = Maps.newLinkedHashMap();
      Map<ResourceKey<Level>, ServerLevel> oldMap = this.levels;
      newMap.putAll(oldMap);
      newMap.put(dim, world);
      this.levels = newMap;
   }

   @Override
   public void dimlib_removeDimensionFromWorldMap(ResourceKey<Level> dimension) {
      LinkedHashMap<ResourceKey<Level>, ServerLevel> newMap = Maps.newLinkedHashMap();
      Map<ResourceKey<Level>, ServerLevel> oldMap = this.levels;

      for (Entry<ResourceKey<Level>, ServerLevel> entry : oldMap.entrySet()) {
         if (entry.getKey() != dimension) {
            newMap.put(entry.getKey(), entry.getValue());
         }
      }

      this.levels = newMap;
   }

   @Override
   public boolean dimlib_getCanDirectlyRegisterDimensions() {
      return this.ip_canDirectlyRegisterDimension;
   }

   @Override
   public boolean dimlib_getIsFinishedCreatingWorlds() {
      return this.ip_finishedCreatingWorlds;
   }

   @Override
   public LevelStorageAccess dimlib_getStorageSource() {
      return this.storageSource;
   }

   @Override
   public Executor dimlib_getExecutor() {
      return this.executor;
   }

   @Override
   public void dimlib_waitUntilNextTick() {
      Validate.isTrue(!this.runningTask());
      this.waitUntilNextTick();
   }

   @Override
   public void dimlib_addTask(Runnable task) {
      if (this.dimlib_taskList == null) {
         this.dimlib_taskList = new ArrayList<>();
      }

      this.dimlib_taskList.add(task);
   }

   @Override
   public void dimlib_processTasks() {
      if (this.dimlib_taskList != null) {
         for (Runnable task : this.dimlib_taskList) {
            task.run();
         }

         this.dimlib_taskList = null;
      }
   }
}
