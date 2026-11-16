package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.util.Tuple;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import qouteall.imm_ptl.core.compat.GravityChangerInterface;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.DQuaternion;

@Environment(EnvType.CLIENT)
public class TransformationManager {
   private static DQuaternion animationDeltaStart;
   private static long interpolationStartTime = 0L;
   private static long interpolationEndTime = 1L;
   public static final Minecraft client = Minecraft.getInstance();
   public static boolean isIsometricView = false;
   public static float isometricViewLength = 50.0F;
   public static boolean isCalculatingViewBobbingOffset = false;

   public static DQuaternion getPlayerCameraRotation() {
      LocalPlayer player = client.player;
      if (player == null) {
         return DQuaternion.identity;
      }

      Direction gravity = GravityChangerInterface.invoker.getGravityDirection(player);
      return getCameraRotationWithGravity(gravity, player.getXRot(), player.getYRot());
   }

   private static DQuaternion getCameraRotationWithGravity(Direction gravityDirection, float pitch, float yaw) {
      DQuaternion gravity = GravityChangerInterface.invoker.getExtraCameraRotation(gravityDirection);
      DQuaternion rawCameraRotation = DQuaternion.getCameraRotation(pitch, yaw);
      return gravity == null ? rawCameraRotation : rawCameraRotation.hamiltonProduct(gravity);
   }

   @Nullable
   private static DQuaternion getCurrentAnimationDelta() {
      Double animationProgress = getAnimationProgress();
      if (animationProgress != null) {
         double progress = animationProgress;
         return DQuaternion.interpolate(animationDeltaStart, DQuaternion.identity, mapProgress(progress));
      } else {
         return null;
      }
   }

   private static void processTransformation(Camera camera, PoseStack matrixStack) {
      DQuaternion currentAnimationDelta = getCurrentAnimationDelta();
      if (currentAnimationDelta != null) {
         matrixStack.mulPose(currentAnimationDelta.toMcQuaternion());
      }

      WorldRenderInfo.applyAdditionalTransformations(matrixStack);
   }

   public static Matrix4f processTransformation(Camera camera, Matrix4f matrix4f) {
      PoseStack poseStack = new PoseStack();
      poseStack.last().pose().set(matrix4f);
      processTransformation(camera, poseStack);
      return poseStack.last().pose();
   }

   public static boolean isAnimationRunning() {
      return getAnimationProgress() != null;
   }

   @Nullable
   public static Double getAnimationProgress() {
      if (interpolationStartTime == 0L) {
         return null;
      }

      if (animationDeltaStart == null) {
         return null;
      }

      double progress = (RenderStates.renderStartNanoTime - interpolationStartTime) / ((double)interpolationEndTime - interpolationStartTime);
      return progress >= 0.0 && progress <= 1.0 ? progress : null;
   }

   public static double mapProgress(double progress) {
      return Math.sin(progress * (Math.PI / 2));
   }

