package qouteall.imm_ptl.core.render.context_management;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.function.Consumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector4f;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.ducks.IECamera;

public class FogRendererContext {
   public float rainFogMultiplier;
   public static Consumer<FogRendererContext> copyContextFromObject;
   public static Consumer<FogRendererContext> copyContextToObject;
   public static StaticFieldsSwappingManager<FogRendererContext> swappingManager;
   @Nullable
   private static FogRenderer fogColorProbe;

   public static void init() {
      swappingManager = new StaticFieldsSwappingManager<>(copyContextFromObject, copyContextToObject, false, FogRendererContext::new);
   }

   public static void update() {
      if (swappingManager != null) {
         swappingManager.setOuterDimension(RenderStates.originalPlayerDimension);
         swappingManager.resetChecks();
         if (ClientWorldLoader.getIsInitialized()) {
            ClientWorldLoader.getClientWorlds()
               .forEach(
                  world -> {
                     ResourceKey<Level> dimension = world.dimension();
                     swappingManager.contextMap
                        .computeIfAbsent(
                           dimension,
                           k -> new StaticFieldsSwappingManager.ContextRecord<>(
                              dimension, new FogRendererContext(), dimension != RenderStates.originalPlayerDimension
                           )
                        );
                  }
               );
         }
      }
   }

   public static Vec3 getCurrentFogColor() {
      return RenderStates.currentFogColor;
   }

   public static Vec3 getFogColorOf(ClientLevel destWorld, Vec3 pos) {
      Minecraft client = Minecraft.getInstance();
      Profiler.get().push("get_fog_color");
      ClientLevel oldWorld = client.level;
      ResourceKey<Level> newWorldKey = destWorld.dimension();
      swappingManager.contextMap
         .computeIfAbsent(newWorldKey, k -> new StaticFieldsSwappingManager.ContextRecord<>((ResourceKey<Level>)k, new FogRendererContext(), true));
      swappingManager.pushSwapping(newWorldKey);
      client.level = destWorld;
      Camera newCamera = new Camera();
      ((IECamera)newCamera).ip_resetState(pos, destWorld);
      ((IECamera)newCamera).portal_setFocusedEntity(client.getCameraEntity());
      ((IECamera)newCamera).portal_tickAttributeProbe();
      GpuBufferSlice oldFog = RenderSystem.getShaderFog();

      try {
         if (fogColorProbe == null) {
            fogColorProbe = new FogRenderer();
         }

         Vector4f color = fogColorProbe.setupFog(
            newCamera,
            client.options.getEffectiveRenderDistance(),
            client.getDeltaTracker(),
            client.gameRenderer.getDarkenWorldAmount(RenderStates.getPartialTick()),
            destWorld
         );
         return new Vec3(color.x, color.y, color.z);
      } finally {
         RenderSystem.setShaderFog(oldFog);
         swappingManager.popSwapping();
         client.level = oldWorld;
         Profiler.get().pop();
      }
   }

   public static void onPlayerTeleport(ResourceKey<Level> from, ResourceKey<Level> to) {
      swappingManager.updateOuterDimensionAndChangeContext(to);
      Minecraft client = Minecraft.getInstance();
      if (client.player != null && client.level != null) {
         EnvironmentAttributeProbe probe = client.gameRenderer.getMainCamera().attributeProbe();
         probe.reset();
         probe.tick(client.level, client.player.getEyePosition());
      }
   }
}
