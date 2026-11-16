package qouteall.imm_ptl.core.ducks;

import net.minecraft.world.phys.Vec3;

public interface IEFrustum {
   boolean ip_canDetermineInvisibleWithCamCoord(float var1, float var2, float var3, float var4, float var5, float var6);

   Vec3 ip_getViewVec3();
}
