package qouteall.imm_ptl.core.mixin.common.collision;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier.StepBasedCollector;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.IPMcHelper;
import qouteall.imm_ptl.core.api.ImmPtlEntityExtension;
import qouteall.imm_ptl.core.collision.PortalCollisionHandler;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;
import qouteall.imm_ptl.core.portal.EndPortalEntity;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.CountDownInt;

@Mixin(Entity.class)
public abstract class MixinEntity implements IEEntity, ImmPtlEntityExtension {
   @Unique
   @Nullable
   private PortalCollisionHandler ip_portalCollisionHandler;
   @Shadow
   private Level level;
   @Shadow
   public int tickCount;
   @Shadow
   private Vec3 position;
   @Shadow
   private BlockPos blockPosition;
   @Shadow
   private ChunkPos chunkPosition;
   @Shadow
   @Final
   private static Logger LOGGER;
   @Shadow
   @Nullable
   private BlockState inBlockState;
   @Unique
   private static final CountDownInt IMM_PTL_LOG_COUNTER = new CountDownInt(20);

   @Shadow
   protected abstract Vec3 collide(Vec3 var1);

   @Shadow
   public abstract Component getName();

   @Shadow
   public abstract double getX();

   @Shadow
   public abstract double getY();

   @Shadow
   public abstract double getZ();

   @Shadow
   protected abstract void unsetRemoved();

