package qouteall.dimlib;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public class DimLibUtil {
   public static final String MODID = "dimlib";
   public static final Gson GSON;

   public static long secondToNano(double second) {
      return (long)(second * 1.0E9);
   }

   public static double nanoToSecond(long l) {
      return l / 1.0E9;
   }

   static {
      GsonBuilder gsonBuilder = new GsonBuilder();
      gsonBuilder.setPrettyPrinting();
      gsonBuilder.registerTypeAdapter((new TypeToken<ResourceKey<Level>>() {}).getType(), new DimLibUtil.DimensionIDJsonAdapter());
      GSON = gsonBuilder.create();
   }

   private static class DimensionIDJsonAdapter implements JsonSerializer<ResourceKey<Level>>, JsonDeserializer<ResourceKey<Level>> {
      public ResourceKey<Level> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
         String str = json.getAsString();
         return ResourceKey.create(Registries.DIMENSION, Identifier.parse(str));
      }

      public JsonElement serialize(ResourceKey<Level> src, Type typeOfSrc, JsonSerializationContext context) {
         return new JsonPrimitive(src.identifier().toString());
      }
   }
}
