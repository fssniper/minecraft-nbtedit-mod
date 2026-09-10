package nbtedit.client.nbt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import nbtedit.client.tree.TreeNode;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jspecify.annotations.Nullable;

public final class NbtNode implements TreeNode<NbtNode> {
	private final @Nullable NbtNode parent;
	private @Nullable String key;
	private Tag tag;
	private final List<NbtNode> children = new ArrayList<>();
	private boolean expanded;

	private NbtNode(@Nullable NbtNode parent, @Nullable String key, Tag tag) {
		this.parent = parent;
		this.key = key;
		this.tag = tag;
		this.rebuild();
	}

	public static NbtNode root(String name, CompoundTag tag) {
		NbtNode node = new NbtNode(null, name, tag);
		node.expanded = true;
		return node;
	}

	@Override
	public @Nullable NbtNode parent() {
		return this.parent;
	}

	public Tag tag() {
		return this.tag;
	}

	public @Nullable String key() {
		return this.key;
	}

	@Override
	public boolean expanded() {
		return this.expanded;
	}

	@Override
	public List<NbtNode> children() {
		return this.children;
	}

	public boolean isRoot() {
		return this.parent == null;
	}

	@Override
	public boolean isContainer() {
		return NbtValues.isContainer(this.tag);
	}

	public boolean isNamed() {
		return this.key != null;
	}

	@Override
	public int depth() {
		return this.parent == null ? 0 : this.parent.depth() + 1;
	}

	public int indexInParent() {
		return this.parent == null ? -1 : this.parent.children.indexOf(this);
	}

	@Override
	public String badge() {
		return NbtValues.badge(this.tag.getId());
	}

	@Override
	public String summary() {
		return NbtValues.summary(this.tag);
	}

	@Override
	public String searchText() {
		return NbtValues.text(this.tag);
	}

	@Override
	public int color() {
		return NbtValues.color(this.tag.getId());
	}

	@Override
	public String label() {
		if (this.key != null) {
			return this.key;
		}

		return "[" + this.indexInParent() + "]";
	}

	public void setExpanded(boolean expanded) {
		this.expanded = expanded && this.isContainer();
	}

	@Override
	public void toggle() {
		this.setExpanded(!this.expanded);
	}

	@Override
	public void toggleBranch(int limit) {
		if (this.expanded) {
			this.collapseBranch();
		} else {
			this.expandBranch(limit);
		}
	}

	public void expandBranch(int limit) {
		Deque<NbtNode> pending = new ArrayDeque<>();
		pending.add(this);
		int opened = 0;
		while (!pending.isEmpty() && opened < limit) {
			NbtNode node = pending.poll();
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
		for (NbtNode child : this.children) {
			child.collapseBranch();
		}
	}

	public void collectVisible(List<NbtNode> out) {
		out.add(this);
		if (this.expanded) {
			for (NbtNode child : this.children) {
				child.collectVisible(out);
			}
		}
	}

	public boolean replaceWith(Tag newTag) {
		if (this.parent == null) {
			return false;
		}

		if (this.parent.tag instanceof CompoundTag compound && this.key != null) {
			compound.put(this.key, newTag);
		} else if (this.parent.tag instanceof CollectionTag collection) {
			if (!collection.setTag(this.indexInParent(), newTag)) {
				return false;
			}
		} else {
			return false;
		}

		this.parent.rebuild();
		return true;
	}

	public boolean rename(String newKey) {
		if (this.parent == null || this.key == null || !(this.parent.tag instanceof CompoundTag compound)) {
			return false;
		}

		if (newKey.isEmpty() || newKey.equals(this.key)) {
			return false;
		}

		compound.remove(this.key);
		compound.put(newKey, this.tag);
		this.key = newKey;
		this.parent.rebuild();
		return true;
	}

	public boolean remove() {
		if (this.parent == null) {
			return false;
		}

		if (this.parent.tag instanceof CompoundTag compound && this.key != null) {
			compound.remove(this.key);
		} else if (this.parent.tag instanceof CollectionTag collection) {
			collection.remove(this.indexInParent());
		} else {
			return false;
		}

		this.parent.rebuild();
		return true;
	}

	public boolean canAddChild(@Nullable String childKey) {
		if (this.tag instanceof CompoundTag compound) {
			return childKey != null && !childKey.isEmpty() && !compound.contains(childKey);
		}

		return this.tag instanceof CollectionTag;
	}

	public boolean addChild(@Nullable String childKey, Tag childTag) {
		if (this.tag instanceof CompoundTag compound) {
			if (childKey == null || childKey.isEmpty() || compound.contains(childKey)) {
				return false;
			}

			compound.put(childKey, childTag);
		} else if (this.tag instanceof CollectionTag collection) {
			if (!collection.addTag(collection.size(), childTag)) {
				return false;
			}
		} else {
			return false;
		}

		this.expanded = true;
		this.rebuild();
		return true;
	}

	public boolean acceptsKeys() {
		return this.tag instanceof CompoundTag;
	}

	private void rebuild() {
		List<NbtNode> previous = new ArrayList<>(this.children);
		this.children.clear();
		if (this.tag instanceof CompoundTag compound) {
			compound.keySet().stream().sorted(String::compareToIgnoreCase).forEach(name -> {
				Tag child = compound.get(name);
				if (child != null) {
					this.children.add(this.reuseOrCreate(previous, name, child));
				}
			});
		} else if (this.tag instanceof CollectionTag collection) {
			for (int i = 0; i < collection.size(); i++) {
				this.children.add(this.reuseOrCreate(previous, null, collection.get(i)));
			}
		}
	}

	private NbtNode reuseOrCreate(List<NbtNode> previous, @Nullable String childKey, Tag childTag) {
		Iterator<NbtNode> iterator = previous.iterator();
		while (iterator.hasNext()) {
			NbtNode node = iterator.next();
			if (node.tag == childTag) {
				iterator.remove();
				node.key = childKey;
				return node;
			}
		}

		return new NbtNode(this, childKey, childTag);
	}
}
