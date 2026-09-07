package nbtedit.client.screen;

import java.util.function.Predicate;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public class TextInputScreen extends Screen {
	private static final int FIELD_WIDTH = 260;

	private final Screen parent;
	private final Component prompt;
	private final String initialValue;
	private final Predicate<String> acceptor;
	private final LinearLayout layout = LinearLayout.vertical().spacing(6);
	private @Nullable EditBox input;
	private @Nullable StringWidget errorLabel;

	public TextInputScreen(Screen parent, Component title, Component prompt, String initialValue, Predicate<String> acceptor) {
		super(title);
		this.parent = parent;
		this.prompt = prompt;
		this.initialValue = initialValue;
		this.acceptor = acceptor;
	}

	@Override
	protected void init() {
		this.layout.addChild(new StringWidget(this.title, this.font));
		this.layout.addChild(new StringWidget(this.prompt, this.font));
		this.input = this.layout.addChild(new EditBox(this.font, FIELD_WIDTH, 20, this.prompt));
		this.input.setMaxLength(Short.MAX_VALUE);
		this.input.setValue(this.initialValue);
		this.errorLabel = this.layout.addChild(new StringWidget(Component.empty(), this.font));
		LinearLayout buttons = this.layout.addChild(LinearLayout.horizontal().spacing(8));
		buttons.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.confirm()).width(126).build());
		buttons.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose()).width(126).build());
		this.layout.visitWidgets(this::addRenderableWidget);
		this.repositionElements();
	}

	@Override
	protected void setInitialFocus() {
		if (this.input != null) {
			this.setInitialFocus(this.input);
		}
	}

	@Override
	protected void repositionElements() {
		this.layout.arrangeElements();
		FrameLayout.centerInRectangle(this.layout, this.getRectangle());
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isConfirmation()) {
			this.confirm();
			return true;
		}

		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	private void confirm() {
		if (this.input == null) {
			return;
		}

		if (this.acceptor.test(this.input.getValue())) {
			this.minecraft.gui.setScreen(this.parent);
		} else if (this.errorLabel != null) {
			this.errorLabel.setMessage(Component.translatable("nbtedit.error.invalid_value").withColor(0xFFFF6060));
			this.repositionElements();
		}
	}
}
