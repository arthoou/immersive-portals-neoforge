package qouteall.imm_ptl.core.portal.custom_portal_gen.form;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import qouteall.imm_ptl.core.portal.nether_portal.BlockPortalShape;
import qouteall.imm_ptl.core.portal.nether_portal.NetherPortalGeneration;

public class ClassicalForm extends NetherPortalLikeForm {
   public static final MapCodec<ClassicalForm> CODEC = RecordCodecBuilder.mapCodec(
      instance -> instance.group(
            BuiltInRegistries.BLOCK.byNameCodec().fieldOf("from_frame_block").forGetter(o -> o.fromFrameBlock),
            BuiltInRegistries.BLOCK.byNameCodec().fieldOf("area_block").forGetter(o -> o.areaBlock),
            BuiltInRegistries.BLOCK.byNameCodec().fieldOf("to_frame_block").forGetter(o -> o.toFrameBlock),
            Codec.BOOL.fieldOf("generate_frame_if_not_found").forGetter(o -> o.generateFrameIfNotFound)
         )
         .apply(instance, instance.stable(ClassicalForm::new))
   );
   public final Block fromFrameBlock;
   public final Block areaBlock;
   public final Block toFrameBlock;

   public ClassicalForm(Block fromFrameBlock, Block areaBlock, Block toFrameBlock, boolean generateFrameIfNotFound) {
      super(generateFrameIfNotFound);
      this.fromFrameBlock = fromFrameBlock;
      this.areaBlock = areaBlock;
      this.toFrameBlock = toFrameBlock;
   }

   @Override
   public MapCodec<? extends PortalGenForm> getCodec() {
      return CODEC;
   }

   @Override
   public PortalGenForm getReverse() {
      return new ClassicalForm(this.toFrameBlock, this.areaBlock, this.fromFrameBlock, this.generateFrameIfNotFound);
   }

   @Override
   public void generateNewFrame(ServerLevel fromWorld, BlockPortalShape fromShape, ServerLevel toWorld, BlockPortalShape toShape) {
      NetherPortalGeneration.embodyNewFrame(toWorld, toShape, this.toFrameBlock.defaultBlockState());
   }

   @Override
   public Predicate<BlockState> getOtherSideFramePredicate() {
      return blockState -> blockState.getBlock() == this.toFrameBlock;
   }

   @Override
   public Predicate<BlockState> getThisSideFramePredicate() {
      return blockState -> blockState.getBlock() == this.fromFrameBlock;
   }

   @Override
   public Predicate<BlockState> getAreaPredicate() {
      return blockState -> blockState.getBlock() == this.areaBlock;
   }
}
