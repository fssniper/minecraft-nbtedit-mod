package nbtedit.client.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import nbtedit.client.screen.WorldBrowserScreen;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FramerateLimitTracker.class)
public abstract class FramerateLimitTrackerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	// Menus outside a world are held at 60 fps, which makes the disclaimer marquee judder on faster screens.
	@Inject(method = "getThrottleReason", at = @At("RETURN"), cancellable = true)
	private void nbtedit$unthrottleMarquee(CallbackInfoReturnable<FramerateLimitTracker.FramerateThrottleReason> info) {
		if (info.getReturnValue() == FramerateLimitTracker.FramerateThrottleReason.OUT_OF_LEVEL_MENU
			&& this.minecraft.gui.screen() instanceof WorldBrowserScreen browser
			&& browser.bannerShown()) {
			info.setReturnValue(FramerateLimitTracker.FramerateThrottleReason.NONE);
		}
	}
}
