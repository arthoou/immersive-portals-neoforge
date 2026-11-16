package qouteall.dimlib.mixin.common;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import java.util.Map;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.Holder.Reference;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import qouteall.dimlib.ducks.IMappedRegistry;

@Mixin(MappedRegistry.class)
public abstract class MixinMappedRegistry<T> implements IMappedRegistry {
   @Shadow
   @Final
   private Map<Identifier, Reference<T>> byLocation;
   @Unique
   private static final Logger dimlib_LOGGER = LogUtils.getLogger();
   @Shadow
   @Final
   private ObjectList<Reference<T>> byId;
   @Shadow
   @Final
   private Reference2IntMap<T> toId;
   @Shadow
   @Final
   private Map<ResourceKey<T>, Reference<T>> byKey;
   @Shadow
   @Final
   ResourceKey<? extends Registry<T>> key;
   @Shadow
   @Final
   private Map<T, Reference<T>> byValue;
   @Shadow
   private boolean frozen;
   @Shadow
   @Final
   private Map<ResourceKey<T>, RegistrationInfo> registrationInfos;

   @Shadow
   @Nullable
   public abstract T byId(int var1);

   @Override
   public boolean dimlib_getIsFrozen() {
      return this.frozen;
   }

   @Override
   public void dimlib_setIsFrozen(boolean cond) {
      this.frozen = cond;
   }

   @Override
   public boolean dimlib_forceRemove(Identifier id) {
      dimlib_LOGGER.debug("[DimLib] Trying to remove {} from {}", id, this.key);
      Reference<T> holder = this.byLocation.remove(id);
      if (holder == null) {
         dimlib_LOGGER.debug("[DimLib] {} not found in {} when trying to remove", id, this.key);
         return false;
      }

      ResourceKey<T> eleKey = holder.key();
      T value = holder.value();
      int intId = this.toId.getInt(value);
      if (intId == -1) {
         dimlib_LOGGER.error("[DimLib] missing integer id for {} {}", value, id);
      } else {
         this.toId.removeInt(value);
         this.byId.set(intId, null);
      }

      this.byKey.remove(eleKey);
      this.byValue.remove(value);
      this.registrationInfos.remove(eleKey);
      return true;
   }
}
