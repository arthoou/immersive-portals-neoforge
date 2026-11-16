package qouteall.imm_ptl.core.mixin.common.interaction;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import qouteall.imm_ptl.core.portal.PortalUtils;

@Mixin(BucketItem.class)
public class MixinBucketItem {
   @Redirect(
      method = "use",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/item/BucketItem;getPlayerPOVHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/ClipContext$Fluid;)Lnet/minecraft/world/phys/BlockHitResult;"
      )
   )
   private BlockHitResult redirectRayTrace(Level level, Player player, Fluid fluid, @Local(argsOnly = true) LocalRef<Level> worldRef) {
      double distance = 5.0;
      Vec3 eyePos = player.getEyePosition();
      Vec3 viewVector = player.getViewVector(1.0F);
      PortalUtils.PortalAwareRaytraceResult r = PortalUtils.portalAwareRayTraceFull(
         player.level(), eyePos, viewVector, distance, player, Block.OUTLINE, fluid, new ArrayList<>(), 1
      );
      if (r != null) {
         worldRef.set(r.world());
         return r.hitResult();
      } else {
         Vec3 dest = eyePos.add(viewVector.scale(distance));
         return BlockHitResult.miss(dest, Direction.getApproximateNearest(viewVector.x, viewVector.y, viewVector.z), BlockPos.containing(dest));
      }
   }
}
