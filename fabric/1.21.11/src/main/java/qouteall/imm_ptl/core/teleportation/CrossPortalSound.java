package qouteall.imm_ptl.core.teleportation;

import java.util.Comparator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.IPMcHelper;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.context_management.RenderStates;

@Environment(EnvType.CLIENT)
public class CrossPortalSound {
   public static final float VOLUME_RADIUS_MULT = 16.0F;
   public static final float MIN_SOUND_RADIUS = 16.0F;

   public static boolean isPlayerWorld(ClientLevel world) {
      return world.dimension() == RenderStates.originalPlayerDimension;
   }

   @Nullable
   public static SimpleSoundInstance createCrossPortalSound(
      ClientLevel soundWorld, SoundEvent soundEvent, SoundSource soundSource, Vec3 soundPos, float soundVol, float soundPitch, long seed
   ) {
      Minecraft client = Minecraft.getInstance();
      if (client.player == null) {
         return null;
      }

      Profiler.get().push("cross_portal_sound");
      double soundRadius = Math.min(64.0F, Math.max(16.0F * soundVol, 16.0F));
      Vec3 playerCameraPos = RenderStates.originalPlayerPos.add(McHelper.getEyeOffset(client.player));
      SimpleSoundInstance result = IPMcHelper.getNearbyPortalList(
            soundWorld,
            soundPos,
            IPGlobal.maxNormalPortalRadius,
            portal -> portal.getDestDim() == RenderStates.originalPlayerDimension && isPlayerInRange(portal, soundPos, soundRadius, playerCameraPos)
         )
         .stream()
         .min(Comparator.comparingDouble(portal -> getPortalDistance(portal, soundPos)))
         .map(
            portal -> {
               Vec3 soundEnterPortalPoint = portal.getNearestPointInPortal(soundPos);
               Vec3 soundExitPortalPoint = portal.transformPoint(soundEnterPortalPoint);
               float volumeToEnterPortal = (float)soundEnterPortalPoint.distanceTo(soundPos) / 16.0F;
               float volumeMultiplier = Math.max(0.0F, 1.0F - (float)(playerCameraPos.distanceTo(soundExitPortalPoint) / 16.0));
               float volumeAtPortal = Math.max(0.0F, soundVol - volumeToEnterPortal) * volumeMultiplier;
               return new SimpleSoundInstance(
                  soundEvent,
                  soundSource,
                  volumeAtPortal,
                  soundPitch,
                  RandomSource.create(seed),
                  soundExitPortalPoint.x(),
                  soundExitPortalPoint.y(),
                  soundExitPortalPoint.z()
               );
            }
         )
         .orElse(null);
      Profiler.get().pop();
      return result;
   }

   private static boolean isPlayerInRange(Portal portal, Vec3 soundPos, double soundRadius, Vec3 playerCameraPos) {
      Vec3 soundExitPoint = portal.transformPoint(portal.getNearestPointInPortal(soundPos));
      return soundExitPoint.closerThan(playerCameraPos, soundRadius);
   }

   private static double getPortalDistance(Portal portal, Vec3 soundPos) {
      Vec3 soundEnterPortalPoint = portal.getNearestPointInPortal(soundPos);
      return soundEnterPortalPoint.distanceToSqr(soundPos);
   }
}
