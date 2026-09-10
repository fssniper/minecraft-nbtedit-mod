package nbtedit.client.screen;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public abstract class TextEditorScreen extends Screen {
	protected static final int ERROR_COLOR = 0xFFFF6060;

	private static final int MAX_CHARACTERS = 4_000_000;
	private static final int MAX_WIDTH = 420;
	private static final int BUTTON_WIDTH = 126;
	private static final int HEADER_HEIGHT = 8 + 9 + 8 + 20 + 4;
	private static final int FOOTER_HEIGHT = 60;

	private final Screen parent;
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, HEADER_HEIGHT, FOOTER_HEIGHT);
	private final String initialText;
	private String text;
	private @Nullable MultiLineEditBox editBox;
	private @Nullable StringWidget messageLabel;

	protected TextEditorScreen(Screen parent, Component title, String text) {
		super(title);
		this.parent = parent;
		this.initialText = text;
		this.text = text;
	}

	/** Returns false when the text was rejected, which keeps the screen open. */
	protected abstract boolean apply(String text);

	protected Component rejectedMessage() {
		return Component.translatable("nbtedit.error.invalid_value");
	}

	protected void addHeaderControls(LinearLayout header) {
	}

	protected final String text() {
		return this.text;
	}

	protected final boolean changed() {
		return !this.text.equals(this.initialText);
	}

	protected final Screen parent() {
		return this.parent;
	}

	protected final void showMessage(Component message) {
		if (this.messageLabel != null) {
			this.messageLabel.setMessage(message);
			this.repositionElements();
		}
	}

	@Override
	protected void init() {
		LinearLayout header = this.layout.addToHeader(LinearLayout.vertical().spacing(4));
		header.defaultCellSetting().alignHorizontallyCenter();
		header.addChild(new StringWidget(this.title, this.font));
		this.addHeaderControls(header);
		int boxWidth = Math.min(this.width - 40, MAX_WIDTH);
		MultiLineEditBox box = MultiLineEditBox.builder().build(this.font, boxWidth, this.layout.getContentHeight(), this.title);
		box.setCharacterLimit(MAX_CHARACTERS);
		box.setValue(this.text);
		box.setValueListener(value -> this.text = value);
		this.editBox = this.layout.addToContents(box);
		LinearLayout footer = this.layout.addToFooter(LinearLayout.vertical().spacing(4));
		footer.defaultCellSetting().alignHorizontallyCenter();
		this.messageLabel = footer.addChild(new StringWidget(Component.empty(), this.font));
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

	protected final void confirm() {
		if (this.apply(this.text)) {
			this.minecraft.gui.setScreen(this.parent);
		} else {
			this.showMessage(this.rejectedMessage().copy().withColor(ERROR_COLOR));
		}
	}
}
