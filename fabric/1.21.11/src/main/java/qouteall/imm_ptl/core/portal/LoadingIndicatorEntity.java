package qouteall.imm_ptl.core.portal;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.EntityType.Builder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.McHelper;
import qouteall.q_misc_util.my_util.IntBox;
import qouteall.q_misc_util.platform.Platform;

public class LoadingIndicatorEntity extends Entity {
   public static final EntityType<LoadingIndicatorEntity> entityType = Platform.get()
      .forceTrackedVelocityUpdates(
         Builder.of(LoadingIndicatorEntity::new, MobCategory.MISC).sized(1.0F, 1.0F).fireImmune().clientTrackingRange(6).updateInterval(20), true
      )
      .build(ResourceKey.create(Registries.ENTITY_TYPE, McHelper.newResourceLocation("immersive_portals", "loading_indicator")));
   private static final EntityDataAccessor<Component> TEXT = SynchedEntityData.defineId(LoadingIndicatorEntity.class, EntityDataSerializers.COMPONENT);
   private static final EntityDataAccessor<BlockPos> BOX_LOW_POS = SynchedEntityData.defineId(LoadingIndicatorEntity.class, EntityDataSerializers.BLOCK_POS);
   private static final EntityDataAccessor<BlockPos> BOX_HIGH_POS = SynchedEntityData.defineId(LoadingIndicatorEntity.class, EntityDataSerializers.BLOCK_POS);
   public boolean isValid = false;

   public LoadingIndicatorEntity(EntityType type, Level world) {
      super(type, world);
   }

   @Override
   public void tick() {
      super.tick();
      if (this.level().isClientSide()) {
         this.tickClient();
      } else if (!this.isValid) {
         this.remove(RemovalReason.KILLED);
      }
   }

   @Environment(EnvType.CLIENT)
   private void tickClient() {
      this.addParticles();
      if (this.tickCount > 40) {
         LocalPlayer player = Minecraft.getInstance().player;
         if (player != null && player.level() == this.level() && player.position().distanceToSqr(this.position()) < 256.0) {
            this.showMessageClient();
         }
      }
   }

   @Environment(EnvType.CLIENT)
   private void addParticles() {
      int num = this.tickCount < 100 ? 50 : 20;
      IntBox box = this.getBox();
      BlockPos size = box.getSize();
      RandomSource random = this.level().getRandom();

      for (int i = 0; i < num; i++) {
         Vec3 p = new Vec3(random.nextDouble(), random.nextDouble(), random.nextDouble()).multiply(Vec3.atLowerCornerOf(size)).add(Vec3.atLowerCornerOf(box.l));
         double speedMultiplier = 20.0;
         double vx = speedMultiplier * (random.nextFloat() - 0.5) * 0.5;
         double vy = speedMultiplier * (random.nextFloat() - 0.5) * 0.5;
         double vz = speedMultiplier * (random.nextFloat() - 0.5) * 0.5;
         this.level().addParticle(ParticleTypes.PORTAL, p.x, p.y, p.z, vx, vy, vz);
      }
   }

   @Override
   protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
      builder.define(TEXT, Component.literal("Loading..."));
      builder.define(BOX_LOW_POS, BlockPos.ZERO);
      builder.define(BOX_HIGH_POS, BlockPos.ZERO);
   }

   @Override
   public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
      return false;
   }

   @Override
   protected void readAdditionalSaveData(ValueInput input) {
   }

   @Override
   protected void addAdditionalSaveData(ValueOutput output) {
   }

   public void inform(Component str) {
      this.setText(str);
   }

   public void setText(Component str) {
      this.getEntityData().set(TEXT, str);
   }

   public Component getText() {
      return this.getEntityData().get(TEXT);
   }

   public void setBox(IntBox box) {
      this.getEntityData().set(BOX_LOW_POS, box.l);
      this.getEntityData().set(BOX_HIGH_POS, box.h);
   }

   public IntBox getBox() {
      return new IntBox(this.getEntityData().get(BOX_LOW_POS), this.getEntityData().get(BOX_HIGH_POS));
   }

   @Environment(EnvType.CLIENT)
   private void showMessageClient() {
      Gui inGameHud = Minecraft.getInstance().gui;
      inGameHud.setOverlayMessage(this.getText(), false);
   }
}
