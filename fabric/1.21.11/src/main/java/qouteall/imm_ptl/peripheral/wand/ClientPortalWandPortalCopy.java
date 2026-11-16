package qouteall.imm_ptl.peripheral.wand;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.datafixers.util.Pair;
import java.util.Arrays;
import java.util.Comparator;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.mc_utils.WireRenderingHelper;
import qouteall.imm_ptl.core.platform_specific.IPConfig;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalUtils;
import qouteall.imm_ptl.core.portal.animation.TimingFunction;
import qouteall.imm_ptl.core.portal.animation.UnilateralPortalState;
import qouteall.imm_ptl.core.render.TransformationManager;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.q_misc_util.CustomTextOverlay;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.api.McRemoteProcedureCall;
import qouteall.q_misc_util.my_util.AARotation;
import qouteall.q_misc_util.my_util.DQuaternion;
import qouteall.q_misc_util.my_util.RayTraceResult;
import qouteall.q_misc_util.my_util.WithDim;
import qouteall.q_misc_util.my_util.animation.Animated;
import qouteall.q_misc_util.my_util.animation.RenderedPoint;

@Environment(EnvType.CLIENT)
public class ClientPortalWandPortalCopy {
   @NotNull
   private static ClientPortalWandPortalCopy.Status status = new ClientPortalWandPortalCopy.Status_SelectPortal();
   public static Animated<RenderedPoint> cursor = new Animated<>(
      Animated.RENDERED_POINT_TYPE_INFO, () -> RenderStates.renderStartNanoTime, TimingFunction.sine::mapProgress, RenderedPoint.EMPTY
   );
   public static Animated<UnilateralPortalState> selection = new Animated<>(
      UnilateralPortalState.ANIMATION_TYPE_INFO, () -> RenderStates.renderStartNanoTime, TimingFunction.sine::mapProgress, null
   );
   public static Animated<DQuaternion> placementOrientation = new Animated<>(
      Animated.QUATERNION_TYPE_INFO, () -> RenderStates.renderStartNanoTime, TimingFunction.sine::mapProgress, DQuaternion.identity
   );
   public static Animated<Double> placementOffsetLen = new Animated<>(
      Animated.DOUBLE_DEFAULT_ZERO_TYPE_INFO, () -> RenderStates.renderStartNanoTime, TimingFunction.sine::mapProgress, 0.0
   );
   private static final int colorOfCursor = -16515844;
   private static final int colorOfSelection1 = -11337838;
   private static final int colorOfSelection2 = -224451;
   private static final int colorOfPendingPlacement1 = -16711681;
   private static final int colorOfPendingPlacement2 = -2339841;

   public static void reset() {
      status = new ClientPortalWandPortalCopy.Status_SelectPortal();
      cursor.clearTarget();
      selection.clearTarget();
      placementOrientation.clearTarget();
      placementOffsetLen.clearTarget();
   }

