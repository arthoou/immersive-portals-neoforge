package qouteall.q_misc_util.platform;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.serialization.Codec;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Consumer;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfo.Template;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab.Builder;
import net.minecraft.world.level.Level;

public interface Platform {
   static Platform get() {
      return Platform.Holder.INSTANCE;
   }

   Path getGameDir();

   Path getConfigDir();

   boolean isDedicatedServer();

   boolean isDevelopmentEnvironment();

   boolean isForgeLike();

   boolean isModLoaded(String var1);

   Optional<ModInfo> getModContainer(String var1);

   List<ModInfo> getAllMods();

   int compareVersions(String var1, String var2);

   <T> void registerDynamicRegistry(ResourceKey<Registry<T>> var1, Codec<T> var2);

   <A extends ArgumentType<?>, T extends Template<A>, I extends ArgumentTypeInfo<A, T>> void registerArgumentType(Identifier var1, Class<A> var2, I var3);

   <T> void registerBuiltIn(Registry<T> var1, Identifier var2, T var3);

   Builder createCreativeTabBuilder();

   <T extends Entity> net.minecraft.world.entity.EntityType.Builder<T> forceTrackedVelocityUpdates(
      net.minecraft.world.entity.EntityType.Builder<T> var1, boolean var2
   );

   void onServerTickEnd(Consumer<MinecraftServer> var1);

   void onServerStarted(Consumer<MinecraftServer> var1);

   void onServerDataPackReloadEnd(Consumer<MinecraftServer> var1);

   default void onPlayerChangedDimension(ServerPlayer player, ResourceKey<Level> from, ResourceKey<Level> to) {
   }

   void onRegisterServerCommands(Platform.ServerCommandRegistrar var1);

   void onAttackBlock(Platform.AttackBlockHandler var1);

   @FunctionalInterface
   interface AttackBlockHandler {
      InteractionResult interact(Player var1, Level var2, InteractionHand var3, BlockPos var4, Direction var5);
   }

   class Holder {
      private static final Platform INSTANCE = ServiceLoader.load(Platform.class)
         .findFirst()
         .orElseThrow(() -> new IllegalStateException("No Platform implementation found"));
   }

   @FunctionalInterface
   interface ServerCommandRegistrar {
      void register(CommandDispatcher<CommandSourceStack> var1, CommandBuildContext var2);
   }
}
