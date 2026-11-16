package qouteall.q_misc_util.platform;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.brigadier.CommandDispatcher;
import java.util.ServiceLoader;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

public interface ClientPlatform {
   static ClientPlatform get() {
      return ClientPlatform.Holder.INSTANCE;
   }

   void onClientTickEnd(Consumer<Minecraft> var1);

   void onClientPlayJoin(Consumer<ClientPacketListener> var1);

   void onClientLoginInit(Runnable var1);

   void postClientChunkLoadEvent(ClientLevel var1, LevelChunk var2);

   void postClientChunkUnloadEvent(ClientLevel var1, LevelChunk var2);

   default GpuDevice unwrapGpuDevice(GpuDevice device) {
      return device;
   }

   default GpuTexture unwrapGpuTexture(GpuTexture texture) {
      return texture;
   }

   default GpuTextureView unwrapGpuTextureView(GpuTextureView view) {
      return view;
   }

   default boolean hasNativeStencilSupport() {
      return false;
   }

   default boolean isStencilEnabled(RenderTarget target) {
      throw new UnsupportedOperationException();
   }

   default void setStencilEnabled(RenderTarget target, boolean enabled) {
      throw new UnsupportedOperationException();
   }

   default void onRenderTargetBuffersCreated(RenderTarget target) {
   }

   default TextureTarget createSecondaryTextureTarget(String label, int width, int height, boolean useDepth) {
      return new TextureTarget(label, width, height, useDepth);
   }

   void onRegisterClientCommands(ClientPlatform.ClientCommandRegistrationHandler var1);

   default Object beforeNestedWorldRender(LevelRenderer outerRenderer) {
      return null;
   }

   default void afterNestedWorldRender(LevelRenderer outerRenderer, Object token) {
   }

   default void onPlayerChangedDimensionClient(ResourceKey<Level> from, ResourceKey<Level> to) {
   }

   interface ClientCommandRegistrationHandler {
      <S> void register(CommandDispatcher<S> var1, ClientPlatform.ClientCommandSupport<S> var2);
   }

   interface ClientCommandSupport<S> {
      void sendFeedback(S var1, Component var2);
   }

   class Holder {
      private static final ClientPlatform INSTANCE = ServiceLoader.load(ClientPlatform.class)
         .findFirst()
         .orElseThrow(() -> new IllegalStateException("No ClientPlatform implementation found"));
   }
}
