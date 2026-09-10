package nbtedit.client.screen;

import com.google.common.hash.Hashing;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import nbtedit.NBTEdit;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public class ImageViewScreen extends Screen {
	private static final int HEADER_HEIGHT = 8 + 9 + 8 + 9 + 4;
	private static final int FOOTER_HEIGHT = 40;
	private static final int CHECKER_LIGHT = 0xFF3A3A3A;
	private static final int CHECKER_DARK = 0xFF2E2E2E;
	private static final int CHECKER_SIZE = 8;
	private static final int BORDER_COLOR = 0xFF6E6E6E;

	private final Screen parent;
	private final Path path;
	private final Identifier textureId;
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, HEADER_HEIGHT, FOOTER_HEIGHT);
	private final int imageWidth;
	private final int imageHeight;
	private @Nullable DynamicTexture texture;

	private ImageViewScreen(Screen parent, Path path, NativeImage image) {
		super(Component.literal(path.getFileName().toString()));
		this.parent = parent;
		this.path = path;
		this.imageWidth = image.getWidth();
		this.imageHeight = image.getHeight();
		this.textureId = Identifier.fromNamespaceAndPath(
			NBTEdit.MOD_ID, "preview/" + Hashing.sha1().hashString(path.toString(), StandardCharsets.UTF_8)
		);
		this.texture = new DynamicTexture(() -> "NBT Edit preview " + path.getFileName(), image);
	}

	public static ImageViewScreen load(Screen parent, Path path) throws IOException {
		try (InputStream in = Files.newInputStream(path)) {
			return new ImageViewScreen(parent, path, NativeImage.read(in));
		}
	}

	@Override
	protected void init() {
		LinearLayout header = this.layout.addToHeader(LinearLayout.vertical().spacing(4));
		header.defaultCellSetting().alignHorizontallyCenter();
		header.addChild(new StringWidget(this.title, this.font));
		header.addChild(new StringWidget(Component.translatable("nbtedit.image.size", this.imageWidth, this.imageHeight).withColor(0xFF9A9A9A), this.font));
		this.layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).width(150).build());
		this.layout.visitWidgets(this::addRenderableWidget);
		if (this.texture != null) {
			this.minecraft.getTextureManager().register(this.textureId, this.texture);
		}

		this.repositionElements();
	}

	@Override
	protected void repositionElements() {
		this.layout.arrangeElements();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		if (this.texture == null) {
			return;
		}

		int areaWidth = this.width - 40;
		int areaHeight = this.height - this.layout.getHeaderHeight() - this.layout.getFooterHeight() - 20;
		float scale = Math.min((float)areaWidth / this.imageWidth, (float)areaHeight / this.imageHeight);
		int drawWidth = Math.max(1, (int)(this.imageWidth * scale));
		int drawHeight = Math.max(1, (int)(this.imageHeight * scale));
		int x = (this.width - drawWidth) / 2;
		int y = this.layout.getHeaderHeight() + (areaHeight - drawHeight) / 2 + 10;
		this.extractChecker(graphics, x, y, drawWidth, drawHeight);
		graphics.blit(
			RenderPipelines.GUI_TEXTURED, this.textureId, x, y, 0.0F, 0.0F, drawWidth, drawHeight, this.imageWidth, this.imageHeight, this.imageWidth, this.imageHeight
		);
		graphics.outline(x - 1, y - 1, drawWidth + 2, drawHeight + 2, BORDER_COLOR);
	}

	@Override
	public void removed() {
		if (this.texture != null) {
			this.minecraft.getTextureManager().release(this.textureId);
			this.texture.close();
			this.texture = null;
		}
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	/** A checkerboard makes transparent areas of an icon readable. */
	private void extractChecker(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
		for (int row = 0; row * CHECKER_SIZE < height; row++) {
			for (int column = 0; column * CHECKER_SIZE < width; column++) {
				int cellX = x + column * CHECKER_SIZE;
				int cellY = y + row * CHECKER_SIZE;
				int cellWidth = Math.min(CHECKER_SIZE, x + width - cellX);
				int cellHeight = Math.min(CHECKER_SIZE, y + height - cellY);
				graphics.fill(cellX, cellY, cellX + cellWidth, cellY + cellHeight, (row + column) % 2 == 0 ? CHECKER_LIGHT : CHECKER_DARK);
			}
		}
	}
}
