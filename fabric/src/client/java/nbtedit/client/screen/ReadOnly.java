package nbtedit.client.screen;

import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

public interface ReadOnly {
	boolean readOnly();

	static boolean of(@Nullable Screen parent) {
		return parent instanceof ReadOnly screen && screen.readOnly();
	}
}
