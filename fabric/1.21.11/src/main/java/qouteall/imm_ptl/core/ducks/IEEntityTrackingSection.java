package qouteall.imm_ptl.core.ducks;

import java.util.function.Function;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityTypeTest;
import org.jetbrains.annotations.Nullable;

public interface IEEntityTrackingSection<T extends EntityAccess> {
   @Nullable
   <Sub extends T, R> R ip_traverse(EntityTypeTest<T, Sub> var1, Function<Sub, R> var2);
}
