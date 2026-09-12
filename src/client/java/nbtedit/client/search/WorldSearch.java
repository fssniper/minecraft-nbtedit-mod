package nbtedit.client.search;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import nbtedit.NBTEdit;
import nbtedit.client.json.JsonFile;
import nbtedit.client.json.JsonValues;
import nbtedit.client.nbt.NbtFile;
import nbtedit.client.nbt.NbtValues;
import nbtedit.client.region.RegionFileView;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

public final class WorldSearch implements AutoCloseable {
	public static final int DEFAULT_LIMIT = 500;
	private static final int THREADS = 2;
	private static final int PREVIEW_LENGTH = 120;
	private static final List<String> NBT_EXTENSIONS = List.of(".dat", ".dat_old", ".nbt", ".schematic", ".mcstructure");
	private static final List<String> JSON_EXTENSIONS = List.of(".json", ".mcmeta");
	private static final String REGION_EXTENSION = ".mca";

	private final Path worldRoot;
	private final String query;
	private final boolean regions;
	private final int limit;
	private final ExecutorService workers = Executors.newFixedThreadPool(THREADS + 1, task -> {
		Thread thread = new Thread(task, "NBT Edit search");
		thread.setDaemon(true);
		return thread;
	});
	private final Queue<SearchHit> hits = new ConcurrentLinkedQueue<>();
	private final AtomicInteger scanned = new AtomicInteger();
	private final AtomicInteger found = new AtomicInteger();
	private volatile int total;
	private volatile boolean done;
	private volatile boolean cancelled;

	private WorldSearch(Path worldRoot, String query, boolean regions, int limit) {
		this.worldRoot = worldRoot;
		this.query = query.toLowerCase(Locale.ROOT);
		this.regions = regions;
		this.limit = limit;
	}

	public static WorldSearch start(Path worldRoot, String query, boolean regions, int limit) {
		WorldSearch search = new WorldSearch(worldRoot, query, regions, limit);
		search.workers.execute(search::run);
		return search;
	}

	public int scanned() {
		return this.scanned.get();
	}

	public int total() {
		return this.total;
	}

	public int found() {
		return this.found.get();
	}

	public boolean isDone() {
		return this.done;
	}

	public boolean reachedLimit() {
		return this.found.get() >= this.limit;
	}

	public List<SearchHit> drain() {
		List<SearchHit> drained = new ArrayList<>();
		for (SearchHit hit = this.hits.poll(); hit != null; hit = this.hits.poll()) {
			drained.add(hit);
		}

		return drained;
	}

	@Override
	public void close() {
		this.cancelled = true;
		this.workers.shutdownNow();
	}

	private void run() {
		try {
			List<Path> files = this.collect();
			this.total = files.size();
			List<Callable<Void>> tasks = new ArrayList<>(files.size());
			for (Path file : files) {
				tasks.add(() -> {
					this.scan(file);
					return null;
				});
			}

			this.workers.invokeAll(tasks);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			NBTEdit.LOGGER.error("Search over {} failed", this.worldRoot, e);
		} finally {
			this.done = true;
		}
	}

	private List<Path> collect() throws IOException {
		try (Stream<Path> entries = Files.walk(this.worldRoot)) {
			return entries.filter(Files::isRegularFile).filter(this::wanted).toList();
		}
	}

	public static boolean isRegion(Path file) {
		return name(file).endsWith(REGION_EXTENSION);
	}

	public static boolean isJson(Path file) {
		return JSON_EXTENSIONS.stream().anyMatch(name(file)::endsWith);
	}

	private static String name(Path file) {
		return file.getFileName().toString().toLowerCase(Locale.ROOT);
	}

	private boolean wanted(Path file) {
		String name = name(file);
		if (name.endsWith(REGION_EXTENSION)) {
			return this.regions;
		}

		return NBT_EXTENSIONS.stream().anyMatch(name::endsWith) || JSON_EXTENSIONS.stream().anyMatch(name::endsWith);
	}

