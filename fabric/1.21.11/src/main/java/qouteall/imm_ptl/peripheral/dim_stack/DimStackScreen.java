package qouteall.imm_ptl.peripheral.dim_stack;

import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qouteall.imm_ptl.core.CHelper;
import qouteall.q_misc_util.my_util.GuiHelper;

@Environment(EnvType.CLIENT)
public class DimStackScreen extends Screen {
   private static final Logger LOGGER = LoggerFactory.getLogger(DimStackScreen.class);
   private final DimStackGuiController controller;
   @Nullable
   public final Screen parent;
   private final Button finishButton;
   private final Button toggleButton;
   private final Button addDimensionButton;
   private final Button removeDimensionButton;
   private final Button editButton;
   private final Button helpButton;
   private final Button setAsPresetButton;
   private final Button loopButton;
   private final Button gravityModeButton;
   private int titleY;
   public final DimListWidget dimListWidget;
   private boolean isEnabled = false;

   public DimStackScreen(@Nullable Screen parent, DimStackGuiController controller) {
      super(Component.translatable("imm_ptl.altius_screen"));
      this.parent = parent;
      this.toggleButton = Button.builder(Component.literal("..."), buttonWidget -> controller.toggleEnabled()).build();
      this.loopButton = Button.builder(Component.literal("..."), buttonWidget -> controller.toggleLoop()).build();
      this.gravityModeButton = Button.builder(Component.literal("..."), gravityModeButton -> controller.toggleGravityMode()).build();
      this.finishButton = Button.builder(Component.translatable("imm_ptl.finish"), buttonWidget -> controller.onFinish()).build();
      this.controller = controller;
      this.addDimensionButton = Button.builder(Component.translatable("imm_ptl.dim_stack_add"), buttonWidget -> this.onAddEntry()).build();
      this.removeDimensionButton = Button.builder(Component.translatable("imm_ptl.dim_stack_remove"), buttonWidget -> this.onRemoveEntry()).build();
      this.editButton = Button.builder(Component.translatable("imm_ptl.dim_stack_edit"), buttonWidget -> this.onEditEntry()).build();
      this.dimListWidget = new DimListWidget(this.width, this.height, 100, 50, this, DimListWidget.Type.mainDimensionList, controller::onDragged);
      this.helpButton = createHelpButton(this);
      this.setAsPresetButton = Button.builder(Component.translatable("imm_ptl.set_as_dim_stack_default"), button -> this.controller.setAsDefault()).build();
   }

   public void setLoopEnabled(boolean loopEnabled) {
      this.loopButton.setMessage(Component.translatable(loopEnabled ? "imm_ptl.loop_enabled" : "imm_ptl.loop_disabled"));
   }

   public void setGravityTransformEnabled(boolean gravityTransformEnabled) {
      this.gravityModeButton
         .setMessage(
            Component.translatable(gravityTransformEnabled ? "imm_ptl.dim_stack.gravity_transform_enabled" : "imm_ptl.dim_stack.gravity_transform_disabled")
         );
   }

   public static Button createHelpButton(Screen parent) {
      return Button.builder(Component.literal("?"), button -> CHelper.openLinkConfirmScreen(parent, "https://qouteall.fun/immptl/wiki/Dimension-Stack"))
         .build();
   }

   public DimEntryWidget createDimEntryWidget(DimStackEntry entry) {
      return new DimEntryWidget(entry.getDimension(), this.dimListWidget, this.getElementSelectCallback(), entry);
   }

