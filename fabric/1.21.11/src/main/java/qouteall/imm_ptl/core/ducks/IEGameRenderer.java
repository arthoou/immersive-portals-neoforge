package qouteall.imm_ptl.core.ducks;

import com.mojang.blaze3d.ProjectionType;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.state.LevelRenderState;
import org.joml.Matrix4f;

public interface IEGameRenderer {
   void ip_setLightmapTextureManager(LightTexture var1);

   boolean ip_getDoRenderHand();

   void ip_setDoRenderHand(boolean var1);

   void ip_setCamera(Camera var1);

   LevelRenderState ip_getLevelRenderState();

   void ip_setLevelRenderState(LevelRenderState var1);

   SubmitNodeStorage ip_getSubmitNodeStorage();

   void ip_setSubmitNodeStorage(SubmitNodeStorage var1);

   FeatureRenderDispatcher ip_getFeatureRenderDispatcher();

   void ip_setFeatureRenderDispatcher(FeatureRenderDispatcher var1);

   FogRenderer ip_getFogRenderer();

   void ip_setFogRenderer(FogRenderer var1);

   void ip_resetProjectionMatrix(Matrix4f var1, ProjectionType var2);
}
