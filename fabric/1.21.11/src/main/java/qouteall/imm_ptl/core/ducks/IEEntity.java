package qouteall.imm_ptl.core.ducks;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.collision.PortalCollisionHandler;
import qouteall.imm_ptl.core.portal.Portal;

public interface IEEntity {
   void ip_notifyCollidingWithPortal(Entity var1);

   @Nullable
   PortalCollisionHandler ip_getPortalCollisionHandler();

   PortalCollisionHandler ip_getOrCreatePortalCollisionHandler();

   void ip_setPortalCollisionHandler(@Nullable PortalCollisionHandler var1);

   @Deprecated
   @Nullable
   Portal ip_getCollidingPortal();

   void ip_tickCollidingPortal();

   boolean ip_isCollidingWithPortal();

   boolean ip_isRecentlyCollidingWithPortal();

   void ip_clearCollidingPortal();

   void ip_unsetRemoved();

   @Nullable
   AABB ip_getActiveCollisionBox(AABB var1);

   void ip_setPositionWithoutTriggeringCallback(Vec3 var1);

   void ip_setWorld(Level var1);
}
