package qouteall.imm_ptl.core.ducks;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public interface IEServerPlayerEntity {
   void ip_stopRidingWithoutTeleportRequest();

   void ip_startRidingWithoutTeleportRequest(Entity var1);

   void portal_worldChanged(ServerLevel var1, Vec3 var2);
}
