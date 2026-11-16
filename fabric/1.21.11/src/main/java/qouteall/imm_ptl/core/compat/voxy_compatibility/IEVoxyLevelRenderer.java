package qouteall.imm_ptl.core.compat.voxy_compatibility;

import org.jetbrains.annotations.Nullable;

public interface IEVoxyLevelRenderer {
   boolean ip_voxyIsCreationPending();

   void ip_voxySetCreationPending(boolean var1);

   @Nullable
   Object ip_voxyBoundIrisPipeline();

   void ip_voxySetBoundIrisPipeline(@Nullable Object var1);
}
