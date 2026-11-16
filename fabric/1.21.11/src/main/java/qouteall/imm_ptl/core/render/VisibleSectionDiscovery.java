package qouteall.imm_ptl.core.render;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayDeque;
import java.util.Stack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.chunk_loading.PerformanceLevel;
import qouteall.imm_ptl.core.ducks.IERenderSection;
import qouteall.imm_ptl.core.miscellaneous.ClientPerformanceMonitor;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.nether_portal.BlockTraverse;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;

@Environment(EnvType.CLIENT)
public class VisibleSectionDiscovery {
   private static ImmPtlViewArea builtChunks;
   private static Frustum vanillaFrustum;
   private static ObjectArrayList<RenderSection> resultHolder;
   private static final ArrayDeque<RenderSection> tempQueue = new ArrayDeque<>();
   private static SectionPos cameraSectionPos;
   private static long timeMark;
   private static int viewDistance;
   private static final Stack<ObjectArrayList<RenderSection>> listCaches = new Stack<>();

   public static void discoverVisibleSections(
      ClientLevel world, ImmPtlViewArea builtChunks_, Camera camera, Frustum vanillaFrustum_, ObjectArrayList<RenderSection> resultHolder_
   ) {
      builtChunks = builtChunks_;
      vanillaFrustum = vanillaFrustum_;
      resultHolder = resultHolder_;
      resultHolder.clear();
      tempQueue.clear();
      updateViewDistance();
      timeMark = System.nanoTime();
      Vec3 cameraPos = camera.position();
      vanillaFrustum.prepare(cameraPos.x, cameraPos.y, cameraPos.z);
      cameraSectionPos = SectionPos.of(BlockPos.containing(cameraPos));
      SectionPos modifiedVisibleSectionIterationOrigin = null;
      if (PortalRendering.isRendering()) {
         Portal renderingPortal = PortalRendering.getRenderingPortal();
         modifiedVisibleSectionIterationOrigin = renderingPortal.getPortalShape().getModifiedVisibleSectionIterationOrigin(renderingPortal, cameraPos);
      }

      if (modifiedVisibleSectionIterationOrigin != null) {
         checkSection(
            modifiedVisibleSectionIterationOrigin.getX(), modifiedVisibleSectionIterationOrigin.getY(), modifiedVisibleSectionIterationOrigin.getZ(), true
         );
      } else if (cameraPos.y < world.getMinY()) {
         discoverBottomOrTopLayerVisibleChunks(builtChunks.minSectionY);
      } else if (cameraPos.y > world.getMaxY() + 1) {
         discoverBottomOrTopLayerVisibleChunks(builtChunks.endSectionY - 1);
      } else {
         checkSection(cameraSectionPos.x(), cameraSectionPos.y(), cameraSectionPos.z(), true);
      }

      while (!tempQueue.isEmpty()) {
         RenderSection curr = tempQueue.poll();
         int cx = SectionPos.blockToSectionCoord(curr.getRenderOrigin().getX());
         int cy = SectionPos.blockToSectionCoord(curr.getRenderOrigin().getY());
         int cz = SectionPos.blockToSectionCoord(curr.getRenderOrigin().getZ());
         checkSection(cx + 1, cy, cz, false);
         checkSection(cx - 1, cy, cz, false);
         checkSection(cx, cy + 1, cz, false);
         checkSection(cx, cy - 1, cz, false);
         checkSection(cx, cy, cz + 1, false);
         checkSection(cx, cy, cz - 1, false);
      }

      resultHolder = null;
      builtChunks = null;
      vanillaFrustum = null;
   }

   private static void updateViewDistance() {
      int distance = WorldRenderInfo.getRenderDistance();
      viewDistance = PerformanceLevel.getPortalRenderingDistance(ClientPerformanceMonitor.level, distance);
   }

   private static boolean isVisible(RenderSection builtChunk) {
      AABB box = builtChunk.getBoundingBox();
      return vanillaFrustum.isVisible(box);
   }

   private static void discoverBottomOrTopLayerVisibleChunks(int cy) {
      BlockTraverse.searchOnPlane(cameraSectionPos.x(), cameraSectionPos.z(), viewDistance - 1, (cx, cz) -> {
         checkSection(cx, cy, cz, false);
         return null;
      });
   }

   private static void checkSection(int cx, int cy, int cz, boolean skipFrustumTest) {
      if (Math.abs(cx - cameraSectionPos.x()) <= viewDistance) {
         if (Math.abs(cy - cameraSectionPos.y()) <= viewDistance) {
            if (Math.abs(cz - cameraSectionPos.z()) <= viewDistance) {
               RenderSection builtChunk = builtChunks.rawFetch(cx, cy, cz, timeMark);
               if (builtChunk != null) {
                  IERenderSection ieRenderSection = (IERenderSection)builtChunk;
                  if (ieRenderSection.portal_getMark() != timeMark) {
                     ieRenderSection.portal_setMark(timeMark);
                     if (skipFrustumTest || isVisible(builtChunk)) {
                        tempQueue.add(builtChunk);
                        resultHolder.add(builtChunk);
                     }
                  }
               }
            }
         }
      }
   }

   public static ObjectArrayList<RenderSection> takeList() {
      return listCaches.isEmpty() ? new ObjectArrayList<>() : listCaches.pop();
   }

   public static void returnList(ObjectArrayList<RenderSection> list) {
      list.clear();
      listCaches.push(list);
   }

   public static void init() {
      IPCGlobal.CLIENT_CLEANUP_EVENT.register(VisibleSectionDiscovery::cleanUp);
      ClientWorldLoader.CLIENT_DIMENSION_DYNAMIC_REMOVE_EVENT.register(dim -> cleanUp());
   }

   private static void cleanUp() {
      listCaches.clear();
      resultHolder = null;
      builtChunks = null;
      vanillaFrustum = null;
   }
}
