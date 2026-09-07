package nbtedit.client.screen;

import java.util.function.Predicate;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public class RawTextScreen extends Screen {
	private static final int MAX_CHARACTERS = 4_000_000;
	private static final int MAX_WIDTH = 420;
	private static final int BUTTON_WIDTH = 126;
	private static final int MODE_BUTTON_WIDTH = 50;
	private static final int HEADER_HEIGHT = 8 + 9 + 8 + 20 + 4;

	private final Screen parent;
	private final Predicate<String> acceptor;
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, HEADER_HEIGHT, 60);
	private String text;
	private @Nullable MultiLineEditBox editBox;
	private @Nullable StringWidget errorLabel;

	public RawTextScreen(Screen parent, Component title, String text, Predicate<String> acceptor) {
		super(title);
		this.parent = parent;
		this.text = text;
		this.acceptor = acceptor;
	}

	@Override
	protected void init() {
		LinearLayout header = this.layout.addToHeader(LinearLayout.vertical().spacing(4));
		header.defaultCellSetting().alignHorizontallyCenter();
		header.addChild(new StringWidget(this.title, this.font));
		LinearLayout modes = header.addChild(LinearLayout.horizontal().spacing(4));
		modes.addChild(Button.builder(Component.translatable("nbtedit.button.mode_tree"), button -> this.confirm()).width(MODE_BUTTON_WIDTH).build());
		Button textButton = modes.addChild(Button.builder(Component.translatable("nbtedit.button.mode_text"), button -> {
		}).width(MODE_BUTTON_WIDTH).build());
		textButton.active = false;
		int boxWidth = Math.min(this.width - 40, MAX_WIDTH);
		MultiLineEditBox box = MultiLineEditBox.builder().build(this.font, boxWidth, this.layout.getContentHeight(), this.title);
		box.setCharacterLimit(MAX_CHARACTERS);
		box.setValue(this.text);
		box.setValueListener(value -> this.text = value);
		this.editBox = this.layout.addToContents(box);
		LinearLayout footer = this.layout.addToFooter(LinearLayout.vertical().spacing(4));
		footer.defaultCellSetting().alignHorizontallyCenter();
		this.errorLabel = footer.addChild(new StringWidget(Component.empty(), this.font));
		LinearLayout buttons = footer.addChild(LinearLayout.horizontal().spacing(8));
		buttons.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.confirm()).width(BUTTON_WIDTH).build());
		buttons.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose()).width(BUTTON_WIDTH).build());
		this.layout.visitWidgets(this::addRenderableWidget);
		this.repositionElements();
	}

	@Override
	protected void setInitialFocus() {
		if (this.editBox != null) {
			this.setInitialFocus(this.editBox);
		}
	}

	@Override
	protected void repositionElements() {
		this.layout.arrangeElements();
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	private void confirm() {
		if (this.acceptor.test(this.text)) {
			this.minecraft.gui.setScreen(this.parent);
		} else if (this.errorLabel != null) {
			this.errorLabel.setMessage(Component.translatable("nbtedit.error.invalid_json").withColor(0xFFFF6060));
			this.repositionElements();
		}
	}
}
