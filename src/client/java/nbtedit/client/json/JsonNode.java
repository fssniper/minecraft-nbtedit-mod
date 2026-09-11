package nbtedit.client.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import nbtedit.client.tree.TreeNode;
import org.jspecify.annotations.Nullable;

public final class JsonNode implements TreeNode<JsonNode> {
	private final @Nullable JsonNode parent;
	private @Nullable String key;
	private JsonElement element;
	private final List<JsonNode> children = new ArrayList<>();
	private boolean expanded;

	private JsonNode(@Nullable JsonNode parent, @Nullable String key, JsonElement element) {
		this.parent = parent;
		this.key = key;
		this.element = element;
		this.rebuild();
	}

	public static JsonNode root(String name, JsonElement element) {
		JsonNode node = new JsonNode(null, name, element);
		node.expanded = true;
		return node;
	}

	@Override
	public @Nullable JsonNode parent() {
		return this.parent;
	}

	public JsonElement element() {
		return this.element;
	}

	public @Nullable String key() {
		return this.key;
	}

	public boolean isRoot() {
		return this.parent == null;
	}

	public boolean isNamed() {
		return this.key != null;
	}

	public boolean acceptsKeys() {
		return this.element.isJsonObject();
	}

	@Override
	public String label() {
		return this.key != null ? this.key : "[" + this.indexInParent() + "]";
	}

	@Override
	public String badge() {
		return JsonValues.badge(JsonValues.kindOf(this.element));
	}

	@Override
	public String summary() {
		return JsonValues.summary(this.element);
	}

	@Override
	public String searchText() {
		return JsonValues.text(this.element);
	}

	@Override
	public int color() {
		return JsonValues.color(JsonValues.kindOf(this.element));
	}

	@Override
	public List<JsonNode> children() {
		return this.children;
	}

	@Override
	public boolean isContainer() {
		return JsonValues.isContainer(this.element);
	}

	@Override
	public boolean expanded() {
		return this.expanded;
	}

	@Override
	public void toggle() {
		this.expanded = !this.expanded && this.isContainer();
	}

	@Override
	public void toggleBranch(int limit) {
		if (this.expanded) {
			this.collapseBranch();
		} else {
			this.expandBranch(limit);
		}
	}

	@Override
	public int depth() {
		return this.parent == null ? 0 : this.parent.depth() + 1;
	}

	public int indexInParent() {
		return this.parent == null ? -1 : this.parent.children.indexOf(this);
	}

	public boolean replaceWith(JsonElement newElement) {
		if (this.parent == null) {
			return false;
		}

		if (this.parent.element instanceof JsonObject object && this.key != null) {
			object.add(this.key, newElement);
		} else if (this.parent.element instanceof JsonArray array) {
			array.set(this.indexInParent(), newElement);
		} else {
			return false;
		}

		this.parent.rebuild();
		return true;
	}

	public boolean rename(String newKey) {
		if (this.parent == null || this.key == null || !(this.parent.element instanceof JsonObject object)) {
			return false;
		}

		if (newKey.isEmpty() || newKey.equals(this.key) || object.has(newKey)) {
			return false;
		}

		object.remove(this.key);
		object.add(newKey, this.element);
		this.key = newKey;
		this.parent.rebuild();
		return true;
	}

	public boolean remove() {
		if (this.parent == null) {
			return false;
		}

		if (this.parent.element instanceof JsonObject object && this.key != null) {
			object.remove(this.key);
		} else if (this.parent.element instanceof JsonArray array) {
			array.remove(this.indexInParent());
		} else {
			return false;
		}

		this.parent.rebuild();
		return true;
	}

	public boolean canAddChild(@Nullable String childKey) {
		if (this.element instanceof JsonObject object) {
			return childKey != null && !childKey.isEmpty() && !object.has(childKey);
		}

		return this.element instanceof JsonArray;
	}

	public boolean addChild(@Nullable String childKey, JsonElement childElement) {
		return this.addChild(childKey, childElement, Integer.MAX_VALUE);
	}

	public boolean addChild(@Nullable String childKey, JsonElement childElement, int index) {
		if (this.element instanceof JsonObject object) {
			if (childKey == null || childKey.isEmpty() || object.has(childKey)) {
				return false;
			}

			object.add(childKey, childElement);
		} else if (this.element instanceof JsonArray array) {
			array.asList().add(Math.clamp(index, 0, array.size()), childElement);
		} else {
			return false;
		}

		this.expanded = true;
		this.rebuild();
		return true;
	}

	public void expandBranch(int limit) {
		Deque<JsonNode> pending = new ArrayDeque<>();
		pending.add(this);
		int opened = 0;
		while (!pending.isEmpty() && opened < limit) {
			JsonNode node = pending.poll();
			if (!node.isContainer()) {
				continue;
			}

			node.expanded = true;
			opened++;
			pending.addAll(node.children);
		}
	}

	public void collapseBranch() {
		this.expanded = false;
		for (JsonNode child : this.children) {
			child.collapseBranch();
		}
	}

	private void rebuild() {
		List<JsonNode> previous = new ArrayList<>(this.children);
		this.children.clear();
		if (this.element instanceof JsonObject object) {
			for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
				this.children.add(this.reuseOrCreate(previous, entry.getKey(), entry.getValue()));
			}
		} else if (this.element instanceof JsonArray array) {
			for (JsonElement child : array) {
				this.children.add(this.reuseOrCreate(previous, null, child));
			}
		}
	}

	private JsonNode reuseOrCreate(List<JsonNode> previous, @Nullable String childKey, JsonElement childElement) {
		Iterator<JsonNode> iterator = previous.iterator();
		while (iterator.hasNext()) {
			JsonNode node = iterator.next();
			if (node.element == childElement) {
				iterator.remove();
				node.key = childKey;
				return node;
			}
		}

		return new JsonNode(this, childKey, childElement);
	}
}
