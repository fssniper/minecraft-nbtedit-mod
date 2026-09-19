package nbtedit.client.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

public class IconButton extends Button {
	public static final int SIZE = 20;
	private static final WidgetSprites SPRITES = new WidgetSprites(
		Identifier.withDefaultNamespace("widget/button"),
		Identifier.withDefaultNamespace("widget/button_disabled"),
		Identifier.withDefaultNamespace("widget/button_highlighted")
	);
	private static final int ICON_SIZE = 16;
	private static final int ICON_TINT = 0xFFFFFFFF;
	private static final int ICON_TINT_INACTIVE = 0xFF8C8C8C;

	private Identifier icon;

	public IconButton(Identifier icon, Component label, Button.OnPress onPress) {
		super(0, 0, SIZE, SIZE, label, onPress, DEFAULT_NARRATION);
		this.icon = icon;
		this.setTooltip(Tooltip.create(label));
	}

	public void setIcon(Identifier icon) {
		this.icon = icon;
	}

	@Override
	public void setMessage(Component message) {
		// Screens retype the label every tick, and a fresh Tooltip would re-wrap its text on every render pass.
		if (message.equals(this.getMessage())) {
			return;
		}

		super.setMessage(message);
		this.setTooltip(Tooltip.create(message));
	}

	protected int backgroundTint() {
		return ICON_TINT;
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int background = ARGB.multiplyAlpha(this.backgroundTint(), this.alpha);
		graphics.blitSprite(
			RenderPipelines.GUI_TEXTURED,
			SPRITES.get(this.active, this.isHoveredOrFocused()),
			this.getX(),
			this.getY(),
			this.getWidth(),
			this.getHeight(),
			background
		);
		int tint = ARGB.multiplyAlpha(this.active ? ICON_TINT : ICON_TINT_INACTIVE, this.alpha);
		int iconX = this.getX() + (this.getWidth() - ICON_SIZE) / 2;
		int iconY = this.getY() + (this.getHeight() - ICON_SIZE) / 2;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, this.icon, iconX, iconY, ICON_SIZE, ICON_SIZE, tint);
	}
}
