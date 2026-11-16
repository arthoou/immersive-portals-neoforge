package qouteall.imm_ptl.peripheral.dim_stack;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import qouteall.q_misc_util.Helper;

public class DimStackEntry {
   public String dimensionIdStr;
   public double scale = 1.0;
   public boolean flipped = false;
   public double horizontalRotation = 0.0;
   @Nullable
   public Integer topY = null;
   @Nullable
   public Integer bottomY = null;
   @Nullable
   public String bedrockReplacementStr = "minecraft:obsidian";
   public boolean connectsPrevious = true;
   public boolean connectsNext = true;

   public DimStackEntry(ResourceKey<Level> dimension) {
      this.dimensionIdStr = dimension.identifier().toString();
   }

   public DimStackEntry() {
   }

   public ResourceKey<Level> getDimension() {
      Validate.notNull(this.dimensionIdStr);
      return Helper.dimIdToKey(this.dimensionIdStr);
   }

   public DimStackEntry copy() {
      DimStackEntry copy = new DimStackEntry();
      copy.dimensionIdStr = this.dimensionIdStr;
      copy.scale = this.scale;
      copy.flipped = this.flipped;
      copy.horizontalRotation = this.horizontalRotation;
      copy.topY = this.topY;
      copy.bottomY = this.bottomY;
      copy.bedrockReplacementStr = this.bedrockReplacementStr;
      copy.connectsPrevious = this.connectsPrevious;
      copy.connectsNext = this.connectsNext;
      return copy;
   }
}
