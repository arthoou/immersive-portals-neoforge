package qouteall.imm_ptl.core.mixin.common.container_gui;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import qouteall.imm_ptl.core.block_manipulation.BlockManipulationServer;

@Mixin(Container.class)
public interface MixinContainer {
   @WrapOperation(
      method = "stillValidBlockEntity(Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/player/Player;F)Z",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isWithinBlockInteractionRange(Lnet/minecraft/core/BlockPos;D)Z")
   )
   private static boolean wrapCanInteractWithBlock(
      Player player, BlockPos blockPos, double distance, Operation<Boolean> operation, @Local BlockEntity blockEntity
   ) {
      Boolean originalResult = (Boolean)operation.call(new Object[]{player, blockPos, distance});
      if (!originalResult) {
         BlockPos targetPos = blockEntity.getBlockPos();
         Level targetWorld = blockEntity.getLevel();
         return BlockManipulationServer.validateReach(player, targetWorld, targetPos);
      } else {
         return true;
      }
   }
}
