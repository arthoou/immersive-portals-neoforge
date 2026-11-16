package qouteall.imm_ptl.core.mixin.common;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.storage.WritableLevelData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.ducks.IEWorld;

@Mixin(Level.class)
public abstract class MixinLevel implements IEWorld {
   @Shadow
   @Final
   protected WritableLevelData levelData;
   @Shadow
   protected float rainLevel;
   @Shadow
   protected float thunderLevel;
   @Shadow
   protected float oRainLevel;
   @Shadow
   protected float oThunderLevel;
   @Shadow
   @Final
   private Thread thread;

   @Shadow
   public abstract ResourceKey<Level> dimension();

   @Shadow
   protected abstract LevelEntityGetter<Entity> getEntities();

   @Inject(method = "Lnet/minecraft/world/level/Level;prepareWeather()V", at = @At("TAIL"))
   private void onInitWeatherGradients(CallbackInfo ci) {
      if (this.dimension() == Level.NETHER) {
         this.rainLevel = 0.0F;
         this.oRainLevel = 0.0F;
         this.thunderLevel = 0.0F;
         this.oThunderLevel = 0.0F;
      }
   }

   @Override
   public WritableLevelData ip_getLevelData() {
      return this.levelData;
   }

   @Override
   public void portal_setWeather(float rainGradPrev, float rainGrad, float thunderGradPrev, float thunderGrad) {
      this.oRainLevel = rainGradPrev;
      this.rainLevel = rainGrad;
      this.oThunderLevel = thunderGradPrev;
      this.thunderLevel = thunderGrad;
   }

   @Override
   public LevelEntityGetter<Entity> portal_getEntityLookup() {
      return this.getEntities();
   }

   @Override
   public Thread portal_getThread() {
      return this.thread;
   }
}
