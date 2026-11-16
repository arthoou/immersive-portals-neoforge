package qouteall.imm_ptl.peripheral.dim_stack;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ContainerObjectSelectionList.Entry;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.McHelper;

public class DimEntryWidget extends Entry<DimEntryWidget> {
   private static final Logger LOGGER = LogUtils.getLogger();
   public final ResourceKey<Level> dimension;
   public final DimListWidget parent;
   private final Consumer<DimEntryWidget> selectCallback;
   @Nullable
   private final Identifier dimIconPath;
   private final Component dimensionName;
   @Nullable
   public final DimStackEntry entry;
   public int entryIndex;
   DimEntryWidget.ArrowType arrowToPrevious = DimEntryWidget.ArrowType.none;
   DimEntryWidget.ArrowType arrowToNext = DimEntryWidget.ArrowType.none;
   public static final int widgetHeight = 50;
   private final List<GuiEventListener> children = new ArrayList<>();

   @NotNull
   @Override
   public List<? extends NarratableEntry> narratables() {
      return List.of();
   }

   public DimEntryWidget(ResourceKey<Level> dimension, DimListWidget parent, Consumer<DimEntryWidget> selectCallback, @Nullable DimStackEntry entry) {
      this.dimension = dimension;
      this.parent = parent;
      this.selectCallback = selectCallback;
      this.dimIconPath = CHelper.getDimensionIconPath(this.dimension);
      this.dimensionName = McHelper.getDimensionName(dimension);
      this.entry = entry;
   }

   @NotNull
   @Override
   public List<? extends GuiEventListener> children() {
      return this.children;
   }

   @Override
   public void renderContent(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, boolean hovered, float delta) {
      int x = this.getX();
      int y = this.getY();
      int rowWidth = this.getWidth();
      Minecraft client = Minecraft.getInstance();
      guiGraphics.drawString(client.font, this.dimensionName.getString(), x + 50 + 3, y, -1);
      guiGraphics.drawString(client.font, this.dimension.identifier().toString(), x + 50 + 3, y + 10, -6710887);
      if (this.dimIconPath != null) {
         guiGraphics.pose().pushMatrix();
         guiGraphics.pose().translate(x, y);
         int iconLen = 46;
         if (this.entry != null && this.entry.flipped) {
            guiGraphics.pose().rotateAbout((float)Math.toRadians(180.0), iconLen / 2.0F, iconLen / 2.0F);
         }

         guiGraphics.blit(RenderPipelines.GUI_TEXTURED, this.dimIconPath, 0, 0, 0.0F, 0.0F, iconLen, iconLen, iconLen, iconLen);
         guiGraphics.pose().popMatrix();
      }

      if (this.entry != null) {
         guiGraphics.drawString(client.font, this.getText1(), x + 50 + 3, y + 20, -6710887);
         guiGraphics.drawString(client.font, this.getText2(), x + 50 + 3, y + 30, -6710887);
         if (this.arrowToPrevious != DimEntryWidget.ArrowType.none) {
            guiGraphics.pose().pushMatrix();
            guiGraphics.pose().translate(x + rowWidth - 13, y);
            guiGraphics.pose().scale(1.5F, 1.5F);
            guiGraphics.drawString(client.font, Component.literal("↑"), 0, 0, this.arrowToPrevious == DimEntryWidget.ArrowType.enabled ? -6710887 : -65536);
            guiGraphics.pose().popMatrix();
         }

         if (this.arrowToNext != DimEntryWidget.ArrowType.none) {
            guiGraphics.pose().pushMatrix();
            guiGraphics.pose().translate(x + rowWidth - 13, y + 50 - 14.5F);
            guiGraphics.pose().scale(1.5F, 1.5F);
            guiGraphics.drawString(client.font, Component.literal("↓"), 0, 0, this.arrowToNext == DimEntryWidget.ArrowType.enabled ? -6710887 : -65536);
            guiGraphics.pose().popMatrix();
         }
      }
   }

   private Component getText1() {
      return this.entry.scale != 1.0
         ? Component.translatable("imm_ptl.scale").append(Component.literal(":" + Double.toString(this.entry.scale)))
         : Component.literal("");
   }

   private Component getText2() {
      return this.entry.horizontalRotation != 0.0
         ? Component.translatable("imm_ptl.horizontal_rotation")
            .append(Component.literal(":" + Double.toString(this.entry.horizontalRotation)))
            .append(Component.literal(" "))
         : Component.literal("");
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
      this.selectCallback.accept(this);
      super.mouseClicked(mouseButtonEvent, doubleClick);
      return true;
   }

   public enum ArrowType {
      none,
      enabled,
      conflicting;
   }
}
