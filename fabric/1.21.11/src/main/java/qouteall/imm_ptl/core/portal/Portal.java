package qouteall.imm_ptl.core.portal;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.EntityType.Builder;
import net.minecraft.world.entity.EntityType.EntityFactory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.api.ImmPtlEntityExtension;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface;
import qouteall.imm_ptl.core.mc_utils.IPEntityEventListenableEntity;
import qouteall.imm_ptl.core.mc_utils.ServerTaskList;
import qouteall.imm_ptl.core.network.ImmPtlNetworking;
import qouteall.imm_ptl.core.platform_specific.IPConfig;
import qouteall.imm_ptl.core.platform_specific.O_O;
import qouteall.imm_ptl.core.portal.animation.AnimationView;
import qouteall.imm_ptl.core.portal.animation.DefaultPortalAnimation;
import qouteall.imm_ptl.core.portal.animation.PortalAnimation;
import qouteall.imm_ptl.core.portal.animation.PortalAnimationDriver;
import qouteall.imm_ptl.core.portal.animation.UnilateralPortalState;
import qouteall.imm_ptl.core.portal.shape.PortalShape;
import qouteall.imm_ptl.core.portal.shape.PortalShapeSerialization;
import qouteall.imm_ptl.core.portal.shape.RectangularPortalShape;
import qouteall.imm_ptl.core.portal.shape.SpecialFlatPortalShape;
import qouteall.imm_ptl.core.render.renderer.PortalRenderer;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.DQuaternion;
import qouteall.q_misc_util.my_util.Mesh2D;
import qouteall.q_misc_util.my_util.MyTaskList;
import qouteall.q_misc_util.my_util.Plane;
import qouteall.q_misc_util.my_util.RayTraceResult;
import qouteall.q_misc_util.my_util.TriangleConsumer;
import qouteall.q_misc_util.platform.Platform;
import qouteall.q_misc_util.platform.PlatformNetworking;
import qouteall.q_misc_util.platform.event.Event;

public class Portal extends Entity implements PortalLike, IPEntityEventListenableEntity {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final EntityType<Portal> ENTITY_TYPE = createPortalEntityType(Portal::new, portalEntityTypeKey("portal"));
   public static final Event<Consumer<Portal>> CLIENT_PORTAL_ACCEPT_SYNC_EVENT = Helper.createConsumerEvent();
   public static final Event<Consumer<Portal>> CLIENT_PORTAL_SPAWN_EVENT = Helper.createConsumerEvent();
   private static final AABB NULL_BOX = new AABB(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
   protected double width = 0.0;
   protected double height = 0.0;
   protected double thickness = 0.0;
   protected Vec3 axisW;
   protected Vec3 axisH;
   protected ResourceKey<Level> dimensionTo;
   protected Vec3 destination;
   protected boolean teleportable = true;
   @Nullable
   protected PortalShape portalShape;
   @Nullable
   public UUID specificPlayerId;
   @Nullable
   protected DQuaternion rotation;
   protected double scaling = 1.0;
   protected boolean teleportChangesScale = true;
   protected boolean teleportChangesGravity = IPConfig.getConfig().portalsChangeGravityByDefault;
   protected boolean interactable = true;
   PortalExtension extension;
   @Nullable
   public String portalTag;
   public boolean isGlobalPortal = false;
   protected boolean fuseView = false;
   @Deprecated
   public boolean renderingMergable = false;
   protected boolean crossPortalCollisionEnabled = true;
   protected boolean doRenderPlayer = true;
   protected boolean visible = true;
   @Nullable
   protected List<String> commandsOnTeleported;
   @Environment(EnvType.CLIENT)
   PortalRenderInfo portalRenderInfo;
   public final PortalAnimation animation = new PortalAnimation();
   @Nullable
   protected PortalState lastTickPortalState;
   protected boolean reloadAndSyncNextTick = false;
   @Nullable
   private AABB thinBoundingBoxCache;
   @Nullable
   private AABB boundingBoxCache;
   @Nullable
   private Vec3 normalCache;
   @Nullable
   private Vec3 contentDirectionCache;
   @Nullable
   private PortalState portalStateCache;
   @Nullable
   private VoxelShape thisSideCollisionExclusion;
   @Nullable
   private UnilateralPortalState thisSideStateCache;
   @Nullable
   private UnilateralPortalState otherSideStateCache;
   public static final Event<Consumer<Portal>> CLIENT_PORTAL_TICK_SIGNAL = Helper.createConsumerEvent();
   public static final Event<Consumer<Portal>> SERVER_PORTAL_TICK_SIGNAL = Helper.createConsumerEvent();
   public static final Event<Consumer<Portal>> PORTAL_DISPOSE_SIGNAL = Helper.createConsumerEvent();
   public static final Event<BiConsumer<Portal, CompoundTag>> READ_PORTAL_DATA_SIGNAL = Helper.createBiConsumerEvent();
   public static final Event<BiConsumer<Portal, CompoundTag>> WRITE_PORTAL_DATA_SIGNAL = Helper.createBiConsumerEvent();

   public static ResourceKey<EntityType<?>> portalEntityTypeKey(String path) {
      return ResourceKey.create(Registries.ENTITY_TYPE, McHelper.newResourceLocation("immersive_portals", path));
   }

   public static <T extends Portal> EntityType<T> createPortalEntityType(EntityFactory<T> constructor, ResourceKey<EntityType<?>> entityTypeKey) {
      return Platform.get()
         .forceTrackedVelocityUpdates(Builder.of(constructor, MobCategory.MISC).sized(0.0F, 0.0F).fireImmune().clientTrackingRange(6).updateInterval(20), true)
         .build(entityTypeKey);
   }

   public Portal(EntityType<?> entityType, Level world) {
      super(entityType, world);
   }

   @Override
   protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
   }