   public static void updateDisplay() {
      Minecraft minecraft = Minecraft.getInstance();
      LocalPlayer player = minecraft.player;
      if (player != null) {
         Vec3 eyePos = player.getEyePosition(RenderStates.getPartialTick());
         Vec3 viewVec = player.getLookAngle();
         if (status instanceof ClientPortalWandPortalCopy.Status_SelectPortal statusSelectPortal) {
            cursor.clearTarget();
            Pair<Portal, RayTraceResult> rayTraceResult = PortalUtils.lenientRayTracePortals(
                  player.level(), eyePos, eyePos.add(viewVec.scale(64.0)), false, p -> true, 0.0
               )
               .orElse(null);
            if (rayTraceResult != null) {
               Portal portal = rayTraceResult.getFirst();
               selection.setTarget(portal.getThisSideState(), Helper.secondToNano(0.5));
               statusSelectPortal.selectedPortalId = portal.getUUID();
               CustomTextOverlay.putText(
                  Component.translatable(
                     "imm_ptl.wand.copy.prompt.copy_or_cut",
                     Minecraft.getInstance().options.keyUse.getTranslatedKeyMessage(),
                     Minecraft.getInstance().options.keyAttack.getTranslatedKeyMessage()
                  )
               );
            } else {
               selection.clearTarget();
               statusSelectPortal.selectedPortalId = null;
               CustomTextOverlay.putText(Component.translatable("imm_ptl.wand.copy.prompt.select_portal"));
            }
         } else {
            if (!(status instanceof ClientPortalWandPortalCopy.Status_PlacingPortal statusPlacingPortal)) {
               throw new RuntimeException();
            }

            selection.clearTarget();
            HitResult hitResult = player.pick(64.0, RenderStates.getPartialTick(), false);
            int alignment = IPConfig.getConfig().portalWandCursorAlignment;
            Vec3 preCursorPos;
            if (hitResult.getType() == Type.BLOCK && hitResult instanceof BlockHitResult blockHitResult) {
               preCursorPos = blockHitResult.getLocation();
            } else {
               preCursorPos = eyePos.add(viewVec.scale(5.0));
            }

            Vec3 cursorPointing = WandUtil.alignOnBlocks(player.level(), preCursorPos, alignment);
            cursor.setTarget(new RenderedPoint(new WithDim<>(player.level().dimension(), cursorPointing), 1.0), Helper.secondToNano(0.3));
            DQuaternion camRotInverse = TransformationManager.getPlayerCameraRotation().getConjugated();
            AARotation orientationRot = Arrays.stream(AARotation.values())
               .min(Comparator.comparingDouble(r -> DQuaternion.distanceSq(r.quaternion, camRotInverse)))
               .orElseThrow();
            DQuaternion orientation = orientationRot.quaternion;
            ClientPortalWandPortalCopy.PlacementRequirement req = statusPlacingPortal.placementRequirement;
            boolean isFacingUpOrDown = player.getXRot() > 45.0F || player.getXRot() < -45.0F;
            double offsetLen = isFacingUpOrDown ? 0.0 : req.height() / 2.0;
            Vec3 offset = orientation.rotate(new Vec3(0.0, offsetLen, 0.0));
            Vec3 placementOrigin = cursorPointing.add(offset);
            UnilateralPortalState placement = new UnilateralPortalState(player.level().dimension(), placementOrigin, orientation, req.width(), req.height());
            statusPlacingPortal.pendingPlacement = placement;
            placementOrientation.setTarget(orientation, Helper.secondToNano(0.3));
            placementOffsetLen.setTarget(offsetLen, Helper.secondToNano(0.3));
            CustomTextOverlay.putText(
               Component.translatable(
                  "imm_ptl.wand.copy.prompt.place_portal",
                  Minecraft.getInstance().options.keyUse.getTranslatedKeyMessage(),
                  Minecraft.getInstance().options.keyAttack.getTranslatedKeyMessage()
               )
            );
         }
      }
   }

   public static void onLeftClick() {
      if (status instanceof ClientPortalWandPortalCopy.Status_SelectPortal statusSelectPortal) {
         if (statusSelectPortal.selectedPortalId != null) {
            Portal portal = WandUtil.getClientPortalByUUID(statusSelectPortal.selectedPortalId);
            if (portal != null) {
               status = new ClientPortalWandPortalCopy.Status_PlacingPortal(
                  new ClientPortalWandPortalCopy.PlacementRequirement(portal.getWidth(), portal.getHeight()), true
               );
               McRemoteProcedureCall.tellServerToInvoke(
                  "qouteall.imm_ptl.peripheral.wand.PortalWandInteraction.RemoteCallables.copyCutPortal", statusSelectPortal.selectedPortalId, true
               );
            }
         }
      } else if (status instanceof ClientPortalWandPortalCopy.Status_PlacingPortal statusPlacingPortal) {
         McRemoteProcedureCall.tellServerToInvoke("qouteall.imm_ptl.peripheral.wand.PortalWandInteraction.RemoteCallables.clearPortalClipboard");
         status = new ClientPortalWandPortalCopy.Status_SelectPortal();
      }
   }

