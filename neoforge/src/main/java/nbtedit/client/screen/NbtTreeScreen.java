package nbtedit.client.screen;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.IOException;
import java.nio.file.Path;
import nbtedit.NBTEdit;
import nbtedit.client.nbt.NbtDocument;
import nbtedit.client.nbt.NbtNode;
import nbtedit.client.nbt.NbtValues;
import nbtedit.client.widget.IconButton;
import nbtedit.client.widget.Icons;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;
import org.jspecify.annotations.Nullable;

public class NbtTreeScreen extends TreeScreen<NbtNode> {
	private static final TagParser<Tag> SNBT_PARSER = TagParser.create(NbtOps.INSTANCE);

	private final NbtDocument document;
	private NbtNode root;
	private @Nullable Button valueButton;
	private @Nullable Button renameButton;
	private @Nullable Button addButton;
	private @Nullable Button deleteButton;
	private byte lastType = Tag.TAG_STRING;

	public NbtTreeScreen(Screen parent, NbtDocument document) {
		super(parent, document.title());
		this.document = document;
		this.root = NbtNode.root(document.name(), document.root());
	}

	@Override
	protected NbtNode root() {
		return this.root;
	}

	@Override
	protected void addActionButtons(LinearLayout actions) {
		this.valueButton = actions.addChild(new IconButton(Icons.EDIT_VALUE, Component.translatable("nbtedit.button.edit_value"), button -> this.editValue()));
		this.renameButton = actions.addChild(new IconButton(Icons.RENAME, Component.translatable("nbtedit.button.rename"), button -> this.beginRename()));
		this.addButton = actions.addChild(new IconButton(Icons.ADD, Component.translatable("nbtedit.button.add"), button -> this.addTag()));
		this.deleteButton = actions.addChild(new IconButton(Icons.DELETE, Component.translatable("nbtedit.button.delete"), button -> this.deleteTag()));
	}

	@Override
	protected void updateActionButtons() {
		NbtNode node = this.selectedNode();
		boolean editable = !this.readOnly();
		if (this.valueButton != null) {
			this.valueButton.active = editable && node != null && NbtValues.hasEditableText(node.tag());
		}

		if (this.renameButton != null) {
			this.renameButton.active = editable && node != null && this.isRenamable(node);
		}

		if (this.addButton != null) {
			this.addButton.active = editable && this.additionTarget() != null;
		}

		if (this.deleteButton != null) {
			this.deleteButton.active = editable && node != null && !node.isRoot();
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
		if (this.readOnly()) {
			return false;
		}

		switch (event.key()) {
			case InputConstants.KEY_F2 -> this.beginRename();
			case InputConstants.KEY_DELETE -> this.deleteTag();
			case InputConstants.KEY_INSERT -> this.addTag();
			default -> {
				return false;
			}
		}

		return true;
	}

	@Override
	protected String copyText(NbtNode node) {
		return node.tag().toString();
	}

	@Override
	protected @Nullable String keyOf(NbtNode node) {
		return node.key();
	}

	@Override
	protected boolean acceptsKeys(NbtNode target) {
		return target.acceptsKeys();
	}

	@Override
	protected boolean isKeyFree(NbtNode target, String key) {
		return target.canAddChild(key);
	}

	@Override
	protected @Nullable Component pasteChild(NbtNode target, @Nullable String key, String text, int index) {
		Tag tag;

		try {
			tag = SNBT_PARSER.parseFully(text.trim());
		} catch (CommandSyntaxException | RuntimeException e) {
			return Component.translatable("nbtedit.error.invalid_snbt");
		}

		if (!target.addChild(key, tag, index)) {
			return Component.translatable("nbtedit.error.paste_type", NbtValues.typeName(tag.getId()), NbtValues.typeName(target.tag().getId()));
		}

		return null;
	}

	@Override
	protected boolean duplicateInto(NbtNode target, @Nullable String key, NbtNode source, int index) {
		return target.addChild(key, source.tag().copy(), index);
	}

	@Override
	protected boolean removeNode(NbtNode node) {
		return node.remove();
	}

	@Override
	protected Runnable snapshot() {
		CompoundTag copy = this.document.root().copy();
		return () -> {
			this.document.setRoot(copy);
			this.root = NbtNode.root(this.document.name(), copy);
		};
	}

	@Override
	protected boolean writeFile() {
		try {
			this.toastSaved(this.document.path(), this.document.save());
			return true;
		} catch (IOException e) {
			NBTEdit.LOGGER.error("Failed to save {}", this.document.path(), e);
			this.toast(Component.translatable("nbtedit.toast.save_failed"), Component.literal(String.valueOf(e.getMessage())));
			return false;
		}
	}

	@Override
	protected @Nullable NbtNode additionTarget() {
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
						return parsed != null && this.edit(() -> node.replaceWith(parsed));
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
						if (!this.edit(() -> target.addChild(name, NbtValues.defaultTag(type)))) {
							return false;
						}

						this.lastType = type;
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
		if (node == null || node.isRoot() || !this.edit(node::remove)) {
			return;
		}

		this.clearSelection();
	}
}
