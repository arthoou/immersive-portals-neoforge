package qouteall.imm_ptl.core.compat.distant_horizons;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import qouteall.q_misc_util.platform.Platform;

public final class DhRenderStateGuard {
   private static final Logger LOGGER = LogManager.getLogger(DhRenderStateGuard.class);
   private static final String CLIENT_API = "com.seibel.distanthorizons.core.api.internal.ClientApi";
   private static Boolean present;
   private static boolean resolved;
   private static boolean unavailable;
   private static Object renderState;
   private static Field[] fields;
   private static Method[] copyMethods;

   private DhRenderStateGuard() {
   }

   @Nullable
   public static Object[] snapshot() {
      if (isPresent() && resolve()) {
         try {
            Object[] values = new Object[fields.length];

            for (int i = 0; i < fields.length; i++) {
               Object value = fields[i].get(renderState);
               if (value != null && copyMethods[i] != null) {
                  value = copyMethods[i].invoke(value);
               }

               values[i] = value;
            }

            return values;
         } catch (Throwable t) {
            giveUp(t);
            return null;
         }
      } else {
         return null;
      }
   }

   public static void restore(@Nullable Object[] values) {
      if (values != null && !unavailable) {
         try {
            for (int i = 0; i < fields.length; i++) {
               fields[i].set(renderState, values[i]);
            }
         } catch (Throwable t) {
            giveUp(t);
         }
      }
   }

   private static boolean isPresent() {
      if (present == null) {
         present = Platform.get().isModLoaded("distanthorizons");
      }

      return present;
   }

   private static boolean resolve() {
      if (resolved) {
         return !unavailable;
      }

      resolved = true;

      try {
         Class<?> clientApi = Class.forName("com.seibel.distanthorizons.core.api.internal.ClientApi", false, DhRenderStateGuard.class.getClassLoader());
         renderState = clientApi.getField("RENDER_STATE").get(null);
         Class<?> stateClass = renderState.getClass();
         List<Field> found = new ArrayList<>();
         List<Method> copies = new ArrayList<>();

         for (Field field : stateClass.getFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) {
               found.add(field);
               Method copy = null;

               try {
                  Method candidate = field.getType().getMethod("copy");
                  if (candidate.getReturnType() == field.getType()) {
                     copy = candidate;
                  }
               } catch (NoSuchMethodException var10) {
               }

               copies.add(copy);
            }
         }

         if (found.isEmpty()) {
            throw new IllegalStateException("no public fields on " + stateClass.getName());
         }

         fields = found.toArray(new Field[0]);
         copyMethods = copies.toArray(new Method[0]);
         return true;
      } catch (Throwable t) {
         giveUp(t);
         return false;
      }
   }

   private static void giveUp(Throwable t) {
      if (!unavailable) {
         unavailable = true;
         LOGGER.warn(
            "Distant Horizons' render state cannot be kept across portal renders (its internals differ from DH 3.3.3); its fade pass may use a portal's camera after portal views are drawn",
            t
         );
      }
   }
}
