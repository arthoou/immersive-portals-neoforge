package qouteall.imm_ptl.core.mc_utils;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import java.util.Random;
import java.util.function.IntSupplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.animation.StableClientTimer;
import qouteall.imm_ptl.core.portal.animation.UnilateralPortalState;
import qouteall.imm_ptl.core.portal.shape.SpecialFlatPortalShape;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.q_misc_util.my_util.Circle;
import qouteall.q_misc_util.my_util.DQuaternion;
import qouteall.q_misc_util.my_util.Plane;
import qouteall.q_misc_util.my_util.Sphere;

@Environment(EnvType.CLIENT)
public class WireRenderingHelper {
   public static float getLineWidth() {
      return Minecraft.getInstance().getWindow().getAppropriateLineWidth();
   }

   public static void renderSmallCubeFrame(VertexConsumer vertexConsumer, Vec3 cameraPos, Vec3 boxCenter, int color, double scale, PoseStack matrixStack) {
      Random random = new Random(color);
      double boxSize = Math.pow(boxCenter.distanceTo(cameraPos), 0.3) * 0.09;
      matrixStack.pushPose();
      matrixStack.translate(boxCenter.x - cameraPos.x, boxCenter.y - cameraPos.y, boxCenter.z - cameraPos.z);
      matrixStack.scale((float)scale, (float)scale, (float)scale);
      DQuaternion rotation = getRandomSmoothRotation(random);
      double periodLen = 100.0;
      matrixStack.mulPose(rotation.toMcQuaternion());
      Matrix4f matrix = matrixStack.last().pose();
      float alpha = (color >> 24 & 0xFF) / 255.0F;
      float red = (color >> 16 & 0xFF) / 255.0F;
      float green = (color >> 8 & 0xFF) / 255.0F;
      float blue = (color & 0xFF) / 255.0F;
      renderLineBox(
         matrixStack, vertexConsumer, -boxSize / 2.0, -boxSize / 2.0, -boxSize / 2.0, boxSize / 2.0, boxSize / 2.0, boxSize / 2.0, red, green, blue, alpha
      );
      matrixStack.popPose();
   }

