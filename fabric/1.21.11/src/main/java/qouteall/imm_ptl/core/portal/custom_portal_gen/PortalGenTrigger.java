package qouteall.imm_ptl.core.portal.custom_portal_gen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.function.Function;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import qouteall.imm_ptl.core.McHelper;

public abstract class PortalGenTrigger {
   public static final Codec<PortalGenTrigger> triggerCodec;
   public static final Registry<MapCodec<? extends PortalGenTrigger>> codecRegistry;
   public static final MapCodec<PortalGenTrigger.UseItemTrigger> useItemTriggerCodec = RecordCodecBuilder.mapCodec(
      instance -> instance.group(
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(o -> o.item), Codec.BOOL.optionalFieldOf("consume", false).forGetter(o -> o.consume)
         )
         .apply(instance, instance.stable(PortalGenTrigger.UseItemTrigger::new))
   );
   public static final MapCodec<PortalGenTrigger.ThrowItemTrigger> throwItemTriggerCodec = RecordCodecBuilder.mapCodec(
      instance -> instance.group(BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(o -> o.item))
         .apply(instance, instance.stable(PortalGenTrigger.ThrowItemTrigger::new))
   );
   public static final MapCodec<PortalGenTrigger.ConventionalDimensionChangeTrigger> conventionalDimensionChangeCodec = MapCodec.unit(
      PortalGenTrigger.ConventionalDimensionChangeTrigger::new
   );

   public abstract MapCodec<? extends PortalGenTrigger> getCodec();

   static {
      codecRegistry = new MappedRegistry<>(
         ResourceKey.createRegistryKey(McHelper.newResourceLocation("imm_ptl:custom_portal_gen_trigger")),
         Lifecycle.stable()
      );
      Registry.register(codecRegistry, McHelper.newResourceLocation("imm_ptl:use_item"), useItemTriggerCodec);
      Registry.register(codecRegistry, McHelper.newResourceLocation("imm_ptl:throw_item"), throwItemTriggerCodec);
      Registry.register(
         codecRegistry,
         McHelper.newResourceLocation("imm_ptl:conventional_dimension_change"),
         PortalGenTrigger.ConventionalDimensionChangeTrigger.conventionalDimensionChangeCodec
      );
      triggerCodec = codecRegistry.byNameCodec().dispatchStable(PortalGenTrigger::getCodec, Function.identity());
   }

   public static class ConventionalDimensionChangeTrigger extends PortalGenTrigger {
      @Override
      public MapCodec<? extends PortalGenTrigger> getCodec() {
         return conventionalDimensionChangeCodec;
      }
   }

   public static class ThrowItemTrigger extends PortalGenTrigger {
      public final Item item;

      public ThrowItemTrigger(Item item) {
         this.item = item;
      }

      @Override
      public MapCodec<? extends PortalGenTrigger> getCodec() {
         return throwItemTriggerCodec;
      }
   }

   public static class UseItemTrigger extends PortalGenTrigger {
      public final Item item;
      public final boolean consume;

      public UseItemTrigger(Item item, boolean consume) {
         this.item = item;
         this.consume = consume;
      }

      public boolean shouldConsume(UseOnContext context) {
         if (!this.consume) {
            return false;
         }

         Player player = context.getPlayer();
         return player == null || !player.isCreative();
      }

      @Override
      public MapCodec<? extends PortalGenTrigger> getCodec() {
         return useItemTriggerCodec;
      }
   }
}
