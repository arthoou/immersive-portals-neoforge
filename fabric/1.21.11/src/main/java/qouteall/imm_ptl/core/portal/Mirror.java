package qouteall.imm_ptl.core.portal;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3d;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import qouteall.q_misc_util.my_util.DQuaternion;

public class Mirror extends Portal {
   public static final EntityType<Mirror> ENTITY_TYPE = Portal.createPortalEntityType(Mirror::new, Portal.portalEntityTypeKey("mirror"));

   public Mirror(EntityType<?> entityType, Level world) {
      super(entityType, world);
   }

   @Override
   public void tick() {
      super.tick();
      this.setTeleportable(false);
      this.setInteractable(false);
   }

   @Override
   public Vec3 transformLocalVecNonScale(Vec3 localVec) {
      return this.getMirrored(super.transformLocalVecNonScale(localVec));
   }

   @Override
   public boolean canTeleportEntity(Entity entity) {
      return false;
   }

   public Vec3 getMirrored(Vec3 vec) {
      Vec3 normal = this.getNormal();
      return mirroredVec(vec, normal);
   }

   public static Vec3 mirroredVec(Vec3 vec, Vec3 normal) {
      double len = vec.dot(normal);
      return vec.add(normal.scale(len * -2.0));
   }

   @Override
   public Vec3 inverseTransformLocalVecNonScale(Vec3 localVec) {
      return super.inverseTransformLocalVecNonScale(this.getMirrored(localVec));
   }

   @Override
   public Matrix4d getFullSpaceTransformation() {
      Vec3 originPos = this.getOriginPos();
      Vec3 destPos = this.getDestPos();
      DQuaternion rot = this.getRotationD();
      return new Matrix4d()
         .translation(destPos.x, destPos.y, destPos.z)
         .reflect(this.getNormal().x, this.getNormal().y, this.getNormal().z, 0.0)
         .scale(this.getScale())
         .rotate(rot.toMcQuaternion())
         .translate(-originPos.x, -originPos.y, -originPos.z);
   }

   public void setRotationTransformationForMirror(DQuaternion visualRotation) {
      Matrix3d mirrorTrans = new Matrix3d().reflect(this.getNormal().x, this.getNormal().y, this.getNormal().z);
      Matrix3d visualRotationTrans = new Matrix3d().rotate(visualRotation.toMcQuaternion());
      Matrix3d newRotation = new Matrix3d().mul(visualRotationTrans).mul(mirrorTrans);
      Quaterniond mirrorRotation = new Quaterniond().setFromNormalized(newRotation);
      this.setRotation(DQuaternion.fromMcQuaternion(mirrorRotation));
   }
}
