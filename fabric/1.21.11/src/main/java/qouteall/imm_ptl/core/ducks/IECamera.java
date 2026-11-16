package qouteall.imm_ptl.core.ducks;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public interface IECamera {
   void ip_resetState(Vec3 var1, ClientLevel var2);

   void portal_setPos(Vec3 var1);

   float ip_getCameraY();

   float ip_getLastCameraY();

   void ip_setCameraY(float var1, float var2);

   void portal_setFocusedEntity(Entity var1);

   void portal_tickAttributeProbe();
}
