package nbtedit.client.screen;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import nbtedit.client.region.ChunkDocument;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class ChunkEditScreen extends NbtTreeScreen {
	private static boolean warned;

	private final Runnable onSaved;

	private ChunkEditScreen(Screen parent, ChunkDocument document, Runnable onSaved) {
		super(parent, document);
		this.onSaved = onSaved;
	}

	public static void open(Screen parent, Path regionFile, int chunkX, int chunkZ, Runnable onSaved) throws IOException {
		open(parent, regionFile, chunkX, chunkZ, onSaved, List.of());
	}

	public static void open(Screen parent, Path regionFile, int chunkX, int chunkZ, Runnable onSaved, List<String> reveal) throws IOException {
		ChunkEditScreen screen = new ChunkEditScreen(parent, ChunkDocument.load(regionFile, chunkX, chunkZ), onSaved);
		if (!reveal.isEmpty()) {
			screen.revealAfterOpen(reveal);
		}

		Minecraft minecraft = Minecraft.getInstance();
		if (warned) {
			minecraft.gui.setScreen(screen);
			return;
		}

		minecraft.gui
			.setScreen(
				new ConfirmScreen(
					accepted -> {
						warned |= accepted;
						minecraft.gui.setScreen(accepted ? screen : parent);
					},
					Component.translatable("nbtedit.chunk.warning.title"),
					Component.translatable("nbtedit.chunk.warning.message"),
					CommonComponents.GUI_PROCEED,
					CommonComponents.GUI_CANCEL
				)
			);
	}

	@Override
	protected boolean writeFile() {
		boolean written = super.writeFile();
		if (written) {
			this.onSaved.run();
		}

		return written;
	}
}
