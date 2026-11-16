package qouteall.imm_ptl.core.mc_utils;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.bytes.ByteCollection;
import it.unimi.dsi.fastutil.bytes.ByteOpenHashSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagVisitor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;

@IPVanillaCopy
public class MyNbtTextFormatter implements TagVisitor {
   private static final Logger LOGGER = LogManager.getLogger();
   private static final int field_33271 = 8;
   private static final ByteCollection SINGLE_LINE_ELEMENT_TYPES = new ByteOpenHashSet(Arrays.asList((byte)1, (byte)2, (byte)3, (byte)4, (byte)5, (byte)6));
   private static final ChatFormatting NAME_COLOR = ChatFormatting.AQUA;
   private static final ChatFormatting STRING_COLOR = ChatFormatting.GREEN;
   private static final ChatFormatting NUMBER_COLOR = ChatFormatting.GOLD;
   private static final ChatFormatting TYPE_SUFFIX_COLOR = ChatFormatting.RED;
   private static final Pattern SIMPLE_NAME = Pattern.compile("[A-Za-z0-9._+-]+");
   private static final String KEY_VALUE_SEPARATOR = String.valueOf(':');
   private static final String ENTRY_SEPARATOR = String.valueOf(',');
   private static final String SQUARE_OPEN_BRACKET = "[";
   private static final String SQUARE_CLOSE_BRACKET = "]";
   private static final String SEMICOLON = ";";
   private static final String SPACE = " ";
   private static final String CURLY_OPEN_BRACKET = "{";
   private static final String CURLY_CLOSE_BRACKET = "}";
   private static final String NEW_LINE = "\n";
   private final String prefix;
   private final int indentationLevel;
   private Component result = Component.literal("");

   public MyNbtTextFormatter(String prefix, int indentationLevel) {
      this.prefix = prefix;
      this.indentationLevel = indentationLevel;
   }

   public Component apply(Tag element) {
      element.accept(this);
      return this.result;
   }

   @Override
   public void visitString(StringTag element) {
      String string = StringTag.quoteAndEscape(element.value());
      String string2 = string.substring(0, 1);
      MutableComponent text = Component.literal(string.substring(1, string.length() - 1)).withStyle(STRING_COLOR);
      this.result = Component.literal(string2).append(text).append(string2);
   }

   @Override
   public void visitByte(ByteTag element) {
      if (element.value() == 0) {
         this.result = Component.literal("false").withStyle(NUMBER_COLOR);
      } else if (element.value() == 1) {
         this.result = Component.literal("true").withStyle(NUMBER_COLOR);
      } else {
         MutableComponent text = Component.literal("b").withStyle(TYPE_SUFFIX_COLOR);
         this.result = Component.literal(String.valueOf(element.value())).append(text).withStyle(NUMBER_COLOR);
      }
   }

   @Override
   public void visitShort(ShortTag element) {
      MutableComponent text = Component.literal("s").withStyle(TYPE_SUFFIX_COLOR);
      this.result = Component.literal(String.valueOf(element.value())).append(text).withStyle(NUMBER_COLOR);
   }

   @Override
   public void visitInt(IntTag element) {
      this.result = Component.literal(String.valueOf(element.value())).withStyle(NUMBER_COLOR);
   }

   @Override
   public void visitLong(LongTag element) {
      MutableComponent text = Component.literal("L").withStyle(TYPE_SUFFIX_COLOR);
      this.result = Component.literal(String.valueOf(element.value())).append(text).withStyle(NUMBER_COLOR);
   }

   @Override
   public void visitFloat(FloatTag element) {
      MutableComponent text = Component.literal("f").withStyle(TYPE_SUFFIX_COLOR);
      this.result = Component.literal(String.valueOf(element.value())).append(text).withStyle(NUMBER_COLOR);
   }

   @Override
   public void visitDouble(DoubleTag element) {
      MutableComponent text = Component.literal("d").withStyle(TYPE_SUFFIX_COLOR);
      this.result = Component.literal(String.valueOf(element.value())).append(text).withStyle(NUMBER_COLOR);
   }

   @Override
   public void visitByteArray(ByteArrayTag element) {
      MutableComponent text = Component.literal("B").withStyle(TYPE_SUFFIX_COLOR);
      MutableComponent mutableText = Component.literal("[").append(text).append(";");
      byte[] bs = element.getAsByteArray();

      for (int i = 0; i < bs.length; i++) {
         MutableComponent mutableText2 = Component.literal(String.valueOf(bs[i])).withStyle(NUMBER_COLOR);
         mutableText.append(" ").append(mutableText2).append(text);
         if (i != bs.length - 1) {
            mutableText.append(ENTRY_SEPARATOR);
         }
      }

      mutableText.append("]");
      this.result = mutableText;
   }

