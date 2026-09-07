package nbtedit.client.screen;

import java.io.IOException;
import java.nio.file.Path;
import nbtedit.NBTEdit;
import nbtedit.client.nbt.NbtFile;
import nbtedit.client.nbt.NbtNode;
import nbtedit.client.nbt.NbtValues;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public class NbtTreeScreen extends TreeScreen<NbtNode> {
	private static final int BUTTON_WIDTH = 100;

	private final NbtFile file;
	private final NbtNode root;
	private @Nullable Button valueButton;
	private @Nullable Button renameButton;
	private @Nullable Button addButton;
	private @Nullable Button deleteButton;

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
		this.renameButton = rows.addChild(Button.builder(Component.translatable("nbtedit.button.rename"), button -> this.rename()).width(BUTTON_WIDTH).build());
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
			this.renameButton.active = node != null && !node.isRoot() && node.isNamed();
		}

		if (this.addButton != null) {
			this.addButton.active = this.additionTarget() != null;
		}

		if (this.deleteButton != null) {
			this.deleteButton.active = node != null && !node.isRoot();
		}
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
	protected boolean writeFile() {
		try {
			Path backup = this.file.save();
			this.toast(Component.translatable("nbtedit.toast.saved"), Component.literal(backup.getFileName().toString()));
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

	private void rename() {
		NbtNode node = this.selectedNode();
		if (node == null || node.isRoot() || !node.isNamed()) {
			return;
		}

		this.minecraft.gui
			.setScreen(
				new TextInputScreen(this, Component.translatable("nbtedit.rename.title"), Component.translatable("nbtedit.add.name"), node.label(), input -> {
					if (!node.rename(input.trim())) {
						return false;
					}

					this.markDirty();
					return true;
				})
			);
	}

	private void addTag() {
		NbtNode target = this.additionTarget();
		if (target == null) {
			return;
		}

		this.minecraft.gui.setScreen(new AddTagScreen(this, target, added -> {
			if (added) {
				this.markDirty();
			}
		}));
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
