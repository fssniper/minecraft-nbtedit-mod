package nbtedit.client.screen;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import nbtedit.NBTEdit;
import nbtedit.client.region.RegionFileView;
import nbtedit.client.region.RegionFileView.Chunk;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public class RegionScreen extends Screen {
	private static final int ROW_HEIGHT = 14;
	private static final int SCROLL_ROWS = 3;
	private static final int HEADER_HEIGHT = 8 + 9 + 8 + 20 + 4;
	private static final int FOOTER_HEIGHT = 40;
	private static final int SEARCH_WIDTH = 200;
	private static final int BUTTON_WIDTH = 100;
	private static final int LABEL_COLOR = 0xFFFFFFFF;
	private static final int VALUE_COLOR = 0xFFA0A0A0;
	private static final DateTimeFormatter SAVED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

	private final Screen parent;
	private final Path path;
	private final List<Chunk> chunks;
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, HEADER_HEIGHT, FOOTER_HEIGHT);
	private String filter = "";
	private @Nullable ChunkList list;
	private @Nullable Button openButton;

	private RegionScreen(Screen parent, Path path, List<Chunk> chunks) {
		super(Component.translatable("nbtedit.region.title", path.getFileName().toString(), chunks.size()));
		this.parent = parent;
		this.path = path;
		this.chunks = chunks;
	}

	public static RegionScreen load(Screen parent, Path path) throws IOException {
		try (RegionFileView region = RegionFileView.open(path)) {
			return new RegionScreen(parent, path, List.copyOf(region.chunks()));
		}
	}

	@Override
	protected void init() {
		LinearLayout header = this.layout.addToHeader(LinearLayout.vertical().spacing(4));
		header.defaultCellSetting().alignHorizontallyCenter();
		header.addChild(new StringWidget(this.title, this.font));
		Component searchLabel = Component.translatable("nbtedit.search.hint");
		EditBox searchBox = header.addChild(new EditBox(this.font, SEARCH_WIDTH, 20, searchLabel));
		searchBox.setHint(searchLabel.copy().setStyle(EditBox.SEARCH_HINT_STYLE));
		searchBox.setValue(this.filter);
		searchBox.setResponder(value -> {
			this.filter = value.trim().toLowerCase(Locale.ROOT);
			if (this.list != null) {
				this.list.rebuild();
			}
		});
		ChunkList chunkList = new ChunkList(this.minecraft, this.width, this.layout.getContentHeight(), this.layout.getHeaderHeight());
		this.list = this.layout.addToContents(chunkList);
		GridLayout footer = this.layout.addToFooter(new GridLayout().columnSpacing(8).rowSpacing(4));
		footer.defaultCellSetting().alignHorizontallyCenter();
		GridLayout.RowHelper rows = footer.createRowHelper(2);
		this.openButton = rows.addChild(Button.builder(Component.translatable("nbtedit.button.open"), button -> this.openSelected()).width(BUTTON_WIDTH).build());
		rows.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).width(BUTTON_WIDTH).build());
		this.layout.visitWidgets(this::addRenderableWidget);
		this.repositionElements();
		chunkList.rebuild();
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
		this.minecraft.gui.setScreen(this.parent);
	}

	private void updateButtons() {
		if (this.openButton != null) {
			this.openButton.active = this.selectedChunk() != null;
		}
	}

	private @Nullable Chunk selectedChunk() {
		if (this.list == null) {
			return null;
		}

		ChunkList.Row row = this.list.getSelected();
		return row == null ? null : row.chunk();
	}

	private void openSelected() {
		Chunk chunk = this.selectedChunk();
		if (chunk != null) {
			this.open(chunk);
		}
	}

	private void open(Chunk chunk) {
		try (RegionFileView region = RegionFileView.open(this.path)) {
			CompoundTag tag = region.read(chunk);
			Component title = Component.translatable("nbtedit.chunk.title", chunk.x(), chunk.z());
			this.minecraft.gui.setScreen(new NbtViewScreen(this, title, label(chunk), tag));
		} catch (Exception e) {
			NBTEdit.LOGGER.error("Failed to read chunk {}, {} from {}", chunk.x(), chunk.z(), this.path, e);
			SystemToast.addOrUpdate(
				this.minecraft.gui.toastManager(),
				SystemToast.SystemToastId.WORLD_ACCESS_FAILURE,
				Component.translatable("nbtedit.toast.read_failed"),
				Component.literal(label(chunk))
			);
		}
	}

	private static String label(Chunk chunk) {
		return chunk.x() + ", " + chunk.z();
	}

	private static String details(Chunk chunk) {
		String size = Component.translatable("nbtedit.region.size", (chunk.allocatedBytes() + 1023) / 1024).getString();
		return chunk.timestamp() == 0 ? size : size + " - " + SAVED_AT.format(Instant.ofEpochSecond(Integer.toUnsignedLong(chunk.timestamp())));
	}

	private final class ChunkList extends ObjectSelectionList<ChunkList.Row> {
		private final Map<Chunk, Row> rows = new IdentityHashMap<>();

		ChunkList(Minecraft minecraft, int width, int height, int y) {
			super(minecraft, width, height, y, ROW_HEIGHT);
		}

		@Override
		public int getRowWidth() {
			return Math.min(RegionScreen.this.width - 24, 420);
		}

		@Override
		protected double scrollRate() {
			return ROW_HEIGHT * SCROLL_ROWS;
		}

		void rebuild() {
			Row previousSelection = this.getSelected();
			Map<Chunk, Row> previousRows = new IdentityHashMap<>(this.rows);
			List<Chunk> visible = new ArrayList<>();
			for (Chunk chunk : RegionScreen.this.chunks) {
				if (RegionScreen.this.filter.isEmpty() || label(chunk).contains(RegionScreen.this.filter)) {
					visible.add(chunk);
				}
			}

			this.rows.clear();
			this.clearEntries();
			for (Chunk chunk : visible) {
				Row row = previousRows.get(chunk);
				if (row == null) {
					row = new Row(chunk);
				}

				this.rows.put(chunk, row);
				this.addEntry(row);
			}

			if (previousSelection != null && this.rows.get(previousSelection.chunk()) == previousSelection) {
				this.setSelected(previousSelection);
			}
		}

		final class Row extends ObjectSelectionList.Entry<Row> {
			private final Chunk chunk;

			Row(Chunk chunk) {
				this.chunk = chunk;
			}

			Chunk chunk() {
				return this.chunk;
			}

			@Override
			public Component getNarration() {
				return Component.literal(label(this.chunk));
			}

			@Override
			public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
				int y = this.getContentY() + 1;
				graphics.text(RegionScreen.this.font, label(this.chunk), this.getContentX(), y, LABEL_COLOR);
				String details = details(this.chunk);
				graphics.text(RegionScreen.this.font, details, this.getContentRight() - RegionScreen.this.font.width(details), y, VALUE_COLOR);
			}

			@Override
			public boolean keyPressed(KeyEvent event) {
				if (event.isSelection()) {
					RegionScreen.this.open(this.chunk);
					return true;
				}

				return false;
			}

			@Override
			public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
				ChunkList.this.setSelected(this);
				RegionScreen.this.updateButtons();
				if (doubleClick) {
					RegionScreen.this.open(this.chunk);
				}

				return true;
			}
		}
	}
}
