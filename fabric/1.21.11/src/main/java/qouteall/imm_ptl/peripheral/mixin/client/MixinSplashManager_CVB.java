package qouteall.imm_ptl.peripheral.mixin.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.resources.SplashManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SplashManager.class)
public class MixinSplashManager_CVB {
   @Shadow
   private List<Component> splashes;

   @Inject(
      method = "apply(Ljava/util/List;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
      at = @At("RETURN")
   )
   private void onApply(List<Component> list, ResourceManager resourceManager, ProfilerFiller profiler, CallbackInfo ci) {
      List<Component> edited = new ArrayList<>(this.splashes);
      if (ip_removeFirst(edited, "Euclidian!")) {
         edited.add(Component.literal("Non-Euclidian!"));
      }

      if (ip_removeFirst(edited, "Slow acting portals!")) {
         edited.add(Component.literal("Fast acting portals!"));
         edited.add(Component.literal("Immersive Portals!"));
      }

      this.splashes = List.copyOf(edited);
   }

   private static boolean ip_removeFirst(List<Component> splashes, String text) {
      for (int i = 0; i < splashes.size(); i++) {
         if (splashes.get(i).getString().equals(text)) {
            splashes.remove(i);
            return true;
         }
      }

      return false;
   }
}
