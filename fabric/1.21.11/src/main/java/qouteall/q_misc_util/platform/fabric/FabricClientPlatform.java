package qouteall.q_misc_util.platform.fabric;

import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents.Load;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents.Unload;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginConnectionEvents.Init;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Join;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import qouteall.imm_ptl.core.compat.axiom_compatibility.AxiomInterface;
import qouteall.q_misc_util.platform.ClientPlatform;

@Environment(EnvType.CLIENT)
public class FabricClientPlatform implements ClientPlatform {
   @Override
   public void onClientTickEnd(Consumer<Minecraft> listener) {
      ClientTickEvents.END_CLIENT_TICK.register(listener::accept);
   }

   @Override
   public Object beforeNestedWorldRender(LevelRenderer outerRenderer) {
      return new Object[]{FabricWorldRenderContextGuard.snapshot(outerRenderer), AxiomInterface.isPresent() ? AxiomInterface.snapshotRenderStatics() : null};
   }

   @Override
   public void afterNestedWorldRender(LevelRenderer outerRenderer, Object token) {
      Object[] parts = (Object[])token;
      FabricWorldRenderContextGuard.restore((Object[])parts[0]);
      if (parts[1] != null) {
         AxiomInterface.restoreRenderStatics((Object[])parts[1]);
      }
   }

   @Override
   public void onPlayerChangedDimensionClient(ResourceKey<Level> from, ResourceKey<Level> to) {
      if (AxiomInterface.isPresent()) {
         AxiomInterface.onDimensionChanged(to);
      }
   }

   @Override
   public void onClientPlayJoin(Consumer<ClientPacketListener> listener) {
      ClientPlayConnectionEvents.JOIN.register((Join)(handler, sender, client) -> listener.accept(handler));
   }

   @Override
   public void onClientLoginInit(Runnable listener) {
      ClientLoginConnectionEvents.INIT.register((Init)(handler, client) -> listener.run());
   }

   @Override
   public void postClientChunkLoadEvent(ClientLevel level, LevelChunk chunk) {
      ((Load)ClientChunkEvents.CHUNK_LOAD.invoker()).onChunkLoad(level, chunk);
   }

   @Override
   public void postClientChunkUnloadEvent(ClientLevel level, LevelChunk chunk) {
      ((Unload)ClientChunkEvents.CHUNK_UNLOAD.invoker()).onChunkUnload(level, chunk);
   }

   @Override
   public void onRegisterClientCommands(ClientPlatform.ClientCommandRegistrationHandler handler) {
      ClientCommandRegistrationCallback.EVENT
         .register((ClientCommandRegistrationCallback)(dispatcher, registryAccess) -> handler.register(dispatcher, FabricClientCommandSource::sendFeedback));
   }
}
