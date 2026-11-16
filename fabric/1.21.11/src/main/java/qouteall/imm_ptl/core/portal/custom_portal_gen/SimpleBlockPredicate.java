package qouteall.imm_ptl.core.portal.custom_portal_gen;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SimpleBlockPredicate implements Predicate<BlockState> {
   public static final SimpleBlockPredicate pass = new SimpleBlockPredicate();
   public final String name;
   @Nullable
   private final TagKey<Block> tag;
   @Nullable
   private final Holder<Block> blockHolder;
   public static final Codec<SimpleBlockPredicate> CODEC = new SimpleBlockPredicate.SimpleBlockPredicateCodec();

   public SimpleBlockPredicate(String name, @NotNull TagKey<Block> tag) {
      this.name = name;
      this.tag = tag;
      this.blockHolder = null;
   }

   public SimpleBlockPredicate(String name, @NotNull Holder<Block> blockHolder) {
      this.name = name;
      this.blockHolder = blockHolder;
      this.tag = null;
   }

   private SimpleBlockPredicate() {
      this.tag = null;
      this.blockHolder = null;
      this.name = "imm_ptl:pass";
   }

   public boolean test(BlockState blockState) {
      if (this.tag != null) {
         return blockState.is(this.tag);
      } else {
         return this.blockHolder != null ? blockState.getBlock() == this.blockHolder.value() : true;
      }
   }

   public static class AirPredicate extends SimpleBlockPredicate {
      public AirPredicate() {
         super("minecraft:air", Blocks.AIR.builtInRegistryHolder());
      }

      @Override
      public boolean test(BlockState blockState) {
         return blockState.isAir();
      }
   }

   private static class SimpleBlockPredicateCodec implements Codec<SimpleBlockPredicate> {
      @Override
      public <T> DataResult<Pair<SimpleBlockPredicate, T>> decode(DynamicOps<T> ops, T input) {
         if (ops instanceof RegistryOps<T> registryOps) {
            DataResult<String> stringValue = ops.getStringValue(input);
            if (stringValue.result().isEmpty()) {
               return DataResult.error(() -> "SimpleBlockPredicate should be string");
            }

            String str = stringValue.result().get();
            Optional<HolderGetter<Block>> optionalGetter = registryOps.getter(Registries.BLOCK);
            if (optionalGetter.isEmpty()) {
               return DataResult.error(() -> "Missing block registry");
            }

            HolderGetter<Block> getter = optionalGetter.get();
            if (str.startsWith("#")) {
               String blockTagIdStr = str.substring(1);
               DataResult<Identifier> bockTagRl = Identifier.read(blockTagIdStr);
               if (bockTagRl.result().isEmpty()) {
                  return DataResult.error(() -> "Invalid block tag id:" + blockTagIdStr);
               }

               Identifier resourceLocation = bockTagRl.result().get();
               TagKey<Block> tagKey = TagKey.create(Registries.BLOCK, resourceLocation);
               return DataResult.success(Pair.of(new SimpleBlockPredicate(str, tagKey), ops.empty()), Lifecycle.stable());
            } else {
               DataResult<Identifier> rl = Identifier.read(str);
               if (rl.result().isEmpty()) {
                  return DataResult.error(() -> "Invalid resource location:" + str);
               } else {
                  Identifier resourceLocation = rl.result().get();
                  if (resourceLocation.toString().equals("minecraft:air")) {
                     return DataResult.success(Pair.of(new SimpleBlockPredicate.AirPredicate(), ops.empty()), Lifecycle.stable());
                  } else {
                     Optional<Reference<Block>> blockRef = getter.get(ResourceKey.create(Registries.BLOCK, resourceLocation));
                     if (blockRef.isEmpty()) {
                        TagKey<Block> tagKey = TagKey.create(Registries.BLOCK, resourceLocation);
                        return DataResult.success(Pair.of(new SimpleBlockPredicate(str, tagKey), ops.empty()), Lifecycle.stable());
                     } else {
                        return DataResult.success(Pair.of(new SimpleBlockPredicate(str, blockRef.get()), ops.empty()), Lifecycle.stable());
                     }
                  }
               }
            }
         } else {
            return DataResult.error(() -> "To deserialize SimpleBlockPredicate, the DynamicOps must be RegistryOps");
         }
      }

      public <T> DataResult<T> encode(SimpleBlockPredicate input, DynamicOps<T> ops, T prefix) {
         return DataResult.success(ops.createString(input.name), Lifecycle.stable());
      }
   }
}
