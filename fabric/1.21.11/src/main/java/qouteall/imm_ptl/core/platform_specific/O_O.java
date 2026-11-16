// Reconstructed from the published Apache-2.0 MC 1.21.11 port; see SOURCE-PROVENANCE.md.
package qouteall.imm_ptl.core.platform_specific;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlClientChunkMap;
import qouteall.imm_ptl.core.network.ImmPtlNetworkConfig;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.custom_portal_gen.PortalGenInfo;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.platform.ClientPlatform;
import qouteall.q_misc_util.platform.ModInfo;
import qouteall.q_misc_util.platform.Platform;

import net.minecraft.core.BlockPos;

import qouteall.imm_ptl.core.portal.custom_portal_gen.CustomPortalGeneration;

public class O_O {
    // Arthous: preserve optional Nether/Aether limiter hooks from the local base.
    private static final Identifier AETHER_DIMENSION_ID = McHelper.newResourceLocation("aether", "the_aether");
   public static boolean isDimensionalThreadingPresent = false;
   private static final BlockState obsidianState = Blocks.OBSIDIAN.defaultBlockState();

   public static boolean isForge() {
      return Platform.get().isForgeLike();
   }

   @Environment(EnvType.CLIENT)
   public static void onPlayerChangeDimensionClient(ResourceKey<Level> from, ResourceKey<Level> to) {
      RequiemCompat.onPlayerTeleportedClient();
      ClientPlatform.get().onPlayerChangedDimensionClient(from, to);
   }

   public static void onPlayerTravelOnServer(ServerPlayer player, ServerLevel fromWorld, ServerLevel toWorld) {
      RequiemCompat.onPlayerTeleportedServer(player);
   }

   public static Path getGameDir() {
      return Platform.get().getGameDir();
   }

   public static boolean isObsidian(BlockState blockState) {
      return blockState == obsidianState;
   }

   public static void postClientChunkLoadEvent(LevelChunk chunk) {
      ClientPlatform.get().postClientChunkLoadEvent((ClientLevel)chunk.getLevel(), chunk);
   }

   public static void postClientChunkUnloadEvent(LevelChunk chunk) {
      ClientPlatform.get().postClientChunkUnloadEvent((ClientLevel)chunk.getLevel(), chunk);
   }

   public static boolean isDedicatedServer() {
      return Platform.get().isDedicatedServer();
   }
    public static void postPortalSpawnEventForge(PortalGenInfo info) {
        if (!isIntrinsicNetherPortal(info)) {
            return;
        }

        notifyDuPortalLimiterNetherPortalCreated(
            McHelper.getServerWorld(info.from),
            info.fromShape.anchor
        );
    }

    public static boolean shouldCancelPortalByDuPortalLimiter(ServerLevel level, BlockPos pos, @Nullable Entity entity) {
        return queryDuPortalLimiterShouldBlock("shouldBlockImmersiveNetherPortal", level, pos, entity);
    }

    public static boolean shouldCancelPortalByDuPortalLimiter(CustomPortalGeneration generation, ServerLevel level, BlockPos pos, @Nullable Entity entity) {
        DuPortalLimiterPortalKind kind = getDuPortalLimiterPortalKind(generation);
        if (kind == null) {
            return false;
        }

        if (kind == DuPortalLimiterPortalKind.AETHER) {
            return queryDuPortalLimiterShouldBlock("shouldBlockImmersiveAetherPortal", level, pos, entity);
        }

        return queryDuPortalLimiterShouldBlock("shouldBlockImmersiveNetherPortal", level, pos, entity);
    }

    public static void notifyDuPortalLimiterPortalsGenerated(CustomPortalGeneration generation, Portal[] portals) {
        DuPortalLimiterPortalKind kind = getDuPortalLimiterPortalKind(generation);
        if (kind == null || portals.length == 0) {
            return;
        }

        for (Portal portal : portals) {
            if (portal.level() instanceof ServerLevel level) {
                if (kind == DuPortalLimiterPortalKind.AETHER) {
                    notifyDuPortalLimiterPortalCreated("recordImmersiveAetherPortal", level, portal.blockPosition());
                }
                else {
                    notifyDuPortalLimiterPortalCreated("recordImmersiveNetherPortal", level, portal.blockPosition());
                }
                return;
            }
        }
    }

    private static boolean queryDuPortalLimiterShouldBlock(String methodName, ServerLevel level, BlockPos pos, @Nullable Entity entity) {
        if (!Platform.get().isModLoaded("duportallimiter")) {
            return false;
        }

        try {
            Object result = Class.forName("com.dusmp.duportallimiter.DuPortalLimiter")
                .getMethod(methodName, ServerLevel.class, BlockPos.class, Entity.class)
                .invoke(null, level, pos, entity);
            return Boolean.TRUE.equals(result);
        }
        catch (ReflectiveOperationException | LinkageError exception) {
            Helper.LOGGER.error("Failed to query DuPortalLimiter compatibility hook " + methodName, exception);
            return false;
        }
    }

    private static void notifyDuPortalLimiterNetherPortalCreated(ServerLevel level, BlockPos pos) {
        notifyDuPortalLimiterPortalCreated("recordImmersiveNetherPortal", level, pos);
    }

    private static void notifyDuPortalLimiterPortalCreated(String methodName, ServerLevel level, BlockPos pos) {
        if (!Platform.get().isModLoaded("duportallimiter")) {
            return;
        }

        try {
            Class.forName("com.dusmp.duportallimiter.DuPortalLimiter")
                .getMethod(methodName, ServerLevel.class, BlockPos.class)
                .invoke(null, level, pos);
        }
        catch (ReflectiveOperationException | LinkageError exception) {
            Helper.LOGGER.error("Failed to update DuPortalLimiter compatibility hook " + methodName, exception);
        }
    }

