package qouteall.imm_ptl.core.compat.mixin.sodium;

import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.render.viewport.frustum.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumInterface;

import java.lang.reflect.Method;

@Mixin(value = Viewport.class, remap = false)
public class MixinSodiumViewport {
    @Unique
    private static final float ip_SECTION_VISIBILITY_MARGIN = 9.125F;
    
    @Redirect(
        method = "isBoxVisible",
        at = @At(
            value = "INVOKE",
            target = "Lnet/caffeinemc/mods/sodium/client/render/viewport/frustum/Frustum;testAab(FFFFFF)Z"
        ),
        require = 0
    )
    private boolean redirectTestAab(
        Frustum instance,
        float minX, float minY, float minZ, float maxX, float maxY, float maxZ
    ) {
        boolean inFrustum = instance.testAab(
            minX, minY, minZ, maxX, maxY, maxZ
        );
        
        if (inFrustum) {
            if (SodiumInterface.frustumCuller != null) {
                boolean canDetermineInvisible =
                    SodiumInterface.frustumCuller.canDetermineInvisibleWithCameraCoord(
                        minX, minY, minZ, maxX, maxY, maxZ
                    );
                return !canDetermineInvisible;
            }
        }
        
        return inFrustum;
    }
    
    @Redirect(
        method = "isBoxVisible",
        at = @At(
            value = "INVOKE",
            target = "Lnet/caffeinemc/mods/sodium/client/render/viewport/frustum/Frustum;testSection(FFF)Z"
        ),
        require = 0
    )
    private boolean redirectTestSection(
        Frustum instance,
        float x, float y, float z
    ) {
        boolean inFrustum = ip_testSection(instance, x, y, z);
        
        if (inFrustum) {
            if (SodiumInterface.frustumCuller != null) {
                boolean canDetermineInvisible =
                    SodiumInterface.frustumCuller.canDetermineInvisibleWithCameraCoord(
                        x - ip_SECTION_VISIBILITY_MARGIN,
                        y - ip_SECTION_VISIBILITY_MARGIN,
                        z - ip_SECTION_VISIBILITY_MARGIN,
                        x + ip_SECTION_VISIBILITY_MARGIN,
                        y + ip_SECTION_VISIBILITY_MARGIN,
                        z + ip_SECTION_VISIBILITY_MARGIN
                    );
                return !canDetermineInvisible;
            }
        }
        
        return inFrustum;
    }
    
    @Unique
    private static boolean ip_testSection(Frustum instance, float x, float y, float z) {
        try {
            Method method = instance.getClass().getMethod(
                "testSection", float.class, float.class, float.class
            );
            return (Boolean) method.invoke(instance, x, y, z);
        }
        catch (ReflectiveOperationException e) {
            return instance.testAab(
                x - ip_SECTION_VISIBILITY_MARGIN,
                y - ip_SECTION_VISIBILITY_MARGIN,
                z - ip_SECTION_VISIBILITY_MARGIN,
                x + ip_SECTION_VISIBILITY_MARGIN,
                y + ip_SECTION_VISIBILITY_MARGIN,
                z + ip_SECTION_VISIBILITY_MARGIN
            );
        }
    }
}
