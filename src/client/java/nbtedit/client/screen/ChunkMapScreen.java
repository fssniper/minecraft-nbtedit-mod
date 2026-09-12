package nbtedit.client.screen;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import nbtedit.NBTEdit;
import nbtedit.client.region.ChunkIndex;
import nbtedit.client.region.RegionFileView;
import nbtedit.client.region.TerrainTile;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public class ChunkMapScreen extends Screen {
	private static final int HEADER_HEIGHT = 8 + 9 + 8 + 20 + 4;
	private static final int FOOTER_HEIGHT = 40;
	private static final int MARGIN = 8;
	private static final int JUMP_WIDTH = 110;
	private static final int CYCLE_WIDTH = 90;
	private static final int BUTTON_WIDTH = 100;
	private static final int MAP_BACKGROUND = 0xFF101012;
	private static final int BORDER_COLOR = 0xFF6E6E6E;
	private static final int GRID_COLOR = 0x30FFFFFF;
	private static final int SELECTION_COLOR = 0xFFFFFFFF;
	private static final int SIZE_LOW = 0xFF27402F;
	private static final int SIZE_HIGH = 0xFFB6F07A;
	private static final int SAVED_LOW = 0xFF25374F;
	private static final int SAVED_HIGH = 0xFF86BCFF;
	private static final int SECTOR_BYTES = 4096;
	private static final int MAX_SPAN = 4096;
	private static final int GRID_MIN_SCALE = 3;
	private static final int TERRAIN_MIN_SCALE = 4;
	private static final int UNLOADED_COLOR = 0xFF2B2B30;
	private static final int REGION_SIZE = 32;
	private static final List<Integer> ZOOM_STEPS = List.of(1, 2, 3, 4, 6, 8, 12, 16, 24, 32);
	private static final DateTimeFormatter SAVED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

	private final Screen parent;
	private final Path openedFile;
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, HEADER_HEIGHT, FOOTER_HEIGHT);
	private final Identifier textureId = Identifier.fromNamespaceAndPath(NBTEdit.MOD_ID, "chunk_map");
	private ChunkIndex index;
	private ColorMode colorMode = ColorMode.TERRAIN;
	private TerrainTiles terrain = new TerrainTiles();
	private @Nullable DynamicTexture texture;
	private int textureWidth;
	private int textureHeight;
	private int originX;
	private int originZ;
	private int scale = 4;
	private int sizeCeiling = SECTOR_BYTES;
	private double panX;
	private double panZ;
	private boolean viewReady;
	private @Nullable ChunkPos selected;
	private @Nullable Button openButton;
	private @Nullable Button listButton;

	private enum ColorMode {
		TERRAIN("nbtedit.map.color.terrain"),
		SIZE("nbtedit.map.color.size"),
		SAVED("nbtedit.map.color.saved");

		private final String key;

		ColorMode(String key) {
			this.key = key;
		}

		private Component label() {
			return Component.translatable(this.key);
		}
	}

	private ChunkMapScreen(Screen parent, Path openedFile, ChunkIndex index) {
		super(Component.empty());
		this.parent = parent;
		this.openedFile = openedFile;
		this.index = index;
	}

	public static ChunkMapScreen load(Screen parent, Path regionFile) throws IOException {
		Path folder = regionFile.getParent();
		if (folder == null) {
			throw new IOException("Region file has no folder: " + regionFile);
		}

		return new ChunkMapScreen(parent, regionFile, ChunkIndex.scan(folder));
	}

	@Override
	protected void init() {
		LinearLayout header = this.layout.addToHeader(LinearLayout.vertical().spacing(4));
		header.defaultCellSetting().alignHorizontallyCenter();
		header.addChild(new StringWidget(this.mapTitle(), this.font));
		LinearLayout controls = header.addChild(LinearLayout.horizontal().spacing(4));
		Component jumpLabel = Component.translatable("nbtedit.map.jump");
		EditBox jumpBox = controls.addChild(new EditBox(this.font, JUMP_WIDTH, 20, jumpLabel));
		jumpBox.setHint(jumpLabel.copy().setStyle(EditBox.SEARCH_HINT_STYLE));
		jumpBox.setResponder(this::jumpTo);
		List<Path> layers = ChunkIndex.layersNextTo(this.index.folder());
		if (layers.size() > 1) {
			controls.addChild(
				CycleButton.<Path>builder(layer -> Component.literal(layer.getFileName().toString()), this.index.folder())
					.withValues(layers)
					.create(0, 0, CYCLE_WIDTH, 20, Component.translatable("nbtedit.map.layer"), (button, layer) -> this.switchLayer(layer))
			);
		}

		controls.addChild(
			CycleButton.<ColorMode>builder(ColorMode::label, this.colorMode)
				.withValues(ColorMode.values())
				.create(0, 0, CYCLE_WIDTH, 20, Component.translatable("nbtedit.map.color"), (button, mode) -> {
					this.colorMode = mode;
					this.buildTexture();
				})
		);
		GridLayout footer = this.layout.addToFooter(new GridLayout().columnSpacing(8).rowSpacing(4));
		footer.defaultCellSetting().alignHorizontallyCenter();
		GridLayout.RowHelper rows = footer.createRowHelper(3);
		this.openButton = rows.addChild(Button.builder(Component.translatable("nbtedit.button.open"), button -> this.openSelected()).width(BUTTON_WIDTH).build());
		this.listButton = rows.addChild(Button.builder(Component.translatable("nbtedit.button.list"), button -> this.openList()).width(BUTTON_WIDTH).build());
		rows.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).width(BUTTON_WIDTH).build());
		this.layout.visitWidgets(this::addRenderableWidget);
		this.repositionElements();
		this.buildTexture();
		if (this.viewReady) {
			this.clampPan();
		} else {
			this.resetView();
			this.viewReady = true;
		}

		this.updateButtons();
	}

	@Override
	protected void repositionElements() {
		this.layout.arrangeElements();
	}

	@Override
	public void tick() {
		this.terrain.upload();
	}

	@Override
	public void removed() {
		this.releaseTexture();
	}

	@Override
	public void onClose() {
		this.terrain.close();
		this.minecraft.gui.setScreen(this.parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		int left = this.viewLeft();
		int top = this.viewTop();
		int width = this.viewWidth();
		int height = this.viewHeight();
		graphics.fill(left, top, left + width, top + height, MAP_BACKGROUND);
		graphics.enableScissor(left, top, left + width, top + height);
		if (this.texture != null) {
			graphics.blit(
				RenderPipelines.GUI_TEXTURED,
				this.textureId,
				left - (int) this.panX,
				top - (int) this.panZ,
				0.0F,
				0.0F,
				this.textureWidth * this.scale,
				this.textureHeight * this.scale,
				this.textureWidth,
				this.textureHeight,
				this.textureWidth,
				this.textureHeight
			);
		}

		this.extractTerrain(graphics);
		this.extractGrid(graphics, left, top, width, height);
		this.extractSelection(graphics);
		graphics.disableScissor();
		graphics.outline(left - 1, top - 1, width + 2, height + 2, BORDER_COLOR);
		this.extractHover(graphics, mouseX, mouseY);
	}

	private void extractTerrain(GuiGraphicsExtractor graphics) {
		if (this.colorMode != ColorMode.TERRAIN || this.scale < TERRAIN_MIN_SCALE) {
			return;
		}

		int size = REGION_SIZE * this.scale;
		int firstX = Math.floorDiv(this.originX + (int) Math.floor(this.panX / this.scale), REGION_SIZE);
		int lastX = Math.floorDiv(this.originX + (int) Math.floor((this.panX + this.viewWidth()) / this.scale), REGION_SIZE);
		int firstZ = Math.floorDiv(this.originZ + (int) Math.floor(this.panZ / this.scale), REGION_SIZE);
		int lastZ = Math.floorDiv(this.originZ + (int) Math.floor((this.panZ + this.viewHeight()) / this.scale), REGION_SIZE);
		for (int regionZ = firstZ; regionZ <= lastZ; regionZ++) {
			for (int regionX = firstX; regionX <= lastX; regionX++) {
				Path file = this.index.regionFile(regionX, regionZ);
				if (file == null) {
					continue;
				}

				Identifier tile = this.terrain.get(regionX, regionZ);
				if (tile == null) {
					this.terrain.request(file, regionX, regionZ);
					continue;
				}

				graphics.blit(
					RenderPipelines.GUI_TEXTURED,
					tile,
					this.screenX(regionX * REGION_SIZE),
					this.screenZ(regionZ * REGION_SIZE),
					0.0F,
					0.0F,
					size,
					size,
					TerrainTile.PIXELS,
					TerrainTile.PIXELS,
					TerrainTile.PIXELS,
					TerrainTile.PIXELS
				);
			}
		}
	}

	private void extractGrid(GuiGraphicsExtractor graphics, int left, int top, int width, int height) {
		if (this.scale < GRID_MIN_SCALE) {
			return;
		}

		int step = REGION_SIZE * this.scale;
		int firstX = Math.floorDiv(this.originX, REGION_SIZE) * REGION_SIZE;
		int firstZ = Math.floorDiv(this.originZ, REGION_SIZE) * REGION_SIZE;
		for (int x = this.screenX(firstX); x < left + width; x += step) {
			if (x >= left) {
				graphics.fill(x, top, x + 1, top + height, GRID_COLOR);
			}
		}

		for (int z = this.screenZ(firstZ); z < top + height; z += step) {
			if (z >= top) {
				graphics.fill(left, z, left + width, z + 1, GRID_COLOR);
			}
		}
	}

	private void extractSelection(GuiGraphicsExtractor graphics) {
		ChunkPos chunk = this.selected;
		if (chunk == null) {
			return;
		}

		int size = Math.max(this.scale, GRID_MIN_SCALE);
		int x = this.screenX(chunk.x()) - (size - this.scale) / 2;
		int z = this.screenZ(chunk.z()) - (size - this.scale) / 2;
		graphics.outline(x - 1, z - 1, size + 2, size + 2, SELECTION_COLOR);
	}

	private void extractHover(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		ChunkPos chunk = this.chunkAt(mouseX, mouseY);
		ChunkIndex.Entry entry = chunk == null ? null : this.index.get(chunk.x(), chunk.z());
		if (entry == null) {
			return;
		}

		graphics.setTooltipForNextFrame(this.font, describe(entry), Optional.empty(), mouseX, mouseY);
	}

	private static List<Component> describe(ChunkIndex.Entry entry) {
		int blockX = entry.x() * 16;
		int blockZ = entry.z() * 16;
		Component size = Component.translatable("nbtedit.region.size", (entry.allocatedBytes() + 1023) / 1024);
		Component saved = entry.timestamp() == 0
			? Component.translatable("nbtedit.map.never_saved")
			: Component.literal(SAVED_AT.format(Instant.ofEpochSecond(Integer.toUnsignedLong(entry.timestamp()))));
		return List.of(
			Component.translatable("nbtedit.map.chunk", entry.x(), entry.z()),
			Component.translatable("nbtedit.map.blocks", blockX, blockX + 15, blockZ, blockZ + 15),
			size,
			saved,
			Component.literal(entry.file().getFileName().toString())
		);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		ChunkPos chunk = this.chunkAt((int) event.x(), (int) event.y());
		if (chunk != null) {
			this.setFocused(null);
			this.selected = this.index.get(chunk.x(), chunk.z()) == null ? null : chunk;
			this.updateButtons();
			if (doubleClick) {
				this.openSelected();
			}

			return true;
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (this.inViewport((int) event.x(), (int) event.y())) {
			this.pan(-dx, -dy);
			return true;
		}

		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (scrollY == 0.0 || !this.inViewport((int) x, (int) y)) {
			return super.mouseScrolled(x, y, scrollX, scrollY);
		}

		int step = ZOOM_STEPS.indexOf(this.scale) + (scrollY > 0.0 ? 1 : -1);
		if (step < 0 || step >= ZOOM_STEPS.size()) {
			return true;
		}

		int next = ZOOM_STEPS.get(step);
		double anchorX = x - this.viewLeft() + this.panX;
		double anchorZ = y - this.viewTop() + this.panZ;
		this.panX = anchorX * next / this.scale - (x - this.viewLeft());
		this.panZ = anchorZ * next / this.scale - (y - this.viewTop());
		this.scale = next;
		this.clampPan();
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.getFocused() != null) {
			return super.keyPressed(event);
		}

		int dx = event.key() == GLFW.GLFW_KEY_LEFT ? -1 : event.key() == GLFW.GLFW_KEY_RIGHT ? 1 : 0;
		int dz = event.key() == GLFW.GLFW_KEY_UP ? -1 : event.key() == GLFW.GLFW_KEY_DOWN ? 1 : 0;
		if (dx != 0 || dz != 0) {
			this.moveSelection(dx, dz);
			return true;
		}

		if (event.isConfirmation()) {
			this.openSelected();
			return true;
		}

		return super.keyPressed(event);
	}

	private void moveSelection(int dx, int dz) {
		ChunkPos chunk = this.selected;
		ChunkPos next = chunk == null ? new ChunkPos(this.centerChunkX(), this.centerChunkZ()) : new ChunkPos(chunk.x() + dx, chunk.z() + dz);
		this.selected = next;
		this.reveal(next);
		this.updateButtons();
	}

	private void jumpTo(String value) {
		String[] parts = value.trim().split("[\\s,]+");
		if (parts.length != 2) {
			return;
		}

		try {
			ChunkPos chunk = new ChunkPos(Integer.parseInt(parts[0]) >> 4, Integer.parseInt(parts[1]) >> 4);
			this.selected = chunk;
			this.centerOn(chunk);
			this.updateButtons();
		} catch (NumberFormatException e) {
			return;
		}
	}

	private void switchLayer(Path folder) {
		if (folder.equals(this.index.folder())) {
			return;
		}

		try {
			this.index = ChunkIndex.scan(folder);
		} catch (IOException e) {
			NBTEdit.LOGGER.error("Failed to scan {}", folder, e);
			this.toast(Component.translatable("nbtedit.toast.read_failed"), Component.literal(folder.getFileName().toString()));
			return;
		}

		this.selected = null;
		this.terrain.close();
		this.terrain = new TerrainTiles();
		this.buildTexture();
		this.resetView();
		this.rebuildWidgets();
	}

	private void openList() {
		Path file = this.selectedFile();

		try {
			this.minecraft.gui.setScreen(RegionScreen.load(this, file));
		} catch (IOException e) {
			NBTEdit.LOGGER.error("Failed to read {}", file, e);
			this.toast(Component.translatable("nbtedit.toast.read_failed"), Component.literal(file.getFileName().toString()));
		}
	}

	private Path selectedFile() {
		ChunkPos chunk = this.selected;
		ChunkIndex.Entry entry = chunk == null ? null : this.index.get(chunk.x(), chunk.z());
		if (entry != null) {
			return entry.file();
		}

		Path folder = this.index.folder();
		return folder.equals(this.openedFile.getParent()) ? this.openedFile : folder.resolve(this.openedFile.getFileName());
	}

	private void openSelected() {
		ChunkPos chunk = this.selected;
		ChunkIndex.Entry entry = chunk == null ? null : this.index.get(chunk.x(), chunk.z());
		if (entry == null) {
			return;
		}

		try (RegionFileView region = RegionFileView.open(entry.file())) {
			CompoundTag tag = region.read(entry.x(), entry.z());
			Component title = Component.translatable("nbtedit.chunk.title", entry.x(), entry.z());
			this.minecraft.gui.setScreen(new NbtViewScreen(this, title, entry.x() + ", " + entry.z(), tag));
		} catch (Exception e) {
			NBTEdit.LOGGER.error("Failed to read chunk {}, {} from {}", entry.x(), entry.z(), entry.file(), e);
			this.toast(Component.translatable("nbtedit.toast.read_failed"), Component.literal(entry.x() + ", " + entry.z()));
		}
	}

	private void toast(Component title, Component message) {
		SystemToast.addOrUpdate(this.minecraft.gui.toastManager(), SystemToast.SystemToastId.WORLD_ACCESS_FAILURE, title, message);
	}

	private void updateButtons() {
		ChunkPos chunk = this.selected;
		if (this.openButton != null) {
			this.openButton.active = chunk != null && this.index.get(chunk.x(), chunk.z()) != null;
		}

		if (this.listButton != null) {
			this.listButton.active = !this.index.isEmpty();
		}
	}

	private Component mapTitle() {
		Path folder = this.index.folder();
		Path dimension = folder.getParent();
		String place = dimension == null ? folder.getFileName().toString() : dimension.getFileName() + " / " + folder.getFileName();
		return Component.translatable("nbtedit.map.title", place, this.index.size(), this.index.regionCount());
	}

	private void buildTexture() {
		this.releaseTexture();
		if (this.index.isEmpty()) {
			this.textureWidth = 0;
			this.textureHeight = 0;
			return;
		}

		this.sizeCeiling = this.sizeCeiling();
		this.originX = this.index.minX();
		this.originZ = this.index.minZ();
		this.textureWidth = Math.min(this.index.maxX() - this.originX + 1, MAX_SPAN);
		this.textureHeight = Math.min(this.index.maxZ() - this.originZ + 1, MAX_SPAN);
		NativeImage image = new NativeImage(this.textureWidth, this.textureHeight, true);
		for (ChunkIndex.Entry entry : this.index.entries()) {
			int x = entry.x() - this.originX;
			int z = entry.z() - this.originZ;
			if (x >= 0 && x < this.textureWidth && z >= 0 && z < this.textureHeight) {
				image.setPixel(x, z, this.colorOf(entry));
			}
		}

		this.texture = new DynamicTexture(() -> "NBT Edit chunk map", image);
		this.minecraft.getTextureManager().register(this.textureId, this.texture);
	}

	private int colorOf(ChunkIndex.Entry entry) {
		if (this.colorMode == ColorMode.TERRAIN) {
			return UNLOADED_COLOR;
		}

		if (this.colorMode == ColorMode.SAVED) {
			int oldest = this.index.oldestSave();
			int newest = this.index.newestSave();
			float span = newest - oldest;
			float age = entry.timestamp() == 0 || span <= 0.0F ? 1.0F : (entry.timestamp() - oldest) / span;
			return ARGB.srgbLerp(age, SAVED_LOW, SAVED_HIGH);
		}

		float weight = (float) (entry.allocatedBytes() - SECTOR_BYTES) / Math.max(1, this.sizeCeiling - SECTOR_BYTES);
		return ARGB.srgbLerp(Math.clamp(weight, 0.0F, 1.0F), SIZE_LOW, SIZE_HIGH);
	}

	private int sizeCeiling() {
		int[] sizes = this.index.entries().stream().mapToInt(ChunkIndex.Entry::allocatedBytes).sorted().toArray();
		return sizes.length == 0 ? SECTOR_BYTES : sizes[Math.min(sizes.length - 1, sizes.length * 99 / 100)];
	}

	private void releaseTexture() {
		if (this.texture != null) {
			this.minecraft.getTextureManager().release(this.textureId);
			this.texture.close();
			this.texture = null;
		}
	}

	private void resetView() {
		this.scale = ZOOM_STEPS.getFirst();
		for (int step : ZOOM_STEPS) {
			if (this.textureWidth * step <= this.viewWidth() && this.textureHeight * step <= this.viewHeight()) {
				this.scale = step;
			}
		}

		this.clampPan();
		ChunkPos opened = this.openedRegionCenter();
		if (opened != null) {
			this.centerOn(opened);
		}
	}

	private @Nullable ChunkPos openedRegionCenter() {
		String[] parts = this.openedFile.getFileName().toString().split("\\.");
		if (parts.length != 4) {
			return null;
		}

		try {
			int x = Integer.parseInt(parts[1]) * REGION_SIZE + REGION_SIZE / 2;
			int z = Integer.parseInt(parts[2]) * REGION_SIZE + REGION_SIZE / 2;
			return new ChunkPos(x, z);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private void centerOn(ChunkPos chunk) {
		this.panX = (chunk.x() - this.originX + 0.5) * this.scale - this.viewWidth() / 2.0;
		this.panZ = (chunk.z() - this.originZ + 0.5) * this.scale - this.viewHeight() / 2.0;
		this.clampPan();
	}

	private void reveal(ChunkPos chunk) {
		int x = this.screenX(chunk.x());
		int z = this.screenZ(chunk.z());
		if (x < this.viewLeft() || z < this.viewTop() || x + this.scale > this.viewLeft() + this.viewWidth() || z + this.scale > this.viewTop() + this.viewHeight()) {
			this.centerOn(chunk);
		}
	}

	private void pan(double dx, double dz) {
		this.panX += dx;
		this.panZ += dz;
		this.clampPan();
	}

	private void clampPan() {
		this.panX = clampAxis(this.panX, this.textureWidth * this.scale, this.viewWidth());
		this.panZ = clampAxis(this.panZ, this.textureHeight * this.scale, this.viewHeight());
	}

	private static double clampAxis(double pan, int content, int view) {
		return content <= view ? (content - view) / 2.0 : Math.clamp(pan, 0.0, content - view);
	}

	private @Nullable ChunkPos chunkAt(int x, int y) {
		if (!this.inViewport(x, y)) {
			return null;
		}

		int chunkX = this.originX + (int) Math.floor((x - this.viewLeft() + this.panX) / this.scale);
		int chunkZ = this.originZ + (int) Math.floor((y - this.viewTop() + this.panZ) / this.scale);
		return new ChunkPos(chunkX, chunkZ);
	}

	private boolean inViewport(int x, int y) {
		return x >= this.viewLeft() && x < this.viewLeft() + this.viewWidth() && y >= this.viewTop() && y < this.viewTop() + this.viewHeight();
	}

	private int screenX(int chunkX) {
		return this.viewLeft() + (chunkX - this.originX) * this.scale - (int) this.panX;
	}

	private int screenZ(int chunkZ) {
		return this.viewTop() + (chunkZ - this.originZ) * this.scale - (int) this.panZ;
	}

	private int centerChunkX() {
		return this.originX + (int) ((this.panX + this.viewWidth() / 2.0) / this.scale);
	}

	private int centerChunkZ() {
		return this.originZ + (int) ((this.panZ + this.viewHeight() / 2.0) / this.scale);
	}

	private int viewLeft() {
		return MARGIN;
	}

	private int viewTop() {
		return this.layout.getHeaderHeight() + MARGIN / 2;
	}

	private int viewWidth() {
		return this.width - MARGIN * 2;
	}

	private int viewHeight() {
		return this.height - this.layout.getHeaderHeight() - this.layout.getFooterHeight() - MARGIN;
	}
}
