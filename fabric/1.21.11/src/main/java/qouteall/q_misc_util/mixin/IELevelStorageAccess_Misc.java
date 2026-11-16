package qouteall.q_misc_util.mixin;

import net.minecraft.world.level.storage.LevelStorageSource.LevelDirectory;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelStorageAccess.class)
public interface IELevelStorageAccess_Misc {
   @Accessor("levelDirectory")
   LevelDirectory ip_getLevelPath();
}
