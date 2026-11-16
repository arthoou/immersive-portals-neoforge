package qouteall.imm_ptl.peripheral.mixin.client.portal_wand;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.peripheral.wand.PortalWandItem;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer_PortalWand {
   @Inject(
      method = {"lambda$addMainPass$1", "method_62214"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/gizmos/DrawableGizmoPrimitives;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/renderer/state/CameraRenderState;Lorg/joml/Matrix4f;)V",
         shift = Shift.AFTER
      ),
      require = 1
   )
   private void onRender(
      CallbackInfo ci, @Local PoseStack poseStack, @Local(ordinal = 0) BufferSource bufferSource, @Local(argsOnly = true) LevelRenderState levelRenderState
   ) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         ItemStack itemStack = player.getMainHandItem();
         if (itemStack.getItem() == PortalWandItem.instance) {
            Vec3 camPos = levelRenderState.cameraRenderState.pos;
            PortalWandItem.clientRender(player, itemStack, poseStack, bufferSource, camPos.x, camPos.y, camPos.z);
         }
      }
   }
}
