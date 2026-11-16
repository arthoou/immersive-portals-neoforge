package qouteall.imm_ptl.core.compat;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import qouteall.q_misc_util.platform.EarlyLoaderDetection;

public class IPCompatMixinPlugin implements IMixinConfigPlugin {
   @Override
   public void onLoad(String mixinPackage) {
   }

   @Override
   public String getRefMapperConfig() {
      return null;
   }

   @Override
   public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
      if (mixinClassName.contains("Voxy")) {
         return !EarlyLoaderDetection.isModLoaded("voxy") ? false : !mixinClassName.contains("Iris") || EarlyLoaderDetection.isModLoaded("iris");
      }

      if (mixinClassName.contains("Euphoria")) {
         return EarlyLoaderDetection.isModLoaded("euphoria_patcher") && EarlyLoaderDetection.isModLoaded("iris");
      }

      if (mixinClassName.contains("Wakes")) {
         return EarlyLoaderDetection.isModLoaded("wakes");
      }

      if (mixinClassName.contains("Axiom")) {
         return EarlyLoaderDetection.isModLoaded("axiom");
      }

      if (!mixinClassName.contains("IrisSodium")) {
         if (mixinClassName.contains("Iris")) {
            return EarlyLoaderDetection.isModLoaded("iris");
         } else if (mixinClassName.contains("Sodium")) {
            return EarlyLoaderDetection.isModLoaded("sodium");
         } else if (mixinClassName.contains("Flywheel")) {
            return EarlyLoaderDetection.isModLoaded("flywheel");
         } else {
            return mixinClassName.contains("CardinalComp") ? EarlyLoaderDetection.isModLoaded("cardinal-components-base") : false;
         }
      } else {
         boolean sodiumLoaded = EarlyLoaderDetection.isModLoaded("sodium");
         boolean irisLoaded = EarlyLoaderDetection.isModLoaded("iris");
         return sodiumLoaded && irisLoaded;
      }
   }

   @Override
   public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
   }

   @Override
   public List<String> getMixins() {
      return null;
   }

   @Override
   public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
   }

   @Override
   public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
   }
}
