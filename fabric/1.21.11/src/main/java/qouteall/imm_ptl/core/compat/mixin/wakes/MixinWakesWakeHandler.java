package qouteall.imm_ptl.core.compat.mixin.wakes;

import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com/goby56/wakes/simulation/WakeHandler", remap = false)
public abstract class MixinWakesWakeHandler {
   @Shadow(remap = false)
   public Level world;
   @Shadow(remap = false)
   @Final
   private ArrayList<?> splashPlanes;

   @Shadow(remap = false)
   protected abstract void reset();

   @Inject(method = "init", at = @At("HEAD"), cancellable = true, remap = false)
   private static void ip_onlyClientLevels(Level level, CallbackInfo ci) {
      if (!(level instanceof ClientLevel)) {
         ci.cancel();
      }
   }

   @Inject(method = "tick", at = @At("HEAD"), remap = false)
   private void ip_followTheCurrentLevelOnTick(CallbackInfo ci) {
      this.ip_followTheCurrentLevel();
   }

   @Inject(method = "insert", at = @At("HEAD"), remap = false)
   private void ip_followTheCurrentLevelOnInsert(CallbackInfo ci) {
      this.ip_followTheCurrentLevel();
   }

   @Unique
   private void ip_followTheCurrentLevel() {
      ClientLevel current = Minecraft.getInstance().level;
      if (current != null && this.world != current) {
         this.reset();
         this.splashPlanes.clear();
         this.world = current;
      }
   }
}
