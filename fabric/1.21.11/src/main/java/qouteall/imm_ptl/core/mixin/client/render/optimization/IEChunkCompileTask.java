package qouteall.imm_ptl.core.mixin.client.render.optimization;

import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection.CompileTask;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CompileTask.class)
public interface IEChunkCompileTask {
   @Accessor("isCancelled")
   AtomicBoolean getIsCancelled();
}
