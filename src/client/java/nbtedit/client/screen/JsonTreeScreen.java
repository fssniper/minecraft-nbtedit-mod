package nbtedit.client.screen;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import nbtedit.NBTEdit;
import nbtedit.client.json.JsonFile;
import nbtedit.client.json.JsonNode;
import nbtedit.client.json.JsonValues;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public class JsonTreeScreen extends TreeScreen<JsonNode> {
	private static final int BUTTON_WIDTH = 100;
	private static final int MODE_BUTTON_WIDTH = 50;

	private final JsonFile file;
	private JsonNode root;
	private @Nullable Button valueButton;
	private @Nullable Button renameButton;
	private @Nullable Button addButton;
	private @Nullable Button deleteButton;
	private JsonValues.Kind lastKind = JsonValues.Kind.STRING;

	public JsonTreeScreen(Screen parent, JsonFile file) {
		super(parent, Component.literal(file.path().getFileName().toString()));
		this.file = file;
		this.root = JsonNode.root(file.path().getFileName().toString(), file.root());
	}

	@Override
	protected JsonNode root() {
		return this.root;
	}

	@Override
	protected void addActionButtons(GridLayout.RowHelper rows) {
		this.valueButton = rows.addChild(
			Button.builder(Component.translatable("nbtedit.button.edit_value"), button -> this.editValue()).width(BUTTON_WIDTH).build()
		);
		this.renameButton = rows.addChild(Button.builder(Component.translatable("nbtedit.button.rename"), button -> this.beginRename()).width(BUTTON_WIDTH).build());
		this.addButton = rows.addChild(Button.builder(Component.translatable("nbtedit.button.add_json"), button -> this.addValue()).width(BUTTON_WIDTH).build());
		this.deleteButton = rows.addChild(Button.builder(Component.translatable("nbtedit.button.delete"), button -> this.deleteValue()).width(BUTTON_WIDTH).build());
	}

	@Override
	protected void addHeaderControls(LinearLayout row) {
		Button treeButton = row.addChild(Button.builder(Component.translatable("nbtedit.button.mode_tree"), button -> {
		}).width(MODE_BUTTON_WIDTH).build());
		treeButton.active = false;
		row.addChild(Button.builder(Component.translatable("nbtedit.button.mode_text"), button -> this.editRaw()).width(MODE_BUTTON_WIDTH).build());
	}

	@Override
	protected void updateActionButtons() {
		JsonNode node = this.selectedNode();
		if (this.valueButton != null) {
			this.valueButton.active = node != null && JsonValues.hasEditableText(node.element());
		}

		if (this.renameButton != null) {
			this.renameButton.active = node != null && this.isRenamable(node);
		}

		if (this.addButton != null) {
			this.addButton.active = this.additionTarget() != null;
		}

		if (this.deleteButton != null) {
			this.deleteButton.active = node != null && !node.isRoot();
		}
	}

	@Override
	protected boolean isRenamable(JsonNode node) {
		return !node.isRoot() && node.isNamed();
	}

	@Override
	protected boolean applyRename(JsonNode node, String name) {
		return node.rename(name);
	}

	@Override
	protected boolean isInlineEditable(JsonNode node) {
		return JsonValues.hasEditableText(node.element());
	}

	@Override
	protected String inlineText(JsonNode node) {
		return JsonValues.text(node.element());
	}

	@Override
	protected boolean applyInlineEdit(JsonNode node, String text) {
		JsonElement parsed = JsonValues.parse(JsonValues.kindOf(node.element()), text);
		return parsed != null && node.replaceWith(parsed);
	}

	@Override
	protected boolean handleShortcut(KeyEvent event) {
		switch (event.key()) {
			case GLFW.GLFW_KEY_F2 -> this.beginRename();
			case GLFW.GLFW_KEY_DELETE -> this.deleteValue();
			case GLFW.GLFW_KEY_INSERT -> this.addValue();
			default -> {
				return false;
			}
		}

		return true;
	}

	@Override
	protected String copyText(JsonNode node) {
		return node.element().toString();
	}

	@Override
	protected @Nullable String keyOf(JsonNode node) {
		return node.key();
	}

	@Override
	protected boolean acceptsKeys(JsonNode target) {
		return target.acceptsKeys();
	}

	@Override
	protected boolean isKeyFree(JsonNode target, String key) {
		return target.canAddChild(key);
	}

	@Override
	protected @Nullable Component pasteChild(JsonNode target, @Nullable String key, String text, int index) {
		JsonElement element;

		try {
			element = JsonParser.parseString(text);
		} catch (RuntimeException e) {
			return Component.translatable("nbtedit.error.invalid_json");
		}

		return target.addChild(key, element, index) ? null : Component.translatable("nbtedit.error.invalid_json");
	}

	@Override
	protected boolean duplicateInto(JsonNode target, @Nullable String key, JsonNode source, int index) {
		return target.addChild(key, source.element().deepCopy(), index);
	}

	@Override
	protected boolean removeNode(JsonNode node) {
		return node.remove();
	}

	@Override
	protected boolean writeFile() {
		try {
			this.toastSaved(this.file.path(), this.file.save());
			return true;
		} catch (IOException e) {
			NBTEdit.LOGGER.error("Failed to save {}", this.file.path(), e);
			this.toast(Component.translatable("nbtedit.toast.save_failed"), Component.literal(String.valueOf(e.getMessage())));
			return false;
		}
	}

	@Override
	protected @Nullable JsonNode additionTarget() {
		JsonNode node = this.selectedNode();
		if (node == null) {
			return this.root.isContainer() ? this.root : null;
		}

		if (node.isContainer()) {
			return node;
		}

		JsonNode parent = node.parent();
		return parent != null && parent.isContainer() ? parent : null;
	}

	private void editValue() {
		JsonNode node = this.selectedNode();
		if (node == null || !JsonValues.hasEditableText(node.element())) {
			return;
		}

		JsonValues.Kind kind = JsonValues.kindOf(node.element());
		this.minecraft.gui
			.setScreen(
				new TextInputScreen(
					this,
					Component.translatable("nbtedit.edit.title", JsonValues.typeName(kind)),
					Component.literal(node.label()),
					JsonValues.text(node.element()),
					input -> {
						JsonElement parsed = JsonValues.parse(kind, input);
						if (parsed == null || !node.replaceWith(parsed)) {
							return false;
						}

						this.markDirty();
						return true;
					}
				)
			);
	}

	private void addValue() {
		JsonNode target = this.additionTarget();
		if (target == null) {
			return;
		}

		this.minecraft.gui
			.setScreen(
				new AddEntryScreen<>(
					this,
					this.pathOf(target),
					List.of(JsonValues.Kind.values()),
					this.lastKind,
					JsonValues::label,
					target.acceptsKeys(),
					target::canAddChild,
					(kind, name) -> {
						if (!target.addChild(name, JsonValues.defaultElement(kind))) {
							return false;
						}

						this.lastKind = kind;
						this.markDirty();
						JsonNode added = this.addedChild(target, name);
						if (added != null) {
							this.editValueAfterReturn(added);
						}

						return true;
					}
				)
			);
	}

	private void editRaw() {
		this.minecraft.gui
			.setScreen(
				new RawTextScreen(
					this,
					Component.translatable("nbtedit.raw.title", this.file.path().getFileName().toString()),
					JsonFile.toPrettyString(this.file.root()),
					input -> {
						if (input.isBlank()) {
							return false;
						}

						JsonElement parsed;

						try {
							parsed = JsonParser.parseString(input);
						} catch (RuntimeException e) {
							return false;
						}

						this.file.setRoot(parsed);
						this.root = JsonNode.root(this.file.path().getFileName().toString(), parsed);
						this.markDirty();
						return true;
					}
				)
			);
	}

	private void deleteValue() {
		JsonNode node = this.selectedNode();
		if (node == null || node.isRoot() || !node.remove()) {
			return;
		}

		this.clearSelection();
		this.markDirty();
	}
}
