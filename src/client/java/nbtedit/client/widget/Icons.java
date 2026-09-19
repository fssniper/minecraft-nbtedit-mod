package nbtedit.client.widget;

import nbtedit.NBTEdit;
import net.minecraft.resources.Identifier;

public final class Icons {
	public static final Identifier ADD = icon("add");
	public static final Identifier DELETE = icon("delete");
	public static final Identifier EDIT_VALUE = icon("edit_value");
	public static final Identifier LIST = icon("list");
	public static final Identifier MODE_TEXT = icon("mode_text");
	public static final Identifier MODE_TREE = icon("mode_tree");
	public static final Identifier OPEN = icon("open");
	public static final Identifier RENAME = icon("rename");
	public static final Identifier SAVE = icon("save");
	public static final Identifier SEARCH = icon("search");
	public static final Identifier STOP = icon("stop");

	private Icons() {
	}

	private static Identifier icon(String name) {
		return Identifier.fromNamespaceAndPath(NBTEdit.MOD_ID, "icon/" + name);
	}
}
