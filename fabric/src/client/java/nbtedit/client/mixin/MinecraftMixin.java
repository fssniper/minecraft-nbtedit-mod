package nbtedit.client.mixin;

import nbtedit.client.input.NbtEditKey;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void nbtedit$tickKey(CallbackInfo info) {
		NbtEditKey.tick((Minecraft)(Object)this);
	}
}
