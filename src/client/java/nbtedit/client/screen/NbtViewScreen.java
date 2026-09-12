package nbtedit.client.screen;

import nbtedit.client.nbt.NbtNode;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public class NbtViewScreen extends TreeScreen<NbtNode> {
	private static final int FOOTER_HEIGHT = 40;
	private static final int FOOTER_COLUMNS = 1;

	private final NbtNode root;

	public NbtViewScreen(Screen parent, Component title, String rootName, CompoundTag tag) {
		super(parent, title, FOOTER_HEIGHT, FOOTER_COLUMNS);
		this.root = NbtNode.root(rootName, tag);
	}

	@Override
	protected NbtNode root() {
		return this.root;
	}

	@Override
	protected boolean readOnly() {
		return true;
	}

	@Override
	protected void addActionButtons(GridLayout.RowHelper rows) {
	}

	@Override
	protected void updateActionButtons() {
	}

	@Override
	protected boolean writeFile() {
		return false;
	}

	@Override
	protected Runnable snapshot() {
		return () -> {
		};
	}

	@Override
	protected String copyText(NbtNode node) {
		return node.tag().toString();
	}

	@Override
	protected @Nullable String keyOf(NbtNode node) {
		return node.key();
	}
}
