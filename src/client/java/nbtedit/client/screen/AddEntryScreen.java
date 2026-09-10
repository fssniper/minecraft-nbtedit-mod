package nbtedit.client.screen;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import nbtedit.client.widget.DropdownWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public class AddEntryScreen<K> extends Screen {
	@FunctionalInterface
	public interface Inserter<K> {
		boolean insert(K kind, @Nullable String name);
	}

	private static final Identifier PANEL_SPRITE = Identifier.withDefaultNamespace("popup/background");
	private static final int PANEL_BORDER = 18;
	private static final int FIELD_WIDTH = 220;
	private static final int BUTTON_WIDTH = 106;
	private static final int PATH_COLOR = 0xFF9A9A9A;
	private static final int ERROR_COLOR = 0xFFFF6060;

	private final Screen background;
	private final Component path;
	private final List<K> kinds;
	private final Function<K, Component> kindLabel;
	private final boolean named;
	private final Predicate<String> nameFree;
	private final Inserter<K> inserter;
	private final LinearLayout layout = LinearLayout.vertical().spacing(6);
	private K kind;
	private @Nullable DropdownWidget<K> dropdown;
	private @Nullable EditBox nameBox;
	private @Nullable Button confirmButton;
	private @Nullable StringWidget hint;

	public AddEntryScreen(
		Screen background, Component path, List<K> kinds, K kind, Function<K, Component> kindLabel, boolean named, Predicate<String> nameFree, Inserter<K> inserter
	) {
		super(Component.translatable("nbtedit.add.title"));
		this.background = background;
		this.path = path;
		this.kinds = kinds;
		this.kind = kind;
		this.kindLabel = kindLabel;
		this.named = named;
		this.nameFree = nameFree;
		this.inserter = inserter;
	}

	@Override
	protected void init() {
		this.layout.addChild(new StringWidget(this.title, this.font));
		StringWidget pathWidget = this.layout.addChild(new StringWidget(this.path.copy().withColor(PATH_COLOR), this.font));
		pathWidget.setMaxWidth(FIELD_WIDTH);
		this.dropdown = this.layout
			.addChild(new DropdownWidget<>(FIELD_WIDTH, 20, this.kinds, this.kind, this.kindLabel, selected -> this.kind = selected));
		if (this.named) {
			this.nameBox = this.layout.addChild(new EditBox(this.font, FIELD_WIDTH, 20, Component.translatable("nbtedit.add.name")));
			this.nameBox.setHint(Component.translatable("nbtedit.add.name").copy().setStyle(EditBox.DEFAULT_HINT_STYLE));
			this.nameBox.setResponder(value -> this.validate());
		} else {
			this.layout.addChild(new StringWidget(Component.translatable("nbtedit.add.appended").withColor(PATH_COLOR), this.font));
		}

		this.hint = this.layout.addChild(new StringWidget(Component.empty(), this.font));
		LinearLayout buttons = this.layout.addChild(LinearLayout.horizontal().spacing(8));
		this.confirmButton = buttons.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.confirm()).width(BUTTON_WIDTH).build());
		buttons.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose()).width(BUTTON_WIDTH).build());
		this.layout.visitWidgets(this::addRenderableWidget);
		this.repositionElements();
		this.validate();
	}

	@Override
	protected void setInitialFocus() {
		if (this.nameBox != null) {
			this.setInitialFocus(this.nameBox);
		} else if (this.dropdown != null) {
			this.setInitialFocus(this.dropdown);
		}
	}

	@Override
	protected void repositionElements() {
		this.layout.arrangeElements();
		FrameLayout.centerInRectangle(this.layout, this.getRectangle());
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		this.background.extractBackground(graphics, mouseX, mouseY, a);
		graphics.nextStratum();
		this.background.extractRenderState(graphics, -1, -1, a);
		graphics.nextStratum();
		this.extractTransparentBackground(graphics);
		graphics.blitSprite(
			RenderPipelines.GUI_TEXTURED,
			PANEL_SPRITE,
			this.layout.getX() - PANEL_BORDER,
			this.layout.getY() - PANEL_BORDER,
			this.layout.getWidth() + PANEL_BORDER * 2,
			this.layout.getHeight() + PANEL_BORDER * 2
		);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		if (this.dropdown != null) {
			this.dropdown.extractOpenList(graphics, mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (this.dropdown != null && this.dropdown.isOpen() && !this.dropdown.isMouseOver(event.x(), event.y())) {
			this.dropdown.close();
			return true;
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isConfirmation() && this.getFocused() instanceof EditBox && this.confirmButton != null && this.confirmButton.active) {
			this.confirm();
			return true;
		}

		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.background);
	}

	private String name() {
		return this.nameBox == null ? "" : this.nameBox.getValue().trim();
	}

	private void validate() {
		if (this.confirmButton == null || this.hint == null) {
			return;
		}

		if (!this.named) {
			this.confirmButton.active = true;
			return;
		}

		String name = this.name();
		if (name.isEmpty()) {
			this.showHint(Component.translatable("nbtedit.error.empty_name"), false);
		} else if (!this.nameFree.test(name)) {
			this.showHint(Component.translatable("nbtedit.error.duplicate_name"), false);
		} else {
			this.showHint(Component.empty(), true);
		}
	}

	private void showHint(Component message, boolean valid) {
		if (this.confirmButton == null || this.hint == null) {
			return;
		}

		this.confirmButton.active = valid;
		this.hint.setMessage(valid ? message : message.copy().withColor(ERROR_COLOR));
		this.repositionElements();
	}

	private void confirm() {
		if (this.inserter.insert(this.kind, this.named ? this.name() : null)) {
			this.minecraft.gui.setScreen(this.background);
		} else {
			this.showHint(Component.translatable("nbtedit.error.invalid_value"), false);
		}
	}
}
