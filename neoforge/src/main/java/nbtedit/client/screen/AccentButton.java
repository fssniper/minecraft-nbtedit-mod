package nbtedit.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

public class AccentButton extends Button {
	private static final WidgetSprites SPRITES = new WidgetSprites(
		Identifier.withDefaultNamespace("widget/button"),
		Identifier.withDefaultNamespace("widget/button_disabled"),
		Identifier.withDefaultNamespace("widget/button_highlighted")
	);
	private static final int TINT = 0xFF3FA9F5;
	private static final int TINT_ACTIVE = 0xFF7FD8FF;

	public AccentButton(int width, int height, Component message, Button.OnPress onPress) {
		super(0, 0, width, height, message, onPress, DEFAULT_NARRATION);
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int tint = ARGB.multiplyAlpha(this.isHoveredOrFocused() ? TINT_ACTIVE : TINT, this.alpha);
		graphics.blitSprite(
			RenderPipelines.GUI_TEXTURED, SPRITES.get(this.active, this.isHoveredOrFocused()), this.getX(), this.getY(), this.getWidth(), this.getHeight(), tint
		);
		this.extractDefaultLabel(graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
	}
}
