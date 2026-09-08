package nbtedit.client.screen;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import nbtedit.client.tree.TreeNode;
import nbtedit.client.tree.TreeRows;
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
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public abstract class TreeScreen<T extends TreeNode<T>> extends Screen {
	private static final int LABEL_COLOR = 0xFFFFFFFF;
	private static final int ROW_HEIGHT = 14;
	private static final int INDENT = 10;
	private static final int SCROLL_ROWS = 3;
	private static final int HEADER_HEIGHT = 8 + 9 + 8 + 20 + 4;
	private static final int SEARCH_WIDTH = 200;
	private static final int FOOTER_HEIGHT = 60;
	private static final int FOOTER_COLUMNS = 3;
	private static final int BUTTON_WIDTH = 100;
	private static final int VALUE_COLOR = 0xFFA0A0A0;
	private static final int ERROR_COLOR = 0xFFFF6060;
	private static final int MIN_INLINE_WIDTH = 60;

	private final Screen parent;
	private final HeaderAndFooterLayout layout;
	private final int footerColumns;
	private @Nullable TreeList list;
	private @Nullable EditBox searchBox;
	private @Nullable Button saveButton;
	private @Nullable EditBox inlineEditor;
	private @Nullable T inlineNode;
	private InlineMode inlineMode = InlineMode.VALUE;
	private String filter = "";
	private boolean dirty;

	protected TreeScreen(Screen parent, Component title) {
		this(parent, title, FOOTER_HEIGHT, FOOTER_COLUMNS);
	}

	protected TreeScreen(Screen parent, Component title, int footerHeight, int footerColumns) {
		super(title);
		this.parent = parent;
		this.layout = new HeaderAndFooterLayout(this, HEADER_HEIGHT, footerHeight);
		this.footerColumns = footerColumns;
	}

	protected abstract T root();

	protected abstract void addActionButtons(GridLayout.RowHelper rows);

	protected abstract void updateActionButtons();

	protected abstract boolean writeFile();

	protected boolean isInlineEditable(T node) {
		return false;
	}

	protected String inlineText(T node) {
		return node.searchText();
	}

	protected boolean applyInlineEdit(T node, String text) {
		return false;
	}

	protected boolean isRenamable(T node) {
		return false;
	}

	protected boolean applyRename(T node, String name) {
		return false;
	}

	protected boolean handleShortcut(KeyEvent event) {
		return false;
	}

	protected void addHeaderControls(LinearLayout row) {
	}

	protected int branchExpandLimit() {
		return 10000;
	}

	@Override
	protected void init() {
		LinearLayout header = this.layout.addToHeader(LinearLayout.vertical().spacing(4));
		header.defaultCellSetting().alignHorizontallyCenter();
		header.addChild(new StringWidget(this.title, this.font));
		LinearLayout controls = header.addChild(LinearLayout.horizontal().spacing(4));
		Component searchLabel = Component.translatable("nbtedit.search.hint");
		this.searchBox = controls.addChild(new EditBox(this.font, SEARCH_WIDTH, 20, searchLabel));
		this.searchBox.setHint(searchLabel.copy().setStyle(EditBox.SEARCH_HINT_STYLE));
		this.searchBox.setValue(this.filter);
		this.searchBox.setResponder(value -> {
			this.filter = value.trim().toLowerCase(Locale.ROOT);
			this.refreshRows();
		});
		this.addHeaderControls(controls);
		TreeList treeList = new TreeList(this.minecraft, this.width, this.layout.getContentHeight(), this.layout.getHeaderHeight());
		this.list = this.layout.addToContents(treeList);
		GridLayout footer = this.layout.addToFooter(new GridLayout().columnSpacing(4).rowSpacing(4));
		footer.defaultCellSetting().alignHorizontallyCenter();
		GridLayout.RowHelper rows = footer.createRowHelper(this.footerColumns);
		this.addActionButtons(rows);
		this.saveButton = rows.addChild(Button.builder(Component.translatable("nbtedit.button.save"), button -> this.save()).width(BUTTON_WIDTH).build());
		rows.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).width(BUTTON_WIDTH).build());
		this.layout.visitWidgets(this::addRenderableWidget);
		this.repositionElements();
		treeList.rebuild();
		this.updateButtons();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (this.inlineEditor != null && !this.inlineEditor.isMouseOver(event.x(), event.y())) {
			this.commitInlineEdit();
			this.closeInlineEdit();
		}

		boolean handled = super.mouseClicked(event, doubleClick);
		if (this.inlineEditor != null) {
			this.setFocused(this.inlineEditor);
		}

		return handled;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (this.inlineEditor != null) {
			this.commitInlineEdit();
			this.closeInlineEdit();
		}

		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	protected void repositionElements() {
		this.closeInlineEdit();
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
	public boolean keyPressed(KeyEvent event) {
		if (this.inlineEditor != null) {
			if (event.isConfirmation()) {
				if (this.commitInlineEdit()) {
					this.closeInlineEdit();
					this.focusList();
				} else {
					this.inlineEditor.setTextColor(ERROR_COLOR);
				}

				return true;
			}

			if (event.isEscape()) {
				this.closeInlineEdit();
				this.focusList();
				return true;
			}
		}

		if (!(this.getFocused() instanceof EditBox) && this.handleShortcut(event)) {
			return true;
		}

		if (event.hasControlDown() && event.key() == GLFW.GLFW_KEY_S) {
			this.save();
			return true;
		}

		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		if (!this.dirty) {
			this.minecraft.gui.setScreen(this.parent);
			return;
		}

		this.minecraft.gui
			.setScreen(
				new ConfirmScreen(
					saveFirst -> {
						if (saveFirst) {
							this.save();
						}

						this.minecraft.gui.setScreen(this.parent);
					},
					Component.translatable("nbtedit.unsaved.title"),
					Component.translatable("nbtedit.unsaved.message"),
					Component.translatable("nbtedit.button.save"),
					Component.translatable("nbtedit.button.discard")
				)
			);
	}

	@Override
	protected void setInitialFocus() {
		if (this.list != null) {
			this.setInitialFocus(this.list);
		}
	}

	private void focusList() {
		if (this.list != null) {
			this.setFocused(this.list);
		}
	}

	protected final @Nullable T selectedNode() {
		if (this.list == null) {
			return null;
		}

		TreeList.Row row = this.list.getSelected();
		return row == null ? null : row.node();
	}

	protected final void clearSelection() {
		if (this.list != null) {
			this.list.setSelected(null);
		}
	}

	protected final void beginRename() {
		if (this.list == null) {
			return;
		}

		TreeList.Row row = this.list.getSelected();
		if (row != null) {
			this.beginInlineEdit(row, InlineMode.NAME);
		}
	}

	private void beginInlineEdit(TreeList.Row row, InlineMode mode) {
		T node = row.node();
		if (!(mode == InlineMode.NAME ? this.isRenamable(node) : this.isInlineEditable(node))) {
			return;
		}

		this.closeInlineEdit();
		int x = mode == InlineMode.NAME ? row.labelX() : row.valueX();
		int width = Math.max(MIN_INLINE_WIDTH, row.getContentRight() - x);
		EditBox box = new EditBox(this.font, x, row.getY(), width, ROW_HEIGHT, Component.literal(node.label()));
		box.setMaxLength(Short.MAX_VALUE);
		box.setValue(mode == InlineMode.NAME ? node.label() : this.inlineText(node));
		box.moveCursorToEnd(false);
		this.inlineEditor = this.addRenderableWidget(box);
		this.inlineNode = node;
		this.inlineMode = mode;
		this.setFocused(box);
	}

	private boolean commitInlineEdit() {
		EditBox box = this.inlineEditor;
		T node = this.inlineNode;
		if (box == null || node == null) {
			return false;
		}

		boolean applied = this.inlineMode == InlineMode.NAME
			? this.applyRename(node, box.getValue().trim())
			: this.applyInlineEdit(node, box.getValue());
		if (!applied) {
			return false;
		}

		this.markDirty();
		return true;
	}

	private void closeInlineEdit() {
		EditBox box = this.inlineEditor;
		this.inlineEditor = null;
		this.inlineNode = null;
		if (box != null) {
			this.removeWidget(box);
			this.setFocused(null);
		}
	}

	protected final void markDirty() {
		this.dirty = true;
		this.refreshRows();
		this.updateButtons();
	}

	protected final void refreshRows() {
		if (this.list != null) {
			this.list.rebuild();
		}
	}

	protected final void toastSaved(Path file, @Nullable Path backup) {
		Component message = backup == null
			? Component.literal(file.getFileName().toString())
			: Component.translatable("nbtedit.toast.backup", backup.getFileName().toString());
		this.toast(Component.translatable("nbtedit.toast.saved"), message);
	}

	protected final void toast(Component title, Component message) {
		this.minecraft.gui.toastManager().addToast(new SystemToast(SystemToast.SystemToastId.WORLD_BACKUP, title, message));
	}

	private void save() {
		if (!this.dirty) {
			return;
		}

		if (this.writeFile()) {
			this.dirty = false;
		}

		this.updateButtons();
	}

	private void updateButtons() {
		if (this.saveButton != null) {
			this.saveButton.active = this.dirty;
		}

		this.updateActionButtons();
	}

	private enum InlineMode {
		VALUE,
		NAME
	}

	private final class TreeList extends ObjectSelectionList<TreeList.Row> {
		private final Map<T, Row> rows = new IdentityHashMap<>();

		TreeList(Minecraft minecraft, int width, int height, int y) {
			super(minecraft, width, height, y, ROW_HEIGHT);
		}

		@Override
		public int getRowWidth() {
			return Math.min(TreeScreen.this.width - 24, 420);
		}

		@Override
		protected double scrollRate() {
			return ROW_HEIGHT * SCROLL_ROWS;
		}

		void focusNode(T node) {
			Row row = this.rows.get(node);
			if (row != null) {
				this.setFocused(row);
				TreeScreen.this.updateButtons();
			}
		}

		void rebuild() {
			Row previousSelection = this.getSelected();
			Map<T, Row> previousRows = new IdentityHashMap<>(this.rows);
			List<T> visible = new ArrayList<>();
			TreeRows.collect(TreeScreen.this.root(), TreeScreen.this.filter, visible);
			this.rows.clear();
			this.clearEntries();
			for (T node : visible) {
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
			private final T node;

			Row(T node) {
				this.node = node;
			}

			T node() {
				return this.node;
			}

			int labelX() {
				int x = this.getContentX() + this.node.depth() * INDENT + 8;
				return x + TreeScreen.this.font.width(this.node.badge()) + 4;
			}

			int valueX() {
				return this.labelX() + TreeScreen.this.font.width(this.node.label());
			}

			@Override
			public Component getNarration() {
				return Component.literal(this.node.label());
			}

			@Override
			public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
				int x = this.valueX();
				int y = this.getContentY() + 1;
				int labelX = this.getContentX() + this.node.depth() * INDENT;
				if (this.node.isContainer() && !this.node.children().isEmpty()) {
					graphics.text(TreeScreen.this.font, this.node.expanded() ? "-" : "+", labelX, y, LABEL_COLOR);
				}

				labelX += 8;
				String badge = this.node.badge();
				graphics.text(TreeScreen.this.font, badge, labelX, y, this.node.color());
				labelX += TreeScreen.this.font.width(badge) + 4;
				graphics.text(TreeScreen.this.font, this.node.label(), labelX, y, LABEL_COLOR);
				int room = this.getContentRight() - x - 4;
				if (room > 20) {
					String value = ": " + this.node.summary().replace('\n', ' ');
					graphics.text(TreeScreen.this.font, TreeScreen.this.font.plainSubstrByWidth(value, room), x, y, VALUE_COLOR);
				}
			}

			private void toggle(boolean wholeBranch) {
				if (wholeBranch) {
					this.node.toggleBranch(TreeScreen.this.branchExpandLimit());
				} else {
					this.node.toggle();
				}

				TreeList.this.rebuild();
			}

			@Override
			public boolean keyPressed(KeyEvent event) {
				if (event.isRight()) {
					if (this.node.isContainer() && !this.node.children().isEmpty()) {
						if (this.node.expanded()) {
							TreeList.this.focusNode(this.node.children().getFirst());
						} else {
							this.toggle(event.hasShiftDown());
						}
					}

					return true;
				}

				if (event.isLeft()) {
					if (this.node.isContainer() && this.node.expanded()) {
						this.toggle(event.hasShiftDown());
					} else {
						T parent = this.node.parent();
						if (parent != null) {
							TreeList.this.focusNode(parent);
						}
					}

					return true;
				}

				if (event.isSelection()) {
					if (this.node.isContainer()) {
						this.toggle(event.hasShiftDown());
					} else {
						TreeScreen.this.beginInlineEdit(this, InlineMode.VALUE);
					}

					return true;
				}

				return false;
			}

			@Override
			public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
				if (this.node.isContainer() && !doubleClick) {
					this.toggle(event.hasShiftDown());
				}

				TreeList.this.setSelected(this);
				TreeScreen.this.updateButtons();
				if (doubleClick) {
					boolean overLabel = event.x() < this.valueX();
					TreeScreen.this.beginInlineEdit(this, overLabel ? InlineMode.NAME : InlineMode.VALUE);
				}

				return true;
			}
		}
	}
}
