package qouteall.imm_ptl.core.miscellaneous;

import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageWidget;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import qouteall.imm_ptl.core.platform_specific.IPConfig;

public class IPortalInitialScreen extends Screen {
   private static final int PAGE_NUM = 4;
   private final Runnable onClose;
   private final Button prevButton;
   private final StringWidget pageNumberWidget;
   private final Button iKnowButton;
   private final ImageWidget iconWidget;
   private final StringWidget titleWidget;
   private final MultiLineTextWidget contentWidget;
   private int currentPageIndex = 0;

   public IPortalInitialScreen(Runnable onClose) {
      super(Component.empty());
      this.onClose = onClose;
      this.prevButton = Button.builder(Component.translatable("iportal.initial_screen.prev"), button -> this.onPrevious()).build();
      this.pageNumberWidget = new IPortalInitialScreen.CenteredStringWidget(Component.empty(), this.font);
      this.iKnowButton = Button.builder(Component.translatable("iportal.initial_screen.i_know"), button -> this.onIKnow()).build();
      this.iconWidget = ImageWidget.texture(30, 30, Identifier.fromNamespaceAndPath("immersive_portals", "icon.png"), 30, 30);
      this.titleWidget = new IPortalInitialScreen.CenteredStringWidget(Component.translatable("iportal.initial_screen.title"), this.font);
      this.contentWidget = new MultiLineTextWidget(Component.empty(), this.font);
   }

   private void onPrevious() {
      if (this.currentPageIndex > 0) {
         this.currentPageIndex--;
         this.updateUiStatus(this.currentPageIndex);
      }
   }

   private void onIKnow() {
      if (this.currentPageIndex == 3) {
         IPConfig config = IPConfig.getConfig();
         config.initialScreenShown = true;
         config.saveConfigFile();
         this.onClose();
      } else {
         this.currentPageIndex++;
         this.updateUiStatus(this.currentPageIndex);
      }
   }

   private void updateUiStatus(int newPageIndex) {
      this.currentPageIndex = newPageIndex;
      this.prevButton.visible = newPageIndex > 0;
      this.pageNumberWidget.setMessage(Component.literal("%d / %d".formatted(newPageIndex + 1, 4)));
      this.contentWidget.setMessage(Component.translatable("iportal.initial_screen.content." + newPageIndex));
   }

   @Override
   public void onClose() {
      this.onClose.run();
   }

   @Override
   public void init() {
      this.contentWidget.setMaxWidth(this.width - 40);
      this.pageNumberWidget.setWidth(50);
      this.pageNumberWidget.setHeight(this.iKnowButton.getHeight());
      this.titleWidget.setHeight(this.iconWidget.getHeight());
      this.addRenderableWidget(this.prevButton);
      this.addRenderableWidget(this.pageNumberWidget);
      this.addRenderableWidget(this.iKnowButton);
      this.addRenderableWidget(this.iconWidget);
      this.addRenderableWidget(this.titleWidget);
      this.addRenderableWidget(this.contentWidget);
      HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, 60, 33);
      LinearLayout headerLayout = layout.addToHeader(LinearLayout.horizontal());
      headerLayout.addChild(this.iconWidget, headerLayout.defaultCellSetting().padding(5));
      headerLayout.addChild(this.titleWidget, headerLayout.defaultCellSetting().padding(5));
      LinearLayout contentLayout = layout.addToContents(LinearLayout.vertical());
      contentLayout.addChild(this.contentWidget, headerLayout.defaultCellSetting().padding(5));
      LinearLayout footerLayout = layout.addToFooter(LinearLayout.horizontal());
      footerLayout.addChild(this.prevButton, footerLayout.defaultCellSetting().padding(5));
      footerLayout.addChild(this.pageNumberWidget, footerLayout.defaultCellSetting().padding(5));
      footerLayout.addChild(this.iKnowButton, footerLayout.defaultCellSetting().padding(5));
      layout.arrangeElements();
      this.updateUiStatus(this.currentPageIndex);
   }

   private static class CenteredStringWidget extends StringWidget {
      public CenteredStringWidget(Component message, Font font) {
         super(message, font);
      }

      @Override
      public void visitLines(ActiveTextCollector collector) {
         Component message = this.getMessage();
         int textWidth = this.getFont().width(message);
         int x = this.getX() + Math.round(0.5F * (this.getWidth() - textWidth));
         int y = this.getY() + (this.getHeight() - 9) / 2;
         collector.accept(x, y, message.getVisualOrderText());
      }
   }
}
