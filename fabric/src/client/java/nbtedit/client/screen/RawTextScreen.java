package nbtedit.client.screen;

import java.util.function.Predicate;
import nbtedit.client.widget.IconButton;
import nbtedit.client.widget.Icons;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class RawTextScreen extends TextEditorScreen {
	private final Predicate<String> acceptor;

	public RawTextScreen(Screen parent, Component title, String text, Predicate<String> acceptor) {
		super(parent, title, text);
		this.acceptor = acceptor;
	}

	@Override
	protected void addHeaderControls(LinearLayout header) {
		LinearLayout modes = header.addChild(LinearLayout.horizontal().spacing(4));
		modes.addChild(new IconButton(Icons.MODE_TREE, Component.translatable("nbtedit.button.mode_tree"), button -> this.confirm()));
		Button textButton = modes.addChild(new IconButton(Icons.MODE_TEXT, Component.translatable("nbtedit.button.mode_text"), button -> {
		}));
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
