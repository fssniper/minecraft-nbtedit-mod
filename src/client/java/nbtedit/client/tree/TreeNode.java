package nbtedit.client.tree;

import java.util.List;

public interface TreeNode<T extends TreeNode<T>> {
	String label();

	String badge();

	String summary();

	String searchText();

	int color();

	List<T> children();

	boolean isContainer();

	boolean expanded();

	void toggle();

	void toggleBranch(int limit);

	int depth();
}