   public static void renderLineBox(
      PoseStack poseStack,
      VertexConsumer consumer,
      double minX,
      double minY,
      double minZ,
      double maxX,
      double maxY,
      double maxZ,
      float red,
      float green,
      float blue,
      float alpha
   ) {
      Pose pose = poseStack.last();
      float lineWidth = getLineWidth();
      float x0 = (float)minX;
      float y0 = (float)minY;
      float z0 = (float)minZ;
      float x1 = (float)maxX;
      float y1 = (float)maxY;
      float z1 = (float)maxZ;
      consumer.addVertex(pose, x0, y0, z0).setColor(red, green, blue, alpha).setNormal(pose, 1.0F, 0.0F, 0.0F).setLineWidth(lineWidth);
      consumer.addVertex(pose, x1, y0, z0).setColor(red, green, blue, alpha).setNormal(pose, 1.0F, 0.0F, 0.0F).setLineWidth(lineWidth);
      consumer.addVertex(pose, x0, y0, z0).setColor(red, green, blue, alpha).setNormal(pose, 0.0F, 1.0F, 0.0F).setLineWidth(lineWidth);
      consumer.addVertex(pose, x0, y1, z0).setColor(red, green, blue, alpha).setNormal(pose, 0.0F, 1.0F, 0.0F).setLineWidth(lineWidth);
      consumer.addVertex(pose, x0, y0, z0).setColor(red, green, blue, alpha).setNormal(pose, 0.0F, 0.0F, 1.0F).setLineWidth(lineWidth);
      consumer.addVertex(pose, x0, y0, z1).setColor(red, green, blue, alpha).setNormal(pose, 0.0F, 0.0F, 1.0F).setLineWidth(lineWidth);
      consumer.addVertex(pose, x1, y0, z0).setColor(red, green, blue, alpha).setNormal(pose, 0.0F, 1.0F, 0.0F).setLineWidth(lineWidth);
      consumer.addVertex(pose, x1, y1, z0).setColor(red, green, blue, alpha).setNormal(pose, 0.0F, 1.0F, 0.0F).setLineWidth(lineWidth);
      consumer.addVertex(pose, x1, y1, z0).setColor(red, green, blue, alpha).setNormal(pose, -1.0F, 0.0F, 0.0F).setLineWidth(lineWidth);
      consumer.addVertex(pose, x0, y1, z0).setColor(red, green, blue, alpha).setNormal(pose, -1.0F, 0.0F, 0.0F).setLineWidth(lineWidth);
      consumer.addVertex(pose, x0, y1, z0).setColor(red, green, blue, alpha).setNormal(pose, 0.0F, 0.0F, 1.0F).setLineWidth(lineWidth);
      consumer.addVertex(pose, x0, y1, z1).setColor(red, green, blue, alpha).setNormal(pose, 0.0F, 0.0F, 1.0F).setLineWidth(lineWidth);
      consumer.addVertex(pose, x0, y1, z1).setColor(red, green, blue, alpha).setNormal(pose, 0.0F, -1.0F, 0.0F).setLineWidth(lineWidth);
      consumer.addVertex(pose, x0, y0, z1).setColor(red, green, blue, alpha).setNormal(pose, 0.0F, -1.0F, 0.0F).setLineWidth(lineWidth);
      consumer.addVertex(pose, x0, y0, z1).setColor(red, green, blue, alpha).setNormal(pose, 1.0F, 0.0F, 0.0F).setLineWidth(lineWidth);
      consumer.addVertex(pose, x1, y0, z1).setColor(red, green, blue, alpha).setNormal(pose, 1.0F, 0.0F, 0.0F).setLineWidth(lineWidth);
      consumer.addVertex(pose, x1, y0, z1).setColor(red, green, blue, alpha).setNormal(pose, 0.0F, 0.0F, -1.0F).setLineWidth(lineWidth);
      consumer.addVertex(pose, x1, y0, z0).setColor(red, green, blue, alpha).setNormal(pose, 0.0F, 0.0F, -1.0F).setLineWidth(lineWidth);
      consumer.addVertex(pose, x0, y1, z1).setColor(red, green, blue, alpha).setNormal(pose, 1.0F, 0.0F, 0.0F).setLineWidth(lineWidth);
      consumer.addVertex(pose, x1, y1, z1).setColor(red, green, blue, alpha).setNormal(pose, 1.0F, 0.0F, 0.0F).setLineWidth(lineWidth);
      consumer.addVertex(pose, x1, y0, z1).setColor(red, green, blue, alpha).setNormal(pose, 0.0F, 1.0F, 0.0F).setLineWidth(lineWidth);
      consumer.addVertex(pose, x1, y1, z1).setColor(red, green, blue, alpha).setNormal(pose, 0.0F, 1.0F, 0.0F).setLineWidth(lineWidth);
      consumer.addVertex(pose, x1, y1, z0).setColor(red, green, blue, alpha).setNormal(pose, 0.0F, 0.0F, 1.0F).setLineWidth(lineWidth);
      consumer.addVertex(pose, x1, y1, z1).setColor(red, green, blue, alpha).setNormal(pose, 0.0F, 0.0F, 1.0F).setLineWidth(lineWidth);
   }

   public static DQuaternion getRandomSmoothRotation(Random random) {
      double time = (double)StableClientTimer.getStableTickTime() + StableClientTimer.getStablePartialTicks();
      DQuaternion rotation = DQuaternion.identity;

      for (int i = 0; i < 6; i++) {
         rotation = rotation.hamiltonProduct(DQuaternion.rotationByDegrees(randomVec(random), CHelper.getSmoothCycles(random.nextInt(30, 60)) * 360.0));
      }

      return rotation;
   }

