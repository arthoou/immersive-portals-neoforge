package qouteall.imm_ptl.core.mixin.client.accessor;

import net.minecraft.client.multiplayer.ClientLevel.ClientLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientLevelData.class)
public interface IEClientLevelData {
   @Accessor("isFlat")
   boolean ip_getIsFlat();
}
