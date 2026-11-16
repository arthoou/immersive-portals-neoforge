package qouteall.q_misc_util.mixin;

import com.mojang.datafixers.DataFixer;
import java.lang.ref.WeakReference;
import java.net.Proxy;
import java.util.concurrent.Executor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.util.thread.ReentrantBlockableEventLoop;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.q_misc_util.MiscGlobals;
import qouteall.q_misc_util.dimension.DimensionIntId;
import qouteall.q_misc_util.ducks.IEMinecraftServer_Misc;

@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServer_Misc extends ReentrantBlockableEventLoop implements IEMinecraftServer_Misc {
   @Shadow
   @Final
   public LevelStorageAccess storageSource;
   @Shadow
   @Final
   private Executor executor;

   public MixinMinecraftServer_Misc(String string) {
      super(string);
      throw new RuntimeException();
   }

   @Shadow
   public abstract boolean isDedicatedServer();

   @Shadow
   protected abstract void waitUntilNextTick();

   @Inject(method = "<init>", at = @At("RETURN"))
   private void onConstruct(
      Thread thread,
      LevelStorageAccess levelStorageAccess,
      PackRepository packRepository,
      WorldStem worldStem,
      Proxy proxy,
      DataFixer dataFixer,
      Services services,
      LevelLoadListener levelLoadListener,
      CallbackInfo ci
   ) {
      MiscGlobals.refMinecraftServer = new WeakReference<>((MinecraftServer)(Object)this);
   }

   @Inject(method = "createLevels", at = @At("RETURN"))
   private void onWorldsCreated(CallbackInfo ci) {
      DimensionIntId.onServerStarted((MinecraftServer)(Object)this);
   }

   @Override
   public LevelStorageAccess ip_getStorageSource() {
      return this.storageSource;
   }
}