   public static double getRandomSmoothCycle(Random random) {
      double totalFactor = 0.1;
      double total = 0.0;

      for (int i = 0; i < 5; i++) {
         double smoothCycle = CHelper.getSmoothCycles(random.nextInt(30, 300));
         double sin = Math.sin((Math.PI * 2) * smoothCycle);
         double factor = random.nextDouble(0.1, 1.0);
         totalFactor += factor;
         total += sin * factor;
      }

      return total / totalFactor;
   }

   @NotNull
   public static Vec3 randomVec(Random random) {
      return new Vec3(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5);
   }

   public static void renderPlane(
      VertexConsumer vertexConsumer, Vec3 cameraPos, Plane plane, double renderedPlaneScale, int color, PoseStack matrixStack, boolean isLineStrip
   ) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         Vec3 planeCenter = plane.pos();
         Vec3 normal = plane.normal();
         Vec3 anyVecNonNormal = new Vec3(13.0, 29.0, 71.0).normalize();
         if (Math.abs(normal.dot(anyVecNonNormal)) > 0.99) {
            anyVecNonNormal = new Vec3(1.0, 0.0, 0.0);
         }

         Vec3 planeX = normal.cross(anyVecNonNormal).normalize();
         Vec3 planeY = normal.cross(planeX).normalize();
         matrixStack.pushPose();
         matrixStack.translate(planeCenter.x - cameraPos.x, planeCenter.y - cameraPos.y, planeCenter.z - cameraPos.z);
         matrixStack.mulPose(DQuaternion.rotationByDegrees(normal, CHelper.getSmoothCycles(211L) * 360.0).toMcQuaternion());
         Matrix4f matrix = matrixStack.last().pose();
         double cameraDistanceToCenter = player.getEyePosition(RenderStates.getPartialTick()).distanceTo(planeCenter);
         int lineNumPerSide = 10;
         double lineInterval = cameraDistanceToCenter * 0.2 * renderedPlaneScale;
         double lineLenPerSide = lineNumPerSide * lineInterval;

         for (int ix = -lineNumPerSide; ix <= lineNumPerSide; ix++) {
            Vec3 lineStart = planeX.scale(ix * lineInterval).add(planeY.scale(-lineLenPerSide));
            Vec3 lineEnd = planeX.scale(ix * lineInterval).add(planeY.scale(lineLenPerSide));
            if (isLineStrip) {
               putLineToLineStrip(vertexConsumer, color, planeY, matrix, lineStart, lineEnd);
            } else {
               putLine(vertexConsumer, color, planeY, matrix, matrixStack.last().normal(), lineStart, lineEnd);
            }
         }

         for (int iy = -lineNumPerSide; iy <= lineNumPerSide; iy++) {
            Vec3 lineStart = planeY.scale(iy * lineInterval).add(planeX.scale(-lineLenPerSide));
            Vec3 lineEnd = planeY.scale(iy * lineInterval).add(planeX.scale(lineLenPerSide));
            if (isLineStrip) {
               putLineToLineStrip(vertexConsumer, color, planeX, matrix, lineStart, lineEnd);
            } else {
               putLine(vertexConsumer, color, planeX, matrix, matrixStack.last().normal(), lineStart, lineEnd);
            }
         }

