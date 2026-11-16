package qouteall.imm_ptl.core.mixin.client.render.optimization;

import java.lang.reflect.Field;
import java.util.Arrays;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.CloudRenderer.TextureData;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;
import qouteall.imm_ptl.core.render.context_management.CloudContext;
import qouteall.imm_ptl.core.render.context_management.RenderStates;

@Mixin(CloudRenderer.class)
public abstract class MixinCloudRenderer {
   @Shadow
   private boolean needsRebuild;
   @Shadow
   private int prevCellX;
   @Shadow
   private int prevCellZ;
   @Shadow
   @Nullable
   private CloudStatus prevType;
   @Shadow
   private @Nullable TextureData texture;
   @Shadow
   private int quadCount;
   @Shadow
   @Nullable
   private MappableRingBuffer utb;
   @Unique
   private static final Field ip_prevRelativeCameraPosField = Arrays.stream(CloudRenderer.class.getDeclaredFields())
      .filter(f -> f.getType().isEnum() && f.getType().getEnclosingClass() == CloudRenderer.class)
      .findFirst()
      .map(f -> {
         f.setAccessible(true);
         return (Field)f;
      })
      .orElseThrow(() -> new IllegalStateException("Cannot find CloudRenderer.prevRelativeCameraPos"));

   @Unique
   private int ip_getPrevRelativeCameraPos() {
      try {
         return ((Enum)ip_prevRelativeCameraPosField.get(this)).ordinal();
      } catch (IllegalAccessException e) {
         throw new RuntimeException(e);
      }
   }

   @Unique
   private void ip_setPrevRelativeCameraPos(int ordinal) {
      try {
         Object[] constants = ip_prevRelativeCameraPosField.getType().getEnumConstants();
         ip_prevRelativeCameraPosField.set(this, constants[ordinal]);
      } catch (IllegalAccessException e) {
         throw new RuntimeException(e);
      }
   }

   @Inject(method = "render", at = @At("HEAD"))
   private void onBeginRenderClouds(int color, CloudStatus cloudStatus, float cloudHeight, Vec3 cameraPos, long gameTime, float partialTick, CallbackInfo ci) {
      if (RenderStates.getRenderedPortalNum() != 0) {
         if (IPGlobal.cloudOptimization) {
            this.portal_onBeginCloudRendering(color, cloudStatus, cloudHeight, cameraPos, gameTime, partialTick);
         }
      }
   }

   @Inject(method = "render", at = @At("RETURN"))
   private void onEndRenderClouds(int color, CloudStatus cloudStatus, float cloudHeight, Vec3 cameraPos, long gameTime, float partialTick, CallbackInfo ci) {
      if (RenderStates.getRenderedPortalNum() != 0) {
         if (IPGlobal.cloudOptimization) {
            this.portal_onEndCloudRendering(color);
         }
      }
   }

   @Unique
   private void portal_yieldCloudContext(CloudContext context, int cloudColor) {
      context.prevCellX = this.prevCellX;
      context.prevCellZ = this.prevCellZ;
      context.prevRelativeCameraPos = this.ip_getPrevRelativeCameraPos();
      context.prevType = this.prevType;
      context.quadCount = this.quadCount;
      context.cloudsBuffer = this.utb;
      context.dimension = Minecraft.getInstance().level.dimension();
      context.cloudColor = cloudColor;
      this.utb = null;
      this.needsRebuild = true;
   }

   @Unique
   private void portal_loadCloudContext(CloudContext context) {
      Validate.isTrue(context.dimension == Minecraft.getInstance().level.dimension());
      if (this.utb != null && this.utb != context.cloudsBuffer) {
         this.utb.close();
      }

      this.prevCellX = context.prevCellX;
      this.prevCellZ = context.prevCellZ;
      this.ip_setPrevRelativeCameraPos(context.prevRelativeCameraPos);
      this.prevType = context.prevType;
      this.quadCount = context.quadCount;
      this.utb = context.cloudsBuffer;
      this.needsRebuild = false;
   }

   @Unique
   @IPVanillaCopy
   private void portal_onBeginCloudRendering(int color, CloudStatus cloudStatus, float cloudHeight, Vec3 cameraPos, long gameTime, float partialTick) {
      if (this.texture != null) {
         float f = (float)(cloudHeight - cameraPos.y);
         float f1 = f + 4.0F;
         int relativeCameraPos;
         if (f1 < 0.0F) {
            relativeCameraPos = 0;
         } else if (f > 0.0F) {
            relativeCameraPos = 2;
         } else {
            relativeCameraPos = 1;
         }

         float f2 = (float)(gameTime % (this.texture.width() * 400L)) + partialTick;
         double d0 = cameraPos.x + f2 * 0.030000001F;
         double d1 = cameraPos.z + 3.96F;
         double d2 = this.texture.width() * 12.0;
         double d3 = this.texture.height() * 12.0;
         d0 -= Mth.floor(d0 / d2) * d2;
         d1 -= Mth.floor(d1 / d3) * d3;
         int cellX = Mth.floor(d0 / 12.0);
         int cellZ = Mth.floor(d1 / 12.0);
         CloudContext context = CloudContext.findAndTakeContext(cellX, cellZ, relativeCameraPos, cloudStatus, Minecraft.getInstance().level.dimension(), color);
         if (context != null) {
            this.portal_loadCloudContext(context);
         }
      }
   }

   @Unique
   private void portal_onEndCloudRendering(int color) {
      if (!this.needsRebuild && this.utb != null) {
         CloudContext newContext = new CloudContext();
         this.portal_yieldCloudContext(newContext, color);
         CloudContext.appendContext(newContext);
      }
   }
}
