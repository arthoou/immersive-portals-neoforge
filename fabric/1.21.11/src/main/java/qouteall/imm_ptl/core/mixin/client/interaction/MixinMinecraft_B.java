package qouteall.imm_ptl.core.mixin.client.interaction;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.block_manipulation.BlockManipulationClient;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft_B {
   @Shadow
   public ClientLevel level;
   @Shadow
   public HitResult hitResult;
   @Shadow
   protected int missTime;

   @Shadow
   protected abstract void pickBlock();

   @Shadow
   protected abstract boolean startAttack();

   @Shadow
   protected abstract void continueAttack(boolean var1);

   @Shadow
   protected abstract void startUseItem();

   @WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;startAttack()Z"))
   private boolean wrapStartAttack(Minecraft instance, Operation<Boolean> original) {
      ClientLevel remoteWorld = BlockManipulationClient.getRemotePointedWorld();
      if (BlockManipulationClient.isPointingToPortal()) {
         BlockManipulationClient.withSwitchedContext(() -> (Boolean)original.call(new Object[]{instance}), false);
         return false;
      } else {
         return (Boolean)original.call(new Object[]{instance});
      }
   }

   @WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;continueAttack(Z)V"))
   private void wrapContinueAttack(Minecraft instance, boolean leftClick, Operation<Void> original) {
      if (BlockManipulationClient.isPointingToPortal()) {
         BlockManipulationClient.withSwitchedContext(() -> {
            original.call(new Object[]{instance, leftClick});
            return null;
         }, false);
      } else {
         original.call(new Object[]{instance, leftClick});
      }
   }

   @WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;startUseItem()V"))
   private void wrapStartUseItem(Minecraft instance, Operation<Void> original) {
      if (BlockManipulationClient.isPointingToPortal()) {
         BlockManipulationClient.withSwitchedContext(() -> {
            original.call(new Object[]{instance});
            return null;
         }, true);
      } else {
         original.call(new Object[]{instance});
      }
   }

   @WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;pickBlock()V"))
   private void wrapPickBlock(Minecraft instance, Operation<Void> original) {
      if (BlockManipulationClient.isPointingToPortal()) {
         ClientLevel remoteWorld = ClientWorldLoader.getWorld(BlockManipulationClient.remotePointedDim);
         ClientLevel oldWorld = this.level;
         HitResult oldTarget = this.hitResult;
         this.level = remoteWorld;
         this.hitResult = BlockManipulationClient.remoteHitResult;

         try {
            original.call(new Object[]{instance});
         } finally {
            this.level = oldWorld;
            this.hitResult = oldTarget;
         }
      } else {
         original.call(new Object[]{instance});
      }
   }
}
