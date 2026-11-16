package qouteall.imm_ptl.core.portal.animation;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.McHelper;
import qouteall.q_misc_util.Helper;

public interface PortalAnimationDriver {
   Map<Identifier, Function<CompoundTag, PortalAnimationDriver>> deserializerRegistry = new HashMap<>();

   static void registerDeserializer(Identifier key, Function<CompoundTag, PortalAnimationDriver> deserializer) {
      deserializerRegistry.put(key, deserializer);
   }

   @Nullable
   static PortalAnimationDriver fromTag(CompoundTag tag) {
      String type = tag.getStringOr("type", "");
      Function<CompoundTag, PortalAnimationDriver> deserializer = deserializerRegistry.get(McHelper.newResourceLocation(type));
      if (deserializer == null) {
         Helper.err("Unknown animation type " + type);
         return null;
      }

      try {
         return deserializer.apply(tag);
      } catch (Exception e) {
         e.printStackTrace();
         return null;
      }
   }

   CompoundTag toTag();

   @NotNull
   AnimationResult getAnimationResult(long var1, float var3, AnimationContext var4);

   @Nullable
   DeltaUnilateralPortalState getEndingResult(long var1, AnimationContext var3);

   default PortalAnimationDriver getFlippedVersion() {
      return this;
   }

   default Component getInfo() {
      return Component.literal("Unknown Animation");
   }
}
