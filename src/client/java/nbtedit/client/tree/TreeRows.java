package nbtedit.client.tree;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TreeRows {
	private TreeRows() {
	}

	public static <T extends TreeNode<T>> void collect(T root, String filter, List<T> out) {
		if (filter.isEmpty()) {
			collectExpanded(root, out);
			return;
		}

		String query = filter.toLowerCase(Locale.ROOT);
		out.add(root);
		for (T child : root.children()) {
			collectMatching(child, query, out);
		}
	}

	private static <T extends TreeNode<T>> void collectExpanded(T node, List<T> out) {
		out.add(node);
		if (node.expanded()) {
			for (T child : node.children()) {
				collectExpanded(child, out);
			}
		}
	}

	private static <T extends TreeNode<T>> boolean collectMatching(T node, String query, List<T> out) {
		List<T> childRows = new ArrayList<>();
		boolean matchBelow = false;
		for (T child : node.children()) {
			matchBelow |= collectMatching(child, query, childRows);
		}

		if (!matchBelow && !matches(node, query)) {
			return false;
		}

		out.add(node);
		if (matchBelow) {
			out.addAll(childRows);
		} else if (node.expanded()) {
			for (T child : node.children()) {
				collectExpanded(child, out);
			}
		}

		return true;
	}

	private static <T extends TreeNode<T>> boolean matches(T node, String query) {
		if (node.label().toLowerCase(Locale.ROOT).contains(query)) {
			return true;
		}

		return !node.isContainer() && node.searchText().toLowerCase(Locale.ROOT).contains(query);
	}
}
