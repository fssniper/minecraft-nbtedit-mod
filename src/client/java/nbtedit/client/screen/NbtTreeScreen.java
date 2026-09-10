package nbtedit.client.screen;

import java.io.IOException;
import java.nio.file.Path;
import nbtedit.NBTEdit;
import nbtedit.client.nbt.NbtFile;
import nbtedit.client.nbt.NbtNode;
import nbtedit.client.nbt.NbtValues;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public class NbtTreeScreen extends TreeScreen<NbtNode> {
	private static final int BUTTON_WIDTH = 100;

	private final NbtFile file;
	private final NbtNode root;
	private @Nullable Button valueButton;
	private @Nullable Button renameButton;
	private @Nullable Button addButton;
	private @Nullable Button deleteButton;
	private byte lastType = Tag.TAG_STRING;

	public NbtTreeScreen(Screen parent, NbtFile file) {
		super(parent, Component.literal(file.path().getFileName().toString()));
		this.file = file;
		this.root = NbtNode.root(file.path().getFileName().toString(), file.root());
	}

	@Override
	protected NbtNode root() {
		return this.root;
	}

	@Override
	protected void addActionButtons(GridLayout.RowHelper rows) {
		this.valueButton = rows.addChild(
			Button.builder(Component.translatable("nbtedit.button.edit_value"), button -> this.editValue()).width(BUTTON_WIDTH).build()
		);
		this.renameButton = rows.addChild(Button.builder(Component.translatable("nbtedit.button.rename"), button -> this.beginRename()).width(BUTTON_WIDTH).build());
		this.addButton = rows.addChild(Button.builder(Component.translatable("nbtedit.button.add"), button -> this.addTag()).width(BUTTON_WIDTH).build());
		this.deleteButton = rows.addChild(Button.builder(Component.translatable("nbtedit.button.delete"), button -> this.deleteTag()).width(BUTTON_WIDTH).build());
	}

	@Override
	protected void updateActionButtons() {
		NbtNode node = this.selectedNode();
		if (this.valueButton != null) {
			this.valueButton.active = node != null && NbtValues.hasEditableText(node.tag());
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
	protected boolean isRenamable(NbtNode node) {
		return !node.isRoot() && node.isNamed();
	}

	@Override
	protected boolean applyRename(NbtNode node, String name) {
		return node.rename(name);
	}

	@Override
	protected boolean isInlineEditable(NbtNode node) {
		return NbtValues.hasEditableText(node.tag());
	}

	@Override
	protected String inlineText(NbtNode node) {
		return NbtValues.text(node.tag());
	}

	@Override
	protected boolean applyInlineEdit(NbtNode node, String text) {
		Tag parsed = NbtValues.parse(node.tag().getId(), text);
		return parsed != null && node.replaceWith(parsed);
	}

	@Override
	protected boolean handleShortcut(KeyEvent event) {
		switch (event.key()) {
			case GLFW.GLFW_KEY_F2 -> this.beginRename();
			case GLFW.GLFW_KEY_DELETE -> this.deleteTag();
			case GLFW.GLFW_KEY_INSERT -> this.addTag();
			default -> {
				return false;
			}
		}

		return true;
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

	private @Nullable NbtNode additionTarget() {
		NbtNode node = this.selectedNode();
		if (node == null) {
			return this.root;
		}

		if (node.isContainer()) {
			return node;
		}

		return node.parent();
	}

	private void editValue() {
		NbtNode node = this.selectedNode();
		if (node == null || !NbtValues.hasEditableText(node.tag())) {
			return;
		}

		byte type = node.tag().getId();
		this.minecraft.gui
			.setScreen(
				new TextInputScreen(
					this,
					Component.translatable("nbtedit.edit.title", NbtValues.typeName(type)),
					Component.literal(node.label()),
					NbtValues.text(node.tag()),
					input -> {
						Tag parsed = NbtValues.parse(type, input);
						if (parsed == null || !node.replaceWith(parsed)) {
							return false;
						}

						this.markDirty();
						return true;
					}
				)
			);
	}

	private void addTag() {
		NbtNode target = this.additionTarget();
		if (target == null) {
			return;
		}

		this.minecraft.gui
			.setScreen(
				new AddEntryScreen<>(
					this,
					this.pathOf(target),
					NbtValues.creatableTypes(),
					this.lastType,
					NbtValues::label,
					target.acceptsKeys(),
					target::canAddChild,
					(type, name) -> {
						if (!target.addChild(name, NbtValues.defaultTag(type))) {
							return false;
						}

						this.lastType = type;
						this.markDirty();
						NbtNode added = this.addedChild(target, name);
						if (added != null) {
							this.editValueAfterReturn(added);
						}

						return true;
					}
				)
			);
	}

	private void deleteTag() {
		NbtNode node = this.selectedNode();
		if (node == null || node.isRoot() || !node.remove()) {
			return;
		}

		this.clearSelection();
		this.markDirty();
	}
}
