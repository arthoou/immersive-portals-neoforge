// Reconstructed from the published Apache-2.0 MC 1.21.11 port; see SOURCE-PROVENANCE.md.
package qouteall.imm_ptl.peripheral.portal_generation;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.PortalShape;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.portal.custom_portal_gen.CustomPortalGeneration;

import qouteall.imm_ptl.core.platform_specific.O_O;

public class IntrinsicPortalGeneration {
   public static final IntrinsicNetherPortalForm intrinsicNetherPortalForm = new IntrinsicNetherPortalForm();
   public static final DiligentNetherPortalForm diligentNetherPortalForm = new DiligentNetherPortalForm();
   public static final PortalHelperForm portalHelperForm = new PortalHelperForm();
   public static final CustomPortalGeneration intrinsicToNether = new CustomPortalGeneration(
      Lists.newArrayList(Level.OVERWORLD), Level.NETHER, 8, 1, true, intrinsicNetherPortalForm, null, new ArrayList<>(), new ArrayList<>()
   );
   public static final CustomPortalGeneration intrinsicFromNether = intrinsicToNether.getReverse();
   public static final CustomPortalGeneration diligentToNether = new CustomPortalGeneration(
      Lists.newArrayList(Level.OVERWORLD), Level.NETHER, 8, 1, true, diligentNetherPortalForm, null, new ArrayList<>(), new ArrayList<>()
   );
   public static final CustomPortalGeneration diligentFromNether = diligentToNether.getReverse();
   public static final CustomPortalGeneration portalHelper = new CustomPortalGeneration(
      Lists.newArrayList(CustomPortalGeneration.ANY_DIMENSION),
      CustomPortalGeneration.THE_SAME_DIMENSION,
      1,
      1,
      false,
      portalHelperForm,
      null,
      new ArrayList<>(),
      new ArrayList<>()
   );

   public static void init() {
      intrinsicToNether.identifier = McHelper.newResourceLocation("imm_ptl:intrinsic_nether_portal");
      intrinsicFromNether.identifier = McHelper.newResourceLocation("imm_ptl:intrinsic_nether_portal");
      diligentFromNether.identifier = McHelper.newResourceLocation("imm_ptl:intrinsic_diligent_nether_portal");
      diligentToNether.identifier = McHelper.newResourceLocation("imm_ptl:intrinsic_diligent_nether_portal");
      portalHelper.identifier = McHelper.newResourceLocation("imm_ptl:intrinsic_portal_helper");
   }

   public static boolean onFireLitOnObsidian(ServerLevel fromWorld, BlockPos firePos, @Nullable Entity triggeringEntity) {
      IPGlobal.NetherPortalMode mode = IPGlobal.netherPortalMode;
      if (mode == IPGlobal.NetherPortalMode.normal || mode == IPGlobal.NetherPortalMode.adaptive) {
         if (O_O.shouldCancelPortalByDuPortalLimiter(fromWorld, firePos, triggeringEntity)) {
            return false;
         }
         ResourceKey<Level> fromDimension = fromWorld.dimension();
         if (fromDimension == Level.OVERWORLD) {
            CustomPortalGeneration gen = mode == IPGlobal.NetherPortalMode.normal ? intrinsicToNether : diligentToNether;
            return gen.perform(fromWorld, firePos, triggeringEntity);
         }

         if (fromDimension == Level.NETHER) {
            CustomPortalGeneration gen = mode == IPGlobal.NetherPortalMode.normal ? intrinsicFromNether : diligentFromNether;
            return gen.perform(fromWorld, firePos, triggeringEntity);
         }
      }

      return false;
   }

   public static boolean activatePortalHelper(ServerLevel fromWorld, BlockPos firePos) {
      return portalHelper.perform(fromWorld, firePos, null);
   }

   public static boolean onCrouchingPlayerIgnite(ServerLevel world, ServerPlayer player, BlockPos firePos) {
      if (IPGlobal.netherPortalMode == IPGlobal.NetherPortalMode.disabled) {
         return false;
      }

      if (!IPGlobal.lightVanillaNetherPortalWhenCrouching) {
         return false;
      }

      boolean dimensionCorrect = world.dimension() == Level.OVERWORLD || world.dimension() == Level.NETHER;
      if (!dimensionCorrect) {
         return false;
      }

      Optional<PortalShape> newPortal = PortalShape.findEmptyPortalShape(world, firePos, Axis.X);
      if (newPortal.isEmpty()) {
         return false;
      }

      PortalShape areaHelper = newPortal.get();
      areaHelper.createPortalBlocks(world);
      return true;
   }
}