   @Override
   protected void init() {
      this.addRenderableWidget(this.toggleButton);
      this.addRenderableWidget(this.finishButton);
      this.addRenderableWidget(this.addDimensionButton);
      this.addRenderableWidget(this.removeDimensionButton);
      this.addRenderableWidget(this.editButton);
      this.addRenderableWidget(this.helpButton);
      this.addRenderableWidget(this.setAsPresetButton);
      this.addRenderableWidget(this.loopButton);
      this.addRenderableWidget(this.gravityModeButton);
      this.addWidget(this.dimListWidget);
      GuiHelper.layout(
         0,
         this.height,
         GuiHelper.blankSpace(5),
         new GuiHelper.LayoutElement(true, 20, (from, to) -> {
            this.helpButton.setX(this.width - 30);
            this.helpButton.setY(from);
            this.helpButton.setWidth(20);
            this.setAsPresetButton.setX(this.width - 125);
            this.setAsPresetButton.setY(from);
            this.setAsPresetButton.setWidth(90);
         }),
         new GuiHelper.LayoutElement(
            true,
            20,
            GuiHelper.combine(
               GuiHelper.layoutButtonVertically(this.toggleButton),
               GuiHelper.layoutButtonVertically(this.loopButton),
               GuiHelper.layoutButtonVertically(this.gravityModeButton)
            )
         ),
         GuiHelper.blankSpace(5),
         new GuiHelper.LayoutElement(false, 1, (from, to) -> {
            this.dimListWidget.setSize(this.width, to - from);
            this.dimListWidget.setPosition(0, from);
         }),
         GuiHelper.blankSpace(5),
         new GuiHelper.LayoutElement(
            true,
            20,
            (from, to) -> {
               this.finishButton.setY(from);
               this.addDimensionButton.setY(from);
               this.removeDimensionButton.setY(from);
               this.editButton.setY(from);
               GuiHelper.layout(
                  0,
                  this.width,
                  GuiHelper.blankSpace(10),
                  new GuiHelper.LayoutElement(false, 1, GuiHelper.layoutButtonHorizontally(this.finishButton)),
                  GuiHelper.blankSpace(5),
                  new GuiHelper.LayoutElement(false, 1, GuiHelper.layoutButtonHorizontally(this.addDimensionButton)),
                  GuiHelper.blankSpace(5),
                  new GuiHelper.LayoutElement(false, 1, GuiHelper.layoutButtonHorizontally(this.removeDimensionButton)),
                  GuiHelper.blankSpace(5),
                  new GuiHelper.LayoutElement(false, 1, GuiHelper.layoutButtonHorizontally(this.editButton)),
                  GuiHelper.blankSpace(10)
               );
            }
         ),
         GuiHelper.blankSpace(5)
      );
      GuiHelper.layout(
         0,
         this.width,
         GuiHelper.blankSpace(10),
         new GuiHelper.LayoutElement(false, 10, GuiHelper.layoutButtonHorizontally(this.toggleButton)),
         GuiHelper.blankSpace(5),
         new GuiHelper.LayoutElement(false, 8, GuiHelper.layoutButtonHorizontally(this.loopButton)),
         GuiHelper.blankSpace(5),
         new GuiHelper.LayoutElement(false, 10, GuiHelper.layoutButtonHorizontally(this.gravityModeButton)),
         GuiHelper.blankSpace(10)
      );
   }

   @Override
   public void onClose() {
      this.controller.onFinish();
   }

   private Consumer<DimEntryWidget> getElementSelectCallback() {
      return w -> this.dimListWidget.setSelected(w);
   }

   @Override
   public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
      super.render(guiGraphics, mouseX, mouseY, partialTick);
      if (this.isEnabled) {
         this.dimListWidget.render(guiGraphics, mouseX, mouseY, partialTick);
      }

      Font font = Minecraft.getInstance().font;
      guiGraphics.drawString(font, this.title, 20, 10, -1);
   }

   public void setEnabled(boolean cond) {
      this.isEnabled = cond;
      if (this.isEnabled) {
         this.toggleButton.setMessage(Component.translatable("imm_ptl.altius_toggle_true"));
      } else {
         this.toggleButton.setMessage(Component.translatable("imm_ptl.altius_toggle_false"));
      }

      this.addDimensionButton.visible = this.isEnabled;
      this.removeDimensionButton.visible = this.isEnabled;
      this.editButton.visible = this.isEnabled;
      this.loopButton.visible = this.isEnabled;
      this.gravityModeButton.visible = this.isEnabled;
   }

   private void onAddEntry() {
      DimEntryWidget selected = this.dimListWidget.getSelected();
      int position;
      if (selected == null) {
         position = 0;
      } else {
         position = this.dimListWidget.children().indexOf(selected);
      }

      if (position < 0 || position > this.dimListWidget.children().size()) {
         position = -1;
      }

      int insertingPosition = position + 1;
      Minecraft.getInstance()
         .setScreen(
            new SelectDimensionScreen(
               this, dimensionType -> this.controller.addEntry(insertingPosition, new DimStackEntry(dimensionType)), this.controller.getDimensionList()
            )
         );
   }

   private void onRemoveEntry() {
      DimEntryWidget selected = this.dimListWidget.getSelected();
      if (selected != null) {
         int position = this.dimListWidget.children().indexOf(selected);
         if (position != -1) {
            this.controller.removeEntry(position);
         }
      }
   }

   private void onEditEntry() {
      DimEntryWidget selected = this.dimListWidget.getSelected();
      if (selected != null) {
         Minecraft.getInstance().setScreen(new DimStackEntryEditScreen(this, selected, () -> {
            int newlyChangingEntryIndex = this.dimListWidget.children().indexOf(selected);
            if (newlyChangingEntryIndex == -1) {
               LOGGER.error("The edited entry is missing in the list");
            } else {
               this.controller.editEntry(newlyChangingEntryIndex, selected.entry);
            }
         }));
      }
   }
}