   @Redirect(
      method = "Lnet/minecraft/world/entity/Entity;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;")
   )
   private Vec3 redirectHandleCollisions(Entity entity, Vec3 attemptedMove) {
      if (IPGlobal.enableServerCollision || entity.level().isClientSide()) {
         if (attemptedMove.lengthSqr() > 3600.0) {
            if (IMM_PTL_LOG_COUNTER.tryDecrement()) {
               LOGGER.error(
                  "[ImmPtl] Skipping collision calculation because entity moves too fast {} {} {}",
                  entity,
                  attemptedMove,
                  entity.level().getGameTime(),
                  new Throwable()
               );
            }

            return Vec3.ZERO;
         } else if (IPGlobal.crossPortalCollision && this.ip_portalCollisionHandler != null && this.ip_portalCollisionHandler.hasCollisionEntry()) {
            Vec3 result = this.ip_portalCollisionHandler.handleCollision((Entity)(Object)this, attemptedMove);
            if (result.lengthSqr() > 400.0) {
               if (IMM_PTL_LOG_COUNTER.tryDecrement()) {
                  LOGGER.error("[ImmPtl] cross portal collision result too large {} {} {}", this, attemptedMove, result);
               }

               return Vec3.ZERO;
            } else {
               return result;
            }
         } else {
            return this.collide(attemptedMove);
         }
      } else {
         return entity instanceof Player ? attemptedMove : Vec3.ZERO;
      }
   }

   @Inject(method = "Lnet/minecraft/world/entity/Entity;fireImmune()Z", at = @At("HEAD"), cancellable = true)
   private void onIsFireImmune(CallbackInfoReturnable<Boolean> cir) {
      if (this.ip_getCollidingPortal() instanceof EndPortalEntity) {
         cir.setReturnValue(true);
         cir.cancel();
      }
   }

   @Shadow
   protected abstract AABB makeBoundingBox(Vec3 var1);

   @WrapOperation(
      method = "checkInsideBlocks(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/entity/InsideBlockEffectApplier$StepBasedCollector;Lit/unimi/dsi/fastutil/longs/LongSet;I)I",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;makeBoundingBox(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/AABB;")
   )
   private AABB redirectBoundingBoxInCheckingBlockCollision(Entity entity, Vec3 pos, Operation<AABB> original) {
      return this.ip_getActiveCollisionBox((AABB)original.call(new Object[]{entity, pos}));
   }

   @Inject(
      method = "checkInsideBlocks(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/entity/InsideBlockEffectApplier$StepBasedCollector;Lit/unimi/dsi/fastutil/longs/LongSet;I)I",
      at = @At("HEAD"),
      cancellable = true
   )
   private void onCheckInsideBlocks(Vec3 from, Vec3 to, StepBasedCollector collector, LongSet visited, int steps, CallbackInfoReturnable<Integer> cir) {
      if (this.ip_getActiveCollisionBox(this.makeBoundingBox(to)) == null) {
         cir.setReturnValue(0);
      }
   }

   @Inject(method = "Lnet/minecraft/world/entity/Entity;isInWall()Z", at = @At("HEAD"), cancellable = true)
   private void onIsInsideWall(CallbackInfoReturnable<Boolean> cir) {
      if (this.ip_isRecentlyCollidingWithPortal()) {
         cir.setReturnValue(false);
      }
   }

   @Inject(method = "Lnet/minecraft/world/entity/Entity;setPosRaw(DDD)V", at = @At("HEAD"))
   private void onSetPos(double nx, double ny, double nz, CallbackInfo ci) {
      Entity this_ = (Entity)(Object)this;
      if (this_ instanceof Player
         && IPGlobal.teleportationDebugEnabled
         && (Math.abs(this.getX() - nx) > 10.0 || Math.abs(this.getY() - ny) > 10.0 || Math.abs(this.getZ() - nz) > 10.0)) {
         Helper.log(
            String.format(
               "%s %s teleported from %s %s %s to %s %s %s",
               this.getName().getContents(),
               this.level.dimension(),
               (int)this.getX(),
               (int)this.getY(),
               (int)this.getZ(),
               (int)nx,
               (int)ny,
               (int)nz
            )
         );
         new Throwable().printStackTrace();
      }
   }

   @Inject(method = "getInBlockState", at = @At("HEAD"), cancellable = true)
   private void onGetInBlockState(CallbackInfoReturnable<BlockState> cir) {
      Portal collidingPortal = this.ip_getCollidingPortal();
      Entity this_ = (Entity)(Object)this;
      if (collidingPortal != null && collidingPortal.getNormal().y > 0.0) {
         BlockPos remoteLandingPos = BlockPos.containing(collidingPortal.transformPoint(this_.position()));
         Level destinationWorld = collidingPortal.getDestinationWorld();
         if (destinationWorld.hasChunkAt(remoteLandingPos)) {
            BlockState result = destinationWorld.getBlockState(remoteLandingPos);
            if (!result.isAir()) {
               cir.setReturnValue(result);
               cir.cancel();
            }
         }
      }
   }

   @Override
   public Portal ip_getCollidingPortal() {
      if (this.ip_portalCollisionHandler == null) {
         return null;
      } else {
         return this.ip_portalCollisionHandler.portalCollisions.isEmpty() ? null : this.ip_portalCollisionHandler.portalCollisions.get(0).portal;
      }
   }

   @Override
   public void ip_tickCollidingPortal() {
      Entity this_ = (Entity)(Object)this;
      if (this.ip_portalCollisionHandler != null) {
         this.ip_portalCollisionHandler.update(this_);
      }

      if (this.level.isClientSide()) {
         IPMcHelper.onClientEntityTick(this_);
      }
   }

   @Override
   public void ip_notifyCollidingWithPortal(Entity portal) {
      Entity this_ = (Entity)(Object)this;
      if (this.ip_portalCollisionHandler == null) {
         this.ip_portalCollisionHandler = new PortalCollisionHandler();
      }

      this.ip_portalCollisionHandler.notifyCollidingWithPortal(this_, (Portal)portal);
   }

   @Override
   public boolean ip_isCollidingWithPortal() {
      return this.ip_portalCollisionHandler == null ? false : this.ip_portalCollisionHandler.hasCollisionEntry();
   }

   @Override
   public boolean ip_isRecentlyCollidingWithPortal() {
      return this.ip_portalCollisionHandler == null ? false : this.ip_portalCollisionHandler.isRecentlyCollidingWithPortal((Entity)(Object)this);
   }

   @Override
   public void ip_unsetRemoved() {
      this.unsetRemoved();
   }

   @IPVanillaCopy
   @Override
   public void ip_setPositionWithoutTriggeringCallback(Vec3 newPos) {
      double x = newPos.x;
      double y = newPos.y;
      double z = newPos.z;
      if (this.position.x != x || this.position.y != y || this.position.z != z) {
         this.position = new Vec3(x, y, z);
         int bx = Mth.floor(x);
         int by = Mth.floor(y);
         int bz = Mth.floor(z);
         if (bx != this.blockPosition.getX() || by != this.blockPosition.getY() || bz != this.blockPosition.getZ()) {
            this.blockPosition = new BlockPos(bx, by, bz);
            this.inBlockState = null;
            if (SectionPos.blockToSectionCoord(bx) != this.chunkPosition.x || SectionPos.blockToSectionCoord(bz) != this.chunkPosition.z) {
               this.chunkPosition = new ChunkPos(this.blockPosition);
            }
         }
      }
   }

   @Override
   public void ip_clearCollidingPortal() {
      this.ip_portalCollisionHandler = null;
   }

   @Nullable
   @Override
   public AABB ip_getActiveCollisionBox(AABB originalBox) {
      Entity this_ = (Entity)(Object)this;
      return this.ip_portalCollisionHandler == null ? originalBox : this.ip_portalCollisionHandler.getActiveCollisionBox(this_, originalBox);
   }

   @Nullable
   @Override
   public PortalCollisionHandler ip_getPortalCollisionHandler() {
      return this.ip_portalCollisionHandler;
   }

   @Override
   public PortalCollisionHandler ip_getOrCreatePortalCollisionHandler() {
      if (this.ip_portalCollisionHandler == null) {
         this.ip_portalCollisionHandler = new PortalCollisionHandler();
      }

      return this.ip_portalCollisionHandler;
   }

   @Override
   public void ip_setPortalCollisionHandler(@Nullable PortalCollisionHandler handler) {
      this.ip_portalCollisionHandler = handler;
   }

   @Override
   public void ip_setWorld(Level world) {
      this.level = world;
   }

   private long ip_getStableTiming() {
      return this.tickCount;
   }
}
