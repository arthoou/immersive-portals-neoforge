package qouteall.imm_ptl.core.mixin.common.collision;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Projectile.class)
public abstract class MixinProjectile extends MixinEntity {
   @WrapOperation(
      method = "getOwner",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/entity/EntityReference;getEntity(Lnet/minecraft/world/entity/EntityReference;Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/entity/Entity;"
      )
   )
   private Entity wrapGetOwnerEntity(EntityReference<Entity> owner, Level level, Operation<Entity> original) {
      Entity entity = (Entity)original.call(new Object[]{owner, level});
      if (entity == null && owner != null) {
         if (level instanceof ServerLevel serverLevel) {
            MinecraftServer server = serverLevel.getServer();

            for (ServerLevel world : server.getAllLevels()) {
               if (world != serverLevel) {
                  Entity found = EntityReference.getEntity(owner, world);
                  if (found != null) {
                     return found;
                  }
               }
            }
         }

         return null;
      } else {
         return entity;
      }
   }
}