   public static void onRightClick() {
      if (status instanceof ClientPortalWandPortalCopy.Status_SelectPortal statusSelectPortal) {
         if (statusSelectPortal.selectedPortalId != null) {
            Portal portal = WandUtil.getClientPortalByUUID(statusSelectPortal.selectedPortalId);
            if (portal != null) {
               status = new ClientPortalWandPortalCopy.Status_PlacingPortal(
                  new ClientPortalWandPortalCopy.PlacementRequirement(portal.getWidth(), portal.getHeight()), false
               );
               McRemoteProcedureCall.tellServerToInvoke(
                  "qouteall.imm_ptl.peripheral.wand.PortalWandInteraction.RemoteCallables.copyCutPortal", statusSelectPortal.selectedPortalId, false
               );
            }
         }
      } else if (status instanceof ClientPortalWandPortalCopy.Status_PlacingPortal statusPlacingPortal && statusPlacingPortal.pendingPlacement != null) {
         McRemoteProcedureCall.tellServerToInvoke(
            "qouteall.imm_ptl.peripheral.wand.PortalWandInteraction.RemoteCallables.confirmCopyCut",
            statusPlacingPortal.pendingPlacement.position(),
            statusPlacingPortal.pendingPlacement.orientation()
         );
         status = new ClientPortalWandPortalCopy.Status_SelectPortal();
      }
   }

   public static void render(PoseStack matrixStack, BufferSource bufferSource, double camX, double camY, double camZ) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         if (!PortalRendering.isRendering()) {
            ResourceKey<Level> currDim = player.level().dimension();
            Vec3 cameraPos = new Vec3(camX, camY, camZ);
            VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderTypes.lines());
            Vec3 cursorPos = null;
            RenderedPoint currentCursor = cursor.getCurrent();
            if (currentCursor != null) {
               WithDim<Vec3> posWithDim = currentCursor.pos();
               if (posWithDim != null && posWithDim.dimension() == currDim) {
                  cursorPos = posWithDim.value();
               }
            }

            if (cursorPos != null) {
               WireRenderingHelper.renderSmallCubeFrame(vertexConsumer, cameraPos, cursorPos, -16515844, cursor.getCurrent().scale(), matrixStack);
            }

            UnilateralPortalState rect = selection.getCurrent();
            if (rect != null && rect.dimension() == currDim) {
               WireRenderingHelper.renderRectFrameFlow(matrixStack, cameraPos, vertexConsumer, rect, -11337838, -224451);
            }

            if (status instanceof ClientPortalWandPortalCopy.Status_PlacingPortal statusPlacingPortal) {
               DQuaternion orientation = placementOrientation.getCurrent();
               Double offsetLen = placementOffsetLen.getCurrent();
               Validate.isTrue(orientation != null);
               Validate.isTrue(offsetLen != null);
               Vec3 offset = orientation.rotate(new Vec3(0.0, offsetLen, 0.0));
               if (cursorPos != null) {
                  ClientPortalWandPortalCopy.PlacementRequirement req = statusPlacingPortal.placementRequirement;
                  UnilateralPortalState placement = new UnilateralPortalState(currDim, cursorPos.add(offset), orientation, req.width(), req.height());
                  WireRenderingHelper.renderRectFrameFlow(matrixStack, cameraPos, vertexConsumer, placement, -16711681, -2339841);
               }
            }
         }
      }
   }

   public record PlacementRequirement(double width, double height) {
   }

   private sealed interface Status permits ClientPortalWandPortalCopy.Status_SelectPortal, ClientPortalWandPortalCopy.Status_PlacingPortal {
   }

   private static final class Status_PlacingPortal implements ClientPortalWandPortalCopy.Status {
      @NotNull
      public final ClientPortalWandPortalCopy.PlacementRequirement placementRequirement;
      public final boolean isCut;
      @Nullable
      public UnilateralPortalState pendingPlacement;

      private Status_PlacingPortal(@NotNull ClientPortalWandPortalCopy.PlacementRequirement placementRequirement, boolean isCut) {
         this.placementRequirement = placementRequirement;
         this.isCut = isCut;
      }
   }

   private static final class Status_SelectPortal implements ClientPortalWandPortalCopy.Status {
      @Nullable
      public UUID selectedPortalId;
   }
}
