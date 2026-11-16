package qouteall.imm_ptl.core.compat.voxy_compatibility;

import com.mojang.logging.LogUtils;
import java.util.Map.Entry;
import me.cortex.voxy.client.core.IGetVoxyRenderSystem;
import me.cortex.voxy.client.core.gl.Capabilities;
import me.cortex.voxy.client.iris.IGetIrisVoxyPipelineData;
import me.cortex.voxy.client.iris.IrisVoxyRenderPipelineData;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface;
import qouteall.imm_ptl.core.render.GuiPortalRendering;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.renderer.RendererUsingStencil;

@Environment(EnvType.CLIENT)
public class VoxyInterface {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final String KEEP_PROPERTY = "immptl.voxy.lodSystemPerDimension";
   public static final long KEEP_MIN_FREE_VRAM = 3221225472L;
   public static VoxyInterface.Invoker invoker = new VoxyInterface.Invoker();
   public static boolean prewarmingNow = false;
   public static boolean creatingNow = false;
   public static boolean skipGcNow = false;
   private static boolean gateBypassed = false;

   public static boolean isSecondaryContext() {
      return ClientWorldLoader.getIsCreatingClientWorld() || ClientWorldLoader.getIsWorldSwitched() || PortalRendering.isRendering();
   }

   public static boolean shouldHideRenderSystem(LevelRenderer self) {
      if (gateBypassed) {
         return false;
      }

      if (ClientWorldLoader.getIsCreatingClientWorld() || GuiPortalRendering.isRendering()) {
         return true;
      }

      if (!PortalRendering.isRendering()) {
         return false;
      }

      Minecraft mc = Minecraft.getInstance();
      return mc.player == null || mc.level == mc.player.level();
   }

   public static void afterLodDraw() {
      if (isStencilPortalPass()) {
         GL11.glEnable(2960);
         RendererUsingStencil.setStencilLimitation(PortalRendering.getPortalLayer());
      }
   }

   public static void onVoxyGlDisable(int cap) {
      if (cap == 2960 && isStencilPortalPass()) {
         GL11.glEnable(2960);
         RendererUsingStencil.setStencilLimitation(PortalRendering.getPortalLayer());
      } else {
         GL11.glDisable(cap);
      }
   }

   private static boolean isStencilPortalPass() {
      return PortalRendering.isRendering() && IPCGlobal.renderer == IPCGlobal.rendererUsingStencil;
   }

   public static class Invoker {
      public boolean isVoxyPresent() {
         return false;
      }

      public void onPlayerDimensionChangePromoted(LevelRenderer promotedRenderer, ClientLevel toWorld, LevelRenderer demotedRenderer) {
      }

      public void onRendererAboutToRender(LevelRenderer renderer) {
      }

      public boolean isLowOnVideoMemory() {
         return false;
      }
   }

   public static class OnVoxyPresent extends VoxyInterface.Invoker {
      private int lastKeepDecision = -1;
      private boolean foreignBindingWarned = false;

      @Override
      public boolean isVoxyPresent() {
         return true;
      }

      @Nullable
      private static Object systemOf(LevelRenderer renderer) {
         boolean saved = VoxyInterface.gateBypassed;
         VoxyInterface.gateBypassed = true;

         try {
            return ((IGetVoxyRenderSystem)renderer).voxy$getRenderSystem();
         } finally {
            VoxyInterface.gateBypassed = saved;
         }
      }

      boolean keepSystemsPerRenderer() {
         String forced = System.getProperty("immptl.voxy.lodSystemPerDimension");
         boolean keep;
         String reason;
         if (!"true".equalsIgnoreCase(forced) && !"false".equalsIgnoreCase(forced)) {
            Capabilities caps = Capabilities.INSTANCE;
            if (caps.canQueryGpuMemory) {
               long free = caps.getFreeDedicatedGpuMemory();
               keep = free >= 3221225472L;
               reason = "free VRAM " + (free >> 20) + " MiB";
            } else {
               keep = false;
               reason = "the free VRAM cannot be read on this GPU";
            }
         } else {
            keep = Boolean.parseBoolean(forced);
            reason = "immptl.voxy.lodSystemPerDimension=" + forced;
         }

         int decision = keep ? 1 : 0;
         if (decision != this.lastKeepDecision) {
            this.lastKeepDecision = decision;
            VoxyInterface.LOGGER
               .info(
                  "[Voxy compat] LOD render systems: {} ({})", keep ? "one per dimension renderer, kept across crossings" : "one, following the player", reason
               );
         }

         return keep;
      }

      @Override
      public boolean isLowOnVideoMemory() {
         return !this.keepSystemsPerRenderer();
      }

      @Override
      public void onPlayerDimensionChangePromoted(LevelRenderer promotedRenderer, ClientLevel toWorld, LevelRenderer demotedRenderer) {
         if (promotedRenderer != demotedRenderer) {
            String dim = toWorld.dimension().identifier().toString();

            try {
               if (!this.keepSystemsPerRenderer() && systemOf(demotedRenderer) != null) {
                  this.shutdown(demotedRenderer, "the player left its dimension");
               }

               if (systemOf(promotedRenderer) != null) {
                  VoxyInterface.LOGGER.info("[Voxy compat] crossing into {}: its LOD render system is kept, nothing to rebuild", dim);
                  return;
               }

               ((IEVoxyLevelRenderer)promotedRenderer).ip_voxySetCreationPending(true);
               VoxyInterface.LOGGER.info("[Voxy compat] crossing into {}: no LOD render system yet, created at its next render", dim);
            } catch (Throwable t) {
               VoxyInterface.LOGGER.error("[Voxy compat] moving the LOD render system at the crossing into {} failed", dim, t);
            }
         }
      }

