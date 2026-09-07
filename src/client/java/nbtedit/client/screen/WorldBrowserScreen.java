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
import java.util.stream.Stream;
import nbtedit.NBTEdit;
import nbtedit.client.json.JsonFile;
import nbtedit.client.nbt.NbtFile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;
import org.jspecify.annotations.Nullable;

public class WorldBrowserScreen extends Screen {
	private static final int INDENT = 10;
	private static final int SCROLL_ROWS = 3;
	private static final int BRANCH_EXPAND_LIMIT = 200;
	private static final int ROW_HEIGHT = 14;
	private static final int DIRECTORY_COLOR = 0xFFFFD966;
	private static final int FILE_COLOR = 0xFFFFFFFF;
	private static final int IGNORED_COLOR = 0xFF707070;
	private static final List<String> NBT_EXTENSIONS = List.of(".dat", ".dat_old", ".nbt", ".schematic", ".mcstructure");
	private static final List<String> JSON_EXTENSIONS = List.of(".json", ".mcmeta");

	private final Runnable onDone;
	private final Path worldRoot;
	private final FileNode root;
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, 33, 40);
	private @Nullable FileList list;
	private @Nullable Button openButton;

	public WorldBrowserScreen(LevelStorageAccess levelAccess, Runnable onDone) {
		super(Component.translatable("nbtedit.browser.title", levelAccess.getLevelId()));
		this.onDone = onDone;
		this.worldRoot = levelAccess.getLevelPath(LevelResource.ROOT);
		this.root = new FileNode(this.worldRoot, true, 0);
		this.root.expanded = true;
	}

	@Override
	protected void init() {
		this.layout.addTitleHeader(this.title, this.font);
		FileList fileList = new FileList(this.minecraft, this.width, this.layout.getContentHeight(), this.layout.getHeaderHeight());
		this.list = this.layout.addToContents(fileList);
		GridLayout footer = this.layout.addToFooter(new GridLayout().columnSpacing(8).rowSpacing(4));
		footer.defaultCellSetting().alignHorizontallyCenter();
		GridLayout.RowHelper rows = footer.createRowHelper(2);
		this.openButton = rows.addChild(Button.builder(Component.translatable("nbtedit.button.open_file"), button -> this.openSelected()).width(150).build());
		rows.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).width(150).build());
		this.layout.visitWidgets(this::addRenderableWidget);
		this.repositionElements();
		fileList.rebuild();
		this.updateButtons();
	}

	@Override
	protected void repositionElements() {
		if (this.list != null) {
			this.list.updateSize(this.width, this.layout);
		}

		this.layout.arrangeElements();
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
			this.openButton.active = node != null && node.editable();
		}
	}

	private @Nullable FileNode selectedNode() {
		if (this.list == null) {
			return null;
		}

		FileList.Row row = this.list.getSelected();
		return row == null ? null : row.node();
	}

	private void openSelected() {
		FileNode node = this.selectedNode();
		if (node == null || !node.editable()) {
			return;
		}

		this.open(node);
	}

	private void open(FileNode node) {
		try {
			switch (node.kind()) {
				case NBT -> this.minecraft.gui.setScreen(new NbtTreeScreen(this, NbtFile.load(node.path)));
				case JSON -> this.minecraft.gui.setScreen(new JsonTreeScreen(this, JsonFile.load(node.path)));
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
		JSON
	}

	private static final class FileNode {
		private final Path path;
		private final boolean directory;
		private final int depth;
		private boolean expanded;
		private @Nullable List<FileNode> children;

		private FileNode(Path path, boolean directory, int depth) {
			this.path = path;
			this.directory = directory;
			this.depth = depth;
		}

		private FileKind kind() {
			if (this.directory) {
				return FileKind.NONE;
			}

			String name = this.path.getFileName().toString().toLowerCase(Locale.ROOT);
			if (NBT_EXTENSIONS.stream().anyMatch(name::endsWith)) {
				return FileKind.NBT;
			}

			return JSON_EXTENSIONS.stream().anyMatch(name::endsWith) ? FileKind.JSON : FileKind.NONE;
		}

		private boolean editable() {
			return this.kind() != FileKind.NONE;
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
					.forEach(entry -> result.add(new FileNode(entry, Files.isDirectory(entry), this.depth + 1)));
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
				int color = this.node.directory ? DIRECTORY_COLOR : this.node.editable() ? FILE_COLOR : IGNORED_COLOR;
				graphics.text(WorldBrowserScreen.this.font, name, x, y, color);
			}

			@Override
			public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
				FileList.this.setSelected(this);
				WorldBrowserScreen.this.updateButtons();
				if (this.node.directory) {
					if (!doubleClick) {
						if (event.hasShiftDown()) {
							this.node.toggleBranch(BRANCH_EXPAND_LIMIT);
						} else {
							this.node.expanded = !this.node.expanded;
						}

						FileList.this.rebuild();
					}
				} else if (doubleClick && this.node.editable()) {
					WorldBrowserScreen.this.open(this.node);
				}

				return true;
			}
		}
	}
}