	private void scan(Path file) {
		if (this.cancelled) {
			return;
		}

		String name = name(file);

		try {
			if (name.endsWith(REGION_EXTENSION)) {
				this.scanRegion(file);
			} else if (NBT_EXTENSIONS.stream().anyMatch(name::endsWith)) {
				this.visitTag(NbtFile.load(file).root(), new ArrayDeque<>(), file, null);
			} else {
				this.visitJson(JsonFile.load(file).root(), new ArrayDeque<>(), file);
			}
		} catch (Exception e) {
			NBTEdit.LOGGER.warn("Skipped {} while searching", file, e);
		} finally {
			this.scanned.incrementAndGet();
		}
	}

	private void scanRegion(Path file) throws IOException {
		try (RegionFileView region = RegionFileView.open(file)) {
			for (RegionFileView.Chunk chunk : region.chunks()) {
				if (this.cancelled) {
					return;
				}

				try {
					this.visitTag(region.read(chunk), new ArrayDeque<>(), file, new ChunkPos(chunk.x(), chunk.z()));
				} catch (IOException | RuntimeException e) {
					NBTEdit.LOGGER.warn("Skipped chunk {}, {} of {} while searching", chunk.x(), chunk.z(), file, e);
				}
			}
		}
	}

	private void visitTag(Tag tag, Deque<String> path, Path file, @Nullable ChunkPos chunk) {
		if (this.cancelled) {
			return;
		}

		if (tag instanceof CompoundTag compound) {
			this.report(path, NbtValues.summary(tag), false, file, chunk);
			for (String key : compound.keySet()) {
				Tag child = compound.get(key);
				if (child != null) {
					path.addLast(key);
					this.visitTag(child, path, file, chunk);
					path.removeLast();
				}
			}
		} else if (tag instanceof CollectionTag collection) {
			this.report(path, NbtValues.summary(tag), false, file, chunk);
			for (int index = 0; index < collection.size(); index++) {
				path.addLast("[" + index + "]");
				this.visitTag(collection.get(index), path, file, chunk);
				path.removeLast();
			}
		} else {
			this.report(path, NbtValues.text(tag), true, file, chunk);
		}
	}

	private void visitJson(JsonElement element, Deque<String> path, Path file) {
		if (this.cancelled) {
			return;
		}

		if (element instanceof JsonObject object) {
			this.report(path, JsonValues.summary(element), false, file, null);
			for (Map.Entry<String, JsonElement> member : object.entrySet()) {
				path.addLast(member.getKey());
				this.visitJson(member.getValue(), path, file);
				path.removeLast();
			}
		} else if (element.isJsonArray()) {
			this.report(path, JsonValues.summary(element), false, file, null);
			for (int index = 0; index < element.getAsJsonArray().size(); index++) {
				path.addLast("[" + index + "]");
				this.visitJson(element.getAsJsonArray().get(index), path, file);
				path.removeLast();
			}
		} else {
			this.report(path, JsonValues.text(element), true, file, null);
		}
	}

	private void report(Deque<String> path, String value, boolean leaf, Path file, @Nullable ChunkPos chunk) {
		if (path.isEmpty()) {
			return;
		}

		if (!this.matches(path.getLast()) && !(leaf && this.matches(value))) {
			return;
		}

		this.hits.add(new SearchHit(file, chunk, List.copyOf(path), preview(value)));
		if (this.found.incrementAndGet() >= this.limit) {
			this.cancelled = true;
		}
	}

	private boolean matches(String text) {
		return text.toLowerCase(Locale.ROOT).contains(this.query);
	}

	private static String preview(String value) {
		String single = value.replace('\n', ' ');
		return single.length() <= PREVIEW_LENGTH ? single : single.substring(0, PREVIEW_LENGTH) + "...";
	}
}
