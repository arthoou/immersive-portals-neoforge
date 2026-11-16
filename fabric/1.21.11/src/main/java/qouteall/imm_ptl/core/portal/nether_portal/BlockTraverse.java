package qouteall.imm_ptl.core.portal.nether_portal;

import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import qouteall.q_misc_util.my_util.IntBox;

public class BlockTraverse {
   public static <T> T searchFromTo(int from, int to, BlockTraverse.IntFunc<T> func) {
      if (from > to) {
         for (int i = from; i >= to; i--) {
            T obj = func.eval(i);
            if (obj != null) {
               return obj;
            }
         }
      } else {
         for (int i = from; i <= to; i++) {
            T obj = func.eval(i);
            if (obj != null) {
               return obj;
            }
         }
      }

      return null;
   }

   public static <T> T searchOnPlane(int centerX, int centerZ, int range, BlockTraverse.BiIntFunc<T> func) {
      T centerResult = func.eval(centerX, centerZ);
      if (centerResult != null) {
         return centerResult;
      }

      for (int layer = 1; layer < range; layer++) {
         for (int w = 0; w < layer * 2; w++) {
            T obj = func.eval(layer + centerX, w + 1 - layer + centerZ);
            if (obj != null) {
               return obj;
            }
         }

         for (int w = 0; w < layer * 2; w++) {
            T obj = func.eval(-w + layer - 1 + centerX, layer + centerZ);
            if (obj != null) {
               return obj;
            }
         }

         for (int w = 0; w < layer * 2; w++) {
            T obj = func.eval(-layer + centerX, -w + layer - 1 + centerZ);
            if (obj != null) {
               return obj;
            }
         }

         for (int w = 0; w < layer * 2; w++) {
            T obj = func.eval(w + 1 - layer + centerX, -layer + centerZ);
            if (obj != null) {
               return obj;
            }
         }
      }

      return null;
   }

   public static <T> T searchColumnedRaw(int centerX, int centerZ, int range, int startY, int endY, BlockTraverse.TriIntFunc<T> func) {
      return searchOnPlane(centerX, centerZ, range, (x, z) -> searchFromTo(startY, endY, y -> func.eval(x, y, z)));
   }

   public static <T> T searchColumned(int centerX, int centerZ, int range, int startY, int endY, Function<BlockPos, T> func) {
      MutableBlockPos temp = new MutableBlockPos();
      return searchColumnedRaw(centerX, centerZ, range, startY, endY, (x, y, z) -> {
         temp.set(x, y, z);
         return func.apply(temp);
      });
   }

   public static <T> T searchInBox(IntBox box, BlockTraverse.TriIntFunc<T> func) {
      for (int x = box.l.getX(); x <= box.h.getX(); x++) {
         for (int y = box.l.getY(); y <= box.h.getY(); y++) {
            for (int z = box.l.getZ(); z <= box.h.getZ(); z++) {
               T obj = func.eval(x, y, z);
               if (obj != null) {
                  return obj;
               }
            }
         }
      }

      return null;
   }

   public static <T> T searchInBox(IntBox box, Function<BlockPos, T> func) {
      MutableBlockPos temp = new MutableBlockPos();
      return searchInBox(box, (x, y, z) -> {
         temp.set(x, y, z);
         return func.apply(temp);
      });
   }

   public static boolean boxAllMatch(IntBox box, Predicate<BlockPos> predicate) {
      Boolean result = searchInBox(box, mutable -> predicate.test(mutable) ? Boolean.TRUE : null);
      return result != null;
   }

   public interface BiIntFunc<T> {
      T eval(int var1, int var2);
   }

   public interface IntFunc<T> {
      T eval(int var1);
   }

   public interface TriIntFunc<T> {
      T eval(int var1, int var2, int var3);
   }
}
