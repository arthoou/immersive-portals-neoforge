package qouteall.imm_ptl.peripheral.mixin.client.dim_stack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldCallback;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.RegistryAccess.Frozen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.peripheral.dim_stack.DimStackGuiController;
import qouteall.imm_ptl.peripheral.dim_stack.DimStackManagement;
import qouteall.imm_ptl.peripheral.dim_stack.DimensionStackAPI;
import qouteall.imm_ptl.peripheral.ducks.IECreateWorldScreen;
import qouteall.q_misc_util.Helper;

@Mixin(CreateWorldScreen.class)
public abstract class MixinCreateWorldScreen_CVB extends Screen implements IECreateWorldScreen {
   @Shadow
   @Final
   private static Logger LOGGER;
   @Shadow
   @Final
   private WorldCreationUiState uiState;
   @Nullable
   private DimStackGuiController ip_dimStackController;

   protected MixinCreateWorldScreen_CVB(Component title) {
      super(title);
      throw new RuntimeException();
   }

   @Inject(method = "<init>", at = @At("RETURN"))
   private void onInitEnd(
      Minecraft minecraft,
      Runnable onClose,
      WorldCreationContext worldCreationContext,
      Optional<ResourceKey<WorldPreset>> optional,
      OptionalLong optionalLong,
      CreateWorldCallback callback,
      CallbackInfo ci
   ) {
      DimStackManagement.dimStackToApply = DimStackManagement.getDimStackPreset();
      if (DimStackManagement.dimStackToApply != null) {
         LOGGER.info("[ImmPtl] Applying dimension stack preset");
      }
   }

   @Override
   public void ip_openDimStackScreen() {
      if (this.ip_dimStackController == null) {
         CreateWorldScreen this_ = (CreateWorldScreen)(Object)this;
         this.ip_dimStackController = new DimStackGuiController(this_, () -> this.portal_getDimensionList(), info -> {
            DimStackManagement.dimStackToApply = info;
            Minecraft.getInstance().setScreen(this_);
         });
         this.ip_dimStackController.initializeAsDefault();
      }

      Minecraft.getInstance().setScreen(this.ip_dimStackController.view);
   }

   private List<ResourceKey<Level>> portal_getDimensionList() {
      Helper.log("Getting the dimension list");
      Set<ResourceKey<Level>> result = new LinkedHashSet<>();

      try {
         WorldCreationContext settings = this.uiState.getSettings();
         Frozen registryAccess = settings.worldgenLoadContext();
         WorldDimensions selectedDimensions = settings.selectedDimensions();

         for (Entry<ResourceKey<LevelStem>, LevelStem> entry : selectedDimensions.dimensions().entrySet()) {
            result.add(Helper.dimIdToKey(entry.getKey().identifier()));
         }

         for (Entry<ResourceKey<LevelStem>, LevelStem> entry : settings.datapackDimensions().entrySet()) {
            result.add(Helper.dimIdToKey(entry.getKey().identifier()));
         }

         Collection<ResourceKey<Level>> other = DimensionStackAPI.DIMENSION_STACK_CANDIDATE_COLLECTION_EVENT
            .invoker()
            .getExtraDimensionKeys(registryAccess, settings.options());
         result.addAll(other);
      } catch (Exception e) {
         LOGGER.error("ImmPtl getting dimension list", e);
         if (result.isEmpty()) {
            result.add(Helper.dimIdToKey("error:error"));
         }
      }

      return new ArrayList<>(result);
   }
}
