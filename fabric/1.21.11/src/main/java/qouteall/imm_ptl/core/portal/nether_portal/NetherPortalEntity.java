package qouteall.imm_ptl.core.portal.nether_portal;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.platform_specific.O_O;
import qouteall.imm_ptl.core.portal.PortalPlaceholderBlock;
import qouteall.q_misc_util.my_util.DQuaternion;

public class NetherPortalEntity extends BreakablePortalEntity {
   private static final BreakablePortalEntity.OverlayInfo overlay_x = new BreakablePortalEntity.OverlayInfo(
      Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, Axis.Z), 0.5, 0.0, null
   );
   private static final BreakablePortalEntity.OverlayInfo overlay_y_up = new BreakablePortalEntity.OverlayInfo(
      Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, Axis.X), 0.5, 1.0, DQuaternion.rotationByDegrees(new Vec3(1.0, 0.0, 0.0), 90.0)
   );
   private static final BreakablePortalEntity.OverlayInfo overlay_y_down = new BreakablePortalEntity.OverlayInfo(
      Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, Axis.X),
      0.5,
      -1.0,
      DQuaternion.rotationByDegrees(new Vec3(1.0, 0.0, 0.0), 90.0)
   );
   private static final BreakablePortalEntity.OverlayInfo overlay_z = new BreakablePortalEntity.OverlayInfo(
      Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, Axis.X), 0.5, 0.0, null
   );
   public static final EntityType<NetherPortalEntity> ENTITY_TYPE = createPortalEntityType(NetherPortalEntity::new, portalEntityTypeKey("nether_portal_new"));

   public NetherPortalEntity(EntityType<?> entityType, Level world) {
      super(entityType, world);
   }

   @Override
   public void tick() {
      super.tick();
   }

   @Override
   protected boolean isPortalIntactOnThisSide() {
      return this.blockPortalShape.area.stream().allMatch(blockPos -> this.level().getBlockState(blockPos).getBlock() == PortalPlaceholderBlock.instance)
         && this.blockPortalShape.frameAreaWithoutCorner.stream().allMatch(blockPos -> O_O.isObsidian(this.level().getBlockState(blockPos)));
   }

   @Environment(EnvType.CLIENT)
   @Override
   protected void addSoundAndParticle() {
      if (IPGlobal.enableNetherPortalEffect) {
         RandomSource random = this.level().getRandom();

         for (int i = 0; i < (int)Math.ceil(this.getWidth() * this.getHeight() / 20.0); i++) {
            if (random.nextInt(10) == 0) {
               double px = (random.nextDouble() * 2.0 - 1.0) * (this.getWidth() / 2.0);
               double py = (random.nextDouble() * 2.0 - 1.0) * (this.getHeight() / 2.0);
               Vec3 pos = this.getPointInPlane(px, py);
               double speedMultiplier = 20.0;
               double vx = speedMultiplier * (random.nextFloat() - 0.5) * 0.5;
               double vy = speedMultiplier * (random.nextFloat() - 0.5) * 0.5;
               double vz = speedMultiplier * (random.nextFloat() - 0.5) * 0.5;
               this.level().addParticle(ParticleTypes.PORTAL, pos.x, pos.y, pos.z, vx, vy, vz);
            }
         }

         if (random.nextInt(800) == 0) {
            this.level()
               .playLocalSound(
                  this.getX(), this.getY(), this.getZ(), SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS, 0.5F, random.nextFloat() * 0.4F + 0.8F, false
               );
         }
      }
   }

   @Override
   public BreakablePortalEntity.OverlayInfo getActualOverlay() {
      if (IPGlobal.netherPortalOverlay) {
         switch (this.blockPortalShape.axis) {
            case X:
               return overlay_x;
            case Y:
               return this.getNormal().y > 0.0 ? overlay_y_up : overlay_y_down;
            case Z:
               return overlay_z;
         }
      }

      return super.getActualOverlay();
   }
}
