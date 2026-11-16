package qouteall.imm_ptl.core.mixin.common.miscellaneous;

import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import qouteall.imm_ptl.core.IPMcHelper;
import qouteall.q_misc_util.Helper;

@Mixin(BlockGetter.class)
public interface MixinBlockGetter {
   @ModifyVariable(method = "traverseBlocks", at = @At("HEAD"), argsOnly = true, index = 1)
   private static <T, C> Vec3 onTraverseBlocks(Vec3 originalArgument, Vec3 from, Vec3 _to, C context, BiFunction<C, BlockPos, T> tester, Function<C, T> onFail) {
      if (from.distanceToSqr(_to) > 262144.0) {
         IPMcHelper.limitedLogger.invoke(() -> Helper.LOGGER.error("Raycast too far", new Throwable()));
         return _to.subtract(from).normalize().scale(30.0).add(from);
      } else {
         return _to;
      }
   }
}
