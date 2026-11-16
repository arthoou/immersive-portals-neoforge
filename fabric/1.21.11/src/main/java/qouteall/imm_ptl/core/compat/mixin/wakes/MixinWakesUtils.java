package qouteall.imm_ptl.core.compat.mixin.wakes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com/goby56/wakes/utils/WakesUtils", remap = false)
public class MixinWakesUtils {
   @Inject(method = {"placeWakeTrail", "placeFallSplash"}, at = @At("HEAD"), cancellable = true, remap = false)
   private static void ip_onPlaceForEntity(Entity entity, CallbackInfo ci) {
      if (ip_isInAnotherLevel(entity)) {
         ci.cancel();
      }
   }

   @Inject(method = "spawnSplashPlane", at = @At("HEAD"), cancellable = true, remap = false)
   private static void ip_onSpawnSplashPlane(Level level, Entity owner, CallbackInfo ci) {
      if (ip_isInAnotherLevel(owner)) {
         ci.cancel();
      }
   }

   @Unique
   private static boolean ip_isInAnotherLevel(Entity entity) {
      LocalPlayer player = Minecraft.getInstance().player;
      return player != null && entity.level() != player.level();
   }
}
