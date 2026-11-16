package qouteall.q_misc_util;

import java.util.TreeMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.profiling.Profiler;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class CustomTextOverlay {
   private static final TreeMap<String, CustomTextOverlay.Entry> ENTRIES = new TreeMap<>();
   private static final boolean renderAtBottomCenter = true;
   @Nullable
   private static MultiLineLabel multiLineLabelCache;

   public static void putText(Component component, double durationSeconds, String key) {
      ENTRIES.put(key, new CustomTextOverlay.Entry(component, System.nanoTime() + Helper.secondToNano(durationSeconds)));
      multiLineLabelCache = null;
   }

   public static void putText(Component component, double durationSeconds) {
      putText(component, durationSeconds, "5_defaultKey");
   }

   public static void putText(Component component, String key) {
      putText(component, 0.2, key);
   }

   public static void putText(Component component) {
      putText(component, 0.2, "5_defaultKey");
   }

   public static boolean remove(String key) {
      return ENTRIES.remove(key) != null;
   }

   public static void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
      long currTime = System.nanoTime();
      boolean removes = ENTRIES.entrySet().removeIf(e -> e.getValue().clearingTime < currTime);
      if (removes) {
         multiLineLabelCache = null;
      }

      if (!ENTRIES.isEmpty()) {
         if (multiLineLabelCache == null) {
            MutableComponent component = Component.empty();
            boolean isBeginning = true;

            for (CustomTextOverlay.Entry entry : ENTRIES.values()) {
               if (isBeginning) {
                  isBeginning = false;
               } else {
                  component.append("\n");
               }

               component.append(entry.component());
            }

            multiLineLabelCache = MultiLineLabel.create(Minecraft.getInstance().font, component, Minecraft.getInstance().getWindow().getGuiScaledWidth() - 20);
            assert multiLineLabelCache != null;
         }

         Minecraft minecraft = Minecraft.getInstance();
         guiGraphics.pose().pushMatrix();
         int guiScaledWidth = minecraft.getWindow().getGuiScaledWidth();
         int guiScaledHeight = minecraft.getWindow().getGuiScaledHeight();
         Font font = minecraft.gui.getFont();
         Profiler.get().push("imm_ptl_custom_overlay");
         multiLineLabelCache.visitLines(TextAlignment.CENTER, guiScaledWidth / 2, (int)(guiScaledHeight * 0.75), 9, guiGraphics.textRenderer());
         guiGraphics.pose().popMatrix();
         Profiler.get().pop();
      }
   }

   public record Entry(Component component, long clearingTime) {
   }
}