   public static void managePlayerRotationAndChangeGravity(Portal portal) {
      if (portal.getRotation() != null) {
         LocalPlayer player = client.player;
         Direction oldGravityDir = GravityChangerInterface.invoker.getGravityDirection(player);
         DQuaternion oldCameraRotation = getCameraRotationWithGravity(
            oldGravityDir, player.getViewXRot(RenderStates.getPartialTick()), player.getViewYRot(RenderStates.getPartialTick())
         );
         DQuaternion currentAnimationDelta = getCurrentAnimationDelta();
         if (currentAnimationDelta != null) {
            oldCameraRotation = oldCameraRotation.hamiltonProduct(currentAnimationDelta);
         }

         DQuaternion immediateFinalRot = oldCameraRotation.hamiltonProduct(portal.getRotation().getConjugated());
         Direction oldBaseGravityDir = GravityChangerInterface.invoker.getBaseGravityDirection(player);
         Direction newBaseGravityDir = portal.getTeleportedGravityDirection(oldBaseGravityDir);
         if (newBaseGravityDir != oldBaseGravityDir) {
            GravityChangerInterface.invoker.setClientPlayerGravityDirection(player, newBaseGravityDir);
         }

         Direction immediateNewGravityDir = GravityChangerInterface.invoker.getGravityDirection(player);
         DQuaternion newGravityRot = DQuaternion.fromNullable(GravityChangerInterface.invoker.getExtraCameraRotation(immediateNewGravityDir));
         DQuaternion newRawCameraRotation = immediateFinalRot.hamiltonProduct(newGravityRot.getConjugated());
         Tuple<Double, Double> pitchYaw = DQuaternion.getPitchYawFromRotation(newRawCameraRotation);
         float finalYaw = (float)pitchYaw.getB().doubleValue();
         float finalPitch = (float)pitchYaw.getA().doubleValue();
         if (finalPitch > 90.0F) {
            finalPitch = 90.0F - (finalPitch - 90.0F);
         } else if (finalPitch < -90.0F) {
            finalPitch = -90.0F + (-90.0F - finalPitch);
         }

         player.setYRot(finalYaw);
         player.setXRot(finalPitch);
         player.yRotO = finalYaw;
         player.xRotO = finalPitch;
         player.yBob = finalYaw;
         player.xBob = finalPitch;
         player.yBobO = finalYaw;
         player.xBobO = finalPitch;
         DQuaternion newCameraRotationWithGravity = getCameraRotationWithGravity(immediateNewGravityDir, finalPitch, finalYaw);
         DQuaternion newAnimationDelta = newCameraRotationWithGravity.getConjugated().hamiltonProduct(immediateFinalRot);
         if (newAnimationDelta.getRotatingAngleDegrees() > 0.1) {
            animationDeltaStart = newAnimationDelta;
            interpolationStartTime = RenderStates.renderStartNanoTime;
            interpolationEndTime = interpolationStartTime + Helper.secondToNano(getAnimationDurationSeconds());
         }

         updateCamera(client);
      }
   }

   private static double getAnimationDurationSeconds() {
      return 1.0;
   }

   private static void updateCamera(Minecraft client) {
      Camera camera = client.gameRenderer.getMainCamera();
      camera.setup(
         client.level,
         client.player,
         !client.options.getCameraType().isFirstPerson(),
         client.options.getCameraType().isMirrored(),
         RenderStates.getPartialTick()
      );
   }

   public static Matrix4f getMirrorTransformation(Vec3 normal) {
      float x = (float)normal.x;
      float y = (float)normal.y;
      float z = (float)normal.z;
      Matrix4f matrix = new Matrix4f();
      matrix.reflection(x, y, z, 0.0F);
      return matrix;
   }

   public static Matrix4f getIsometricProjection() {
      int w = client.getWindow().getWidth();
      int h = client.getWindow().getHeight();
      float wView = isometricViewLength / h * w;
      float near = -2000.0F;
      float far = 2000.0F;
      float left = -wView / 2.0F;
      float right = wView / 2.0F;
      float top = isometricViewLength / 2.0F;
      float bottom = -isometricViewLength / 2.0F;
      float[] arr = new float[]{
         2.0F / (right - left),
         0.0F,
         0.0F,
         -(right + left) / (right - left),
         0.0F,
         2.0F / (top - bottom),
         0.0F,
         -(top + bottom) / (top - bottom),
         0.0F,
         0.0F,
         -2.0F / (far - near),
         -(far + near) / (far - near),
         0.0F,
         0.0F,
         0.0F,
         1.0F
      };
      Matrix4f m1 = new Matrix4f();
      m1.set(arr);
      return m1;
   }

   public static Vec3 getIsometricAdjustedCameraPos() {
      Camera camera = client.gameRenderer.getMainCamera();
      return getIsometricAdjustedCameraPos(camera);
   }

   public static Vec3 getIsometricAdjustedCameraPos(Camera camera) {
      Vec3 cameraPos = camera.position();
      if (!isIsometricView) {
         return cameraPos;
      }

      Quaternionf rotation = camera.rotation();
      Vector3f vec = new Vector3f(0.0F, 0.0F, client.options.getEffectiveRenderDistance() * -10);
      rotation.transform(vec);
      return cameraPos.add(vec.x(), vec.y(), vec.z());
   }

   @Environment(EnvType.CLIENT)
   public static class RemoteCallables {
      public static void enableIsometricView(float viewLength) {
         TransformationManager.isometricViewLength = viewLength;
         TransformationManager.isIsometricView = true;
         TransformationManager.client.smartCull = false;
      }

      public static void disableIsometricView() {
         TransformationManager.isIsometricView = false;
         TransformationManager.client.smartCull = true;
      }
   }
}
