package nbtedit.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import nbtedit.client.screen.ReadOnlyBrowser;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

public final class NbtEditKey {
	private static @Nullable KeyMapping open;

	private NbtEditKey() {
	}

	public static void register() {
		open = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.nbtedit.open", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), KeyMapping.Category.MISC)
		);
	}

	public static void tick(Minecraft minecraft) {
		KeyMapping mapping = open;
		if (mapping == null) {
			return;
		}

		boolean pressed = false;
		while (mapping.consumeClick()) {
			pressed = true;
		}

		if (pressed && minecraft.gui.screen() == null) {
			ReadOnlyBrowser.open(minecraft);
		}
	}
}
