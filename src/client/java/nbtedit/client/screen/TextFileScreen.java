package nbtedit.client.screen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import nbtedit.NBTEdit;
import nbtedit.client.config.NbtEditConfig;
import nbtedit.client.io.SafeWrite;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class TextFileScreen extends TextEditorScreen {
	public static final int MAX_SIZE = 2 * 1024 * 1024;

	private final Path path;
	private boolean saved;

	private TextFileScreen(Screen parent, Path path, String text) {
		super(parent, Component.literal(path.getFileName().toString()), text);
		this.path = path;
	}

	public static TextFileScreen load(Screen parent, Path path) throws IOException {
		long size = Files.size(path);
		if (size > MAX_SIZE) {
			throw new IOException("File is too large to edit as text: " + size + " bytes");
		}

		String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
		return new TextFileScreen(parent, path, text);
	}

	@Override
	protected boolean apply(String text) {
		return this.write(text);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.hasControlDown() && event.key() == GLFW.GLFW_KEY_S) {
			this.write(this.text());
			return true;
		}

		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		if (!this.changed() || this.saved) {
			super.onClose();
			return;
		}

		this.minecraft.gui
			.setScreen(
				new ConfirmScreen(
					saveFirst -> {
						if (saveFirst) {
							this.write(this.text());
						}

						this.minecraft.gui.setScreen(this.parent());
					},
					Component.translatable("nbtedit.unsaved.title"),
					Component.translatable("nbtedit.unsaved.message"),
					Component.translatable("nbtedit.button.save"),
					Component.translatable("nbtedit.button.discard")
				)
			);
	}

	private boolean write(String text) {
		try {
			Path backup = SafeWrite.replace(
				this.path, NbtEditConfig.get().keptBackups(), target -> Files.writeString(target, text, StandardCharsets.UTF_8)
			);
			this.saved = true;
			Component message = backup == null
				? Component.literal(this.path.getFileName().toString())
				: Component.translatable("nbtedit.toast.backup", backup.getFileName().toString());
			this.minecraft.gui.toastManager().addToast(new SystemToast(SystemToast.SystemToastId.WORLD_BACKUP, Component.translatable("nbtedit.toast.saved"), message));
			return true;
		} catch (IOException e) {
			NBTEdit.LOGGER.error("Failed to save {}", this.path, e);
			this.showMessage(Component.translatable("nbtedit.toast.save_failed").withColor(ERROR_COLOR));
			return false;
		}
	}
}
