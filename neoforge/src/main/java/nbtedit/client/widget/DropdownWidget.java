package nbtedit.client.widget;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;

public class DropdownWidget<T> extends AbstractWidget {
	private static final WidgetSprites SPRITES = new WidgetSprites(
		Identifier.withDefaultNamespace("widget/button"),
		Identifier.withDefaultNamespace("widget/button_disabled"),
		Identifier.withDefaultNamespace("widget/button_highlighted")
	);
	private static final int ROW_HEIGHT = 14;
	private static final int PADDING = 3;
	private static final int PANEL_COLOR = 0xF0100010;
	private static final int BORDER_COLOR = 0xFF6E6E6E;
	private static final int HIGHLIGHT_COLOR = 0xFF3A6EA5;
	private static final int ARROW_COLOR = 0xFFC0C0C0;
	private static final int ARROW_WIDTH = 8;
	private static final int ARROW_HEIGHT = 4;

	private final List<T> values;
	private final Function<T, Component> label;
	private final Consumer<T> onSelect;
	private T value;
	private boolean open;
	private int highlighted;

	public DropdownWidget(int width, int height, List<T> values, T value, Function<T, Component> label, Consumer<T> onSelect) {
		super(0, 0, width, height, label.apply(value));
		this.values = List.copyOf(values);
		this.value = value;
		this.label = label;
		this.onSelect = onSelect;
		this.highlighted = Math.max(0, this.values.indexOf(value));
	}

	public T value() {
		return this.value;
	}

	public boolean isOpen() {
		return this.open;
	}

	public void close() {
		this.open = false;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		graphics.blitSprite(
			RenderPipelines.GUI_TEXTURED,
			SPRITES.get(this.active, this.isHoveredOrFocused() || this.open),
			this.getX(),
			this.getY(),
			this.getWidth(),
			this.getHeight(),
			ARGB.white(this.alpha)
		);
		Font font = Minecraft.getInstance().font;
		int textY = this.getY() + (this.getHeight() - 9) / 2 + 1;
		graphics.text(font, this.label.apply(this.value), this.getX() + PADDING + 2, textY, -1);
		extractArrow(graphics, this.getRight() - PADDING - ARROW_WIDTH, this.getY() + (this.getHeight() - ARROW_HEIGHT) / 2, !this.open, ARROW_COLOR);
	}

	public void extractOpenList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (!this.open) {
			return;
		}

		graphics.nextStratum();
		Font font = Minecraft.getInstance().font;
		int listHeight = this.listHeight();
		int top = this.listTop();
		graphics.fill(this.getX(), top, this.getRight(), top + listHeight, PANEL_COLOR);
		graphics.outline(this.getX(), top, this.getWidth(), listHeight, BORDER_COLOR);
		this.highlighted = this.indexAt(mouseX, mouseY, this.highlighted);
		for (int i = 0; i < this.values.size(); i++) {
			int rowTop = top + 1 + i * ROW_HEIGHT;
			if (i == this.highlighted) {
				graphics.fill(this.getX() + 1, rowTop, this.getRight() - 1, rowTop + ROW_HEIGHT, HIGHLIGHT_COLOR);
			}

			graphics.text(font, this.label.apply(this.values.get(i)), this.getX() + PADDING + 2, rowTop + (ROW_HEIGHT - 9) / 2 + 1, -1);
		}
	}

	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		if (super.isMouseOver(mouseX, mouseY)) {
			return true;
		}

		if (!this.open) {
			return false;
		}

		int top = this.listTop();
		return mouseX >= this.getX() && mouseX < this.getRight() && mouseY >= top && mouseY < top + this.listHeight();
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		if (!this.open) {
			this.open = true;
			this.highlighted = Math.max(0, this.values.indexOf(this.value));
			return;
		}

		int index = this.indexAt((int)event.x(), (int)event.y(), -1);
		if (index >= 0) {
			this.select(index);
		}

		this.open = false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (!this.open) {
			if (event.isSelection()) {
				this.open = true;
				this.highlighted = Math.max(0, this.values.indexOf(this.value));
				return true;
			}

			return false;
		}

		if (event.isDown()) {
			this.highlighted = Math.floorMod(this.highlighted + 1, this.values.size());
			return true;
		}

		if (event.isUp()) {
			this.highlighted = Math.floorMod(this.highlighted - 1, this.values.size());
			return true;
		}

		if (event.isSelection()) {
			this.select(this.highlighted);
			this.open = false;
			return true;
		}

		if (event.isEscape()) {
			this.open = false;
			return true;
		}

		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY == 0.0) {
			return false;
		}

		int step = scrollY > 0.0 ? -1 : 1;
		if (this.open) {
			this.highlighted = Math.floorMod(this.highlighted + step, this.values.size());
		} else {
			this.select(Math.floorMod(this.values.indexOf(this.value) + step, this.values.size()));
		}

		return true;
	}

	@Override
	public void setFocused(boolean focused) {
		super.setFocused(focused);
		if (!focused) {
			this.open = false;
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, this.label.apply(this.value));
	}

	private static void extractArrow(GuiGraphicsExtractor graphics, int x, int y, boolean down, int color) {
		for (int row = 0; row < ARROW_HEIGHT; row++) {
			int top = down ? y + row : y + ARROW_HEIGHT - 1 - row;
			graphics.fill(x + row, top, x + ARROW_WIDTH - row, top + 1, color);
		}
	}

	private void select(int index) {
		T selected = this.values.get(index);
		if (selected.equals(this.value)) {
			return;
		}

		this.value = selected;
		this.highlighted = index;
		this.setMessage(this.label.apply(selected));
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
		this.onSelect.accept(selected);
	}

	private int indexAt(int mouseX, int mouseY, int fallback) {
		int top = this.listTop();
		if (mouseX < this.getX() || mouseX >= this.getRight() || mouseY < top + 1 || mouseY >= top + this.listHeight()) {
			return fallback;
		}

		int index = (mouseY - top - 1) / ROW_HEIGHT;
		return index >= 0 && index < this.values.size() ? index : fallback;
	}

	private int listHeight() {
		return this.values.size() * ROW_HEIGHT + 2;
	}

	private int listTop() {
		int below = this.getBottom();
		int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
		return below + this.listHeight() <= screenHeight ? below : this.getY() - this.listHeight();
	}
}
