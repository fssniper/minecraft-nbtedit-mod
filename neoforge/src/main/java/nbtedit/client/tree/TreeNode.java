package nbtedit.client.tree;

import java.util.List;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public interface TreeNode<T extends TreeNode<T>> {
	String label();

	String badge();

	String summary();

	String searchText();

	Component typeLabel();

	int color();

	@Nullable T parent();

	List<T> children();

	boolean isContainer();

	boolean expanded();

	void toggle();

	void toggleBranch(int limit);

	int depth();
}
