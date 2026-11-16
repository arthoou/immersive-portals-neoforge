package qouteall.dimlib.mixin.client;

import eu.midnightdust.lib.config.MidnightConfig;
import java.util.Objects;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.BackupConfirmScreen.Listener;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.dimlib.config.DimLibConfig;

@Mixin(BackupConfirmScreen.class)
public class MixinBackupConfirmScreen extends Screen {
   @Shadow
   @Final
   protected Listener onProceed;
   @Shadow
   private Checkbox eraseCache;
   @Shadow
   private MultiLineLabel message;
   @Unique
   private boolean dimlib_isExperimentalWarning = false;

   protected MixinBackupConfirmScreen(Component title) {
      super(title);
      throw new RuntimeException();
   }

   @Inject(
      method = "<init>(Ljava/lang/Runnable;Lnet/minecraft/client/gui/screens/BackupConfirmScreen$Listener;Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/Component;Z)V",
      at = @At("RETURN")
   )
   private void onInitEnd(Runnable runnable, Listener listener, Component component, Component component2, Component confirmation, boolean bl, CallbackInfo ci) {
      this.dimlib_isExperimentalWarning = Objects.equals(component, Component.translatable("selectWorld.backupQuestion.experimental"));
   }

   @Inject(method = "init", at = @At("RETURN"))
   private void onInitEnd(CallbackInfo ci) {
      if (this.dimlib_isExperimentalWarning) {
         int i = (this.message.getLineCount() + 1) * 9;
         this.addRenderableWidget(Button.builder(Component.translatable("dimlib.i_know_what_i_am_doing_and_disable_warning"), button -> {
            DimLibConfig.suppressExperimentalWarning = true;
            MidnightConfig.write("dimlib");
            this.onProceed.proceed(false, this.eraseCache.selected());
         }).bounds(this.width / 2 - 200, 124 + i + 40, 400, 20).build());
      }
   }
}
