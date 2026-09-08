package nbtedit.client.tree;

import java.util.List;
import org.jspecify.annotations.Nullable;

public interface TreeNode<T extends TreeNode<T>> {
	String label();

	String badge();

	String summary();

	String searchText();

	int color();

	@Nullable T parent();

	List<T> children();

	boolean isContainer();

	boolean expanded();

	void toggle();

	void toggleBranch(int limit);

	int depth();
}
