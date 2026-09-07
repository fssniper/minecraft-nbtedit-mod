package nbtedit.client.screen;

import com.google.gson.JsonElement;
import java.util.List;
import java.util.function.Consumer;
import nbtedit.client.json.JsonNode;
import nbtedit.client.json.JsonValues;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public class AddJsonValueScreen extends Screen {
	private static final int FIELD_WIDTH = 260;

	private final Screen parent;
	private final JsonNode target;
	private final Consumer<Boolean> callback;
	private final LinearLayout layout = LinearLayout.vertical().spacing(6);
	private JsonValues.Kind kind = JsonValues.Kind.STRING;
	private @Nullable EditBox nameBox;
	private @Nullable EditBox valueBox;
	private @Nullable StringWidget errorLabel;

	public AddJsonValueScreen(Screen parent, JsonNode target, Consumer<Boolean> callback) {
		super(Component.translatable("nbtedit.add_json.title"));
		this.parent = parent;
		this.target = target;
		this.callback = callback;
	}

	@Override
	protected void init() {
		this.layout.addChild(new StringWidget(this.title, this.font));
		this.layout
			.addChild(
				CycleButton.<JsonValues.Kind>builder(value -> Component.literal(JsonValues.typeName(value)), this.kind)
					.withValues(List.of(JsonValues.Kind.values()))
					.create(0, 0, FIELD_WIDTH, 20, Component.translatable("nbtedit.add.type"), (button, value) -> this.kind = value)
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
	public void onClose() {
		this.callback.accept(false);
		this.minecraft.gui.setScreen(this.parent);
	}

	private void confirm() {
		String value = this.valueBox == null ? "" : this.valueBox.getValue();
		JsonElement element = JsonValues.parse(this.kind, value.isEmpty() ? defaultInput(this.kind) : value);
		String name = this.nameBox == null ? null : this.nameBox.getValue().trim();
		if (element == null || !this.target.addChild(name, element)) {
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

	private static String defaultInput(JsonValues.Kind kind) {
		return switch (kind) {
			case NUMBER -> "0";
			case BOOLEAN -> "false";
			default -> "";
		};
	}
}
