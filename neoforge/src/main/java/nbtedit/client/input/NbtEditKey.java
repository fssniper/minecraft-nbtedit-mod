package nbtedit.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import nbtedit.client.screen.ReadOnlyBrowser;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

public final class NbtEditKey {
	private static final KeyMapping OPEN =
		new KeyMapping("key.nbtedit.open", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), KeyMapping.Category.MISC);

	private NbtEditKey() {
	}

	public static void register(RegisterKeyMappingsEvent event) {
		event.register(OPEN);
	}

	public static void tick(ClientTickEvent.Post event) {
		boolean pressed = false;
		while (OPEN.consumeClick()) {
			pressed = true;
		}

		Minecraft minecraft = Minecraft.getInstance();
		if (pressed && minecraft.gui.screen() == null) {
			ReadOnlyBrowser.open(minecraft);
		}
	}
}
