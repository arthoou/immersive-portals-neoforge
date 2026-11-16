package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTextureView;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderSystem.AutoStorageIndexBuffer;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.IndexType;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.BlitRenderState;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.renderer.PerspectiveProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;
import qouteall.imm_ptl.core.mixin.client.render.IEGuiGraphics;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.q_misc_util.platform.ClientPlatform;

public class MyRenderHelper {
   public static final Minecraft client = Minecraft.getInstance();
   private static final Matrix4f IDENTITY = new Matrix4f();
   private static final int SCREEN_SIZE_UBO_SIZE = Math.max(16, new Std140SizeCalculator().putFloat().putFloat().get());
   @Nullable
   private static PerspectiveProjectionMatrixBuffer projectionBuffer;
   @Nullable
   private static PerspectiveProjectionMatrixBuffer identityProjectionBuffer;
   @Nullable
   private static GpuBufferSlice identityProjectionSlice;
   @Nullable
   private static GpuBuffer screenSizeUniformBuffer;
   private static boolean debugEnabled = false;

   public static void init() {
   }

   public static GpuBufferSlice getProjectionBuffer(Matrix4f projectionMatrix) {
      if (projectionBuffer == null) {
         projectionBuffer = new PerspectiveProjectionMatrixBuffer("imm_ptl");
      }

      return projectionBuffer.getBuffer(projectionMatrix);
   }

   public static GpuBufferSlice getIdentityProjectionBuffer() {
      if (identityProjectionBuffer == null) {
         identityProjectionBuffer = new PerspectiveProjectionMatrixBuffer("imm_ptl identity");
         identityProjectionSlice = identityProjectionBuffer.getBuffer(new Matrix4f());
      }

      return identityProjectionSlice;
   }

   public static Matrix4f getProjectionMatrix() {
      Matrix4f matrix = RenderStates.projectionMatrix;
      return matrix == null ? new Matrix4f() : matrix;
   }

   public static int getFbo(RenderTarget target) {
      GlDevice device = (GlDevice)ClientPlatform.get().unwrapGpuDevice(RenderSystem.getDevice());
      GlTextureView colorView = (GlTextureView)ClientPlatform.get().unwrapGpuTextureView(target.getColorTextureView());
      GpuTexture depth = ClientPlatform.get().unwrapGpuTexture(target.getDepthTexture());
      return colorView.getFbo(device.directStateAccess(), depth);
   }

   public static void bindRenderTargetFbo(RenderTarget target) {
      GlStateManager._glBindFramebuffer(36160, getFbo(target));
   }

   @IPVanillaCopy
   public static void drawMesh(
      RenderPipeline pipeline,
      MeshData meshData,
      RenderTarget target,
      Matrix4fc modelViewMatrix,
      GpuBufferSlice projection,
      @Nullable Consumer<RenderPass> extraSetup
   ) {
      try {
         GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
            .writeTransform(modelViewMatrix, new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new Vector3f(), new Matrix4f());
         VertexFormat format = pipeline.getVertexFormat();
         GpuBuffer vertexBuffer = format.uploadImmediateVertexBuffer(meshData.vertexBuffer());
         GpuBuffer indexBuffer;
         IndexType indexType;
         if (meshData.indexBuffer() == null) {
            AutoStorageIndexBuffer sequentialBuffer = RenderSystem.getSequentialBuffer(meshData.drawState().mode());
            indexBuffer = sequentialBuffer.getBuffer(meshData.drawState().indexCount());
            indexType = sequentialBuffer.type();
         } else {
            indexBuffer = format.uploadImmediateIndexBuffer(meshData.indexBuffer());
            indexType = meshData.drawState().indexType();
         }

         try (RenderPass renderPass = RenderSystem.getDevice()
               .createCommandEncoder()
               .createRenderPass(
                  () -> "ImmPtl " + pipeline.getLocation(),
                  target.getColorTextureView(),
                  OptionalInt.empty(),
                  target.useDepth ? target.getDepthTextureView() : null,
                  OptionalDouble.empty()
               )) {
            renderPass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("Projection", projection);
            renderPass.setUniform("DynamicTransforms", transforms);
            renderPass.setVertexBuffer(0, vertexBuffer);
            renderPass.setIndexBuffer(indexBuffer, indexType);
            if (extraSetup != null) {
               extraSetup.accept(renderPass);
            }

            renderPass.drawIndexed(0, 0, meshData.drawState().indexCount(), 1);
         }
      } finally {
         meshData.close();
      }
   }