         matrixStack.popPose();
      }
   }

   public static void renderCircle(VertexConsumer vertexConsumer, Vec3 cameraPos, Circle circle, int color, PoseStack matrixStack) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         Vec3 planeCenter = circle.plane().pos();
         Vec3 normal = circle.plane().normal();
         Vec3 anyVecNonNormal = new Vec3(0.0, 1.0, 0.0);
         if (Math.abs(normal.dot(anyVecNonNormal)) > 0.9) {
            anyVecNonNormal = new Vec3(1.0, 0.0, 0.0);
         }

         Vec3 planeX = normal.cross(anyVecNonNormal).normalize();
         Vec3 planeY = normal.cross(planeX).normalize();
         Vec3 circleCenter = circle.circleCenter();
         double circleRadius = circle.radius();
         matrixStack.pushPose();
         matrixStack.translate(circleCenter.x - cameraPos.x, circleCenter.y - cameraPos.y, circleCenter.z - cameraPos.z);
         Matrix4f matrix = matrixStack.last().pose();
         int vertexNum = Mth.clamp((int)Math.round(circleRadius * 40.0), 40, 400);

         for (int i = 0; i < vertexNum; i++) {
            double angle = i * 2 * Math.PI / vertexNum;
            double nextAngle = (i + 1) * 2 * Math.PI / vertexNum;
            boolean isBegin = i == 0;
            boolean isEnd = i == vertexNum - 1;
            Vec3 lineStart = planeX.scale(Math.cos(angle) * circleRadius).add(planeY.scale(Math.sin(angle) * circleRadius));
            Vec3 lineEnd = planeX.scale(Math.cos(nextAngle) * circleRadius).add(planeY.scale(Math.sin(nextAngle) * circleRadius));
            if (isBegin) {
               vertexConsumer.addVertex(matrix, (float)lineStart.x, (float)lineStart.y, (float)lineStart.z)
                  .setColor(0)
                  .setNormal((float)normal.x, (float)normal.y, (float)normal.z)
                  .setLineWidth(getLineWidth());
               vertexConsumer.addVertex(matrix, (float)lineStart.x, (float)lineStart.y, (float)lineStart.z)
                  .setColor(color)
                  .setNormal((float)normal.x, (float)normal.y, (float)normal.z)
                  .setLineWidth(getLineWidth());
            }

            vertexConsumer.addVertex(matrix, (float)lineEnd.x, (float)lineEnd.y, (float)lineEnd.z)
               .setColor(color)
               .setNormal((float)normal.x, (float)normal.y, (float)normal.z)
               .setLineWidth(getLineWidth());
            if (isEnd) {
               vertexConsumer.addVertex(matrix, (float)lineEnd.x, (float)lineEnd.y, (float)lineEnd.z)
                  .setColor(0)
                  .setNormal((float)normal.x, (float)normal.y, (float)normal.z)
                  .setLineWidth(getLineWidth());
            }
         }

         matrixStack.popPose();
      }
   }

   public static void renderLockShape(VertexConsumer vertexConsumer, Vec3 cameraPos, Vec3 center, double scale, int color, PoseStack matrixStack) {
      double w = 380.0;
      double h = 270.0;
      double ringWidth = 60.0;
      double ringAreaWidth = 152.0;
      double rightAreaHeight = 136.0;
      Vec3[] lineVertices = new Vec3[]{
         new Vec3(w / 2.0, h / 2.0, 0.0),
         new Vec3(-w / 2.0, h / 2.0, 0.0),
         new Vec3(-w / 2.0, h / 2.0, 0.0),
         new Vec3(-w / 2.0, -h / 2.0, 0.0),
         new Vec3(-w / 2.0, -h / 2.0, 0.0),
         new Vec3(w / 2.0, -h / 2.0, 0.0),
         new Vec3(w / 2.0, -h / 2.0, 0.0),
         new Vec3(w / 2.0, h / 2.0, 0.0),
         new Vec3(ringAreaWidth / 2.0, h / 2.0, 0.0),
         new Vec3(ringAreaWidth / 2.0, h / 2.0 + rightAreaHeight, 0.0),
         new Vec3(ringAreaWidth / 2.0, h / 2.0 + rightAreaHeight, 0.0),
         new Vec3(-ringAreaWidth / 2.0, h / 2.0 + rightAreaHeight, 0.0),
         new Vec3(-ringAreaWidth / 2.0, h / 2.0 + rightAreaHeight, 0.0),
         new Vec3(-ringAreaWidth / 2.0, h / 2.0, 0.0),
         new Vec3(ringAreaWidth / 2.0 + ringWidth, h / 2.0, 0.0),
         new Vec3(ringAreaWidth / 2.0 + ringWidth, h / 2.0 + rightAreaHeight + ringWidth, 0.0),
         new Vec3(ringAreaWidth / 2.0 + ringWidth, h / 2.0 + rightAreaHeight + ringWidth, 0.0),
         new Vec3(-ringAreaWidth / 2.0 - ringWidth, h / 2.0 + rightAreaHeight + ringWidth, 0.0),
         new Vec3(-ringAreaWidth / 2.0 - ringWidth, h / 2.0 + rightAreaHeight + ringWidth, 0.0),
         new Vec3(-ringAreaWidth / 2.0 - ringWidth, h / 2.0, 0.0)
      };
      float renderScale = (float)(scale * 0.001);
      DQuaternion rotation = DQuaternion.rotationByDegrees(new Vec3(0.0, 1.0, 0.0), CHelper.getSmoothCycles(60L) * 360.0);
      renderLines(vertexConsumer, cameraPos, center, lineVertices, renderScale, rotation, color, matrixStack);
   }

   public static void renderLines(
      VertexConsumer vertexConsumer, Vec3 cameraPos, Vec3 center, Vec3[] lineVertices, double scale, DQuaternion rotation, int color, PoseStack matrixStack
   ) {
      matrixStack.pushPose();
      matrixStack.translate(center.x - cameraPos.x, center.y - cameraPos.y, center.z - cameraPos.z);
      matrixStack.mulPose(rotation.toMcQuaternion());
      matrixStack.scale((float)scale, (float)scale, (float)scale);
      Matrix4f matrix = matrixStack.last().pose();
      Matrix3f normalMatrix = matrixStack.last().normal();

      for (int i = 0; i < lineVertices.length / 2; i++) {
         putLine(vertexConsumer, color, matrix, normalMatrix, lineVertices[i * 2], lineVertices[i * 2 + 1]);
      }

      matrixStack.popPose();
   }

   public static void putLine(VertexConsumer vertexConsumer, int color, Matrix4f matrix, Matrix3f normalMatrix, Vec3 lineStart, Vec3 lineEnd) {
      putLine(vertexConsumer, color, lineEnd.subtract(lineStart), matrix, normalMatrix, lineStart, lineEnd);
   }

   public static void putLine(VertexConsumer vertexConsumer, int color, Vec3 normal, Matrix4f matrix, Matrix3f normalMatrix, Vec3 lineStart, Vec3 lineEnd) {
      Vector3f normalTemp = new Vector3f();
      normalTemp.set(normal.x(), normal.y(), normal.z());
      normalMatrix.transform(normalTemp);
      vertexConsumer.addVertex(matrix, (float)lineStart.x, (float)lineStart.y, (float)lineStart.z)
         .setColor(color)
         .setNormal(normalTemp.x(), normalTemp.y(), normalTemp.z())
         .setLineWidth(getLineWidth());
      vertexConsumer.addVertex(matrix, (float)lineEnd.x, (float)lineEnd.y, (float)lineEnd.z)
         .setColor(color)
         .setNormal(normalTemp.x(), normalTemp.y(), normalTemp.z())
         .setLineWidth(getLineWidth());
   }

   private static void putLineToLineStrip(VertexConsumer vertexConsumer, int color, Vec3 normal, Matrix4f matrix, Vec3 lineStart, Vec3 lineEnd) {
      vertexConsumer.addVertex(matrix, (float)lineStart.x, (float)lineStart.y, (float)lineStart.z)
         .setColor(0)
         .setNormal((float)normal.x, (float)normal.y, (float)normal.z)
         .setLineWidth(getLineWidth());
      vertexConsumer.addVertex(matrix, (float)lineStart.x, (float)lineStart.y, (float)lineStart.z)
         .setColor(color)
         .setNormal((float)normal.x, (float)normal.y, (float)normal.z)
         .setLineWidth(getLineWidth());
      vertexConsumer.addVertex(matrix, (float)lineEnd.x, (float)lineEnd.y, (float)lineEnd.z)
         .setColor(color)
         .setNormal((float)normal.x, (float)normal.y, (float)normal.z)
         .setLineWidth(getLineWidth());
      vertexConsumer.addVertex(matrix, (float)lineEnd.x, (float)lineEnd.y, (float)lineEnd.z)
         .setColor(0)
         .setNormal((float)normal.x, (float)normal.y, (float)normal.z)
         .setLineWidth(getLineWidth());
   }

   public static void renderRectLine(
      VertexConsumer vertexConsumer,
      Vec3 cameraPos,
      UnilateralPortalState rect,
      int flowCount,
      int color,
      double shrinkFactor,
      int flowDirection,
      PoseStack matrixStack
   ) {
      matrixStack.pushPose();
      matrixStack.translate(rect.position().x - cameraPos.x, rect.position().y - cameraPos.y, rect.position().z - cameraPos.z);
      Vec3[] vertices = getRectVertices(rect, shrinkFactor);
      Random random = new Random(color);
      renderFlowLines(vertexConsumer, vertices, flowCount, color, flowDirection, matrixStack, () -> random.nextInt(30, 300));
      matrixStack.popPose();
   }

   public static void renderFlowLines(
      VertexConsumer vertexConsumer, Vec3[] vertices, int flowCount, int color, int flowDirection, PoseStack matrixStack, IntSupplier randCycleSupplier
   ) {
      Matrix4f matrix = matrixStack.last().pose();
      Matrix3f normalMatrix = matrixStack.last().normal();

      for (int i = 0; i < flowCount; i++) {
         double offset = flowDirection * CHelper.getSmoothCycles(randCycleSupplier.getAsInt());
         double totalStartRatio = i * 2.0 / (flowCount * 2) + offset;
         double totalEndRatio = (i * 2.0 + 1.0) / (flowCount * 2) + offset;
         renderSubLineInLineLoop(vertexConsumer, matrix, normalMatrix, vertices, color, totalStartRatio, totalEndRatio);
      }
   }

   private static Vec3[] getRectVertices(UnilateralPortalState rect, double shrinkFactor) {
      Vec3 normal = rect.orientation().getNormal();
      Vec3 axisW = rect.orientation().getAxisW();
      Vec3 axisH = rect.orientation().getAxisH();
      Vec3 facingOffset = normal.scale(0.01);
      return new Vec3[]{
         axisW.scale(shrinkFactor * rect.width() / 2.0).add(axisH.scale(shrinkFactor * rect.height() / 2.0)).add(facingOffset),
         axisW.scale(shrinkFactor * rect.width() / 2.0).add(axisH.scale(-1.0 * shrinkFactor * rect.height() / 2.0)).add(facingOffset),
         axisW.scale(-1.0 * shrinkFactor * rect.width() / 2.0).add(axisH.scale(-1.0 * shrinkFactor * rect.height() / 2.0)).add(facingOffset),
         axisW.scale(-1.0 * shrinkFactor * rect.width() / 2.0).add(axisH.scale(shrinkFactor * rect.height() / 2.0)).add(facingOffset),
         axisW.scale(shrinkFactor * rect.width() / 2.0).add(axisH.scale(shrinkFactor * rect.height() / 2.0)).add(facingOffset)
      };
   }

   public static void renderSubLineInLineLoop(
      VertexConsumer vertexConsumer, Matrix4f matrix, Matrix3f normalMatrix, Vec3[] lineVertices, int color, double totalStartRatio, double totalEndRatio
   ) {
      int lineNum = lineVertices.length - 1;
      double startRatioByLine = totalStartRatio * lineNum;
      double endRatioByLine = totalEndRatio * lineNum;
      int startRatioLineIndex = (int)Math.floor(startRatioByLine);
      int endRatioLineIndex = (int)Math.floor(endRatioByLine);

      for (int lineIndex = startRatioLineIndex; lineIndex <= endRatioLineIndex; lineIndex++) {
         double startLimit = lineIndex;
         double endLimit = lineIndex + 1;
         double startRatio = Math.max(startLimit, startRatioByLine);
         double endRatio = Math.min(endLimit, endRatioByLine);
         putLinePart(
            vertexConsumer,
            color,
            matrix,
            normalMatrix,
            lineVertices[Math.floorMod(lineIndex, lineNum)],
            lineVertices[Math.floorMod(lineIndex, lineNum) + 1],
            startRatio - lineIndex,
            endRatio - lineIndex
         );
      }
   }

   private static void putLinePart(
      VertexConsumer vertexConsumer, int color, Matrix4f matrix, Matrix3f normalMatrix, Vec3 lineStart, Vec3 lineEnd, double startRatio, double endRatio
   ) {
      Vec3 vec = lineEnd.subtract(lineStart);
      Vec3 partStartPos = lineStart.add(vec.scale(startRatio));
      Vec3 partEndPos = lineStart.add(vec.scale(endRatio));
      putLine(vertexConsumer, color, matrix, normalMatrix, partStartPos, partEndPos);
   }

   public static void renderSphere(
      VertexConsumer vertexConsumer,
      int color,
      PoseStack matrixStack,
      Vec3 cameraPos,
      Sphere sphere,
      DQuaternion sphereOrientation,
      double animationProgress,
      double rotationProgress
   ) {
      matrixStack.pushPose();
      matrixStack.translate(sphere.center().x - cameraPos.x, sphere.center().y - cameraPos.y, sphere.center().z - cameraPos.z);
      matrixStack.mulPose(sphereOrientation.toMcQuaternion());
      matrixStack.scale((float)sphere.radius(), (float)sphere.radius(), (float)sphere.radius());
      Matrix4f matrix = matrixStack.last().pose();
      int meridianCount = 30;
      int parallelCount = 15;
      int vertexNum = Mth.clamp((int)Math.round(sphere.radius() * 40.0), 20, 400);
      int transparentColor = color & 16777215;

      for (int i = 0; i < meridianCount; i++) {
         double longitude = ((double)i / meridianCount + rotationProgress) * Math.PI * 2.0;

         for (int j = 0; j < vertexNum; j++) {
            double latitudeRatio = (double)j / vertexNum;
            latitudeRatio = Math.min(latitudeRatio, animationProgress);
            double latitude = latitudeRatio * Math.PI - (Math.PI / 2);
            double x = Math.cos(latitude) * Math.cos(longitude);
            double y = Math.sin(latitude);
            double z = Math.cos(latitude) * Math.sin(longitude);
            boolean isFirst = j == 0;
            if (isFirst) {
               vertexConsumer.addVertex(matrix, (float)x, (float)y, (float)z)
                  .setColor(transparentColor)
                  .setNormal(0.0F, 1.0F, 0.0F)
                  .setLineWidth(getLineWidth());
            }

            vertexConsumer.addVertex(matrix, (float)x, (float)y, (float)z).setColor(color).setNormal(0.0F, 1.0F, 0.0F).setLineWidth(getLineWidth());
            boolean isLast = j == vertexNum - 1;
            if (isLast) {
               vertexConsumer.addVertex(matrix, (float)x, (float)y, (float)z)
                  .setColor(transparentColor)
                  .setNormal(0.0F, 1.0F, 0.0F)
                  .setLineWidth(getLineWidth());
            }
         }
      }

      for (int i = 0; i < parallelCount; i++) {
         double latitudeRatio = (double)i / parallelCount + rotationProgress;
         latitudeRatio -= Math.floor(latitudeRatio);
         double latitude = latitudeRatio * Math.PI - (Math.PI / 2);

         for (int j = 0; j <= vertexNum; j++) {
            double longitudeRatio = (double)j / vertexNum;
            longitudeRatio = Math.min(longitudeRatio, animationProgress);
            double longitude = longitudeRatio * Math.PI * 2.0;
            double x = Math.cos(latitude) * Math.cos(longitude);
            double y = Math.sin(latitude);
            double z = Math.cos(latitude) * Math.sin(longitude);
            boolean isFirst = j == 0;
            if (isFirst) {
               vertexConsumer.addVertex(matrix, (float)x, (float)y, (float)z)
                  .setColor(transparentColor)
                  .setNormal(0.0F, 1.0F, 0.0F)
                  .setLineWidth(getLineWidth());
            }

            vertexConsumer.addVertex(matrix, (float)x, (float)y, (float)z).setColor(color).setNormal(0.0F, 1.0F, 0.0F).setLineWidth(getLineWidth());
            boolean isLast = j == vertexNum;
            if (isLast) {
               vertexConsumer.addVertex(matrix, (float)x, (float)y, (float)z)
                  .setColor(transparentColor)
                  .setNormal(0.0F, 1.0F, 0.0F)
                  .setLineWidth(getLineWidth());
            }
         }
      }

      matrixStack.popPose();
   }

   public static void renderRectFrameFlow(
      PoseStack matrixStack, Vec3 cameraPos, VertexConsumer vertexConsumer, UnilateralPortalState rect, int innerColor, int outerColor
   ) {
      matrixStack.pushPose();
      matrixStack.scale(0.5F, 0.5F, 0.5F);
      renderRectLine(vertexConsumer, cameraPos, rect, 10, innerColor, 0.99, 1, matrixStack);
      renderRectLine(vertexConsumer, cameraPos, rect, 10, outerColor, 1.01, -1, matrixStack);
      matrixStack.popPose();
   }

   public static void renderPortalShapeMeshDebug(PoseStack matrixStack, VertexConsumer vertexConsumer, Portal portal) {
      double cycle = (Math.sin(CHelper.getSmoothCycles(50L) * 2.0 * Math.PI) + 1.0) / 2.0;
      double shrink = 0.03 * cycle;
      if (portal.getPortalShape() instanceof SpecialFlatPortalShape shape) {
         shape.mesh.compact();
         int triangleNum = shape.mesh.getStoredTriangleNum();
         int vertexNum = triangleNum * 3;
         Vec3[] vertexes = new Vec3[vertexNum];
         double halfWidth = portal.getWidth() / 2.0;
         double halfHeight = portal.getHeight() / 2.0;
         Vec3 X = portal.getAxisW().scale(halfWidth);
         Vec3 Y = portal.getAxisH().scale(halfHeight);
         matrixStack.pushPose();
         Matrix4f matrix = matrixStack.last().pose();
         Matrix3f normalMatrix = matrixStack.last().normal();

         for (int i = 0; i < triangleNum; i++) {
            int p0Index = shape.mesh.getTrianglePointIndex(i, 0);
            int p1Index = shape.mesh.getTrianglePointIndex(i, 1);
            int p2Index = shape.mesh.getTrianglePointIndex(i, 2);
            double p0x = shape.mesh.pointCoords.getDouble(p0Index * 2);
            double p0y = shape.mesh.pointCoords.getDouble(p0Index * 2 + 1);
            double p1x = shape.mesh.pointCoords.getDouble(p1Index * 2);
            double p1y = shape.mesh.pointCoords.getDouble(p1Index * 2 + 1);
            double p2x = shape.mesh.pointCoords.getDouble(p2Index * 2);
            double p2y = shape.mesh.pointCoords.getDouble(p2Index * 2 + 1);
            double centerX = (p0x + p1x + p2x) / 3.0;
            double centerY = (p0y + p1y + p2y) / 3.0;
            double x0 = p0x * (1.0 - shrink) + centerX * shrink;
            double y0 = p0y * (1.0 - shrink) + centerY * shrink;
            double x1 = p1x * (1.0 - shrink) + centerX * shrink;
            double y1 = p1y * (1.0 - shrink) + centerY * shrink;
            double x2 = p2x * (1.0 - shrink) + centerX * shrink;
            double y2 = p2y * (1.0 - shrink) + centerY * shrink;
            putLine(vertexConsumer, -2130771968, matrix, normalMatrix, X.scale(x0).add(Y.scale(y0)), X.scale(x1).add(Y.scale(y1)));
            putLine(vertexConsumer, -2130771968, matrix, normalMatrix, X.scale(x1).add(Y.scale(y1)), X.scale(x2).add(Y.scale(y2)));
            putLine(vertexConsumer, -2130771968, matrix, normalMatrix, X.scale(x2).add(Y.scale(y2)), X.scale(x0).add(Y.scale(y0)));
         }

         matrixStack.popPose();
      }
   }
}
