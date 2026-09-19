package nbtedit.client.screen;

import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;

public final class ReadOnlyBrowser {
	private ReadOnlyBrowser() {
	}

	public static boolean open(Minecraft minecraft) {
		IntegratedServer server = minecraft.getSingleplayerServer();
		if (server == null || !minecraft.isLocalServer()) {
			return false;
		}

		Path worldRoot = server.getWorldPath(LevelResource.ROOT);
		String levelId = worldRoot.getFileName().toString();
		minecraft.schedule(() -> minecraft.gui.setScreen(new WorldBrowserScreen(worldRoot, levelId, true, () -> minecraft.gui.setScreen(null))));
		return true;
	}
}