   private static void writeScreenSize(int width, int height) {
      CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
      if (screenSizeUniformBuffer == null) {
         screenSizeUniformBuffer = RenderSystem.getDevice().createBuffer(() -> "ImmPtl screen size UBO", 136, SCREEN_SIZE_UBO_SIZE);
      }

      try (MemoryStack stack = MemoryStack.stackPush()) {
         ByteBuffer buffer = Std140Builder.onStack(stack, SCREEN_SIZE_UBO_SIZE).putFloat(width).putFloat(height).get();
         encoder.writeToBuffer(screenSizeUniformBuffer.slice(), buffer);
      }
   }

   public static void drawPortalAreaWithFramebuffer(
      Portal portal, RenderTarget textureProvider, Matrix4f modelViewMatrix, Matrix4f projectionMatrix, RenderTarget target
   ) {
      writeScreenSize(textureProvider.width, textureProvider.height);
      MeshData mesh = ViewAreaRenderer.buildPortalViewAreaTrianglesMesh(Vec3.ZERO, portal, CHelper.getCurrentCameraPos(), RenderStates.getPartialTick());
      drawMesh(IPRenderPipelines.DRAW_FB_IN_AREA, mesh, target, modelViewMatrix, getProjectionBuffer(projectionMatrix), renderPass -> {
         renderPass.bindTexture("DiffuseSampler", textureProvider.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
         renderPass.setUniform("IPScreenSize", screenSizeUniformBuffer);
      });
   }

   public static void renderScreenTriangle(RenderPipeline pipeline, RenderTarget target) {
      renderScreenTriangle(pipeline, 0.0F, 255, 255, 255, 255, target);
   }

   public static void renderScreenTriangle(RenderPipeline pipeline, Vec3 color, RenderTarget target) {
      renderScreenTriangle(pipeline, 0.0F, (int)(color.x * 255.0), (int)(color.y * 255.0), (int)(color.z * 255.0), 255, target);
   }

   @IPVanillaCopy
   public static void renderScreenTriangle(RenderPipeline pipeline, float z, int r, int g, int b, int a, RenderTarget target) {
      BufferBuilder bufferBuilder = Tesselator.getInstance().begin(Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
      bufferBuilder.addVertex(1.0F, -1.0F, z).setColor(r, g, b, a);
      bufferBuilder.addVertex(1.0F, 1.0F, z).setColor(r, g, b, a);
      bufferBuilder.addVertex(-1.0F, 1.0F, z).setColor(r, g, b, a);
      bufferBuilder.addVertex(-1.0F, 1.0F, z).setColor(r, g, b, a);
      bufferBuilder.addVertex(-1.0F, -1.0F, z).setColor(r, g, b, a);
      bufferBuilder.addVertex(1.0F, -1.0F, z).setColor(r, g, b, a);
      drawMesh(pipeline, bufferBuilder.buildOrThrow(), target, IDENTITY, getIdentityProjectionBuffer(), null);
   }

   public static void drawScreenFrameBuffer(RenderTarget textureProvider, boolean doUseAlphaBlend, boolean doEnableModifyAlpha, RenderTarget target) {
      int x = 0;
      int y = 0;
      int viewportWidth = textureProvider.width;
      int viewportHeight = textureProvider.height;
      drawFramebufferWithCoordinatesAndDimensions(textureProvider, doUseAlphaBlend, doEnableModifyAlpha, x, y, viewportWidth, viewportHeight, target);
   }

   public static void drawFramebuffer(
      RenderTarget textureProvider, boolean doUseAlphaBlend, boolean doEnableModifyAlpha, float xMin, float xMax, float yMin, float yMax, RenderTarget target
   ) {
      drawFramebufferWithCoordinatesAndDimensions(
         textureProvider, doUseAlphaBlend, doEnableModifyAlpha, 0, 0, client.getWindow().getWidth(), client.getWindow().getHeight(), target
      );
   }

   public static void drawFramebufferWithViewport(
      RenderTarget textureProvider,
      boolean doUseAlphaBlend,
      boolean doEnableModifyAlpha,
      float left,
      float right,
      float bottom,
      float up,
      int viewportWidth,
      int viewportHeight,
      RenderTarget target
   ) {
      drawFramebufferWithCoordinatesAndDimensions(textureProvider, doUseAlphaBlend, doEnableModifyAlpha, 0, 0, viewportWidth, viewportHeight, target);
   }

   public static void drawFramebufferWithBounds(
      RenderTarget textureProvider, boolean doUseAlphaBlend, boolean doEnableModifyAlpha, int xMin, int xMax, int yMin, int yMax, RenderTarget target
   ) {
      drawFramebufferWithCoordinatesAndDimensions(
         textureProvider, doUseAlphaBlend, doEnableModifyAlpha, xMin, yMin, Mth.abs(xMax - xMin), Mth.abs(yMax - yMin), target
      );
   }

   @IPVanillaCopy
   public static void drawFramebufferWithCoordinatesAndDimensions(
      RenderTarget textureProvider,
      boolean doUseAlphaBlend,
      boolean doEnableModifyAlpha,
      int x,
      int y,
      int viewportWidth,
      int viewportHeight,
      RenderTarget target
   ) {
      CHelper.checkGlError();
      RenderPipeline pipeline = IPRenderPipelines.blitScreen(doUseAlphaBlend, doEnableModifyAlpha);

      try (RenderPass renderPass = RenderSystem.getDevice()
            .createCommandEncoder()
            .createRenderPass(
               () -> "ImmPtl draw framebuffer",
               target.getColorTextureView(),
               OptionalInt.empty(),
               target.useDepth ? target.getDepthTextureView() : null,
               OptionalDouble.empty()
            )) {
         GlStateManager._viewport(x, textureProvider.height - viewportHeight - y, viewportWidth, viewportHeight);
         renderPass.setPipeline(pipeline);
         RenderSystem.bindDefaultUniforms(renderPass);
         renderPass.bindTexture("InSampler", textureProvider.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
         renderPass.draw(0, 3);
      }

      CHelper.checkGlError();
   }

   public static void drawFramebufferInGui(GuiGraphics guiGraphics, RenderTarget framebuffer, int x0, int y0, int x1, int y1) {
      GuiRenderState guiRenderState = ((IEGuiGraphics)guiGraphics).ip_getGuiRenderState();
      guiRenderState.submitGuiElement(
         new BlitRenderState(
            RenderPipelines.GUI_TEXTURED,
            TextureSetup.singleTexture(framebuffer.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)),
            new Matrix3x2f(guiGraphics.pose()),
            x0,
            y0,
            x1,
            y1,
            0.0F,
            1.0F,
            1.0F,
            0.0F,
            -1,
            null
         )
      );
   }

   public static void lateUpdateLight() {
      if (ClientWorldLoader.getIsInitialized()) {
         ClientWorldLoader.getClientWorlds().forEach(world -> {
            if (!RenderStates.isDimensionRendered(world.dimension())) {
               world.getChunkSource().getLightEngine().runLightUpdates();
            }
         });
      }
   }

   public static void earlyRemoteUpload() {
      if (ClientWorldLoader.getIsInitialized()) {
         ClientWorldLoader.WORLD_RENDERER_MAP.forEach((dim, worldRenderer) -> {
            if (client.level.dimension() != dim) {
               SectionRenderDispatcher dispatcher = worldRenderer.getSectionRenderDispatcher();
               if (dispatcher != null) {
                  dispatcher.uploadAllPendingUploads();
               }
            }
         });
      }
   }

   public static void applyMirrorFaceCulling() {
      GL11.glCullFace(1028);
   }

   public static void recoverFaceCulling() {
      GL11.glCullFace(1029);
   }

   public static void clearAlphaTo1(RenderTarget mcFrameBuffer) {
      renderScreenTriangle(IPRenderPipelines.SCREEN_TRIANGLE_ALPHA_ONLY, 0.0F, 0, 0, 0, 255, mcFrameBuffer);
   }

   public static void restoreViewPort() {
      Minecraft client = Minecraft.getInstance();
      GlStateManager._viewport(0, 0, client.getWindow().getWidth(), client.getWindow().getHeight());
   }

   public static float transformFogDistance(float value) {
      if (!WorldRenderInfo.isFogEnabled()) {
         return value * 23333.0F;
      }

      if (PortalRendering.isRendering()) {
         Portal renderingPortal = PortalRendering.getRenderingPortal();
         if (renderingPortal.isFuseView()) {
            return value * 23333.0F;
         }
      }

      return value;
   }

   public static void debugFramebufferDepth() {
      if (debugEnabled) {
         debugEnabled = false;
         int width = client.getMainRenderTarget().width;
         int height = client.getMainRenderTarget().height;
         GlStateManager._glBindFramebuffer(36008, getFbo(client.getMainRenderTarget()));
         ByteBuffer directBuffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.LITTLE_ENDIAN);
         FloatBuffer floatBuffer = directBuffer.asFloatBuffer();
         GL11.glReadPixels(0, 0, width, height, 6402, 5126, floatBuffer);
         float[] data = new float[width * height];
         floatBuffer.rewind();
         floatBuffer.get(data);
         float maxValue = (float)IntStream.range(0, data.length).mapToDouble(i -> data[i]).max().getAsDouble();
         float minValue = (float)IntStream.range(0, data.length).mapToDouble(i -> data[i]).min().getAsDouble();
         byte[] grayData = new byte[width * height];

         for (int i = 0; i < data.length; i++) {
            float datum = data[i];
            datum = (datum - minValue) / (maxValue - minValue);
            grayData[i] = (byte)(datum * 255.0F);
         }

         BufferedImage bufferedImage = new BufferedImage(width, height, 10);
         bufferedImage.setData(Raster.createRaster(bufferedImage.getSampleModel(), new DataBufferByte(grayData, grayData.length), new Point()));
         System.out.println("oops");
      }
   }

   public static void debugFramebufferColorRed() {
      if (debugEnabled) {
         debugEnabled = false;
         int width = client.getMainRenderTarget().width;
         int height = client.getMainRenderTarget().height;
         GlStateManager._glBindFramebuffer(36008, getFbo(client.getMainRenderTarget()));
         ByteBuffer directBuffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.LITTLE_ENDIAN);
         FloatBuffer floatBuffer = directBuffer.asFloatBuffer();
         GL11.glReadPixels(0, 0, width, height, 6403, 5126, floatBuffer);
         float[] data = new float[width * height];
         floatBuffer.rewind();
         floatBuffer.get(data);
         float maxValue = (float)IntStream.range(0, data.length).mapToDouble(i -> data[i]).max().getAsDouble();
         float minValue = (float)IntStream.range(0, data.length).mapToDouble(i -> data[i]).min().getAsDouble();
         byte[] grayData = new byte[width * height];

         for (int i = 0; i < data.length; i++) {
            float datum = data[i];
            datum = (datum - minValue) / (maxValue - minValue);
            grayData[i] = (byte)(datum * 255.0F);
         }

         BufferedImage bufferedImage = new BufferedImage(width, height, 10);
         bufferedImage.setData(Raster.createRaster(bufferedImage.getSampleModel(), new DataBufferByte(grayData, grayData.length), new Point()));
         System.out.println("oops");
      }
   }
}
