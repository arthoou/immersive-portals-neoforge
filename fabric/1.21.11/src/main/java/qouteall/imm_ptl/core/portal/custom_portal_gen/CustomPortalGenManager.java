package qouteall.imm_ptl.core.portal.custom_portal_gen;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.IPPerServerInfo;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.mc_utils.ServerTaskList;
import qouteall.q_misc_util.my_util.WithDim;
import qouteall.q_misc_util.platform.Platform;

public class CustomPortalGenManager {
   private static final Logger LOGGER = LogUtils.getLogger();
   private final Multimap<Item, CustomPortalGeneration> useItemGen = HashMultimap.create();
   private final Multimap<Item, CustomPortalGeneration> throwItemGen = HashMultimap.create();
   private final ArrayList<CustomPortalGeneration> convGen = new ArrayList<>();
   private final Map<UUID, WithDim<Vec3>> playerPosBeforeTravel = new HashMap<>();

   public static void init() {
      registerDataPackRegistries();
      Platform.get().onServerDataPackReloadEnd(server -> onDataPackReloaded(server));
      Platform.get().onServerStarted(CustomPortalGenManager::onDataPackReloaded);
   }

   public static void registerDataPackRegistries() {
      Platform.get().registerDynamicRegistry(CustomPortalGeneration.REGISTRY_KEY, CustomPortalGeneration.CODEC);
      Platform.get().registerDynamicRegistry(CustomPortalGeneration.LEGACY_REGISTRY_KEY, CustomPortalGeneration.CODEC);
   }

   private static void onDataPackReloaded(MinecraftServer server) {
      if (IPGlobal.enableDatapackPortalGen) {
         LOGGER.info("Processing custom portal generation");
         CustomPortalGenManager manager = new CustomPortalGenManager();
         Registry<CustomPortalGeneration> registry = server.registryAccess().lookupOrThrow(CustomPortalGeneration.REGISTRY_KEY);
         Registry<CustomPortalGeneration> legacyRegistry = server.registryAccess().lookupOrThrow(CustomPortalGeneration.LEGACY_REGISTRY_KEY);

         for (Entry<ResourceKey<CustomPortalGeneration>, CustomPortalGeneration> entry : registry.entrySet()) {
            manager.addEntry(server, entry.getKey(), entry.getValue());
         }

         for (Entry<ResourceKey<CustomPortalGeneration>, CustomPortalGeneration> entry : legacyRegistry.entrySet()) {
            manager.addEntry(server, entry.getKey(), entry.getValue());
         }

         IPPerServerInfo perServerInfo = IPPerServerInfo.of(server);
         perServerInfo.customPortalGenManager = manager;
      }
   }

   private void addEntry(MinecraftServer server, ResourceKey<CustomPortalGeneration> key, CustomPortalGeneration gen) {
      gen.identifier = key.identifier();
      CustomPortalGeneration.InitializationResult r1 = gen.initAndCheck(server);
      if (!(r1 instanceof CustomPortalGeneration.InitializationOk)) {
         LOGGER.info("Custom portal generation is not activated: {}\n{}", r1, gen.toString());
      } else {
         LOGGER.info("Loaded Custom Portal Generation {}", key.identifier());
         this.load(gen);
         if (gen.reversible) {
            CustomPortalGeneration reverse = gen.getReverse();
            if (reverse != null) {
               reverse.identifier = key.identifier();
               CustomPortalGeneration.InitializationResult r2 = reverse.initAndCheck(server);
               if (!(r2 instanceof CustomPortalGeneration.InitializationOk)) {
                  LOGGER.info("Reverse custom portal generation is not activated: {}\n{}", r2, reverse.toString());
                  return;
               }
            } else {
               McHelper.sendMessageToFirstLoggedPlayer(server, Component.literal("Cannot create reverse generation of " + gen));
            }
         }
      }
   }

   private void load(CustomPortalGeneration gen) {
      PortalGenTrigger trigger = gen.trigger;
      if (trigger instanceof PortalGenTrigger.UseItemTrigger) {
         this.useItemGen.put(((PortalGenTrigger.UseItemTrigger)trigger).item, gen);
      } else if (trigger instanceof PortalGenTrigger.ThrowItemTrigger) {
         this.throwItemGen.put(((PortalGenTrigger.ThrowItemTrigger)trigger).item, gen);
      } else if (trigger instanceof PortalGenTrigger.ConventionalDimensionChangeTrigger) {
         this.convGen.add(gen);
      }
   }

   public void onItemUse(UseOnContext context, InteractionResult actionResult) {
      if (!context.getLevel().isClientSide()) {
         MinecraftServer server = context.getLevel().getServer();
         Item item = context.getItemInHand().getItem();
         if (this.useItemGen.containsKey(item)) {
            ServerTaskList.of(server)
               .addTask(
                  () -> {
                     for (CustomPortalGeneration gen : this.useItemGen.get(item)) {
                        boolean result = gen.perform(
                           (ServerLevel)context.getLevel(), context.getClickedPos().relative(context.getClickedFace()), context.getPlayer()
                        );
                        if (result) {
                           if (gen.trigger instanceof PortalGenTrigger.UseItemTrigger trigger && trigger.shouldConsume(context)) {
                              context.getItemInHand().shrink(1);
                           }
                           break;
                        }
                     }

                     return true;
                  }
               );
         }
      }
   }

   public void onItemTick(ItemEntity entity) {
      Validate.isTrue(!entity.level().isClientSide());
      if (entity.hasPickUpDelay()) {
         Item item = entity.getItem().getItem();
         Collection<CustomPortalGeneration> gens = this.throwItemGen.get(item);
         if (!gens.isEmpty()) {
            ServerTaskList.of(entity.level().getServer()).addTask(() -> {
               for (CustomPortalGeneration gen : gens) {
                  boolean result = gen.perform((ServerLevel)entity.level(), entity.blockPosition(), entity);
                  if (result) {
                     entity.getItem().shrink(1);
                     break;
                  }
               }

               return true;
            });
         }
      }
   }

   public void onBeforeConventionalDimensionChange(ServerPlayer player) {
      this.playerPosBeforeTravel.put(player.getUUID(), new WithDim<>(player.level().dimension(), player.position()));
   }

   public void onAfterConventionalDimensionChange(ServerPlayer player) {
      UUID uuid = player.getUUID();
      if (this.playerPosBeforeTravel.containsKey(uuid)) {
         WithDim<Vec3> startCoord = this.playerPosBeforeTravel.get(uuid);
         ServerLevel startWorld = player.server.getLevel(startCoord.dimension());
         if (startWorld == null) {
            LOGGER.error("Cannot find world {}", startCoord.dimension());
            return;
         }

         BlockPos startPos = BlockPos.containing(startCoord.value());

         for (CustomPortalGeneration gen : this.convGen) {
            boolean succeeded = gen.perform(startWorld, startPos, player);
            if (succeeded) {
               this.playerPosBeforeTravel.remove(uuid);
               return;
            }
         }
      }

      this.playerPosBeforeTravel.remove(uuid);
   }
}
