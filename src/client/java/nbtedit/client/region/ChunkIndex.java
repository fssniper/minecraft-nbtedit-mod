package nbtedit.client.region;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import nbtedit.NBTEdit;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

public final class ChunkIndex {
	private static final Pattern REGION_NAME = Pattern.compile("^r\\.-?\\d+\\.-?\\d+\\.mca$");
	private static final List<String> LAYER_FOLDERS = List.of("region", "entities", "poi");
	private static final int REGION_SIZE = 32;

	private final Path folder;
	private final Map<Long, Entry> chunks = new HashMap<>();
	private final Map<Long, Path> regionFiles = new HashMap<>();
	private int regionCount;
	private int minX = Integer.MAX_VALUE;
	private int minZ = Integer.MAX_VALUE;
	private int maxX = Integer.MIN_VALUE;
	private int maxZ = Integer.MIN_VALUE;
	private int oldestSave = Integer.MAX_VALUE;
	private int newestSave;

	public record Entry(int x, int z, int timestamp, int allocatedBytes, Path file) {
	}

	private ChunkIndex(Path folder) {
		this.folder = folder;
	}

	public static ChunkIndex scan(Path folder) throws IOException {
		ChunkIndex index = new ChunkIndex(folder);
		try (Stream<Path> entries = Files.list(folder)) {
			entries.filter(entry -> REGION_NAME.matcher(entry.getFileName().toString()).matches()).sorted().forEach(index::addRegion);
		}

		return index;
	}

	public static List<Path> layersNextTo(Path folder) {
		Path parent = folder.getParent();
		if (parent == null) {
			return List.of(folder);
		}

		List<Path> layers = new ArrayList<>();
		for (String name : LAYER_FOLDERS) {
			Path layer = parent.resolve(name);
			if (layer.equals(folder) || Files.isDirectory(layer)) {
				layers.add(layer);
			}
		}

		return layers.contains(folder) ? layers : List.of(folder);
	}

	public Path folder() {
		return this.folder;
	}

	public boolean isEmpty() {
		return this.chunks.isEmpty();
	}

	public int size() {
		return this.chunks.size();
	}

	public int regionCount() {
		return this.regionCount;
	}

	public int minX() {
		return this.minX;
	}

	public int minZ() {
		return this.minZ;
	}

	public int maxX() {
		return this.maxX;
	}

	public int maxZ() {
		return this.maxZ;
	}

	public int oldestSave() {
		return this.oldestSave;
	}

	public int newestSave() {
		return this.newestSave;
	}

	public Collection<Entry> entries() {
		return this.chunks.values();
	}

	public @Nullable Path regionFile(int regionX, int regionZ) {
		return this.regionFiles.get(ChunkPos.pack(regionX, regionZ));
	}

	public @Nullable Entry get(int x, int z) {
		return this.chunks.get(ChunkPos.pack(x, z));
	}

	private void addRegion(Path file) {
		try (RegionFileView region = RegionFileView.open(file)) {
			this.regionCount++;
			for (RegionFileView.Chunk chunk : region.chunks()) {
				this.regionFiles.put(ChunkPos.pack(Math.floorDiv(chunk.x(), REGION_SIZE), Math.floorDiv(chunk.z(), REGION_SIZE)), file);
				this.chunks.put(ChunkPos.pack(chunk.x(), chunk.z()), new Entry(chunk.x(), chunk.z(), chunk.timestamp(), chunk.allocatedBytes(), file));
				this.minX = Math.min(this.minX, chunk.x());
				this.minZ = Math.min(this.minZ, chunk.z());
				this.maxX = Math.max(this.maxX, chunk.x());
				this.maxZ = Math.max(this.maxZ, chunk.z());
				if (chunk.timestamp() != 0) {
					this.oldestSave = Math.min(this.oldestSave, chunk.timestamp());
					this.newestSave = Math.max(this.newestSave, chunk.timestamp());
				}
			}
		} catch (IOException | RuntimeException e) {
			NBTEdit.LOGGER.warn("Skipped unreadable region {}", file, e);
		}
	}
}
