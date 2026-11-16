package qouteall.imm_ptl.peripheral.dim_stack;

import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.GuiHelper;

public class DimStackEntryEditScreen extends Screen {
   private final DimStackScreen parent;
   private final DimEntryWidget editing;
   private final EditBox scaleField;
   private final Button flipButton;
   private final EditBox horizontalRotationField;
   private final EditBox topYField;
   private final EditBox bottomYField;
   private final EditBox bedrockBlockField;
   private final Button connectsPreviousButton;
   private final Button connectsNextButton;
   private final Button finishButton;
   private final GuiHelper.Rect scaleLabelRect = new GuiHelper.Rect();
   private final GuiHelper.Rect flipLabelRect = new GuiHelper.Rect();
   private final GuiHelper.Rect horizontalRotationLabelRect = new GuiHelper.Rect();
   private final GuiHelper.Rect topYLabelRect = new GuiHelper.Rect();
   private final GuiHelper.Rect bottomYLabelRect = new GuiHelper.Rect();
   private final GuiHelper.Rect bedrockLabelRect = new GuiHelper.Rect();
   private final GuiHelper.Rect connectsPreviousRect = new GuiHelper.Rect();
   private final GuiHelper.Rect connectsNextRect = new GuiHelper.Rect();
   private final Button helpButton;

   protected DimStackEntryEditScreen(DimStackScreen parent, DimEntryWidget editing, Runnable callback) {
      super(Component.literal("you cannot see me"));
      this.parent = parent;
      this.editing = editing;
      this.scaleField = new EditBox(Minecraft.getInstance().font, 0, 0, 0, 20, Component.literal("you cannot see me"));
      this.scaleField.setValue(Double.toString(editing.entry.scale));
      this.scaleField.setHighlightPos(0);
      this.scaleField.setCursorPosition(0);
      this.flipButton = Button.builder(Component.translatable(editing.entry.flipped ? "imm_ptl.enabled" : "imm_ptl.disabled"), button -> {
         editing.entry.flipped = !editing.entry.flipped;
         button.setMessage(Component.translatable(editing.entry.flipped ? "imm_ptl.enabled" : "imm_ptl.disabled"));
      }).build();
      this.horizontalRotationField = new EditBox(Minecraft.getInstance().font, 0, 0, 0, 20, Component.literal("you cannot see me"));
      this.horizontalRotationField.setValue(Double.toString(editing.entry.horizontalRotation));
      this.horizontalRotationField.setCursorPosition(0);
      this.horizontalRotationField.setHighlightPos(0);
      this.topYField = new EditBox(Minecraft.getInstance().font, 0, 0, 0, 20, Component.literal("you cannot see me"));
      if (editing.entry.topY != null) {
         this.topYField.setValue(Integer.toString(editing.entry.topY));
      }

      this.topYField.setCursorPosition(0);
      this.topYField.setHighlightPos(0);
      this.bottomYField = new EditBox(Minecraft.getInstance().font, 0, 0, 0, 20, Component.literal("you cannot see me"));
      if (editing.entry.bottomY != null) {
         this.bottomYField.setValue(Integer.toString(editing.entry.bottomY));
      }

      this.bottomYField.setCursorPosition(0);
      this.bottomYField.setHighlightPos(0);
      this.bedrockBlockField = new EditBox(Minecraft.getInstance().font, 0, 0, 0, 20, Component.literal("you cannot see me"));
      this.bedrockBlockField.setMaxLength(200);
      if (editing.entry.bedrockReplacementStr != null) {
         this.bedrockBlockField.setValue(editing.entry.bedrockReplacementStr);
      }

      this.bedrockBlockField.setCursorPosition(0);
      this.bedrockBlockField.setHighlightPos(0);
      this.connectsPreviousButton = Button.builder(Component.translatable(editing.entry.connectsPrevious ? "imm_ptl.enabled" : "imm_ptl.disabled"), button -> {
         editing.entry.connectsPrevious = !editing.entry.connectsPrevious;
         button.setMessage(Component.translatable(editing.entry.connectsPrevious ? "imm_ptl.enabled" : "imm_ptl.disabled"));
      }).build();
      this.connectsNextButton = Button.builder(Component.translatable(editing.entry.connectsNext ? "imm_ptl.enabled" : "imm_ptl.disabled"), button -> {
         editing.entry.connectsNext = !editing.entry.connectsNext;
         button.setMessage(Component.translatable(editing.entry.connectsNext ? "imm_ptl.enabled" : "imm_ptl.disabled"));
      }).build();
      this.finishButton = Button.builder(Component.translatable("imm_ptl.finish"), button -> {
         editing.entry.horizontalRotation = Helper.parseDouble(this.horizontalRotationField.getValue()).orElse(0.0);
         editing.entry.scale = Helper.parseDouble(this.scaleField.getValue()).orElse(1.0);
         OptionalInt topY = Helper.parseInt(this.topYField.getValue());
         editing.entry.topY = topY.isPresent() ? topY.getAsInt() : null;
         OptionalInt bottomY = Helper.parseInt(this.bottomYField.getValue());
         editing.entry.bottomY = bottomY.isPresent() ? bottomY.getAsInt() : null;
         editing.entry.bedrockReplacementStr = this.bedrockBlockField.getValue();
         Minecraft.getInstance().setScreen(parent);
         callback.run();
      }).build();
      this.helpButton = DimStackScreen.createHelpButton(this);
   }

