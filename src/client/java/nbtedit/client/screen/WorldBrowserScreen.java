package nbtedit.client.screen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import nbtedit.NBTEdit;
import nbtedit.client.config.NbtEditConfig;
import nbtedit.client.io.FileProbe;
import nbtedit.client.io.SafeWrite;
import nbtedit.client.json.JsonFile;
import nbtedit.client.nbt.NbtFile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public class WorldBrowserScreen extends Screen {
	private static final int INDENT = 10;
	private static final int SCROLL_ROWS = 3;
	private static final int BRANCH_EXPAND_LIMIT = 200;
	private static final int ROW_HEIGHT = 14;
	private static final int DIRECTORY_COLOR = 0xFFFFD966;
	private static final int FILE_COLOR = 0xFFFFFFFF;
	private static final int IMAGE_COLOR = 0xFF7FD8FF;
	private static final int IGNORED_COLOR = 0xFF707070;
	private static final int CREDITS_COLOR = 0xFF808080;
	private static final int BACKUPS_WIDTH = 96;
	private static final int CORNER_MARGIN = 6;
	private static final int HEADER_HEIGHT = 33;
	private static final int BANNER_HEIGHT = 14;
	private static final int BANNER_COLOR = 0xFFA01818;
	private static final int BANNER_EDGE_COLOR = 0xFF5A0A0A;
	private static final int BANNER_HOVER_COLOR = 0xFFC83030;
	private static final int BANNER_TEXT_COLOR = 0xFFFFFFFF;
	private static final int BANNER_CLOSE_WIDTH = 14;
	private static final int BANNER_TEXT_START = 4;
	private static final int MARQUEE_SPEED = 45;
	private static final int MARQUEE_GAP = 80;
	private static final List<String> NBT_EXTENSIONS = List.of(".dat", ".dat_old", ".nbt", ".schematic", ".mcstructure");
	private static final List<String> JSON_EXTENSIONS = List.of(".json", ".mcmeta");
	private static final List<String> IMAGE_EXTENSIONS = List.of(".png", ".jpg", ".jpeg");
	private static final Pattern BACKUP_SUFFIX = Pattern.compile("\\.\\d{8}-\\d{6}\\.bak$");

	private final Runnable onDone;
	private final Path worldRoot;
	private final FileNode root;
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, HEADER_HEIGHT, 40);
	private boolean bannerShown;
	private long bannerStart;
	private @Nullable FileList list;
	private @Nullable Button openButton;
	private @Nullable Button deleteButton;
	private @Nullable CycleButton<Integer> backupsButton;

	public WorldBrowserScreen(LevelStorageAccess levelAccess, Runnable onDone) {
		super(Component.translatable("nbtedit.browser.title", levelAccess.getLevelId()));
		this.onDone = onDone;
		this.worldRoot = levelAccess.getLevelPath(LevelResource.ROOT);
		this.root = new FileNode(null, this.worldRoot, true, 0);
		this.root.expanded = true;
	}

	@Override
	protected void init() {
		this.bannerShown = !NbtEditConfig.get().disclaimerDismissed();
		this.bannerStart = Util.getMillis();
		this.layout.addTitleHeader(this.title, this.font);
		FileList fileList = new FileList(this.minecraft, this.width, this.layout.getContentHeight(), this.layout.getHeaderHeight());
		this.list = this.layout.addToContents(fileList);
		GridLayout footer = this.layout.addToFooter(new GridLayout().columnSpacing(8).rowSpacing(4));
		footer.defaultCellSetting().alignHorizontallyCenter();
		GridLayout.RowHelper rows = footer.createRowHelper(3);
		this.openButton = rows.addChild(Button.builder(Component.translatable("nbtedit.button.open_file"), button -> this.openSelected()).width(100).build());
		this.deleteButton = rows.addChild(Button.builder(Component.translatable("nbtedit.button.delete"), button -> this.deleteSelected()).width(100).build());
		rows.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).width(100).build());
		this.backupsButton = this.addRenderableWidget(
			CycleButton.<Integer>builder(NbtEditConfig::backupChoiceName, NbtEditConfig.get().keptBackups())
				.withValues(NbtEditConfig.BACKUP_CHOICES)
				.withTooltip(value -> Tooltip.create(Component.translatable("nbtedit.backups.tooltip")))
				.create(0, 0, BACKUPS_WIDTH, 20, Component.translatable("nbtedit.button.backups"), (button, value) -> NbtEditConfig.get().setKeptBackups(value))
		);
		this.layout.visitWidgets(this::addRenderableWidget);
		this.repositionElements();
		fileList.rebuild();
		this.updateButtons();
	}

	@Override
	protected void repositionElements() {
		int bannerHeight = this.bannerShown ? BANNER_HEIGHT : 0;
		this.layout.setHeaderHeight(HEADER_HEIGHT + bannerHeight);
		if (this.backupsButton != null) {
			this.backupsButton.setPosition(this.width - BACKUPS_WIDTH - CORNER_MARGIN, bannerHeight + CORNER_MARGIN);
		}

		if (this.list != null) {
			this.list.updateSize(this.width, this.layout);
		}

		this.layout.arrangeElements();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.text(this.font, Component.translatable("nbtedit.credits"), 6, this.height - 11, CREDITS_COLOR);
		if (this.bannerShown) {
			this.extractBanner(graphics, mouseX, mouseY);
		}
	}

	private void extractBanner(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int closeLeft = this.width - BANNER_CLOSE_WIDTH;
		graphics.fill(0, 0, this.width, BANNER_HEIGHT, BANNER_COLOR);
		if (this.isOverBannerClose(mouseX, mouseY)) {
			graphics.fill(closeLeft, 0, this.width, BANNER_HEIGHT, BANNER_HOVER_COLOR);
		}

		graphics.fill(0, BANNER_HEIGHT - 1, this.width, BANNER_HEIGHT, BANNER_EDGE_COLOR);
		Component text = Component.translatable("nbtedit.disclaimer");
		int cycle = closeLeft + this.font.width(text) + MARQUEE_GAP;
		long travelled = (Util.getMillis() - this.bannerStart) * MARQUEE_SPEED / 1000L;
		int offset = (int) ((closeLeft - BANNER_TEXT_START + travelled) % cycle);
		int textY = (BANNER_HEIGHT - 1 - this.font.lineHeight) / 2 + 1;
		graphics.enableScissor(0, 0, closeLeft, BANNER_HEIGHT);
		graphics.text(this.font, text, closeLeft - offset, textY, BANNER_TEXT_COLOR);
		graphics.disableScissor();
		graphics.text(this.font, "×", closeLeft + (BANNER_CLOSE_WIDTH - this.font.width("×")) / 2, textY, BANNER_TEXT_COLOR);
	}

	private boolean isOverBannerClose(double x, double y) {
		return this.bannerShown && y >= 0 && y < BANNER_HEIGHT && x >= this.width - BANNER_CLOSE_WIDTH && x < this.width;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (this.isOverBannerClose(event.x(), event.y())) {
			this.bannerShown = false;
			NbtEditConfig.get().dismissDisclaimer();
			this.repositionElements();
			return true;
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	protected void setInitialFocus() {
		if (this.list != null) {
			this.setInitialFocus(this.list);
		}
	}

	@Override
	public void tick() {
		this.updateButtons();
	}

	@Override
	public void onClose() {
		this.onDone.run();
	}

	private void updateButtons() {
		FileNode node = this.selectedNode();
		if (this.openButton != null) {
			this.openButton.active = node != null && node.openable();
		}

		if (this.deleteButton != null) {
			this.deleteButton.active = node != null && !node.directory;
		}
	}

	private @Nullable FileNode selectedNode() {
		if (this.list == null) {
			return null;
		}

		FileList.Row row = this.list.getSelected();
		return row == null ? null : row.node();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_DELETE) {
			this.deleteSelected();
			return true;
		}

		return super.keyPressed(event);
	}

	private void deleteSelected() {
		FileNode node = this.selectedNode();
		if (node == null || node.directory) {
			return;
		}

		this.minecraft.gui
			.setScreen(
				new ConfirmScreen(
					confirmed -> {
						if (confirmed) {
							this.delete(node);
						}

						this.minecraft.gui.setScreen(this);
					},
					Component.translatable("nbtedit.delete.title"),
					Component.translatable("nbtedit.delete.message", node.path.getFileName().toString())
				)
			);
	}

	private void delete(FileNode node) {
		try {
			Path backup = SafeWrite.remove(node.path, NbtEditConfig.get().keptBackups());
			if (node.parent != null) {
				node.parent.invalidate();
			}

			if (this.list != null) {
				this.list.rebuild();
			}

			Component message = backup == null
				? Component.literal(node.path.getFileName().toString())
				: Component.translatable("nbtedit.toast.backup", backup.getFileName().toString());
			this.toast(Component.translatable("nbtedit.toast.deleted"), message);
		} catch (IOException e) {
			NBTEdit.LOGGER.error("Failed to delete {}", node.path, e);
			this.toast(Component.translatable("nbtedit.toast.delete_failed"), Component.literal(String.valueOf(e.getMessage())));
		}
	}

	private void toast(Component title, Component message) {
		this.minecraft.gui.toastManager().addToast(new SystemToast(SystemToast.SystemToastId.WORLD_BACKUP, title, message));
	}

	private void openSelected() {
		FileNode node = this.selectedNode();
		if (node == null || !node.openable()) {
			return;
		}

		this.open(node);
	}

	private void open(FileNode node) {
		try {
			switch (node.kind()) {
				case NBT -> this.minecraft.gui.setScreen(new NbtTreeScreen(this, NbtFile.load(node.path)));
				case JSON -> this.minecraft.gui.setScreen(new JsonTreeScreen(this, JsonFile.load(node.path)));
				case TEXT -> this.minecraft.gui.setScreen(TextFileScreen.load(this, node.path));
				case IMAGE -> this.minecraft.gui.setScreen(ImageViewScreen.load(this, node.path));
				case NONE -> {
				}
			}
		} catch (Exception e) {
			NBTEdit.LOGGER.error("Failed to read {}", node.path, e);
			this.minecraft.gui
				.toastManager()
				.addToast(
					new SystemToast(
						SystemToast.SystemToastId.WORLD_ACCESS_FAILURE,
						Component.translatable("nbtedit.toast.read_failed"),
						Component.literal(node.path.getFileName().toString())
					)
				);
		}
	}

	private enum FileKind {
		NONE,
		NBT,
		JSON,
		TEXT,
		IMAGE
	}

	private static final class FileNode {
		private final @Nullable FileNode parent;
		private final Path path;
		private final boolean directory;
		private final int depth;
		private boolean expanded;
		private @Nullable List<FileNode> children;
		private @Nullable FileKind kind;

		private FileNode(@Nullable FileNode parent, Path path, boolean directory, int depth) {
			this.parent = parent;
			this.path = path;
			this.directory = directory;
			this.depth = depth;
		}

		private FileKind kind() {
			if (this.kind == null) {
				this.kind = this.detectKind();
			}

			return this.kind;
		}

		private FileKind detectKind() {
			if (this.directory) {
				return FileKind.NONE;
			}

			String name = BACKUP_SUFFIX.matcher(this.path.getFileName().toString().toLowerCase(Locale.ROOT)).replaceFirst("");
			if (NBT_EXTENSIONS.stream().anyMatch(name::endsWith)) {
				return FileKind.NBT;
			}

			if (JSON_EXTENSIONS.stream().anyMatch(name::endsWith)) {
				return FileKind.JSON;
			}

			if (IMAGE_EXTENSIONS.stream().anyMatch(name::endsWith)) {
				return FileKind.IMAGE;
			}

			// Anything else is offered as text only when it actually reads like text.
			return FileProbe.looksLikeText(this.path) ? FileKind.TEXT : FileKind.NONE;
		}

		private boolean openable() {
			return this.kind() != FileKind.NONE;
		}

		private void invalidate() {
			this.children = null;
		}

		private List<FileNode> children() {
			if (this.children == null) {
				this.children = this.readChildren();
			}

			return this.children;
		}

		private List<FileNode> readChildren() {
			List<FileNode> result = new ArrayList<>();
			try (Stream<Path> entries = Files.list(this.path)) {
				entries.sorted(Comparator.comparing((Path entry) -> !Files.isDirectory(entry)).thenComparing(entry -> entry.getFileName().toString()))
					.forEach(entry -> result.add(new FileNode(this, entry, Files.isDirectory(entry), this.depth + 1)));
			} catch (IOException e) {
				NBTEdit.LOGGER.error("Failed to list {}", this.path, e);
			}

			return result;
		}

		private void toggleBranch(int limit) {
			if (this.expanded) {
				this.collapseBranch();
			} else {
				this.expandBranch(limit);
			}
		}

		private void expandBranch(int limit) {
			Deque<FileNode> pending = new ArrayDeque<>();
			pending.add(this);
			int opened = 0;

			while (!pending.isEmpty() && opened < limit) {
				FileNode node = pending.poll();
				if (!node.directory) {
					continue;
				}

				node.expanded = true;
				opened++;
				pending.addAll(node.children());
			}
		}

		private void collapseBranch() {
			this.expanded = false;
			if (this.children != null) {
				for (FileNode child : this.children) {
					child.collapseBranch();
				}
			}
		}

		private void collectVisible(List<FileNode> out) {
			out.add(this);
			if (this.directory && this.expanded) {
				for (FileNode child : this.children()) {
					child.collectVisible(out);
				}
			}
		}
	}

	private final class FileList extends ObjectSelectionList<FileList.Row> {
		private final Map<FileNode, Row> rows = new IdentityHashMap<>();

		FileList(Minecraft minecraft, int width, int height, int y) {
			super(minecraft, width, height, y, ROW_HEIGHT);
		}

		@Override
		public int getRowWidth() {
			return Math.min(WorldBrowserScreen.this.width - 24, 420);
		}

		@Override
		protected double scrollRate() {
			return ROW_HEIGHT * SCROLL_ROWS;
		}

		void focusNode(FileNode node) {
			Row row = this.rows.get(node);
			if (row != null) {
				this.setFocused(row);
				WorldBrowserScreen.this.updateButtons();
			}
		}

		void rebuild() {
			Row previousSelection = this.getSelected();
			Map<FileNode, Row> previousRows = new IdentityHashMap<>(this.rows);
			List<FileNode> visible = new ArrayList<>();
			WorldBrowserScreen.this.root.collectVisible(visible);
			this.rows.clear();
			this.clearEntries();
			for (FileNode node : visible) {
				Row row = previousRows.get(node);
				if (row == null) {
					row = new Row(node);
				}

				this.rows.put(node, row);
				this.addEntry(row);
			}

			if (previousSelection != null && this.rows.get(previousSelection.node()) == previousSelection) {
				this.setSelected(previousSelection);
			}
		}

		final class Row extends ObjectSelectionList.Entry<Row> {
			private final FileNode node;

			Row(FileNode node) {
				this.node = node;
			}

			FileNode node() {
				return this.node;
			}

			@Override
			public Component getNarration() {
				return Component.literal(this.node.path.getFileName().toString());
			}

			@Override
			public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
				int x = this.getContentX() + this.node.depth * INDENT;
				int y = this.getContentY() + 1;
				if (this.node.directory) {
					graphics.text(WorldBrowserScreen.this.font, this.node.expanded ? "-" : "+", x, y, DIRECTORY_COLOR);
				}

				x += 8;
				String name = this.node.path.getFileName().toString();
				int color = this.rowColor();
				graphics.text(WorldBrowserScreen.this.font, name, x, y, color);
			}

			private void toggle(boolean wholeBranch) {
				if (wholeBranch) {
					this.node.toggleBranch(BRANCH_EXPAND_LIMIT);
				} else {
					this.node.expanded = !this.node.expanded;
				}

				FileList.this.rebuild();
			}

			private int rowColor() {
				if (this.node.directory) {
					return DIRECTORY_COLOR;
				}

				return switch (this.node.kind()) {
					case NONE -> IGNORED_COLOR;
					case IMAGE -> IMAGE_COLOR;
					default -> FILE_COLOR;
				};
			}

			@Override
			public boolean keyPressed(KeyEvent event) {
				if (event.isRight()) {
					if (this.node.directory) {
						if (this.node.expanded) {
							List<FileNode> children = this.node.children();
							if (!children.isEmpty()) {
								FileList.this.focusNode(children.getFirst());
							}
						} else {
							this.toggle(event.hasShiftDown());
						}
					}

					return true;
				}

				if (event.isLeft()) {
					if (this.node.directory && this.node.expanded) {
						this.toggle(event.hasShiftDown());
					} else if (this.node.parent != null) {
						FileList.this.focusNode(this.node.parent);
					}

					return true;
				}

				if (event.isSelection()) {
					if (this.node.directory) {
						this.toggle(event.hasShiftDown());
					} else if (this.node.openable()) {
						WorldBrowserScreen.this.open(this.node);
					}

					return true;
				}

				return false;
			}

			@Override
			public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
				FileList.this.setSelected(this);
				WorldBrowserScreen.this.updateButtons();
				if (this.node.directory) {
					if (!doubleClick) {
						this.toggle(event.hasShiftDown());
					}
				} else if (doubleClick && this.node.openable()) {
					WorldBrowserScreen.this.open(this.node);
				}

				return true;
			}
		}
	}
}
