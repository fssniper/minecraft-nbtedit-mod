package nbtedit.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import nbtedit.client.tree.TreeNode;
import nbtedit.client.tree.TreeRows;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class TreeRowsTest {
	@Test
	void collapsedBranchesAreHidden() {
		Node root = Node.container("root", Node.leaf("kept", "one"), Node.container("closed", Node.leaf("hidden", "two")));
		root.expanded = true;
		assertEquals(List.of("root", "kept", "closed"), labels(root, ""));
	}

	@Test
	void expandedBranchesAreListed() {
		Node open = Node.container("open", Node.leaf("inside", "two"));
		open.expanded = true;
		Node root = Node.container("root", open);
		root.expanded = true;
		assertEquals(List.of("root", "open", "inside"), labels(root, ""));
	}

	@Test
	void filterKeepsThePathToAMatch() {
		Node deep = Node.container("middle", Node.leaf("needle", "value"), Node.leaf("other", "value"));
		Node root = Node.container("root", deep, Node.leaf("elsewhere", "value"));
		assertEquals(List.of("root", "middle", "needle"), labels(root, "needle"));
	}

	@Test
	void filterMatchesValuesOfLeavesOnly() {
		Node root = Node.container("root", Node.leaf("first", "hay"), Node.leaf("second", "needle"));
		assertEquals(List.of("root", "second"), labels(root, "needle"));
	}

	@Test
	void filterIgnoresCase() {
		Node root = Node.container("root", Node.leaf("Needle", "value"));
		assertEquals(List.of("root", "Needle"), labels(root, "needle"));
	}

	@Test
	void aMatchingContainerBringsItsCollapsedChildrenOnlyWhenExpanded() {
		Node collapsed = Node.container("needle", Node.leaf("child", "value"));
		Node root = Node.container("root", collapsed);
		assertEquals(List.of("root", "needle"), labels(root, "needle"));

		collapsed.expanded = true;
		assertEquals(List.of("root", "needle", "child"), labels(root, "needle"));
	}

	@Test
	void theRootIsAlwaysListed() {
		Node root = Node.container("root", Node.leaf("child", "value"));
		assertEquals(List.of("root"), labels(root, "nothing matches this"));
	}

	private static List<String> labels(Node root, String filter) {
		List<Node> rows = new ArrayList<>();
		TreeRows.collect(root, filter, rows);
		return rows.stream().map(Node::label).toList();
	}

	private static final class Node implements TreeNode<Node> {
		private final String label;
		private final String text;
		private final boolean container;
		private final List<Node> children = new ArrayList<>();
		private boolean expanded;

		private Node(String label, String text, boolean container) {
			this.label = label;
			this.text = text;
			this.container = container;
		}

		private static Node leaf(String label, String text) {
			return new Node(label, text, false);
		}

		private static Node container(String label, Node... children) {
			Node node = new Node(label, "", true);
			node.children.addAll(List.of(children));
			return node;
		}

		@Override
		public String label() {
			return this.label;
		}

		@Override
		public String badge() {
			return "";
		}

		@Override
		public String summary() {
			return this.text;
		}

		@Override
		public String searchText() {
			return this.text;
		}

		@Override
		public int color() {
			return 0;
		}

		@Override
		public @Nullable Node parent() {
			return null;
		}

		@Override
		public List<Node> children() {
			return this.children;
		}

		@Override
		public boolean isContainer() {
			return this.container;
		}

		@Override
		public boolean expanded() {
			return this.expanded;
		}

		@Override
		public void toggle() {
			this.expanded = !this.expanded;
		}

		@Override
		public void toggleBranch(int limit) {
			this.toggle();
		}

		@Override
		public int depth() {
			return 0;
		}
	}
}