   @Override
   public void tick() {
      super.tick();
   }

   @Override
   protected void init() {
      this.addWidget(this.scaleField);
      this.addRenderableWidget(this.flipButton);
      this.addWidget(this.horizontalRotationField);
      this.addWidget(this.topYField);
      this.addWidget(this.bottomYField);
      this.addWidget(this.bedrockBlockField);
      this.addWidget(this.connectsPreviousButton);
      this.addWidget(this.connectsNextButton);
      this.addRenderableWidget(this.finishButton);
      this.addRenderableWidget(this.helpButton);
      GuiHelper.layout(
         0,
         this.height,
         GuiHelper.blankSpace(5),
         GuiHelper.fixedLength(20, GuiHelper.combine(GuiHelper.layoutRectVertically(this.scaleLabelRect), GuiHelper.layoutButtonVertically(this.scaleField))),
         GuiHelper.elasticBlankSpace(),
         GuiHelper.fixedLength(20, GuiHelper.combine(GuiHelper.layoutRectVertically(this.flipLabelRect), GuiHelper.layoutButtonVertically(this.flipButton))),
         GuiHelper.elasticBlankSpace(),
         GuiHelper.fixedLength(
            20,
            GuiHelper.combine(GuiHelper.layoutRectVertically(this.horizontalRotationLabelRect), GuiHelper.layoutButtonVertically(this.horizontalRotationField))
         ),
         GuiHelper.elasticBlankSpace(),
         GuiHelper.fixedLength(20, GuiHelper.combine(GuiHelper.layoutRectVertically(this.topYLabelRect), GuiHelper.layoutButtonVertically(this.topYField))),
         GuiHelper.elasticBlankSpace(),
         GuiHelper.fixedLength(
            20, GuiHelper.combine(GuiHelper.layoutRectVertically(this.bottomYLabelRect), GuiHelper.layoutButtonVertically(this.bottomYField))
         ),
         GuiHelper.elasticBlankSpace(),
         GuiHelper.fixedLength(
            20, GuiHelper.combine(GuiHelper.layoutRectVertically(this.bedrockLabelRect), GuiHelper.layoutButtonVertically(this.bedrockBlockField))
         ),
         GuiHelper.elasticBlankSpace(),
         GuiHelper.fixedLength(
            20, GuiHelper.combine(GuiHelper.layoutRectVertically(this.connectsPreviousRect), GuiHelper.layoutButtonVertically(this.connectsPreviousButton))
         ),
         GuiHelper.elasticBlankSpace(),
         GuiHelper.fixedLength(
            20, GuiHelper.combine(GuiHelper.layoutRectVertically(this.connectsNextRect), GuiHelper.layoutButtonVertically(this.connectsNextButton))
         ),
         GuiHelper.elasticBlankSpace(),
         GuiHelper.fixedLength(20, GuiHelper.layoutButtonVertically(this.finishButton)),
         GuiHelper.blankSpace(5)
      );
      GuiHelper.layout(
         0,
         this.width,
         GuiHelper.elasticBlankSpace(),
         GuiHelper.fixedLength(
            150,
            GuiHelper.combine(
               GuiHelper.layoutRectHorizontally(this.scaleLabelRect),
               GuiHelper.layoutRectHorizontally(this.flipLabelRect),
               GuiHelper.layoutRectHorizontally(this.horizontalRotationLabelRect),
               GuiHelper.layoutRectHorizontally(this.topYLabelRect),
               GuiHelper.layoutRectHorizontally(this.bottomYLabelRect),
               GuiHelper.layoutRectHorizontally(this.bedrockLabelRect),
               GuiHelper.layoutRectHorizontally(this.connectsPreviousRect),
               GuiHelper.layoutRectHorizontally(this.connectsNextRect)
            )
         ),
         GuiHelper.blankSpace(20),
         GuiHelper.fixedLength(
            100,
            GuiHelper.combine(
               GuiHelper.layoutButtonHorizontally(this.scaleField),
               GuiHelper.layoutButtonHorizontally(this.flipButton),
               GuiHelper.layoutButtonHorizontally(this.horizontalRotationField),
               GuiHelper.layoutButtonHorizontally(this.topYField),
               GuiHelper.layoutButtonHorizontally(this.bottomYField),
               GuiHelper.layoutButtonHorizontally(this.bedrockBlockField),
               GuiHelper.layoutButtonHorizontally(this.connectsPreviousButton),
               GuiHelper.layoutButtonHorizontally(this.connectsNextButton)
            )
         ),
         GuiHelper.elasticBlankSpace()
      );
      GuiHelper.layout(
         0,
         this.width,
         GuiHelper.blankSpace(20),
         new GuiHelper.LayoutElement(true, 100, GuiHelper.layoutButtonHorizontally(this.finishButton)),
         GuiHelper.elasticBlankSpace()
      );
      this.helpButton.setX(this.width - 50);
      this.helpButton.setY(5);
      this.helpButton.setWidth(20);
   }