    private static boolean isIntrinsicNetherPortal(PortalGenInfo info) {
        return (info.from == Level.OVERWORLD && info.to == Level.NETHER)
            || (info.from == Level.NETHER && info.to == Level.OVERWORLD);
    }

    @Nullable
    private static DuPortalLimiterPortalKind getDuPortalLimiterPortalKind(CustomPortalGeneration generation) {
        if (isAetherPortalGeneration(generation)) {
            return DuPortalLimiterPortalKind.AETHER;
        }

        if (isNetherPortalGeneration(generation)) {
            return DuPortalLimiterPortalKind.NETHER;
        }

        return null;
    }

    private static boolean isAetherPortalGeneration(CustomPortalGeneration generation) {
        if (dimensionMatches(generation.toDimension, AETHER_DIMENSION_ID)) {
            return true;
        }

        if (generation.fromDimensions.stream().anyMatch(dimension -> dimensionMatches(dimension, AETHER_DIMENSION_ID))) {
            return true;
        }

        return generation.identifier != null
            && generation.identifier.getNamespace().equals("aether")
            && generation.identifier.getPath().contains("aether_portal");
    }

    private static boolean isNetherPortalGeneration(CustomPortalGeneration generation) {
        return (generation.fromDimensions.contains(Level.OVERWORLD) && generation.toDimension.equals(Level.NETHER))
            || (generation.fromDimensions.contains(Level.NETHER) && generation.toDimension.equals(Level.OVERWORLD));
    }

    private static boolean dimensionMatches(ResourceKey<Level> dimension, Identifier location) {
        return dimension.identifier().equals(location);
    }

    private enum DuPortalLimiterPortalKind {
        NETHER,
        AETHER
    }

   @Environment(EnvType.CLIENT)
   public static ClientChunkCache createMyClientChunkManager(ClientLevel world, int loadDistance) {
      return new ImmPtlClientChunkMap(world, loadDistance);
   }

   public static boolean getIsPehkuiPresent() {
      return Platform.get().isModLoaded("pehkui");
   }

   @Nullable
   public static boolean isModLoadedWithinVersion(String modId, @Nullable String startVersion, @Nullable String endVersion) {
      Optional<ModInfo> modContainer = Platform.get().getModContainer(modId);
      if (modContainer.isPresent()) {
         String version = modContainer.get().version();

         try {
            if (startVersion != null) {
               int i = Platform.get().compareVersions(startVersion, version);
               if (i > 0) {
                  return false;
               }
            }

            if (endVersion != null) {
               int i = Platform.get().compareVersions(endVersion, version);
               if (i < 0) {
                  return false;
               }
            }
         } catch (IllegalArgumentException e) {
            e.printStackTrace();
         }

         return true;
      } else {
         return false;
      }
   }

   @NotNull
   public static ImmPtlNetworkConfig.ModVersion getImmPtlVersion() {
      ModInfo modInfo = Platform.get().getModContainer("iportal").orElseThrow();
      int[] versionComponents = modInfo.semanticVersionComponents();
      if (versionComponents == null) {
         return ImmPtlNetworkConfig.ModVersion.OTHER;
      } else if (versionComponents.length != 3) {
         Helper.LOGGER.error("immersive portals version {} is not in regular form", modInfo.version());
         return ImmPtlNetworkConfig.ModVersion.OTHER;
      } else {
         return new ImmPtlNetworkConfig.ModVersion(versionComponents[0], versionComponents[1], versionComponents[2]);
      }
   }

   public static String getImmPtlVersionStr() {
      return Platform.get().getModContainer("iportal").orElseThrow().version();
   }

   public static boolean shouldUpdateImmPtl(String latestReleaseVersion) {
      if (Platform.get().isDevelopmentEnvironment()) {
         return false;
      }

      String currentVersion = Platform.get().getModContainer("iportal").get().version();

      try {
         if (Platform.get().compareVersions(latestReleaseVersion, currentVersion) > 0) {
            return true;
         }
      } catch (IllegalArgumentException e) {
         e.printStackTrace();
      }

      return false;
   }

   public static String getModDownloadLink() {
      return "https://modrinth.com/mod/immersiveportals";
   }

   public static String getIssueLink() {
      return "https://github.com/iPortalTeam/ImmersivePortalsMod/discussions";
   }

   @Nullable
   public static Identifier getModIconLocation(String modid) {
      String path = Platform.get().getModContainer(modid).flatMap(ModInfo::iconPath).orElse(null);
      if (path == null) {
         return null;
      }

      if (path.startsWith("/")) {
         path = path.substring(1);
      }

      if (path.startsWith("assets")) {
         path = path.substring("assets".length());
      }

      if (path.startsWith("/")) {
         path = path.substring(1);
      }

      String[] parts = path.split("/");
      return parts.length != 2 ? null : McHelper.newResourceLocation(parts[0], parts[1]);
   }

   @Nullable
   public static String getModName(String modid) {
      return Platform.get().getModContainer(modid).map(ModInfo::name).orElse(null);
   }

   public static boolean isQuilt() {
      return Platform.get().isModLoaded("quilted_fabric_api");
   }

   public static List<String> getLoadedModIds() {
      return Platform.get().getAllMods().stream().map(ModInfo::id).sorted().toList();
   }

   public static boolean allowTeleportingEntity(Entity entity, Portal portal) {
      return true;
   }

   public static boolean isDevEnv() {
      return Platform.get().isDevelopmentEnvironment();
   }
}
