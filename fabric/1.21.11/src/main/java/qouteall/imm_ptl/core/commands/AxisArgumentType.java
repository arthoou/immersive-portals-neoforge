package qouteall.imm_ptl.core.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.core.Direction.Axis;
import net.minecraft.network.chat.Component;
import qouteall.imm_ptl.core.McHelper;
import qouteall.q_misc_util.platform.Platform;

public class AxisArgumentType implements ArgumentType<Axis> {
   public static final AxisArgumentType instance = new AxisArgumentType();
   public static final DynamicCommandExceptionType exceptionType = new DynamicCommandExceptionType(object -> Component.literal("Invalid Axis " + object));

   public static <S> Axis getAxis(CommandContext<S> context, String argName) {
      return context.getArgument(argName, Axis.class);
   }

   public Axis parse(StringReader reader) throws CommandSyntaxException {
      String s = reader.readUnquotedString();

      return switch (s) {
         case "x", "X" -> Axis.X;
         case "y", "Y" -> Axis.Y;
         case "z", "Z" -> Axis.Z;
         default -> throw exceptionType.createWithContext(reader, s);
      };
   }

   @Override
   public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
      return SharedSuggestionProvider.suggest(Arrays.stream(Axis.values()).map(Enum::name).collect(Collectors.toList()), builder);
   }

   @Override
   public Collection<String> getExamples() {
      return Arrays.stream(Axis.values()).map(Enum::toString).collect(Collectors.toList());
   }

   public static void init() {
      Platform.get()
         .registerArgumentType(McHelper.newResourceLocation("imm_ptl:axis"), AxisArgumentType.class, SingletonArgumentInfo.contextFree(() -> instance));
   }
}
