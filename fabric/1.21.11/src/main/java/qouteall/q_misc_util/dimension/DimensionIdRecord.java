package qouteall.q_misc_util.dimension;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import qouteall.q_misc_util.MiscHelper;

@Deprecated
public class DimensionIdRecord {
   public static DimensionIdRecord serverRecord = new DimensionIdRecord();

   public ResourceKey<Level> getDim(int dimIntId) {
      return DimensionIntId.getServerMap(MiscHelper.getServer()).fromIntegerId(dimIntId);
   }
}
