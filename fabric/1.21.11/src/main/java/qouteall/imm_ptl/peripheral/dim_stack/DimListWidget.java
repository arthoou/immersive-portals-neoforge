package qouteall.imm_ptl.peripheral.dim_stack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import org.jetbrains.annotations.Nullable;
import qouteall.q_misc_util.Helper;

public class DimListWidget extends AbstractSelectionList<DimEntryWidget> {
   public static final int ROW_WIDTH = 300;
   public final Screen parent;
   private final DimListWidget.Type type;
   @Nullable
   private final DimListWidget.DraggingCallback draggingCallback;

   public DimListWidget(
      int width, int height, int top, int itemHeight, Screen parent, DimListWidget.Type type, @Nullable DimListWidget.DraggingCallback draggingCallback
   ) {
      super(Minecraft.getInstance(), width, height, top, itemHeight);
      this.parent = parent;
      this.type = type;
      this.draggingCallback = draggingCallback;
   }

   @Override
   public boolean mouseDragged(MouseButtonEvent mouseButtonEvent, double deltaX, double deltaY) {
      double mouseX = mouseButtonEvent.x();
      double mouseY = mouseButtonEvent.y();
      if (this.type == DimListWidget.Type.mainDimensionList && this.draggingCallback != null) {
         DimEntryWidget selected = this.getSelected();
         if (selected != null) {
            DimEntryWidget mouseOn = this.getEntryAtPosition(mouseX, mouseY);
            if (mouseOn != null && mouseOn != selected) {
               int selectedIndex = this.children().indexOf(selected);
               int mouseOnIndex = this.children().indexOf(mouseOn);
               if (selectedIndex != -1 && mouseOnIndex != -1) {
                  this.draggingCallback.run(selectedIndex, mouseOnIndex);
               } else {
                  Helper.err("Invalid dragging");
               }
            }
         }
      }

      return super.mouseDragged(mouseButtonEvent, deltaX, deltaY);
   }

   @Override
   protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
   }

   @Override
   public int getRowWidth() {
      return 300;
   }

   @Override
   protected int scrollBarX() {
      return (this.width - 300) / 2 + 300;
   }

   @Override
   protected void renderListBackground(GuiGraphics guiGraphics) {
   }

   public interface DraggingCallback {
      void run(int var1, int var2);
   }

   public enum Type {
      mainDimensionList,
      addDimensionList;
   }
}
