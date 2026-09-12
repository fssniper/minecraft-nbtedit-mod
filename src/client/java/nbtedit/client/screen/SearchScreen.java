package nbtedit.client.screen;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import nbtedit.NBTEdit;
import nbtedit.client.json.JsonFile;
import nbtedit.client.nbt.NbtFile;
import nbtedit.client.search.SearchHit;
import nbtedit.client.search.WorldSearch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
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
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public class SearchScreen extends Screen {
	private static final int ROW_HEIGHT = 22;
	private static final int SCROLL_ROWS = 2;
	private static final int HEADER_HEIGHT = 8 + 9 + 8 + 20 + 4 + 12;
	private static final int FOOTER_HEIGHT = 40;
	private static final int QUERY_WIDTH = 200;
	private static final int CYCLE_WIDTH = 100;
	private static final int BUTTON_WIDTH = 100;
	private static final int PATH_COLOR = 0xFFFFFFFF;
	private static final int VALUE_COLOR = 0xFFA0A0A0;
	private static final int WHERE_COLOR = 0xFF7FD8FF;
	private static final int STATUS_COLOR = 0xFF9A9A9A;

	private final Screen parent;
	private final Path worldRoot;
	private final List<SearchHit> hits = new ArrayList<>();
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, HEADER_HEIGHT, FOOTER_HEIGHT);
	private boolean searchRegions = true;
	private String query = "";
	private @Nullable WorldSearch search;
	private @Nullable HitList list;
	private @Nullable Button startButton;
	private @Nullable Button openButton;

	public SearchScreen(Screen parent, Path worldRoot) {
		super(Component.translatable("nbtedit.search.title"));
		this.parent = parent;
		this.worldRoot = worldRoot;
	}

	@Override
	protected void init() {
		LinearLayout header = this.layout.addToHeader(LinearLayout.vertical().spacing(4));
		header.defaultCellSetting().alignHorizontallyCenter();
		header.addChild(new StringWidget(this.title, this.font));
		LinearLayout controls = header.addChild(LinearLayout.horizontal().spacing(4));
		Component hint = Component.translatable("nbtedit.search.hint");
		EditBox queryBox = controls.addChild(new EditBox(this.font, QUERY_WIDTH, 20, hint));
		queryBox.setHint(hint.copy().setStyle(EditBox.SEARCH_HINT_STYLE));
		queryBox.setValue(this.query);
		queryBox.setResponder(value -> this.query = value.trim());
		controls.addChild(
			CycleButton.onOffBuilder(this.searchRegions)
				.create(0, 0, CYCLE_WIDTH, 20, Component.translatable("nbtedit.search.regions"), (button, value) -> this.searchRegions = value)
		);
		this.startButton = controls.addChild(Button.builder(Component.translatable("nbtedit.button.search"), button -> this.toggleSearch()).width(BUTTON_WIDTH).build());
		HitList hitList = new HitList(this.minecraft, this.width, this.layout.getContentHeight(), this.layout.getHeaderHeight());
		this.list = this.layout.addToContents(hitList);
		GridLayout footer = this.layout.addToFooter(new GridLayout().columnSpacing(8).rowSpacing(4));
		footer.defaultCellSetting().alignHorizontallyCenter();
		GridLayout.RowHelper rows = footer.createRowHelper(2);
		this.openButton = rows.addChild(Button.builder(Component.translatable("nbtedit.button.open"), button -> this.openSelected()).width(BUTTON_WIDTH).build());
		rows.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).width(BUTTON_WIDTH).build());
		this.layout.visitWidgets(this::addRenderableWidget);
		this.repositionElements();
		hitList.rebuild();
		this.setInitialFocus(queryBox);
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
		WorldSearch running = this.search;
		if (running != null) {
			List<SearchHit> found = running.drain();
			if (!found.isEmpty()) {
				this.hits.addAll(found);
				if (this.list != null) {
					this.list.rebuild();
				}
			}

			if (running.isDone()) {
				this.search = null;
			}
		}

		this.updateButtons();
	}

	@Override
	public void onClose() {
		this.stopSearch();
		this.minecraft.gui.setScreen(this.parent);
	}

	@Override
	public void removed() {
		this.stopSearch();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isConfirmation() && this.getFocused() instanceof EditBox) {
			this.toggleSearch();
			return true;
		}

		return super.keyPressed(event);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		Component status = this.status();
		graphics.text(this.font, status, (this.width - this.font.width(status)) / 2, this.layout.getHeaderHeight() - 12, STATUS_COLOR);
	}

	private Component status() {
		WorldSearch running = this.search;
		if (running == null) {
			return this.hits.isEmpty()
				? Component.translatable("nbtedit.search.idle")
				: Component.translatable("nbtedit.search.finished", this.hits.size());
		}

		if (running.reachedLimit()) {
			return Component.translatable("nbtedit.search.limit", this.hits.size());
		}

		return Component.translatable("nbtedit.search.running", running.scanned(), running.total(), this.hits.size());
	}

	private void toggleSearch() {
		if (this.search != null) {
			this.stopSearch();
			return;
		}

		if (this.query.isEmpty()) {
			return;
		}

		this.hits.clear();
		if (this.list != null) {
			this.list.rebuild();
		}

		this.search = WorldSearch.start(this.worldRoot, this.query, this.searchRegions, WorldSearch.DEFAULT_LIMIT);
		this.updateButtons();
	}

	private void stopSearch() {
		WorldSearch running = this.search;
		this.search = null;
		if (running != null) {
			this.hits.addAll(running.drain());
			running.close();
			if (this.list != null) {
				this.list.rebuild();
			}
		}
	}

	private void updateButtons() {
		if (this.startButton != null) {
			this.startButton.setMessage(Component.translatable(this.search == null ? "nbtedit.button.search" : "nbtedit.button.stop"));
			this.startButton.active = this.search != null || !this.query.isEmpty();
		}

		if (this.openButton != null) {
			this.openButton.active = this.selectedHit() != null;
		}
	}

	private @Nullable SearchHit selectedHit() {
		if (this.list == null) {
			return null;
		}

		HitList.Row row = this.list.getSelected();
		return row == null ? null : row.hit();
	}

	private void openSelected() {
		SearchHit hit = this.selectedHit();
		if (hit != null) {
			this.open(hit);
		}
	}

	private void open(SearchHit hit) {
		this.stopSearch();

		try {
			if (hit.chunk() != null) {
				ChunkEditScreen.open(this, hit.file(), hit.chunk().x(), hit.chunk().z(), () -> {
				}, hit.path());
			} else if (WorldSearch.isJson(hit.file())) {
				this.show(new JsonTreeScreen(this, JsonFile.load(hit.file())), hit);
			} else {
				this.show(new NbtTreeScreen(this, NbtFile.load(hit.file())), hit);
			}
		} catch (Exception e) {
			NBTEdit.LOGGER.error("Failed to open {} from the search", hit.file(), e);
			SystemToast.addOrUpdate(
				this.minecraft.gui.toastManager(),
				SystemToast.SystemToastId.WORLD_ACCESS_FAILURE,
				Component.translatable("nbtedit.toast.read_failed"),
				Component.literal(hit.file().getFileName().toString())
			);
		}
	}

	private void show(TreeScreen<?> screen, SearchHit hit) {
		screen.revealAfterOpen(hit.path());
		this.minecraft.gui.setScreen(screen);
	}

	private final class HitList extends ObjectSelectionList<HitList.Row> {
		private final Map<SearchHit, Row> rows = new IdentityHashMap<>();

		HitList(Minecraft minecraft, int width, int height, int y) {
			super(minecraft, width, height, y, ROW_HEIGHT);
		}

		@Override
		public int getRowWidth() {
			return Math.min(SearchScreen.this.width - 24, 460);
		}

		@Override
		protected double scrollRate() {
			return ROW_HEIGHT * SCROLL_ROWS;
		}

		void rebuild() {
			Row previousSelection = this.getSelected();
			Map<SearchHit, Row> previousRows = new IdentityHashMap<>(this.rows);
			this.rows.clear();
			this.clearEntries();
			for (SearchHit hit : SearchScreen.this.hits) {
				Row row = previousRows.get(hit);
				if (row == null) {
					row = new Row(hit);
				}

				this.rows.put(hit, row);
				this.addEntry(row);
			}

			if (previousSelection != null && this.rows.get(previousSelection.hit()) == previousSelection) {
				this.setSelected(previousSelection);
			}
		}

		final class Row extends ObjectSelectionList.Entry<Row> {
			private final SearchHit hit;

			Row(SearchHit hit) {
				this.hit = hit;
			}

			SearchHit hit() {
				return this.hit;
			}

			@Override
			public Component getNarration() {
				return Component.literal(this.hit.label());
			}

			@Override
			public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
				int x = this.getContentX();
				int y = this.getContentY() + 1;
				int room = this.getContentRight() - x;
				String path = this.hit.label();
				graphics.text(SearchScreen.this.font, SearchScreen.this.font.plainSubstrByWidth(path, room), x, y, PATH_COLOR);
				String where = this.hit.where();
				int whereWidth = SearchScreen.this.font.width(where);
				graphics.text(SearchScreen.this.font, where, x, y + 10, WHERE_COLOR);
				String value = this.hit.value();
				int valueRoom = room - whereWidth - 8;
				if (valueRoom > 20) {
					graphics.text(SearchScreen.this.font, SearchScreen.this.font.plainSubstrByWidth(value, valueRoom), x + whereWidth + 8, y + 10, VALUE_COLOR);
				}
			}

			@Override
			public boolean keyPressed(KeyEvent event) {
				if (event.isSelection()) {
					SearchScreen.this.open(this.hit);
					return true;
				}

				return false;
			}

			@Override
			public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
				HitList.this.setSelected(this);
				SearchScreen.this.updateButtons();
				if (doubleClick) {
					SearchScreen.this.open(this.hit);
				}

				return true;
			}
		}
	}
}
