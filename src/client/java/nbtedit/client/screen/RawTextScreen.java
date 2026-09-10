package nbtedit.client.screen;

import java.util.function.Predicate;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class RawTextScreen extends TextEditorScreen {
	private static final int MODE_BUTTON_WIDTH = 50;

	private final Predicate<String> acceptor;

	public RawTextScreen(Screen parent, Component title, String text, Predicate<String> acceptor) {
		super(parent, title, text);
		this.acceptor = acceptor;
	}

	@Override
	protected void addHeaderControls(LinearLayout header) {
		LinearLayout modes = header.addChild(LinearLayout.horizontal().spacing(4));
		modes.addChild(Button.builder(Component.translatable("nbtedit.button.mode_tree"), button -> this.confirm()).width(MODE_BUTTON_WIDTH).build());
		Button textButton = modes.addChild(Button.builder(Component.translatable("nbtedit.button.mode_text"), button -> {
		}).width(MODE_BUTTON_WIDTH).build());
		textButton.active = false;
	}

	@Override
	protected boolean apply(String text) {
		return this.acceptor.test(text);
	}

	@Override
	protected Component rejectedMessage() {
		return Component.translatable("nbtedit.error.invalid_json");
	}
}