   @Override
   public void visitIntArray(IntArrayTag element) {
      MutableComponent text = Component.literal("I").withStyle(TYPE_SUFFIX_COLOR);
      MutableComponent mutableText = Component.literal("[").append(text).append(";");
      int[] is = element.getAsIntArray();

      for (int i = 0; i < is.length; i++) {
         mutableText.append(" ").append(Component.literal(String.valueOf(is[i])).withStyle(NUMBER_COLOR));
         if (i != is.length - 1) {
            mutableText.append(ENTRY_SEPARATOR);
         }
      }

      mutableText.append("]");
      this.result = mutableText;
   }

   @Override
   public void visitLongArray(LongArrayTag element) {
      MutableComponent text = Component.literal("L").withStyle(TYPE_SUFFIX_COLOR);
      MutableComponent mutableText = Component.literal("[").append(text).append(";");
      long[] ls = element.getAsLongArray();

      for (int i = 0; i < ls.length; i++) {
         MutableComponent text2 = Component.literal(String.valueOf(ls[i])).withStyle(NUMBER_COLOR);
         mutableText.append(" ").append(text2).append(text);
         if (i != ls.length - 1) {
            mutableText.append(ENTRY_SEPARATOR);
         }
      }

      mutableText.append("]");
      this.result = mutableText;
   }

   @Override
   public void visitList(ListTag element) {
      if (element.isEmpty()) {
         this.result = Component.literal("[]");
      } else if (element.size() <= 8 && element.stream().allMatch(t -> SINGLE_LINE_ELEMENT_TYPES.contains(t.getId()))) {
         String string = ENTRY_SEPARATOR + " ";
         MutableComponent mutableText = Component.literal("[");

         for (int i = 0; i < element.size(); i++) {
            if (i != 0) {
               mutableText.append(string);
            }

            mutableText.append(new MyNbtTextFormatter(this.prefix, this.indentationLevel).apply(element.get(i)));
         }

         mutableText.append("]");
         this.result = mutableText;
      } else {
         MutableComponent string = Component.literal("[");

         for (int mutableText = 0; mutableText < element.size(); mutableText++) {
            MutableComponent i = Component.literal(Strings.repeat(this.prefix, this.indentationLevel + 1));
            i.append(new MyNbtTextFormatter(this.prefix, this.indentationLevel + 1).apply(element.get(mutableText)));
            if (mutableText != element.size() - 1) {
               i.append(ENTRY_SEPARATOR).append(" ");
            }

            string.append(i);
         }

         if (!this.prefix.isEmpty()) {
            string.append("\n").append(Strings.repeat(this.prefix, this.indentationLevel));
         }

         string.append("]");
         this.result = string;
      }
   }

   @Override
   public void visitCompound(CompoundTag compound) {
      if (compound.isEmpty()) {
         this.result = Component.literal("{}");
      } else {
         MutableComponent mutableText = Component.literal("{");
         Collection<String> collection = compound.keySet();
         List<String> list = Lists.newArrayList(compound.keySet());
         Collections.sort(list);
         collection = list;
         if (!this.prefix.isEmpty()) {
            mutableText.append("\n");
         }

         Iterator<String> iterator = collection.iterator();

         while (iterator.hasNext()) {
            String string = iterator.next();
            MutableComponent mutableText2 = Component.literal(Strings.repeat(this.prefix, this.indentationLevel + 1))
               .append(escapeName(string))
               .append(KEY_VALUE_SEPARATOR)
               .append(" ")
               .append(new MyNbtTextFormatter(this.prefix, this.indentationLevel + 1).apply(compound.get(string)));
            if (iterator.hasNext()) {
               mutableText2.append(ENTRY_SEPARATOR).append(this.prefix.isEmpty() ? " " : "\n");
            }

            mutableText.append(mutableText2);
         }

         if (!this.prefix.isEmpty()) {
            mutableText.append("\n").append(Strings.repeat(this.prefix, this.indentationLevel));
         }

         mutableText.append("}");
         this.result = mutableText;
      }
   }

   protected static Component escapeName(String name) {
      if (SIMPLE_NAME.matcher(name).matches()) {
         return Component.literal(name).withStyle(NAME_COLOR);
      }

      String string = StringTag.quoteAndEscape(name);
      String string2 = string.substring(0, 1);
      MutableComponent text = Component.literal(string.substring(1, string.length() - 1)).withStyle(NAME_COLOR);
      return Component.literal(string2).append(text).append(string2);
   }

   @Override
   public void visitEnd(EndTag element) {
   }
}
