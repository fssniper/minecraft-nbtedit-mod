package nbtedit.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import nbtedit.client.nbt.NbtNode;
import nbtedit.client.nbt.NbtValues;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public class AddTagScreen extends Screen {
	private static final int FIELD_WIDTH = 260;

	private final Screen parent;
	private final NbtNode target;
	private final Consumer<Boolean> callback;
	private final LinearLayout layout = LinearLayout.vertical().spacing(6);
	private byte type = Tag.TAG_STRING;
	private @Nullable EditBox nameBox;
	private @Nullable EditBox valueBox;
	private @Nullable StringWidget errorLabel;

	public AddTagScreen(Screen parent, NbtNode target, Consumer<Boolean> callback) {
		super(Component.translatable("nbtedit.add.title"));
		this.parent = parent;
		this.target = target;
		this.callback = callback;
	}

	@Override
	protected void init() {
		this.layout.addChild(new StringWidget(this.title, this.font));
		List<Byte> types = new ArrayList<>();
		for (byte id : NbtValues.CREATABLE_TYPES) {
			types.add(id);
		}

		this.layout
			.addChild(
				CycleButton.<Byte>builder(id -> Component.literal(NbtValues.typeName(id)), Byte.valueOf(this.type))
					.withValues(types)
					.create(0, 0, FIELD_WIDTH, 20, Component.translatable("nbtedit.add.type"), (button, value) -> this.type = value)
			);
		if (this.target.acceptsKeys()) {
			Component nameLabel = Component.translatable("nbtedit.add.name");
			this.layout.addChild(new StringWidget(nameLabel, this.font));
			this.nameBox = this.layout.addChild(new EditBox(this.font, FIELD_WIDTH, 20, nameLabel));
		}

		Component valueLabel = Component.translatable("nbtedit.add.value");
		this.layout.addChild(new StringWidget(valueLabel, this.font));
		this.valueBox = this.layout.addChild(new EditBox(this.font, FIELD_WIDTH, 20, valueLabel));
		this.valueBox.setMaxLength(Short.MAX_VALUE);
		this.errorLabel = this.layout.addChild(new StringWidget(Component.empty(), this.font));
		LinearLayout buttons = this.layout.addChild(LinearLayout.horizontal().spacing(8));
		buttons.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.confirm()).width(126).build());
		buttons.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose()).width(126).build());
		this.layout.visitWidgets(this::addRenderableWidget);
		this.repositionElements();
	}

	@Override
	protected void repositionElements() {
		this.layout.arrangeElements();
		FrameLayout.centerInRectangle(this.layout, this.getRectangle());
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isConfirmation() && this.getFocused() instanceof EditBox) {
			this.confirm();
			return true;
		}

		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		this.callback.accept(false);
		this.minecraft.gui.setScreen(this.parent);
	}

	private void confirm() {
		String value = this.valueBox == null ? "" : this.valueBox.getValue();
		Tag tag = NbtValues.parse(this.type, value.isEmpty() ? defaultInput(this.type) : value);
		String name = this.nameBox == null ? null : this.nameBox.getValue().trim();
		if (tag == null || !this.target.addChild(name, tag)) {
			this.showError();
			return;
		}

		this.callback.accept(true);
		this.minecraft.gui.setScreen(this.parent);
	}

	private void showError() {
		if (this.errorLabel != null) {
			this.errorLabel.setMessage(Component.translatable("nbtedit.error.invalid_value").withColor(0xFFFF6060));
			this.repositionElements();
		}
	}

	private static String defaultInput(byte type) {
		return switch (type) {
			case Tag.TAG_BYTE, Tag.TAG_SHORT, Tag.TAG_INT, Tag.TAG_LONG, Tag.TAG_FLOAT, Tag.TAG_DOUBLE -> "0";
			default -> "";
		};
	}
}
