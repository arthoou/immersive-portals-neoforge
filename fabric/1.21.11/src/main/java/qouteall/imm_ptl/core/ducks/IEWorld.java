package qouteall.imm_ptl.core.ducks;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.storage.WritableLevelData;

public interface IEWorld {
   WritableLevelData ip_getLevelData();

   void portal_setWeather(float var1, float var2, float var3, float var4);

   LevelEntityGetter<Entity> portal_getEntityLookup();

   Thread portal_getThread();
}
