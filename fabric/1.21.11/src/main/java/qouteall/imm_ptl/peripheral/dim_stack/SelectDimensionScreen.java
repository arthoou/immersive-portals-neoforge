package qouteall.imm_ptl.peripheral.dim_stack;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public class SelectDimensionScreen extends Screen {
   public final DimStackScreen parent;
   private DimListWidget dimListWidget;
   private Button confirmButton;
   private final Consumer<ResourceKey<Level>> outerCallback;
   private final List<ResourceKey<Level>> dimensionList;

   protected SelectDimensionScreen(DimStackScreen parent, Consumer<ResourceKey<Level>> callback, List<ResourceKey<Level>> dimensionList) {
      super(Component.translatable("imm_ptl.select_dimension"));
      this.parent = parent;
      this.outerCallback = callback;
      this.dimensionList = dimensionList;
   }

   @Override
   protected void init() {
      this.dimListWidget = new DimListWidget(this.width, this.height - 20 - 40, 20, 50, this, DimListWidget.Type.addDimensionList, null);
      this.addWidget(this.dimListWidget);
      Consumer<DimEntryWidget> callback = w -> this.dimListWidget.setSelected(w);

      for (ResourceKey<Level> dim : this.dimensionList) {
         this.dimListWidget.children().add(new DimEntryWidget(dim, this.dimListWidget, callback, new DimStackEntry(dim)));
      }

      this.confirmButton = this.addRenderableWidget(Button.builder(Component.translatable("imm_ptl.confirm_select_dimension"), buttonWidget -> {
         DimEntryWidget selected = this.dimListWidget.getSelected();
         if (selected != null) {
            Minecraft.getInstance().setScreen(this.parent);
            this.outerCallback.accept(selected.dimension);
         }
      }).pos(this.width / 2 - 75, this.height - 28).size(150, 20).build());
   }

   @Override
   public void onClose() {
      this.minecraft.setScreen(this.parent);
   }

   @Override
   public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
      super.render(guiGraphics, mouseX, mouseY, delta);
      this.dimListWidget.render(guiGraphics, mouseX, mouseY, delta);
      guiGraphics.drawCenteredString(this.font, this.title.getString(), this.width / 2, 10, -1);
   }
}
