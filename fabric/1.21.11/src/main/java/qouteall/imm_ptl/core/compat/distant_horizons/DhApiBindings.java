package qouteall.imm_ptl.core.compat.distant_horizons;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeDeferredRenderEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiCancelableEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import qouteall.imm_ptl.core.render.GuiPortalRendering;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.q_misc_util.platform.Platform;

public final class DhApiBindings {
   private static final Logger LOGGER = LogManager.getLogger(DhApiBindings.class);
   private static boolean deferredSeenReported;
   private static boolean deferredReported;
   private static boolean guiReported;

   private DhApiBindings() {
   }

   public static void init() {
      if (Platform.get().isModLoaded("distanthorizons")) {
         try {
            DhApiBindings.Bindings.bind();
            LOGGER.info("Distant Horizons found: its deferred LOD pass is kept out of portal views, and every LOD pass out of GUI portal views");
         } catch (Throwable t) {
            LOGGER.warn("Distant Horizons' API differs from DH 3.3.3; its LOD passes are left to its own portal check", t);
         }
      }
   }

   private static final class Bindings {
      static void bind() {
         DhApi.events.bind(DhApiBeforeDeferredRenderEvent.class, new DhApiBeforeDeferredRenderEvent() {
            public void beforeRender(DhApiCancelableEventParam<DhApiRenderParam> input) {
               if (PortalRendering.isRendering() || GuiPortalRendering.isRendering()) {
                  input.cancelEvent();
                  if (!DhApiBindings.deferredReported) {
                     DhApiBindings.deferredReported = true;
                     DhApiBindings.LOGGER.info("Distant Horizons' deferred LOD pass skipped inside a portal view (reported once)");
                  }
               } else if (!DhApiBindings.deferredSeenReported) {
                  DhApiBindings.deferredSeenReported = true;
                  DhApiBindings.LOGGER.info("Distant Horizons' deferred LOD pass seen (reported once)");
               }
            }
         });
         DhApi.events.bind(DhApiBeforeRenderEvent.class, new DhApiBeforeRenderEvent() {
            public void beforeRender(DhApiCancelableEventParam<DhApiRenderParam> input) {
               if (GuiPortalRendering.isRendering()) {
                  input.cancelEvent();
                  if (!DhApiBindings.guiReported) {
                     DhApiBindings.guiReported = true;
                     DhApiBindings.LOGGER.info("Distant Horizons' LOD pass skipped inside a GUI portal view (reported once)");
                  }
               }
            }
         });
      }
   }
}
