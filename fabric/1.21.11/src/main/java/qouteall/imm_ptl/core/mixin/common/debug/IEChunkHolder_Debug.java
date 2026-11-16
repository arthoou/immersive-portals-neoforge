package qouteall.imm_ptl.core.mixin.common.debug;

import java.util.concurrent.Executor;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkHolder.class)
public interface IEChunkHolder_Debug {
   @Invoker("updateFutures")
   void ip_updateFutures(ChunkMap var1, Executor var2);
}
