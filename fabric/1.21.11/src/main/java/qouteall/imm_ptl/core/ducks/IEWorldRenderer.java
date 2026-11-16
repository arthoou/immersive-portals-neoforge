package qouteall.imm_ptl.core.ducks;

import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.client.renderer.state.ParticlesRenderState;
import net.minecraft.world.entity.Entity;

public interface IEWorldRenderer {
   EntityRenderDispatcher ip_getEntityRenderDispatcher();

   ViewArea ip_getBuiltChunkStorage();

   void ip_renderSingleEntity(Entity var1, double var2, double var4, double var6, float var8, PoseStack var9, BufferSource var10);

   RenderBuffers ip_getRenderBuffers();

   void ip_setRenderBuffers(RenderBuffers var1);

   void portal_fullyDispose();

   void portal_setChunkInfoList(ObjectArrayList<RenderSection> var1);

   ObjectArrayList<RenderSection> portal_getChunkInfoList();

   LevelRenderState ip_getLevelRenderState();

   void ip_setLevelRenderState(LevelRenderState var1);

   SubmitNodeStorage ip_getSubmitNodeStorage();

   void ip_setSubmitNodeStorage(SubmitNodeStorage var1);

   FeatureRenderDispatcher ip_getFeatureRenderDispatcher();

   void ip_setFeatureRenderDispatcher(FeatureRenderDispatcher var1);

   ParticlesRenderState ip_getParticlesRenderState();

   void ip_setParticlesRenderState(ParticlesRenderState var1);

   LevelTargetBundle ip_getTargets();
}
