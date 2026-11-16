package qouteall.imm_ptl.peripheral.wand;

import com.mojang.logging.LogUtils;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.q_misc_util.my_util.Circle;
import qouteall.q_misc_util.my_util.Plane;
import qouteall.q_misc_util.my_util.WithDim;

public class ProtoPortal {
   private static final Logger LOGGER = LogUtils.getLogger();
   @Nullable
   public ProtoPortalSide firstSide;
   @Nullable
   public ProtoPortalSide secondSide;

   public ProtoPortal copy() {
      ProtoPortal newPortal = new ProtoPortal();
      newPortal.firstSide = this.firstSide == null ? null : this.firstSide.copy();
      newPortal.secondSide = this.secondSide == null ? null : this.secondSide.copy();
      return newPortal;
   }

   public void reset() {
      this.firstSide = null;
      this.secondSide = null;
   }

   public boolean isComplete() {
      return this.firstSide != null && this.secondSide != null && this.secondSide.isComplete();
   }

   public boolean isValidPlacement() {
      if (this.firstSide != null) {
         if (!this.firstSide.isValidPlacement(null)) {
            return false;
         }

         if (this.secondSide != null) {
            Validate.isTrue(this.firstSide.isComplete());
            double heightDivWidth = this.firstSide.getHeightDivWidth();
            return this.secondSide.isValidPlacement(heightDivWidth);
         }
      }

      return true;
   }

   @Nullable
   public ResourceKey<Level> getCursorConstraintDim() {
      if (this.firstSide != null && !this.firstSide.isComplete()) {
         return this.firstSide.dimension;
      } else {
         return this.secondSide != null && !this.secondSide.isComplete() ? this.secondSide.dimension : null;
      }
   }

   @Nullable
   public WithDim<Plane> getCursorConstraintPlane() {
      if (this.firstSide != null) {
         WithDim<Plane> plane = this.firstSide.getCursorConstraintPlane();
         if (plane != null) {
            return plane;
         }
      }

      if (this.secondSide != null) {
         WithDim<Plane> plane = this.secondSide.getCursorConstraintPlane();
         if (plane != null) {
            return plane;
         }
      }

      return null;
   }

   @Nullable
   public WithDim<Circle> getCursorConstraintCircle() {
      if (this.firstSide != null && this.secondSide != null) {
         double heightDivWidth = this.firstSide.getHeightDivWidth();
         return this.secondSide.getCursorConstraintCircle(heightDivWidth);
      } else {
         return null;
      }
   }

   public boolean tryPlaceCursor(ResourceKey<Level> dimension, Vec3 pos) {
      if (this.firstSide == null) {
         this.firstSide = new ProtoPortalSide(dimension, pos);
         return true;
      }

      if (!this.firstSide.isComplete()) {
         if (dimension != this.firstSide.dimension) {
            return false;
         }

         this.firstSide.placeCursor(pos);
         return true;
      } else {
         if (this.secondSide == null) {
            this.secondSide = new ProtoPortalSide(dimension, pos);
            return true;
         }

         if (!this.secondSide.isComplete()) {
            if (dimension != this.secondSide.dimension) {
               LOGGER.error("cursor dimension mismatch {} {}", dimension, this.secondSide.dimension);
               return false;
            } else {
               this.secondSide.placeCursor(pos);
               return true;
            }
         } else {
            return false;
         }
      }
   }

   public void undo() {
      if (this.secondSide != null) {
         this.secondSide = this.secondSide.undo();
      } else if (this.firstSide != null) {
         this.firstSide = this.firstSide.undo();
      }
   }

   @Nullable
   public MutableComponent getPromptMessage(@Nullable ProtoPortal pendingState) {
      if (this.firstSide == null) {
         return Component.translatable("imm_ptl.wand.first_side_left_bottom", Minecraft.getInstance().options.keyUse.getTranslatedKeyMessage());
      }

      MutableComponent undoPrompt = Component.literal("\n")
         .append(Component.translatable("imm_ptl.wand.left_click_to_undo", Minecraft.getInstance().options.keyAttack.getTranslatedKeyMessage()));
      if (this.firstSide.rightBottom == null) {
         String widthStr = pendingState == null ? "?" : String.format("%.3f", Objects.requireNonNull(pendingState.firstSide).getWidth());
         return Component.translatable("imm_ptl.wand.first_side_right_bottom", widthStr).append(undoPrompt);
      }

      if (this.firstSide.leftTop == null) {
         String widthStr = pendingState == null ? "?" : String.format("%.3f", Objects.requireNonNull(pendingState.firstSide).getWidth());
         String heightStr = pendingState == null ? "?" : String.format("%.3f", Objects.requireNonNull(pendingState.firstSide).getHeight());
         return Component.translatable("imm_ptl.wand.first_side_left_up", widthStr, heightStr).append(undoPrompt);
      }

      if (this.secondSide == null) {
         return Component.translatable("imm_ptl.wand.second_side_left_bottom").append(undoPrompt);
      }

      if (this.secondSide.rightBottom == null) {
         String widthStr = "?";
         String heightStr = "?";
         String scaleStr = "?";
         if (pendingState != null) {
            Validate.notNull(pendingState.firstSide);
            Validate.notNull(pendingState.secondSide);
            double width = pendingState.secondSide.getWidth();
            double heightDivWidth = pendingState.firstSide.getHeightDivWidth();
            double height = width * heightDivWidth;
            double scale = width / pendingState.firstSide.getWidth();
            widthStr = String.format("%.3f", width);
            heightStr = String.format("%.3f", height);
            scaleStr = String.format("%.3f", scale);
         }

         return Component.translatable("imm_ptl.wand.second_side_right_bottom", widthStr, heightStr, scaleStr).append(undoPrompt);
      } else {
         return this.secondSide.leftTop == null ? Component.translatable("imm_ptl.wand.second_side_left_up").append(undoPrompt) : null;
      }
   }

   public ProtoPortal.Stage getStage() {
      if (this.firstSide == null) {
         return ProtoPortal.Stage.PlacingFirstSideLeftBottom;
      } else if (this.firstSide.rightBottom == null) {
         return ProtoPortal.Stage.PlacingFirstSideRightBottom;
      } else if (this.firstSide.leftTop == null) {
         return ProtoPortal.Stage.PlacingFirstSideLeftTop;
      } else if (this.secondSide == null) {
         return ProtoPortal.Stage.PlacingSecondSideLeftBottom;
      } else if (this.secondSide.rightBottom == null) {
         return ProtoPortal.Stage.PlacingSecondSideRightBottom;
      } else {
         return this.secondSide.leftTop == null ? ProtoPortal.Stage.PlacingSecondSideLeftTop : ProtoPortal.Stage.Completed;
      }
   }

   public enum Stage {
      PlacingFirstSideLeftBottom,
      PlacingFirstSideRightBottom,
      PlacingFirstSideLeftTop,
      PlacingSecondSideLeftBottom,
      PlacingSecondSideRightBottom,
      PlacingSecondSideLeftTop,
      Completed;
   }
}