   @Override
   public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
      return false;
   }

   @Override
   protected void readAdditionalSaveData(ValueInput input) {
      this.readAdditionalSaveData(Helper.readWholeCompound(input));
   }

   @Override
   protected void addAdditionalSaveData(ValueOutput output) {
      CompoundTag compoundTag = new CompoundTag();
      this.addAdditionalSaveData(compoundTag);
      Helper.writeWholeCompound(output, compoundTag);
   }

   protected void readAdditionalSaveData(CompoundTag compoundTag) {
      this.width = compoundTag.getDoubleOr("width", 0.0);
      this.height = compoundTag.getDoubleOr("height", 0.0);
      this.thickness = compoundTag.getDoubleOr("thickness", 0.0);
      this.axisW = Helper.getVec3d(compoundTag, "axisW").normalize();
      this.axisH = Helper.getVec3d(compoundTag, "axisH").normalize();
      this.dimensionTo = Helper.getWorldId(compoundTag, "dimensionTo");
      this.destination = Helper.getVec3d(compoundTag, "destination");
      this.specificPlayerId = Helper.getUuid(compoundTag, "specificPlayer");
      if (compoundTag.contains("portalShape")) {
         CompoundTag portalShapeTag = compoundTag.getCompoundOrEmpty("portalShape");
         PortalShape portalShape = PortalShapeSerialization.deserialize(portalShapeTag);
         if (portalShape == null) {
            LOGGER.error("Cannot deserialize portal shape {}", portalShapeTag);
            this.portalShape = RectangularPortalShape.INSTANCE;
         } else {
            this.portalShape = portalShape;
         }
      } else {
         Mesh2D mesh2D;
         if (compoundTag.contains("specialShape")) {
            boolean shapeNormalized = compoundTag.getBooleanOr("shapeNormalized", false);
            if (shapeNormalized) {
               mesh2D = GeometryPortalShape.readOldMeshFromTag(compoundTag.getListOrEmpty("specialShape"));
            } else {
               mesh2D = GeometryPortalShape.readOldMeshFromTagNonNormalized(compoundTag.getListOrEmpty("specialShape"), this.width / 2.0, this.height / 2.0);
            }
         } else {
            mesh2D = null;
         }

         if (mesh2D == null) {
            this.portalShape = RectangularPortalShape.INSTANCE;
         } else {
            this.portalShape = new SpecialFlatPortalShape(mesh2D);
         }
      }

      if (compoundTag.contains("teleportable")) {
         this.teleportable = compoundTag.getBooleanOr("teleportable", false);
      }

      if (compoundTag.contains("rotationA")) {
         this.setRotationTransformationD(
            new DQuaternion(
               compoundTag.getFloatOr("rotationB", 0.0F),
               compoundTag.getFloatOr("rotationC", 0.0F),
               compoundTag.getFloatOr("rotationD", 0.0F),
               compoundTag.getFloatOr("rotationA", 0.0F)
            )
         );
      } else {
         this.rotation = null;
      }

      if (compoundTag.contains("interactable")) {
         this.interactable = compoundTag.getBooleanOr("interactable", false);
      }

      if (compoundTag.contains("scale")) {
         this.scaling = compoundTag.getDoubleOr("scale", 0.0);
      }

      if (compoundTag.contains("teleportChangesScale")) {
         this.teleportChangesScale = compoundTag.getBooleanOr("teleportChangesScale", false);
      }

      if (compoundTag.contains("teleportChangesGravity")) {
         this.teleportChangesGravity = compoundTag.getBooleanOr("teleportChangesGravity", false);
      } else {
         this.teleportChangesGravity = IPConfig.getConfig().portalsChangeGravityByDefault;
      }

      if (compoundTag.contains("portalTag")) {
         this.portalTag = compoundTag.getStringOr("portalTag", "");
      }

      if (compoundTag.contains("fuseView")) {
         this.fuseView = compoundTag.getBooleanOr("fuseView", false);
      }

      if (compoundTag.contains("renderingMergable")) {
         this.renderingMergable = compoundTag.getBooleanOr("renderingMergable", false);
      }

      if (compoundTag.contains("hasCrossPortalCollision")) {
         this.crossPortalCollisionEnabled = compoundTag.getBooleanOr("hasCrossPortalCollision", false);
      }

      if (compoundTag.contains("commandsOnTeleported")) {
         ListTag list = compoundTag.getListOrEmpty("commandsOnTeleported");
         this.commandsOnTeleported = list.stream().map(t -> ((StringTag)t).value()).collect(Collectors.toList());
      } else {
         this.commandsOnTeleported = null;
      }

      if (compoundTag.contains("doRenderPlayer")) {
         this.doRenderPlayer = compoundTag.getBooleanOr("doRenderPlayer", false);
      } else {
         this.doRenderPlayer = true;
      }

      if (compoundTag.contains("isVisible")) {
         this.visible = compoundTag.getBooleanOr("isVisible", false);
      } else {
         this.visible = true;
      }

      this.animation.readFromTag(compoundTag);
      READ_PORTAL_DATA_SIGNAL.invoker().accept(this, compoundTag);
      this.updateCache();
   }

   protected void addAdditionalSaveData(CompoundTag compoundTag) {
      compoundTag.putDouble("width", this.width);
      compoundTag.putDouble("height", this.height);
      compoundTag.putDouble("thickness", this.thickness);
      Helper.putVec3d(compoundTag, "axisW", this.axisW);
      Helper.putVec3d(compoundTag, "axisH", this.axisH);
      Helper.putWorldId(compoundTag, "dimensionTo", this.dimensionTo);
      Helper.putVec3d(compoundTag, "destination", this.getDestPos());
      if (this.specificPlayerId != null) {
         Helper.putUuid(compoundTag, "specificPlayer", this.specificPlayerId);
      }

      CompoundTag portalShapeTag = PortalShapeSerialization.serialize(this.getPortalShape());
      compoundTag.put("portalShape", portalShapeTag);
      compoundTag.putBoolean("teleportable", this.teleportable);
      if (this.rotation != null) {
         compoundTag.putDouble("rotationA", this.rotation.w);
         compoundTag.putDouble("rotationB", this.rotation.x);
         compoundTag.putDouble("rotationC", this.rotation.y);
         compoundTag.putDouble("rotationD", this.rotation.z);
      }

      compoundTag.putBoolean("interactable", this.interactable);
      compoundTag.putDouble("scale", this.scaling);
      compoundTag.putBoolean("teleportChangesScale", this.teleportChangesScale);
      compoundTag.putBoolean("teleportChangesGravity", this.teleportChangesGravity);
      if (this.portalTag != null) {
         compoundTag.putString("portalTag", this.portalTag);
      }

      compoundTag.putBoolean("fuseView", this.fuseView);
      compoundTag.putBoolean("renderingMergable", this.renderingMergable);
      compoundTag.putBoolean("hasCrossPortalCollision", this.crossPortalCollisionEnabled);
      compoundTag.putBoolean("doRenderPlayer", this.doRenderPlayer);
      compoundTag.putBoolean("isVisible", this.visible);
      if (this.commandsOnTeleported != null) {
         ListTag list = new ListTag();

         for (String command : this.commandsOnTeleported) {
            list.add(StringTag.valueOf(command));
         }

         compoundTag.put("commandsOnTeleported", list);
      }

      this.animation.writeToTag(compoundTag);
      WRITE_PORTAL_DATA_SIGNAL.invoker().accept(this, compoundTag);
   }

   @NotNull
   public PortalShape getPortalShape() {
      if (this.portalShape == null) {
         this.portalShape = RectangularPortalShape.INSTANCE;
      }

      return this.portalShape;
   }

   public void setPortalShape(PortalShape portalShape) {
      this.portalShape = portalShape;
      if (portalShape.isPlanar()) {
         this.thickness = 0.0;
      }

      this.updateCache();
   }

   public void setPortalShapeToDefault() {
      this.setPortalShape(RectangularPortalShape.INSTANCE);
   }

   @Override
   public void ip_onEntityPositionUpdated() {
      this.updateCache();
   }

   @Override
   public void ip_onRemoved(RemovalReason reason) {
      PORTAL_DISPOSE_SIGNAL.invoker().accept(this);
   }

   @Override
   public Vec3 transformPoint(Vec3 pos) {
      Vec3 localPos = pos.subtract(this.getOriginPos());
      return this.transformLocalVec(localPos).add(this.getDestPos());
   }

   @Override
   public Vec3 transformLocalVec(Vec3 localVec) {
      return this.transformLocalVecNonScale(localVec).scale(this.scaling);
   }

   public Vec3 getNormal() {
      if (this.normalCache == null) {
         this.normalCache = this.axisW.cross(this.axisH).normalize();
      }

      return this.normalCache;
   }

   public Vec3 getContentDirection() {
      if (this.contentDirectionCache == null) {
         this.contentDirectionCache = this.transformLocalVecNonScale(this.getNormal().scale(-1.0));
      }

      return this.contentDirectionCache;
   }

   public void onEntityTeleportedOnServer(Entity entity) {
      if (this.commandsOnTeleported != null) {
         McHelper.invokeCommandAs(entity, this.commandsOnTeleported);
      }
   }

   public void reloadAndSyncToClient() {
      this.reloadAndSyncNextTick = false;
      Validate.isTrue(!this.isGlobalPortal, "global portal is not synced by this");
      Validate.isTrue(!this.level().isClientSide(), "must be used on server side");
      this.updateCache();
      Packet<ClientGamePacketListener> packet = this.createSyncPacket();
      McHelper.sendToTrackers(this, packet);
   }

   public void reloadAndSyncToClientNextTick() {
      Validate.isTrue(!this.level().isClientSide(), "must be used on server side");
      this.reloadAndSyncNextTick = true;
   }

   public void reloadAndSyncClusterToClientNextTick() {
      PortalExtension.forClusterPortals(this, Portal::reloadAndSyncToClientNextTick);
   }

   public void reloadAndSyncToClientWithTickDelay(int tickDelay) {
      Validate.isTrue(!this.level().isClientSide(), "must be used on server side");
      ServerTaskList.of(this.level().getServer()).addTask(MyTaskList.withDelay(tickDelay, () -> {
         this.reloadAndSyncToClientNextTick();
         return true;
      }));
   }

   public void updateCache() {
      if (this.axisW != null && this.axisH != null) {
         this.portalStateCache = null;
         this.boundingBoxCache = null;
         this.thinBoundingBoxCache = null;
         this.normalCache = null;
         this.contentDirectionCache = null;
         this.thisSideCollisionExclusion = null;
         this.thisSideStateCache = null;
         this.otherSideStateCache = null;
      }
   }

   @NotNull
   @Override
   public AABB getBoundingBox() {
      if (this.boundingBoxCache == null) {
         this.boundingBoxCache = this.makeBoundingBox();
      }

      return this.boundingBoxCache;
   }

   @Override
   public Vec3 getOriginPos() {
      return this.position();
   }

   @Override
   public Vec3 getDestPos() {
      return this.destination;
   }

   public void setOriginPos(Vec3 pos) {
      this.setPos(pos);
   }

   public void setDestinationDimension(ResourceKey<Level> dim) {
      this.dimensionTo = dim;
   }

   public void setDestination(Vec3 destination) {
      this.destination = destination;
      this.updateCache();
   }

   @Override
   public boolean isFuseView() {
      return this.fuseView;
   }

   @Deprecated
   public boolean isRenderingMergable() {
      return this.renderingMergable;
   }

   public boolean isInteractable() {
      return this.interactable;
   }

   public void setInteractable(boolean interactable) {
      this.interactable = interactable;
   }

   @Override
   public Level getOriginWorld() {
      return this.level();
   }

   @Override
   public Level getDestWorld() {
      return this.getDestinationWorld();
   }

   @Override
   public ResourceKey<Level> getDestDim() {
      return this.dimensionTo;
   }

   @Override
   public double getScale() {
      return this.scaling;
   }

   @Override
   public boolean getIsGlobal() {
      return this.isGlobalPortal;
   }

   @Override
   public boolean isVisible() {
      return this.visible;
   }

   public void setIsVisible(boolean visible) {
      this.visible = visible;
   }

   public boolean canTeleportEntity(Entity entity) {
      if (!this.teleportable) {
         return false;
      }

      if (entity instanceof Portal) {
         return false;
      }

      if (entity instanceof Player) {
         if (this.specificPlayerId != null && !entity.getUUID().equals(this.specificPlayerId)) {
            return false;
         }
      } else if (this.specificPlayerId != null && !this.specificPlayerId.equals(Util.NIL_UUID)) {
         return false;
      }

      return !O_O.allowTeleportingEntity(entity, this) ? false : ((ImmPtlEntityExtension)entity).imm_ptl_canTeleportThroughPortal(this);
   }

   public boolean canCollideWithEntity(Entity entity) {
      return this.canTeleportEntity(entity);
   }

   public boolean isInteractableBy(Player player) {
      if (!IPConfig.getConfig().enableCrossPortalInteraction) {
         return false;
      } else if (!this.isInteractable()) {
         return false;
      } else {
         return !this.isVisible() ? false : this.canTeleportEntity(player);
      }
   }

   @Nullable
   @Override
   public DQuaternion getRotation() {
      return this.rotation;
   }

   @NotNull
   public DQuaternion getRotationD() {
      return DQuaternion.fromNullable(this.getRotation());
   }

   @Override
   public boolean getDoRenderPlayer() {
      return this.doRenderPlayer;
   }

   public boolean getTeleportable() {
      return this.teleportable;
   }

   public void setTeleportable(boolean teleportable) {
      this.teleportable = teleportable;
   }

   public void setOrientationAndSize(Vec3 newAxisW, Vec3 newAxisH, double newWidth, double newHeight) {
      this.setOrientation(newAxisW, newAxisH);
      this.width = newWidth;
      this.height = newHeight;
      this.updateCache();
   }

   public void setOrientation(Vec3 newAxisW, Vec3 newAxisH) {
      this.axisW = newAxisW.normalize();
      this.axisH = newAxisH.normalize();
      this.updateCache();
   }

   public void setWidth(double newWidth) {
      this.width = newWidth;
      this.updateCache();
   }

   public void setHeight(double newHeight) {
      this.height = newHeight;
      this.updateCache();
   }

   public void setThickness(double newThickness) {
      this.thickness = newThickness;
      this.updateCache();
   }

   public void setPortalSize(double newWidth, double newHeight, double newThickness) {
      this.width = newWidth;
      this.height = newHeight;
      this.thickness = newThickness;
      this.updateCache();
   }

   public DQuaternion getOrientationRotation() {
      return PortalManipulation.getPortalOrientationQuaternion(this.axisW, this.axisH);
   }

   public void setOrientationRotation(DQuaternion quaternion) {
      DQuaternion fixed = this.level().isClientSide() ? quaternion : quaternion.fixFloatingPointErrorAccumulation();
      this.setOrientation(McHelper.getAxisWFromOrientation(fixed), McHelper.getAxisHFromOrientation(fixed));
   }

   public void setRotation(@Nullable DQuaternion quaternion) {
      this.setRotationTransformationD(quaternion);
   }

   public void setRotationTransformation(@Nullable DQuaternion quaternion) {
      this.setRotationTransformationD(quaternion);
   }

   public void setRotationTransformationD(@Nullable DQuaternion quaternion) {
      if (quaternion == null) {
         this.rotation = null;
      } else {
         this.rotation = quaternion.fixFloatingPointErrorAccumulation();
      }

      this.updateCache();
   }

   public void setOtherSideOrientation(DQuaternion otherSideOrientation) {
      this.setRotation(PortalManipulation.computeDeltaTransformation(this.getOrientationRotation(), otherSideOrientation));
   }

   public void setScaleTransformation(double newScale) {
      this.scaling = newScale;
   }

   @Environment(EnvType.CLIENT)
   public void renderViewAreaMesh(Vec3 portalPosRelativeToCamera, TriangleConsumer vertexOutput) {
      if (this instanceof Mirror) {
         boolean offsetFront = IrisInterface.invoker.isShaders() || IPGlobal.pureMirror;
         double mirrorOffset = offsetFront ? 0.01 : -0.01;
         portalPosRelativeToCamera = portalPosRelativeToCamera.add(((Mirror)this).getNormal().scale(mirrorOffset));
      }

      this.getPortalShape().renderViewAreaMesh(portalPosRelativeToCamera, this.getThisSideState(), vertexOutput, this.getIsGlobal());
   }

   @NotNull
   @Override
   public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
      return this.createSyncPacket();
   }

   private Packet<ClientGamePacketListener> createSyncPacket() {
      Validate.isTrue(!this.level().isClientSide());
      CompoundTag compoundTag = new CompoundTag();
      this.addAdditionalSaveData(compoundTag);
      return (Packet<ClientGamePacketListener>)(Packet<?>)PlatformNetworking.get()
         .createS2CPacket(
            new ImmPtlNetworking.PortalSyncPacket(
               this.getId(),
               this.getUUID(),
               this.getType(),
               PortalAPI.serverDimKeyToInt(this.level().getServer(), this.getOriginDim()),
               this.getX(),
               this.getY(),
               this.getZ(),
               compoundTag
            )
         );
   }

   @Override
   public boolean broadcastToPlayer(ServerPlayer spectator) {
      return this.specificPlayerId == null ? true : spectator.getUUID().equals(this.specificPlayerId);
   }

   @Override
   public void tick() {
      if (this.getBoundingBox().equals(NULL_BOX)) {
         LOGGER.error("Abnormal bounding box {}", this);
      }

      this.lastTickPortalState = this.getThisTickPortalState();
      if (!this.level().isClientSide() && this.reloadAndSyncNextTick) {
         this.reloadAndSyncToClient();
      }

      if (this.level().isClientSide()) {
         CLIENT_PORTAL_TICK_SIGNAL.invoker().accept(this);
      } else {
         if (!this.isPortalValid()) {
            LOGGER.info("Removed invalid portal {}", this);
            this.remove(RemovalReason.KILLED);
            return;
         }

         SERVER_PORTAL_TICK_SIGNAL.invoker().accept(this);
      }

      this.animation.tick(this);
      super.tick();
   }

   @NotNull
   @Override
   protected AABB makeBoundingBox(Vec3 position) {
      if (this.axisW == null) {
         this.boundingBoxCache = null;
         return NULL_BOX;
      }

      if (this.boundingBoxCache == null) {
         this.boundingBoxCache = this.getPortalShape().getBoundingBox(this.getThisSideState(), this.shouldLimitBoundingBox(), 0.2);
      }

      return this.boundingBoxCache;
   }

   protected boolean shouldLimitBoundingBox() {
      return !this.getIsGlobal();
   }

   @Override
   public void move(MoverType type, Vec3 movement) {
   }

   public boolean isPortalValid() {
      boolean valid = this.dimensionTo != null
         && this.width != 0.0
         && this.height != 0.0
         && this.axisW != null
         && this.axisH != null
         && this.getDestPos() != null
         && this.axisW.lengthSqr() > 0.9
         && this.axisH.lengthSqr() > 0.9
         && this.getY() > McHelper.getMinY(this.level()) - 100;
      if (valid) {
         if (this.level() instanceof ServerLevel serverLevel) {
            ServerLevel destWorld = serverLevel.getServer().getLevel(this.dimensionTo);
            if (destWorld == null) {
               LOGGER.error("Portal Dest Dimension Missing {}", this.dimensionTo.identifier());
               return false;
            }

            boolean inWorldBorder = destWorld.getWorldBorder().isWithinBounds(BlockPos.containing(this.getDestPos()));
            if (!inWorldBorder) {
               LOGGER.error("Destination out of World Border {}", this);
               return false;
            }
         }

         return this.level().isClientSide() ? this.isPortalValidClient() : true;
      } else {
         return false;
      }
   }

   @Environment(EnvType.CLIENT)
   private boolean isPortalValidClient() {
      boolean contains = ClientWorldLoader.getServerDimensions().contains(this.dimensionTo);
      if (!contains) {
         LOGGER.error("Client Portal Dest Dimension Missing {}", this.dimensionTo.identifier());
      }

      return contains;
   }

   @Nullable
   @Override
   public UUID getDiscriminator() {
      return this.getUUID();
   }

   @NotNull
   @Override
   public String toString() {
      return String.format(
         "%s{%s,%s,(%s %.1f %.1f %.1f)->(%s %.1f %.1f %.1f)%s%s%s}",
         this.getClass().getSimpleName(),
         this.getId(),
         this.getApproximateFacingDirection(),
         this.level().dimension().identifier(),
         this.getX(),
         this.getY(),
         this.getZ(),
         this.dimensionTo.identifier(),
         this.getDestPos().x,
         this.getDestPos().y,
         this.getDestPos().z,
         this.specificPlayerId != null ? ",specificAccessor:" + this.specificPlayerId.toString() : "",
         this.hasScaling() ? ",scale:" + this.scaling : "",
         this.portalTag != null ? "," + this.portalTag : ""
      );
   }

   public Direction getApproximateFacingDirection() {
      return Direction.getApproximateNearest(this.getNormal().x, this.getNormal().y, this.getNormal().z);
   }

   public Vec3 transformVelocityRelativeToPortal(Vec3 originalVelocityRelativeToPortal, Entity entity, Vec3 oldEntityPos) {
      Vec3 result = this.transformLocalVec(originalVelocityRelativeToPortal);
      int maxVelocity = 15;
      if (originalVelocityRelativeToPortal.length() > 15.0) {
         result = result.normalize().scale(15.0);
      }

      if (entity instanceof AbstractMinecart && result.lengthSqr() < 0.5) {
         result = result.scale(2.0);
      }

      return result;
   }

   public double getDistanceToPlane(Vec3 pos) {
      return pos.subtract(this.getOriginPos()).dot(this.getNormal());
   }

   public boolean isInFrontOfPortal(Vec3 pos) {
      return this.getDistanceToPlane(pos) > 0.0;
   }

   public Vec3 getPointInPlane(double xInPlane, double yInPlane) {
      return this.getOriginPos().add(this.getPointInPlaneLocal(xInPlane, yInPlane));
   }

   public Vec3 getPointInPlaneLocal(double xInPlane, double yInPlane) {
      return this.axisW.scale(xInPlane).add(this.axisH.scale(yInPlane));
   }

   public Vec3 getPointInPlaneLocalClamped(double xInPlane, double yInPlane) {
      return this.getPointInPlaneLocal(Mth.clamp(xInPlane, -this.width / 2.0, this.width / 2.0), Mth.clamp(yInPlane, -this.height / 2.0, this.height / 2.0));
   }

   public Vec3[] getFourVerticesLocal(double shrinkFactor) {
      return new Vec3[]{
         this.getPointInPlaneLocal(this.width / 2.0 - shrinkFactor, -this.height / 2.0 + shrinkFactor),
         this.getPointInPlaneLocal(-this.width / 2.0 + shrinkFactor, -this.height / 2.0 + shrinkFactor),
         this.getPointInPlaneLocal(this.width / 2.0 - shrinkFactor, this.height / 2.0 - shrinkFactor),
         this.getPointInPlaneLocal(-this.width / 2.0 + shrinkFactor, this.height / 2.0 - shrinkFactor)
      };
   }

   private Vec3[] getFourVerticesLocalRotated(double shrinkFactor) {
      Vec3[] fourVerticesLocal = this.getFourVerticesLocal(shrinkFactor);
      fourVerticesLocal[0] = this.transformLocalVec(fourVerticesLocal[0]);
      fourVerticesLocal[1] = this.transformLocalVec(fourVerticesLocal[1]);
      fourVerticesLocal[2] = this.transformLocalVec(fourVerticesLocal[2]);
      fourVerticesLocal[3] = this.transformLocalVec(fourVerticesLocal[3]);
      return fourVerticesLocal;
   }

   private Vec3[] getFourVerticesLocalCullable(double shrink) {
      double xStart = -this.width / 2.0;
      double xEnd = this.width / 2.0;
      double yStart = -this.height / 2.0;
      double yEnd = this.height / 2.0;
      return new Vec3[]{
         this.getPointInPlaneLocal(xEnd - shrink, yStart + shrink),
         this.getPointInPlaneLocal(xStart + shrink, yStart + shrink),
         this.getPointInPlaneLocal(xEnd - shrink, yEnd - shrink),
         this.getPointInPlaneLocal(xStart + shrink, yEnd - shrink)
      };
   }

   public final Vec3 transformPointRough(Vec3 pos) {
      Vec3 offset = this.getDestPos().subtract(this.getOriginPos());
      return pos.add(offset);
   }

   @Override
   public Vec3 transformLocalVecNonScale(Vec3 localVec) {
      return this.rotation == null ? localVec : this.rotation.rotate(localVec);
   }

   public Vec3 inverseTransformLocalVecNonScale(Vec3 localVec) {
      return this.rotation == null ? localVec : this.rotation.getConjugated().rotate(localVec);
   }

   @Override
   public Vec3 inverseTransformLocalVec(Vec3 localVec) {
      return this.inverseTransformLocalVecNonScale(localVec).scale(1.0 / this.scaling);
   }

   @Override
   public Vec3 inverseTransformPoint(Vec3 point) {
      return this.getOriginPos().add(this.inverseTransformLocalVec(point.subtract(this.getDestPos())));
   }

   public boolean isMovedThroughPortal(Vec3 lastTickPos, Vec3 pos) {
      return this.rayTrace(lastTickPos, pos) != null;
   }

   @Nullable
   public Vec3 rayTrace(Vec3 from, Vec3 to) {
      return this.lenientRayTrace(from, to, 0.001);
   }

   @Nullable
   public Vec3 lenientRayTrace(Vec3 from, Vec3 to, double leniency) {
      RayTraceResult rayTraceResult = this.getPortalShape().raytracePortalShape(this.getThisSideState(), from, to, leniency);
      return rayTraceResult == null ? null : rayTraceResult.hitPos();
   }

   @Nullable
   public RayTraceResult generalRayTrace(Vec3 from, Vec3 to, double leniency) {
      return this.getPortalShape().raytracePortalShape(this.getThisSideState(), from, to, leniency);
   }

   @Override
   public double getDistanceToNearestPointInPortal(Vec3 point) {
      return this.getPortalShape().roughDistanceToPortalShape(this.getThisSideState(), point);
   }

   public Vec3 getPointProjectedToPlane(Vec3 pos) {
      Vec3 originPos = this.getOriginPos();
      return this.getLocalVecProjectedToPlane(pos.subtract(originPos)).add(originPos);
   }

   public Vec3 getLocalVecProjectedToPlane(Vec3 offset) {
      double yInPlane = offset.dot(this.axisH);
      double xInPlane = offset.dot(this.axisW);
      return this.axisW.scale(xInPlane).add(this.axisH.scale(yInPlane));
   }

   public Vec3 getNearestPointInPortal(Vec3 pos) {
      Vec3 originPos = this.getOriginPos();
      Vec3 offset = pos.subtract(originPos);
      double yInPlane = offset.dot(this.axisH);
      double xInPlane = offset.dot(this.axisW);
      xInPlane = Mth.clamp(xInPlane, -this.width / 2.0, this.width / 2.0);
      yInPlane = Mth.clamp(yInPlane, -this.height / 2.0, this.height / 2.0);
      return originPos.add(this.axisW.scale(xInPlane)).add(this.axisH.scale(yInPlane));
   }

   public Level getDestinationWorld() {
      return this.getDestinationWorld(this.level().isClientSide());
   }

   private Level getDestinationWorld(boolean isClient) {
      if (isClient) {
         return CHelper.getClientWorld(this.dimensionTo);
      }

      MinecraftServer server = this.level().getServer();
      assert server != null;
      return server.getLevel(this.dimensionTo);
   }

   public static boolean isParallelPortal(Portal a, Portal b) {
      return a == b
         ? false
         : a.dimensionTo == b.level().dimension()
            && a.level().dimension() == b.dimensionTo
            && a.getOriginPos().distanceTo(b.getDestPos()) < 0.1
            && a.getDestPos().distanceTo(b.getOriginPos()) < 0.1
            && a.getNormal().dot(b.getContentDirection()) < -0.9;
   }

   public static boolean isParallelOrientedPortal(Portal currPortal, Portal outerPortal) {
      double dot = currPortal.getOriginPos().subtract(outerPortal.getDestPos()).dot(outerPortal.getContentDirection());
      return currPortal.level().dimension() == outerPortal.dimensionTo
         && currPortal.getNormal().dot(outerPortal.getContentDirection()) < -0.9
         && Math.abs(dot) < 0.001;
   }

   public static boolean isReversePortal(Portal a, Portal b) {
      return a.dimensionTo == b.level().dimension()
         && a.level().dimension() == b.dimensionTo
         && a.getOriginPos().distanceTo(b.getDestPos()) < 0.1
         && a.getDestPos().distanceTo(b.getOriginPos()) < 0.1
         && a.getNormal().dot(b.getContentDirection()) > 0.9;
   }

   public static boolean isFlippedPortal(Portal a, Portal b) {
      return a == b
         ? false
         : a.level() == b.level()
            && a.dimensionTo == b.dimensionTo
            && a.getOriginPos().distanceTo(b.getOriginPos()) < 0.1
            && a.getDestPos().distanceTo(b.getDestPos()) < 0.1
            && a.getNormal().dot(b.getNormal()) < -0.9;
   }

   @Override
   public double getDestAreaRadiusEstimation() {
      return Math.max(this.width, this.height) * this.scaling;
   }

   @Override
   public boolean isConventionalPortal() {
      return true;
   }

   @Override
   public AABB getThinBoundingBox() {
      if (this.thinBoundingBoxCache == null) {
         this.thinBoundingBoxCache = this.getPortalShape().getBoundingBox(this.getThisSideState(), false, 0.001);
      }

      return this.thinBoundingBoxCache;
   }

   @Environment(EnvType.CLIENT)
   @Override
   public boolean isRoughlyVisibleTo(Vec3 cameraPos) {
      return this.getPortalShape().roughTestVisibility(this.getThisSideState(), cameraPos, IrisInterface.invoker.isShaders());
   }

   @Nullable
   @Override
   public Plane getInnerClipping() {
      return this.getPortalShape().getInnerClipping(this.getThisSideState(), this.getOtherSideState(), this);
   }

   @Nullable
   @Override
   public Vec3[] getOuterFrustumCullingVertices() {
      return this.getFourVerticesLocalCullable(0.0);
   }

   public Matrix4d getFullSpaceTransformation() {
      Vec3 originPos = this.getOriginPos();
      Vec3 destPos = this.getDestPos();
      DQuaternion rot = this.getRotationD();
      return new Matrix4d()
         .translation(destPos.x, destPos.y, destPos.z)
         .scale(this.getScale())
         .rotate(rot.toMcQuaternion())
         .translate(-originPos.x, -originPos.y, -originPos.z);
   }

   public double getWidth() {
      return this.width;
   }

   public double getHeight() {
      return this.height;
   }

   public double getThickness() {
      return this.thickness;
   }

   public Vec3 getAxisW() {
      return this.axisW;
   }

   public void setAxisW(Vec3 axisW) {
      this.axisW = axisW;
      this.updateCache();
   }

   public Vec3 getAxisH() {
      return this.axisH;
   }

   public void setAxisH(Vec3 axisH) {
      this.axisH = axisH;
      this.updateCache();
   }

   public void setDestDim(ResourceKey<Level> dimensionTo) {
      this.dimensionTo = dimensionTo;
   }

   public Vec3 getDestination() {
      return this.destination;
   }

   public double getScaling() {
      return this.scaling;
   }

   public void setScaling(double scaling) {
      this.scaling = scaling;
      this.updateCache();
   }

   public boolean isTeleportChangesScale() {
      return this.teleportChangesScale;
   }

   public void setFuseView(boolean fuseView) {
      this.fuseView = fuseView;
   }

   public boolean isCrossPortalCollisionEnabled() {
      return this.crossPortalCollisionEnabled;
   }

   public void setCrossPortalCollisionEnabled(boolean crossPortalCollisionEnabled) {
      this.crossPortalCollisionEnabled = crossPortalCollisionEnabled;
   }

   public boolean isDoRenderPlayer() {
      return this.doRenderPlayer;
   }

   public void setDoRenderPlayer(boolean doRenderPlayer) {
      this.doRenderPlayer = doRenderPlayer;
   }

   @Nullable
   public List<String> getCommandsOnTeleported() {
      return this.commandsOnTeleported;
   }

   public void setCommandsOnTeleported(@Nullable List<String> commandsOnTeleported) {
      this.commandsOnTeleported = commandsOnTeleported;
   }

   public Portal.TransformationDesc getTransformationDesc() {
      return new Portal.TransformationDesc(this.getDestDim(), this.getFullSpaceTransformation(), this.getRotationD(), this.getScale());
   }

   @Override
   public boolean cannotRenderInMe(Portal portal) {
      return this.respectParallelOrientedPortal() ? isParallelPortal(portal, this) : isParallelOrientedPortal(portal, this);
   }

   public void myUnsetRemoved() {
      this.unsetRemoved();
   }

   @Override
   public void refreshDimensions() {
      this.boundingBoxCache = null;
   }

   @Nullable
   @Override
   public Matrix4f getAdditionalCameraTransformation() {
      return PortalRenderer.getPortalTransformation(this);
   }

   public boolean canDoOuterFrustumCulling() {
      if (this.isFuseView()) {
         return false;
      } else {
         return !this.isVisible() ? false : this.getPortalShape().canDoOuterFrustumCulling();
      }
   }

   public boolean isTeleportable() {
      return this.teleportable;
   }

   public boolean respectParallelOrientedPortal() {
      return false;
   }

   public void onCollidingWithEntity(Entity entity) {
   }

   @Override
   public boolean getHasCrossPortalCollision() {
      return this.crossPortalCollisionEnabled;
   }

   public boolean getTeleportChangesScale() {
      return this.teleportChangesScale;
   }

   public void setTeleportChangesScale(boolean teleportChangesScale) {
      this.teleportChangesScale = teleportChangesScale;
   }

   public boolean getTeleportChangesGravity() {
      return this.teleportChangesGravity;
   }

   public boolean isTeleportChangesGravity() {
      return this.teleportChangesGravity;
   }

   public void setTeleportChangesGravity(boolean cond) {
      this.teleportChangesGravity = cond;
   }

   public Direction getTeleportedGravityDirection(Direction oldGravityDir) {
      return !this.getTeleportChangesGravity() ? oldGravityDir : this.getTransformedGravityDirection(oldGravityDir);
   }

   public Direction getTransformedGravityDirection(Direction oldGravityDir) {
      Vec3 oldGravityVec = Vec3.atLowerCornerOf(oldGravityDir.getUnitVec3i());
      Vec3 newGravityVec = this.transformLocalVecNonScale(oldGravityVec);
      return Direction.getApproximateNearest(newGravityVec.x, newGravityVec.y, newGravityVec.z);
   }

   @NotNull
   public PortalState getPortalState() {
      Validate.isTrue(this.axisH != null, "the portal is not yet initialized");
      return new PortalState(
         this.level().dimension(),
         this.getOriginPos(),
         this.dimensionTo,
         this.getDestPos(),
         this.getScale(),
         this.getRotationD(),
         this.getOrientationRotation(),
         this.width,
         this.height,
         this.thickness,
         this instanceof Mirror
      );
   }

   public void setPortalState(PortalState state) {
      Validate.isTrue(this.level().dimension() == state.fromWorld);
      Validate.isTrue(this.dimensionTo == state.toWorld);
      this.width = state.width;
      this.height = state.height;
      this.thickness = state.thickness;
      this.setOriginPos(state.fromPos);
      this.setDestination(state.toPos);
      PortalManipulation.setPortalOrientationQuaternion(this, state.orientation);
      if (DQuaternion.isClose(state.rotation, DQuaternion.identity)) {
         this.setRotationTransformationD(null);
      } else {
         this.setRotationTransformationD(state.rotation);
      }

      this.setScaleTransformation(state.scaling);
   }

   public UnilateralPortalState getThisSideState() {
      if (this.thisSideStateCache == null) {
         this.thisSideStateCache = new UnilateralPortalState(
            this.getOriginDim(), this.getOriginPos(), this.getOrientationRotation(), this.width, this.height, this.thickness
         );
      }

      return this.thisSideStateCache;
   }

   public UnilateralPortalState getOtherSideState() {
      if (this.otherSideStateCache == null) {
         PortalState portalState = this.getPortalState();
         this.otherSideStateCache = UnilateralPortalState.extractOtherSide(portalState);
      }

      return this.otherSideStateCache;
   }

   public void setThisSideState(UnilateralPortalState ups) {
      this.setThisSideState(ups, false);
   }

   public void setThisSideState(UnilateralPortalState ups, boolean lockScale) {
      Validate.notNull(ups);
      PortalState portalState = this.getPortalState();
      PortalState newPortalState = portalState.withThisSideUpdated(ups, lockScale);
      this.setPortalState(newPortalState);
   }

   @Environment(EnvType.CLIENT)
   public void acceptDataSync(Vec3 pos, CompoundTag customData) {
      PortalState oldState = this.getPortalState();
      this.setPos(pos);
      this.readAdditionalSaveData(customData);
      if (this.animation.defaultAnimation.durationTicks > 0) {
         this.animation.defaultAnimation.startClientDefaultAnimation(this, oldState);
      }

      CLIENT_PORTAL_ACCEPT_SYNC_EVENT.invoker().accept(this);
   }

   public CompoundTag writePortalDataToNbt() {
      CompoundTag nbtCompound = new CompoundTag();
      this.addAdditionalSaveData(nbtCompound);
      return nbtCompound;
   }

   public void readPortalDataFromNbt(CompoundTag compound) {
      try {
         this.readAdditionalSaveData(compound);
      } catch (Exception e) {
         LOGGER.error("Failed to read portal data from nbt {}", compound, e);
         if (!this.isPortalValid()) {
            this.setRemoved(RemovalReason.KILLED);
         }
      }
   }

   public void updatePortalFromNbt(CompoundTag newNbt) {
      CompoundTag data = this.writePortalDataToNbt();
      newNbt.keySet().forEach(key -> data.put(key, newNbt.get(key)));
      this.readPortalDataFromNbt(data);
   }

   public void rectifyClusterPortals(boolean sync) {
      PortalExtension.get(this).rectifyClusterPortals(this, sync);
   }

   public void reloadPortal() {
      Validate.isTrue(!this.level().isClientSide());
      this.updateCache();
      this.rectifyClusterPortals(true);
      this.reloadAndSyncToClientNextTick();
   }

   public DefaultPortalAnimation getDefaultAnimation() {
      return this.animation.defaultAnimation;
   }

   public void clearAnimationDrivers(boolean clearThisSide, boolean clearOtherSide) {
      this.animation.clearAnimationDrivers(this, clearThisSide, clearOtherSide);
   }

   public void addThisSideAnimationDriver(PortalAnimationDriver driver) {
      this.getAnimationView().addThisSideAnimation(driver);
      this.reloadAndSyncClusterToClientNextTick();
   }

   public void addOtherSideAnimationDriver(PortalAnimationDriver driver) {
      this.getAnimationView().addOtherSideAnimation(driver);
      this.reloadAndSyncClusterToClientNextTick();
   }

   public void pauseAnimation() {
      this.animation.setPaused(this, true);
   }

   public void resumeAnimation() {
      this.animation.setPaused(this, false);
   }

   public void resetAnimationReferenceState(boolean resetThisSide, boolean resetOtherSide) {
      this.animation.resetReferenceState(this, resetThisSide, resetOtherSide);
   }

   public AnimationView getAnimationView() {
      PortalExtension extension = PortalExtension.get(this);
      if (extension.flippedPortal != null && extension.flippedPortal.animation.hasAnimationDriver()) {
         return new AnimationView(this, extension.flippedPortal, IntraClusterRelation.FLIPPED);
      } else if (extension.reversePortal != null && extension.reversePortal.animation.hasAnimationDriver()) {
         return new AnimationView(this, extension.reversePortal, IntraClusterRelation.REVERSE);
      } else {
         return extension.parallelPortal != null && extension.parallelPortal.animation.hasAnimationDriver()
            ? new AnimationView(this, extension.parallelPortal, IntraClusterRelation.PARALLEL)
            : new AnimationView(this, this, IntraClusterRelation.SAME);
      }
   }

   public boolean isOtherSideChunkLoaded() {
      Validate.isTrue(!this.level().isClientSide());
      return McHelper.isServerChunkFullyLoaded((ServerLevel)this.getDestWorld(), new ChunkPos(BlockPos.containing(this.getDestPos())));
   }

   @Nullable
   public PortalState getLastTickPortalState() {
      return this.lastTickPortalState == null ? this.getThisTickPortalState() : this.lastTickPortalState;
   }

   @Nullable
   public PortalState getThisTickPortalState() {
      if (this.portalStateCache == null) {
         this.portalStateCache = this.getPortalState();
      }

      return this.portalStateCache;
   }

   public PortalState getAnimationEndingState() {
      return this.animation.getAnimationEndingState(this);
   }

   public Vec3 transformFromPortalLocalToWorld(Vec3 localPos) {
      return this.axisW.scale(localPos.x).add(this.axisH.scale(localPos.y)).add(this.getNormal().scale(localPos.z)).add(this.getOriginPos());
   }

   public Vec3 transformFromWorldToPortalLocal(Vec3 worldPos) {
      Vec3 relativePos = worldPos.subtract(this.getOriginPos());
      return new Vec3(relativePos.dot(this.axisW), relativePos.dot(this.axisH), relativePos.dot(this.getNormal()));
   }

   @Nullable
   public Portal getAnimationHolder() {
      if (this.animation.hasAnimationDriver()) {
         return this;
      } else {
         PortalExtension portalExtension = PortalExtension.get(this);
         if (portalExtension.flippedPortal != null && portalExtension.flippedPortal.animation.hasAnimationDriver()) {
            return portalExtension.flippedPortal;
         } else if (portalExtension.reversePortal != null && portalExtension.reversePortal.animation.hasAnimationDriver()) {
            return portalExtension.reversePortal;
         } else {
            return portalExtension.parallelPortal != null && portalExtension.parallelPortal.animation.hasAnimationDriver()
               ? portalExtension.parallelPortal
               : null;
         }
      }
   }

   public Portal getPossibleAnimationHolder() {
      Portal holder = this.getAnimationHolder();
      return holder != null ? holder : this;
   }

   public long getAnimationEffectiveTime() {
      Portal holder = this.getPossibleAnimationHolder();
      return holder.animation.getEffectiveTime(holder.level().getGameTime());
   }

   public void disableDefaultAnimation() {
      this.animation.defaultAnimation.durationTicks = 0;
      this.reloadAndSyncToClientNextTick();
   }

   public VoxelShape getThisSideCollisionExclusion() {
      if (this.thisSideCollisionExclusion == null) {
         this.thisSideCollisionExclusion = this.getPortalShape().getThisSideCollisionExclusion(this.getThisSideState());
      }

      return this.thisSideCollisionExclusion;
   }

   public record TransformationDesc(ResourceKey<Level> dimensionTo, Matrix4d fullSpaceTransformation, DQuaternion rotation, double scaling) {
      public static boolean isRoughlyEqual(Portal.TransformationDesc a, Portal.TransformationDesc b) {
         if (a.dimensionTo != b.dimensionTo) {
            return false;
         }

         Matrix4d diff = new Matrix4d().set(a.fullSpaceTransformation).sub(b.fullSpaceTransformation);
         double diffSquareSum = diff.m00() * diff.m00()
            + diff.m01() * diff.m01()
            + diff.m02() * diff.m02()
            + diff.m03() * diff.m03()
            + diff.m10() * diff.m10()
            + diff.m11() * diff.m11()
            + diff.m12() * diff.m12()
            + diff.m13() * diff.m13()
            + diff.m20() * diff.m20()
            + diff.m21() * diff.m21()
            + diff.m22() * diff.m22()
            + diff.m23() * diff.m23()
            + diff.m30() * diff.m30()
            + diff.m31() * diff.m31()
            + diff.m32() * diff.m32()
            + diff.m33() * diff.m33();
         return diffSquareSum < 0.1;
      }
   }
}
