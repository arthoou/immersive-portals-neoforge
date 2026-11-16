package qouteall.imm_ptl.core.mixin.common.interaction;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.block_manipulation.BlockManipulationServer;

@Mixin(ServerPlayerGameMode.class)
public class MixinServerPlayerGameMode {
   @Shadow
   protected ServerLevel level;
   @Shadow
   @Final
   protected ServerPlayer player;
   @Shadow
   private boolean hasDelayedDestroy;
   @Shadow
   private boolean isDestroyingBlock;
   @Unique
   private ServerLevel ip_destroyPosLevel;

   @Unique
   private ServerLevel ip_getActualWorld() {
      BlockManipulationServer.Context redirect = BlockManipulationServer.REDIRECT_CONTEXT.get();
      return redirect != null ? redirect.world() : this.level;
   }

   @Redirect(
      method = {"incrementDestroyProgress", "handleBlockBreakAction", "destroyAndAck", "destroyBlock"},
      at = @At(value = "FIELD", opcode = 180, target = "Lnet/minecraft/server/level/ServerPlayerGameMode;level:Lnet/minecraft/server/level/ServerLevel;")
   )
   private ServerLevel redirectGetLevel(ServerPlayerGameMode serverPlayerGameMode) {
      return this.ip_getActualWorld();
   }

   @Redirect(
      method = {"incrementDestroyProgress", "handleBlockBreakAction"},
      at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;level()Lnet/minecraft/server/level/ServerLevel;")
   )
   private ServerLevel redirectGetLevel(ServerPlayer instance) {
      return this.ip_getActualWorld();
   }

   @Redirect(
      method = "useItemOn",
      at = @At(
         value = "NEW",
         target = "(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/item/context/UseOnContext;"
      )
   )
   private UseOnContext redirectNewUseOnContext(Player player, InteractionHand interactionHand, BlockHitResult blockHitResult) {
      return new UseOnContext(this.ip_getActualWorld(), player, interactionHand, player.getItemInHand(interactionHand), blockHitResult);
   }

   @WrapOperation(
      method = "handleBlockBreakAction",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;isWithinBlockInteractionRange(Lnet/minecraft/core/BlockPos;D)Z")
   )
   private boolean wrapDistanceInHandleBlockBreakAction(ServerPlayer instance, BlockPos blockPos, double v, Operation<Boolean> original) {
      BlockManipulationServer.Context redirect = BlockManipulationServer.REDIRECT_CONTEXT.get();
      return redirect != null ? true : (Boolean)original.call(new Object[]{instance, blockPos, v});
   }

   @Inject(
      method = "handleBlockBreakAction",
      at = @At(value = "FIELD", opcode = 181, target = "Lnet/minecraft/server/level/ServerPlayerGameMode;destroyPos:Lnet/minecraft/core/BlockPos;")
   )
   private void onSetDestroyPos(BlockPos pos, Action action, Direction face, int maxBuildHeight, int sequence, CallbackInfo ci) {
      this.ip_destroyPosLevel = this.ip_getActualWorld();
   }

   @Inject(
      method = "handleBlockBreakAction",
      at = @At(value = "FIELD", opcode = 181, target = "Lnet/minecraft/server/level/ServerPlayerGameMode;delayedDestroyPos:Lnet/minecraft/core/BlockPos;")
   )
   private void onSetDelayedDestroyPos(BlockPos pos, Action action, Direction face, int maxBuildHeight, int sequence, CallbackInfo ci) {
      this.ip_destroyPosLevel = this.ip_getActualWorld();
   }

   @Redirect(
      method = "tick",
      at = @At(value = "FIELD", opcode = 180, target = "Lnet/minecraft/server/level/ServerPlayerGameMode;level:Lnet/minecraft/server/level/ServerLevel;")
   )
   private ServerLevel redirectLevelOnTicking(ServerPlayerGameMode instance) {
      return this.ip_destroyPosLevel != null ? this.ip_destroyPosLevel : this.level;
   }

   @Inject(method = "tick", at = @At("RETURN"))
   private void onTickingEnd(CallbackInfo ci) {
      if (!this.hasDelayedDestroy && !this.isDestroyingBlock) {
         this.ip_destroyPosLevel = null;
      }
   }
}
