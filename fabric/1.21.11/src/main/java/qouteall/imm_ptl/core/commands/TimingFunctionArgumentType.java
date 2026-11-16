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
import net.minecraft.network.chat.Component;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.portal.animation.TimingFunction;
import qouteall.q_misc_util.platform.Platform;

public class TimingFunctionArgumentType implements ArgumentType<TimingFunction> {
   public static final TimingFunctionArgumentType instance = new TimingFunctionArgumentType();
   public static final DynamicCommandExceptionType exceptionType = new DynamicCommandExceptionType(
      object -> Component.literal("Invalid Timing Function " + object)
   );

   public static TimingFunction get(CommandContext<?> context, String argName) {
      return context.getArgument(argName, TimingFunction.class);
   }

   public TimingFunction parse(StringReader reader) throws CommandSyntaxException {
      String s = reader.readUnquotedString();
      return TimingFunction.valueOf(s);
   }

   @Override
   public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
      return SharedSuggestionProvider.suggest(Arrays.stream(TimingFunction.values()).map(Enum::name).collect(Collectors.toList()), builder);
   }

   @Override
   public Collection<String> getExamples() {
      return Arrays.stream(TimingFunction.values()).map(Enum::toString).collect(Collectors.toList());
   }

   public static void init() {
      Platform.get()
         .registerArgumentType(
            McHelper.newResourceLocation("imm_ptl:timing_function_argument_type"),
            TimingFunctionArgumentType.class,
            SingletonArgumentInfo.contextFree(() -> instance)
         );
   }
}
