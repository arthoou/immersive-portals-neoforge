package qouteall.imm_ptl.core.portal.util;

import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.animation.UnilateralPortalState;

public record PortalLocalXYNormalized(double nx, double ny) {
   public Vec3 getOffset(UnilateralPortalState state) {
      return state.orientation().rotate(new Vec3((this.nx - 0.5) * state.width(), (this.ny - 0.5) * state.height(), 0.0));
   }

   public Vec3 getPos(UnilateralPortalState state) {
      return state.position().add(this.getOffset(state));
   }

   public Vec3 getOffset(Portal portal) {
      return portal.getAxisW().scale((this.nx - 0.5) * portal.getWidth()).add(portal.getAxisH().scale((this.ny - 0.5) * portal.getHeight()));
   }

   public Vec3 getPos(Portal portal) {
      return portal.getOriginPos().add(this.getOffset(portal));
   }

   public static PortalLocalXYNormalized fromOffset(UnilateralPortalState state, Vec3 offset) {
      Vec3 localPos = state.orientation().getConjugated().rotate(offset);
      return new PortalLocalXYNormalized(localPos.x / state.width() + 0.5, localPos.y / state.height() + 0.5);
   }

   public static PortalLocalXYNormalized fromPos(UnilateralPortalState state, Vec3 pos) {
      return fromOffset(state, pos.subtract(state.position()));
   }

   public static PortalLocalXYNormalized fromOffset(Portal portal, Vec3 offset) {
      return new PortalLocalXYNormalized(portal.getAxisW().dot(offset) / portal.getWidth() + 0.5, portal.getAxisH().dot(offset) / portal.getHeight() + 0.5);
   }

   public static PortalLocalXYNormalized fromPos(Portal portal, Vec3 pos) {
      return fromOffset(portal, pos.subtract(portal.getOriginPos()));
   }

   public PortalLocalXYNormalized clamp() {
      return new PortalLocalXYNormalized(Math.min(Math.max(this.nx, 0.0), 1.0), Math.min(Math.max(this.ny, 0.0), 1.0));
   }

   public PortalLocalXYNormalized snapToGrid(int gridCount) {
      return new PortalLocalXYNormalized((double)Math.round(this.nx * gridCount) / gridCount, (double)Math.round(this.ny * gridCount) / gridCount);
   }

   public boolean isValid() {
      return this.nx >= 0.0 && this.nx <= 1.0 && this.ny >= 0.0 && this.ny <= 1.0;
   }

   public boolean isCloseTo(PortalLocalXYNormalized another, double maxDistance) {
      double distSq = (this.nx - another.nx) * (this.nx - another.nx) + (this.ny - another.ny) * (this.ny - another.ny);
      return distSq < maxDistance * maxDistance;
   }

   public PortalLocalXYNormalized add(PortalLocalXYNormalized another) {
      return new PortalLocalXYNormalized(this.nx + another.nx, this.ny + another.ny);
   }

   public PortalLocalXYNormalized subtract(PortalLocalXYNormalized another) {
      return new PortalLocalXYNormalized(this.nx - another.nx, this.ny - another.ny);
   }
}