   @Override
   public void onClose() {
      Minecraft.getInstance().setScreen(this.parent);
   }

   @Override
   public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
      this.renderBackground(guiGraphics, mouseX, mouseY, delta);
      super.render(guiGraphics, mouseX, mouseY, delta);
      this.scaleField.render(guiGraphics, mouseX, mouseY, delta);
      this.horizontalRotationField.render(guiGraphics, mouseX, mouseY, delta);
      this.topYField.render(guiGraphics, mouseX, mouseY, delta);
      this.bottomYField.render(guiGraphics, mouseX, mouseY, delta);
      this.bedrockBlockField.render(guiGraphics, mouseX, mouseY, delta);
      this.connectsPreviousButton.render(guiGraphics, mouseX, mouseY, delta);
      this.connectsNextButton.render(guiGraphics, mouseX, mouseY, delta);
      this.scaleLabelRect.renderTextLeft(Component.translatable("imm_ptl.scale"), guiGraphics);
      this.flipLabelRect.renderTextLeft(Component.translatable("imm_ptl.flipped"), guiGraphics);
      this.horizontalRotationLabelRect.renderTextLeft(Component.translatable("imm_ptl.horizontal_rotation"), guiGraphics);
      this.topYLabelRect.renderTextLeft(Component.translatable("imm_ptl.top_y"), guiGraphics);
      this.bottomYLabelRect.renderTextLeft(Component.translatable("imm_ptl.bottom_y"), guiGraphics);
      this.bedrockLabelRect.renderTextLeft(Component.translatable("imm_ptl.bedrock_replacement"), guiGraphics);
      this.connectsPreviousRect.renderTextLeft(Component.translatable("imm_ptl.connects_previous"), guiGraphics);
      this.connectsNextRect.renderTextLeft(Component.translatable("imm_ptl.connects_next"), guiGraphics);
   }
}
