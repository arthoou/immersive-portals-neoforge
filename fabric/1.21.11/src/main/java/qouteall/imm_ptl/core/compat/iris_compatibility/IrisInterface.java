package qouteall.imm_ptl.core.compat.iris_compatibility;

import java.lang.reflect.Field;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.materialmap.BlockMaterialMapping;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.jetbrains.annotations.Nullable;
import qouteall.q_misc_util.Helper;

public class IrisInterface {
   public static IrisInterface.Invoker invoker = new IrisInterface.Invoker();

   public static class Invoker {
      public boolean isIrisPresent() {
         return false;
      }

      public boolean isShaders() {
         return false;
      }

      public boolean isRenderingShadowMap() {
         return false;
      }

      public Object getPipeline(LevelRenderer worldRenderer) {
         return null;
      }

      public void setPipeline(LevelRenderer worldRenderer, Object pipeline) {
      }

      public void reloadPipelines() {
      }

      public Object getCurrentPipeline() {
         return null;
      }

      @Nullable
      public String getShaderpackName() {
         return null;
      }

      public void preparePipelineForCurrentLevel() {
      }

      public boolean initializeCurrentPipelineForRendering() {
         return false;
      }
   }

   public static class OnIrisPresent extends IrisInterface.Invoker {
      private Field worldRendererPipelineField = Helper.noError(() -> {
         Field field = LevelRenderer.class.getDeclaredField("pipeline");
         field.setAccessible(true);
         return field;
      });
      @Nullable
      private Field[] pipelineFirstRenderFields = null;
      private boolean pipelineFirstRenderFieldsWarned = false;

      @Override
      public boolean isIrisPresent() {
         return true;
      }

      @Override
      public boolean isShaders() {
         return Iris.getCurrentPack().isPresent();
      }

      @Override
      public boolean isRenderingShadowMap() {
         return ShadowRenderer.ACTIVE;
      }

      @Override
      public Object getPipeline(LevelRenderer worldRenderer) {
         return Helper.noError(() -> (WorldRenderingPipeline)this.worldRendererPipelineField.get(worldRenderer));
      }

      @Override
      public void setPipeline(LevelRenderer worldRenderer, Object pipeline) {
         Helper.noError(() -> {
            this.worldRendererPipelineField.set(worldRenderer, pipeline);
            return null;
         });
      }

      @Override
      public void reloadPipelines() {
         Iris.getPipelineManager().destroyPipeline();
      }

      @Override
      public Object getCurrentPipeline() {
         return Iris.getPipelineManager().getPipelineNullable();
      }

      @Nullable
      @Override
      public String getShaderpackName() {
         return Iris.getCurrentPackName();
      }

      @Override
      public void preparePipelineForCurrentLevel() {
         Iris.getPipelineManager().preparePipeline(Iris.getCurrentDimension());
      }

      @Override
      public boolean initializeCurrentPipelineForRendering() {
         if (!(Iris.getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline pipeline)) {
            return false;
         } else {
            if (this.pipelineFirstRenderFields == null) {
               try {
                  Field initialized = IrisRenderingPipeline.class.getDeclaredField("initializedBlockIds");
                  Field pack = IrisRenderingPipeline.class.getDeclaredField("pack");
                  initialized.setAccessible(true);
                  pack.setAccessible(true);
                  this.pipelineFirstRenderFields = new Field[]{initialized, pack};
               } catch (ReflectiveOperationException | RuntimeException e) {
                  if (!this.pipelineFirstRenderFieldsWarned) {
                     this.pipelineFirstRenderFieldsWarned = true;
                     Helper.LOGGER.warn("Iris' pipeline internals changed (expected 1.10.8); its first-render work stays in the first portal view", e);
                  }

                  return false;
               }
            }

            try {
               if (this.pipelineFirstRenderFields[0].getBoolean(pipeline)) {
                  return false;
               }

               ShaderPack pack = (ShaderPack)this.pipelineFirstRenderFields[1].get(pipeline);
               WorldRenderingSettings.INSTANCE
                  .setBlockStateIds(BlockMaterialMapping.createBlockStateIdMap(pack.getIdMap().getBlockProperties(), pack.getIdMap().getTagEntries()));
               WorldRenderingSettings.INSTANCE.setBlockTypeIds(BlockMaterialMapping.createBlockTypeMap(pack.getIdMap().getBlockRenderTypeMap()));
               if (WorldRenderingSettings.INSTANCE.isReloadRequired()) {
                  Minecraft.getInstance().levelRenderer.allChanged();
                  WorldRenderingSettings.INSTANCE.clearReloadRequired();
               }

               this.pipelineFirstRenderFields[0].setBoolean(pipeline, true);
               return true;
            } catch (ReflectiveOperationException | RuntimeException e) {
               Helper.LOGGER.warn("Initializing an Iris pipeline ahead of its first render failed; it does that itself at its first render", e);
               return false;
            }
         }
      }
   }
}
