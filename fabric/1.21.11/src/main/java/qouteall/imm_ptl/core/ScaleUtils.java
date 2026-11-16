package qouteall.imm_ptl.core;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.ducks.IECamera;
import qouteall.imm_ptl.core.portal.Portal;

public class ScaleUtils {
   public static final Identifier IPORTAL_SCALING = Identifier.fromNamespaceAndPath("iportal", "scaling");

   @Environment(EnvType.CLIENT)
   public static void onClientPlayerTeleported(Portal portal) {
      if (portal.hasScaling() && portal.isTeleportChangesScale()) {
         Minecraft client = Minecraft.getInstance();
         LocalPlayer player = client.player;
         Validate.notNull(player, "Player is null");
         doScalingForEntity(player, portal);
         IECamera camera = (IECamera)client.gameRenderer.getMainCamera();
         camera.ip_setCameraY((float)(camera.ip_getCameraY() * portal.getScaling()), (float)(camera.ip_getLastCameraY() * portal.getScaling()));
      }
   }

   public static void onServerEntityTeleported(Entity entity, Portal portal) {
      if (portal.hasScaling() && portal.isTeleportChangesScale()) {
         doScalingForEntity(entity, portal);
         if (entity.getVehicle() != null) {
            doScalingForEntity(entity.getVehicle(), portal);
         }
      }
   }

   @Nullable
   public static AttributeInstance getScaleAttr(Entity entity) {
      return entity instanceof LivingEntity livingEntity ? livingEntity.getAttributes().getInstance(Attributes.SCALE) : null;
   }

   public static double getScale(Entity entity) {
      return entity instanceof LivingEntity livingEntity ? livingEntity.getScale() : 1.0;
   }

   public static double getBaseScale(Entity entity) {
      AttributeInstance scaleAttr = getScaleAttr(entity);
      return scaleAttr != null ? scaleAttr.getBaseValue() : 1.0;
   }

   public static double getIPortalScaling(Entity entity) {
      AttributeInstance scaleAttr = getScaleAttr(entity);
      if (scaleAttr == null) {
         return 1.0;
      }

      AttributeModifier modifier = scaleAttr.getModifier(IPORTAL_SCALING);
      return modifier == null ? 1.0 : modifier.amount() + 1.0;
   }

   public static void setIPortalScaling(Entity entity, double newScale) {
      AttributeInstance scaleAttr = getScaleAttr(entity);
      if (scaleAttr != null) {
         if (Math.abs(newScale - 1.0) < 1.0E-4) {
            scaleAttr.removeModifier(IPORTAL_SCALING);
            entity.refreshDimensions();
         } else {
            scaleAttr.addOrReplacePermanentModifier(new AttributeModifier(IPORTAL_SCALING, newScale - 1.0, Operation.ADD_MULTIPLIED_TOTAL));
            entity.refreshDimensions();
         }
      }
   }

   public static void setBaseScale(Entity entity, double scale) {
      AttributeInstance scaleAttr = getScaleAttr(entity);
      if (scaleAttr != null) {
         scaleAttr.setBaseValue(scale);
         entity.refreshDimensions();
      }
   }

   public static double computeThirdPersonScale(Entity entity) {
      return getScale(entity);
   }

   public static double computeBlockReachScale(Entity entity) {
      return getScale(entity);
   }

   public static double computeMotionScale(Entity entity) {
      return getScale(entity);
   }

   private static void doScalingForEntity(Entity entity, Portal portal) {
      Vec3 eyePos = McHelper.getEyePos(entity);
      Vec3 lastTickEyePos = McHelper.getLastTickEyePos(entity);
      double oldScale = getIPortalScaling(entity);
      double newScale = transformScale(portal, oldScale);
      if (!entity.level().isClientSide() && isScaleIllegal(newScale)) {
         newScale = 1.0;
         if (entity instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component.literal("Scale out of range"));
         }
      }

      setIPortalScaling(entity, newScale);
      if (!entity.level().isClientSide()) {
         McHelper.setEyePos(entity, eyePos, lastTickEyePos);
         McHelper.updateBoundingBox(entity);
      } else {
         McHelper.setEyePos(entity, eyePos, lastTickEyePos);
         McHelper.updateBoundingBox(entity);
      }
   }

   private static double transformScale(Portal portal, double oldScale) {
      double result = oldScale * portal.getScaling();
      if (Math.abs(result - 1.0) < 1.0E-4) {
         result = 1.0;
      }

      return result;
   }

   private static boolean isScaleIllegal(double scale) {
      return scale > IPGlobal.scaleLimit || scale < 1.0 / (IPGlobal.scaleLimit * 2);
   }
}
