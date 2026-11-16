package qouteall.imm_ptl.core.mixin.common.collision;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import qouteall.imm_ptl.core.ducks.IEEntity;

@Mixin(Player.class)
public abstract class MixinPlayer_Collision {
   @Overwrite
   public boolean canPlayerFitWithinBlocksAndEntitiesWhen(Pose pose) {
      LivingEntity this_ = (LivingEntity)(Object)this;
      AABB box = this_.getDimensions(pose).makeBoundingBox(this_.position());
      AABB activeCollisionBox = ((IEEntity)this_).ip_getActiveCollisionBox(box);
      return activeCollisionBox == null ? true : this_.level().noCollision(this_, activeCollisionBox.deflate(1.0E-7));
   }
}
