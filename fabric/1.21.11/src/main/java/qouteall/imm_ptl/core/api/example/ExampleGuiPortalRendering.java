package qouteall.imm_ptl.core.api.example;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import java.util.WeakHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.chunk_loading.ChunkLoader;
import qouteall.imm_ptl.core.chunk_loading.DimensionalChunkPos;
import qouteall.imm_ptl.core.render.GuiPortalRendering;
import qouteall.imm_ptl.core.render.MyRenderHelper;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.q_misc_util.api.McRemoteProcedureCall;
import qouteall.q_misc_util.my_util.DQuaternion;

public class ExampleGuiPortalRendering {
   @Environment(EnvType.CLIENT)
   private static RenderTarget frameBuffer;
   private static final WeakHashMap<ServerPlayer, ChunkLoader> chunkLoaderMap = new WeakHashMap<>();

   private static void removeChunkLoaderFor(ServerPlayer player) {
      ChunkLoader chunkLoader = chunkLoaderMap.remove(player);
      if (chunkLoader != null) {
         PortalAPI.removeChunkLoaderForPlayer(player, chunkLoader);
      }
   }

   public static void onCommandExecuted(ServerPlayer player, ServerLevel world, Vec3 pos) {
      removeChunkLoaderFor(player);
      ChunkLoader chunkLoader = new ChunkLoader(new DimensionalChunkPos(world.dimension(), new ChunkPos(BlockPos.containing(pos))), 8);
      PortalAPI.addChunkLoaderForPlayer(player, chunkLoader);
      chunkLoaderMap.put(player, chunkLoader);
      McRemoteProcedureCall.tellClientToInvoke(
         player, "qouteall.imm_ptl.core.api.example.ExampleGuiPortalRendering.RemoteCallables.clientActivateExampleGuiPortal", world.dimension(), pos
      );
   }

   @Environment(EnvType.CLIENT)
   public static class GuiPortalScreen extends Screen {
      private final ResourceKey<Level> viewingDimension;
      private final Vec3 viewingPosition;

      public GuiPortalScreen(ResourceKey<Level> viewingDimension, Vec3 viewingPosition) {
         super(Component.literal("GUI Portal Example"));
         this.viewingDimension = viewingDimension;
         this.viewingPosition = viewingPosition;
      }

      @Override
      public void onClose() {
         super.onClose();
         McRemoteProcedureCall.tellServerToInvoke("qouteall.imm_ptl.core.api.example.ExampleGuiPortalRendering.RemoteCallables.serverRemoveChunkLoader");
      }

      @Override
      public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
         super.render(guiGraphics, mouseX, mouseY, delta);
         double t1 = CHelper.getSmoothCycles(503L);
         double t2 = CHelper.getSmoothCycles(197L);
         Matrix4f cameraTransformation = new Matrix4f();
         cameraTransformation.identity();
         cameraTransformation.mul(DQuaternion.rotationByDegrees(new Vec3(1.0, 1.0, 1.0).normalize(), t1 * 360.0).toMatrix());
         Vec3 cameraPosition = this.viewingPosition.add(new Vec3(Math.cos(t2 * 2.0 * Math.PI), 0.0, Math.sin(t2 * 2.0 * Math.PI)).scale(30.0));
         WorldRenderInfo worldRenderInfo = new WorldRenderInfo.Builder()
            .setWorld(ClientWorldLoader.getWorld(this.viewingDimension))
            .setCameraPos(cameraPosition)
            .setCameraTransformation(cameraTransformation)
            .setOverwriteCameraTransformation(true)
            .setDescription(null)
            .setRenderDistance(this.minecraft.options.getEffectiveRenderDistance())
            .setDoRenderHand(false)
            .setEnableViewBobbing(false)
            .setDoRenderSky(false)
            .setHasFog(false)
            .build();
         GuiPortalRendering.submitNextFrameRendering(worldRenderInfo, ExampleGuiPortalRendering.frameBuffer);
         int h = this.height;
         int w = this.width;
         MyRenderHelper.drawFramebufferInGui(
            guiGraphics, ExampleGuiPortalRendering.frameBuffer, (int)(w * 0.2F), (int)(h * 0.2F), (int)(w * 0.8F), (int)(h * 0.8F)
         );
         guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 70, 16777215);
      }

      @Override
      public boolean isPauseScreen() {
         return false;
      }

      @Override
      public boolean keyPressed(KeyEvent keyEvent) {
         if (super.keyPressed(keyEvent)) {
            return true;
         } else if (this.minecraft.options.keyInventory.matches(keyEvent)) {
            this.onClose();
            return true;
         } else {
            return false;
         }
      }
   }

   public static class RemoteCallables {
      @Environment(EnvType.CLIENT)
      public static void clientActivateExampleGuiPortal(ResourceKey<Level> dimension, Vec3 position) {
         if (ExampleGuiPortalRendering.frameBuffer == null) {
            ExampleGuiPortalRendering.frameBuffer = new TextureTarget("imm_ptl_example_gui_portal", 2, 2, true);
         }

         Minecraft.getInstance().setScreen(new ExampleGuiPortalRendering.GuiPortalScreen(dimension, position));
      }

      public static void serverRemoveChunkLoader(ServerPlayer player) {
         ExampleGuiPortalRendering.removeChunkLoaderFor(player);
      }
   }
}