      @Override
      public void onRendererAboutToRender(LevelRenderer renderer) {
         Minecraft mc = Minecraft.getInstance();
         if (renderer == mc.levelRenderer) {
            try {
               IEVoxyLevelRenderer state = (IEVoxyLevelRenderer)renderer;
               boolean shaders = IrisInterface.invoker.isShaders();
               Object expected = shaders ? IrisInterface.invoker.getCurrentPipeline() : null;
               boolean nested = VoxyInterface.isSecondaryContext();
               if (systemOf(renderer) != null) {
                  if (state.ip_voxyBoundIrisPipeline() == expected) {
                     return;
                  }

                  if (nested) {
                     return;
                  }

                  this.shutdown(renderer, "its Iris pipeline changed");
                  state.ip_voxySetCreationPending(true);
               }

               if (!state.ip_voxyIsCreationPending()) {
                  return;
               }

               if (nested && !this.keepSystemsPerRenderer()) {
                  return;
               }

               if (expected != null) {
                  LevelRenderer other = holderOf(expected, renderer);
                  if (other != null) {
                     if (nested) {
                        return;
                     }

                     this.shutdown(other, "the " + dimensionOf(renderer) + " renderer takes over their shared Iris pipeline");
                     ((IEVoxyLevelRenderer)other).ip_voxySetCreationPending(true);
                  }

                  if (VoxyInterface.OnVoxyPresent.IrisBinding.isBound(expected)) {
                     if (!this.foreignBindingWarned) {
                        this.foreignBindingWarned = true;
                        VoxyInterface.LOGGER
                           .warn(
                              "[Voxy compat] the current Iris pipeline already carries a Voxy render pipeline of unknown origin; no LOD render system for the {} renderer",
                              dimensionOf(renderer)
                           );
                     }

                     return;
                  }
               }

               state.ip_voxySetCreationPending(false);
               this.createRendererNow(
                  renderer, expected, nested ? (VoxyInterface.prewarmingNow ? "behind the loading screen" : "in a portal view") : "in its main pass"
               );
            } catch (Throwable t) {
               VoxyInterface.LOGGER.error("[Voxy compat] preparing the LOD render system before a render failed", t);
            }
         }
      }

      private void createRendererNow(LevelRenderer renderer, @Nullable Object boundPipeline, String where) {
         IGetVoxyRenderSystem holder = (IGetVoxyRenderSystem)renderer;
         boolean savedCreating = VoxyInterface.creatingNow;
         boolean savedSkipGc = VoxyInterface.skipGcNow;
         VoxyInterface.creatingNow = true;
         VoxyInterface.skipGcNow = true;
         long start = System.nanoTime();

         try {
            if (systemOf(renderer) == null) {
               holder.voxy$createRenderer();
            }

            boolean present = systemOf(renderer) != null;
            ((IEVoxyLevelRenderer)renderer).ip_voxySetBoundIrisPipeline(present ? boundPipeline : null);
            VoxyInterface.LOGGER
               .info(
                  "[Voxy compat] LOD render system created for the {} renderer {} in {} ms (present={})",
                  dimensionOf(renderer),
                  where,
                  (System.nanoTime() - start) / 1000000L,
                  present
               );
         } catch (Throwable t) {
            VoxyInterface.LOGGER.error("[Voxy compat] LOD render system creation failed; no LODs for that renderer until the next renderer reload", t);
         } finally {
            VoxyInterface.creatingNow = savedCreating;
            VoxyInterface.skipGcNow = savedSkipGc;
         }
      }

      private void shutdown(LevelRenderer renderer, String why) {
         IGetVoxyRenderSystem holder = (IGetVoxyRenderSystem)renderer;
         if (systemOf(renderer) != null) {
            long start = System.nanoTime();

            try {
               holder.voxy$shutdownRenderer();
            } catch (Throwable t) {
               VoxyInterface.LOGGER.error("[Voxy compat] shutting down the {} renderer's LOD render system failed", dimensionOf(renderer), t);
            }

            ((IEVoxyLevelRenderer)renderer).ip_voxySetBoundIrisPipeline(null);
            VoxyInterface.LOGGER
               .info(
                  "[Voxy compat] LOD render system of the {} renderer shut down ({}) in {} ms",
                  dimensionOf(renderer),
                  why,
                  (System.nanoTime() - start) / 1000000L
               );
         }
      }

      @Nullable
      private static LevelRenderer holderOf(Object pipeline, LevelRenderer except) {
         for (LevelRenderer r : ClientWorldLoader.WORLD_RENDERER_MAP.values()) {
            if (r != except && systemOf(r) != null && ((IEVoxyLevelRenderer)r).ip_voxyBoundIrisPipeline() == pipeline) {
               return r;
            }
         }

         return null;
      }

      private static String dimensionOf(LevelRenderer renderer) {
         for (Entry<ResourceKey<Level>, LevelRenderer> e : ClientWorldLoader.WORLD_RENDERER_MAP.entrySet()) {
            if (e.getValue() == renderer) {
               return e.getKey().identifier().toString();
            }
         }

         Minecraft mc = Minecraft.getInstance();
         return mc.level == null ? "?" : mc.level.dimension().identifier().toString();
      }

      private static class IrisBinding {
         static boolean isBound(Object irisPipeline) {
            if (!(irisPipeline instanceof IGetIrisVoxyPipelineData get)) {
               return false;
            } else {
               IrisVoxyRenderPipelineData data = get.voxy$getPipelineData();
               return data != null && data.thePipeline != null;
            }
         }
      }
   }
}
