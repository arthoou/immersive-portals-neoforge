package qouteall.q_misc_util.mixin.client;

import java.util.Set;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientPacketListener.class)
public interface IEClientPacketListener_Misc {
   @Accessor("levels")
   void ip_setLevels(Set<ResourceKey<Level>> var1);
}
