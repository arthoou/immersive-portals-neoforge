package qouteall.imm_ptl.core.platform_specific;

import net.minecraft.SharedConstants;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlClientChunkMap;
import qouteall.imm_ptl.core.network.ImmPtlNetworkConfig;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.custom_portal_gen.CustomPortalGeneration;
import qouteall.imm_ptl.core.portal.custom_portal_gen.PortalGenInfo;
import qouteall.q_misc_util.Helper;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class O_O {
    public static boolean isDimensionalThreadingPresent = false;

    private static final ResourceLocation AETHER_DIMENSION_ID = ResourceLocation.fromNamespaceAndPath("aether", "the_aether");
    
    public static boolean isForge() {
        return false;
    }
    
    //@OnlyIn(Dist.CLIENT)
    public static void onPlayerChangeDimensionClient(
        ResourceKey<Level> from, ResourceKey<Level> to
    ) {
        RequiemCompatClient.onPlayerTeleportedClient();
    }
    
    public static void onPlayerTravelOnServer(
        ServerPlayer player,
        ServerLevel fromWorld, ServerLevel toWorld
    ) {
        RequiemCompat.onPlayerTeleportedServer(player);
    }
    
    public static Path getGameDir() {
        return FMLPaths.GAMEDIR.get();
    }
    
    private static final BlockState obsidianState = Blocks.OBSIDIAN.defaultBlockState();
    
    public static boolean isObsidian(BlockState blockState) {
        return blockState == obsidianState;
    }
    
    public static void postClientChunkLoadEvent(LevelChunk chunk) {
        NeoForge.EVENT_BUS.post(new ChunkEvent.Load(chunk, true));
//        ClientChunkEvents.CHUNK_LOAD.invoker().onChunkLoad(
//            ((ClientLevel) chunk.getLevel()), chunk
//        );
    }
    
    public static void postClientChunkUnloadEvent(LevelChunk chunk) {
        NeoForge.EVENT_BUS.post(new ChunkEvent.Unload(chunk));
//        ClientChunkEvents.CHUNK_UNLOAD.invoker().onChunkUnload(
//            ((ClientLevel) chunk.getLevel()), chunk
//        );
    }
    
    public static boolean isDedicatedServer() {
        return FMLEnvironment.dist == Dist.DEDICATED_SERVER;
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
        if (!ModList.get().isLoaded("duportallimiter")) {
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
        if (!ModList.get().isLoaded("duportallimiter")) {
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

    private static boolean dimensionMatches(ResourceKey<Level> dimension, ResourceLocation location) {
        return dimension.location().equals(location);
    }

    private enum DuPortalLimiterPortalKind {
        NETHER,
        AETHER
    }

    // @Nick1st - Moved to client class
//    @OnlyIn(Dist.CLIENT)
//    public static ClientChunkCache createMyClientChunkManager(ClientLevel world, int loadDistance) {
//        return new ImmPtlClientChunkMap(world, loadDistance);
//    }
    
    public static boolean getIsPehkuiPresent() {
        return ModList.get().isLoaded("pehkui");
    }
    
    @Nullable
    public static String getImmPtlModInfoUrl() {
        String gameVersion = SharedConstants.getCurrentVersion().getName();
        
        if (O_O.isForge()) {
            return "https://qouteall.fun/immptl_info/forge-%s.json".formatted(gameVersion);
        }
        else {
            // it's in github pages
            // https://github.com/qouteall/immptl_info
            return "https://qouteall.fun/immptl_info/%s.json".formatted(gameVersion);
        }
    }
    
    public static boolean isModLoadedWithinVersion(String modId, @Nullable String startVersion, @Nullable String endVersion) {
        Optional<? extends ModContainer> modContainer = ModList.get().getModContainerById(modId);
        if (modContainer.isPresent()) {
            ArtifactVersion version = modContainer.get().getModInfo().getVersion();
            
            try {
                if (startVersion != null) {
                    int i = new DefaultArtifactVersion(startVersion).compareTo(version);
                    if (i > 0) {
                        return false;
                    }
                }
                
                if (endVersion != null) {
                    int i = new DefaultArtifactVersion(endVersion).compareTo(version);
                    if (i < 0) {
                        return false;
                    }
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            
            return true;
            
        }
        else {
            return false;
        }
    }
    
    public static @NotNull ImmPtlNetworkConfig.ModVersion getImmPtlVersion() {
        // TODO @Nick1st
//        Version version = FabricLoader.getInstance()
//            .getModContainer("iportal").orElseThrow()
//            .getMetadata().getVersion();
//
//        if (!(version instanceof SemanticVersionImpl semanticVersion)) {
//            // in dev env, its ${version}
//            return ImmPtlNetworkConfig.ModVersion.OTHER;
//        }
//
//        if (semanticVersion.getVersionComponentCount() != 3) {
//            Helper.LOGGER.error(
//                "immersive portals version {} is not in regular form", semanticVersion
//            );
//            return ImmPtlNetworkConfig.ModVersion.OTHER;
//        }
//
//        return new ImmPtlNetworkConfig.ModVersion(
//            semanticVersion.getVersionComponent(0),
//            semanticVersion.getVersionComponent(1),
//            semanticVersion.getVersionComponent(2)
//        );
        return new ImmPtlNetworkConfig.ModVersion(
                1, 0, 0
        );
    }
    
    public static String getImmPtlVersionStr() {
        // TODO @Nick1st
//        return FabricLoader.getInstance()
//            .getModContainer("iportal").orElseThrow()
//            .getMetadata().getVersion().toString();
        return "";
    }
    
    public static boolean shouldUpdateImmPtl(String latestReleaseVersion) {
        // TODO @Nick1st
//        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
//            return false;
//        }
//
//        Version currentVersion = FabricLoader.getInstance()
//            .getModContainer("iportal").get().getMetadata().getVersion();
//        try {
//            Version latestVersion = Version.parse(latestReleaseVersion);
//
//            if (latestVersion.compareTo(currentVersion) > 0) {
//                return true;
//            }
//        }
//        catch (VersionParsingException e) {
//            e.printStackTrace();
//        }
        
        return false;
    }
    
    public static String getModDownloadLink() {
        return "https://modrinth.com/mod/immersiveportals";
    }
    
    public static String getIssueLink() {
        return "https://github.com/iPortalTeam/ImmersivePortalsMod/discussions";
    }
    
    @Nullable
    public static ResourceLocation getModIconLocation(String modid) {
        // TODO @Nick1st - Fix this
        return null;
//        String path = FabricLoader.getInstance().getModContainer(modid)
//            .flatMap(c -> c.getMetadata().getIconPath(512))
//            .orElse(null);
//        if (path == null) {
//            return null;
//        }
//
//        // for example, if the icon path is "assets/modid/icon.png"
//        // then the result should be modid:icon.png
//
//        if (path.startsWith("/")) {
//            path = path.substring(1);
//        }
//        if (path.startsWith("assets")) {
//            path = path.substring("assets".length());
//        }
//        if (path.startsWith("/")) {
//            path = path.substring(1);
//        }
//        String[] parts = path.split("/");
//        if (parts.length != 2) {
//            return null;
//        }
//        return McHelper.newResourceLocation(parts[0], parts[1]);
    }
    
    @Nullable
    public static String getModName(String modid) {
        return FMLLoader.getLoadingModList().getModFileById(modid).getMods().stream().findFirst().get().getDisplayName();
    }
    
    // most quilt installations use quilted fabric api
    public static boolean isQuilt() {
        return false;
        //return FabricLoader.getInstance().isModLoaded("quilted_fabric_api");
    }
    
    public static List<String> getLoadedModIds() {
        return ModList.get().getSortedMods().stream().map(ModContainer::getModId).sorted().toList();
    }

    public static boolean allowTeleportingEntity(Entity entity, Portal portal) {
        // ForgeHooks.onTravelToDimension() on Forge
        return true;
    }
    
    public static boolean isDevEnv() {
        return !FMLEnvironment.production;
    }
}
