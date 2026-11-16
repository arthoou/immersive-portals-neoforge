package qouteall.imm_ptl.peripheral.mixin.client.dim_stack;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.GridLayout.RowHelper;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen.MoreTab;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import qouteall.imm_ptl.peripheral.ducks.IECreateWorldScreen;

@Mixin(MoreTab.class)
public class MixinCreateWorldScreenMoreTab_CVB {
   @Inject(method = "<init>", at = @At("RETURN"), locals = LocalCapture.CAPTURE_FAILHARD)
   private void onInitEnd(CreateWorldScreen createWorldScreen, CallbackInfo ci, RowHelper rowHelper) {
      rowHelper.addChild(
         Button.builder(Component.translatable("imm_ptl.altius_screen_button"), button -> ((IECreateWorldScreen)createWorldScreen).ip_openDimStackScreen())
            .width(210)
            .build()
      );
   }
}
