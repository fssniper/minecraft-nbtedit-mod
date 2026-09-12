package nbtedit.client.screen;

import com.mojang.blaze3d.platform.NativeImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import nbtedit.NBTEdit;
import nbtedit.client.region.TerrainTile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

final class TerrainTiles implements AutoCloseable {
	private static final int MAX_TILES = 36;
	private static final int WORKERS = 2;
	private static final int UPLOADS_PER_FRAME = 2;
	private static final int CHUNK_PIXELS = TerrainTile.PIXELS / TerrainTile.REGION_CHUNKS;

	private final ExecutorService workers = Executors.newFixedThreadPool(WORKERS, task -> {
		Thread thread = new Thread(task, "NBT Edit terrain");
		thread.setDaemon(true);
		return thread;
	});
	private final Map<Long, Identifier> uploaded = new LinkedHashMap<>(16, 0.75F, true);
	private final Set<Long> pending = ConcurrentHashMap.newKeySet();
	private final Set<Long> failed = ConcurrentHashMap.newKeySet();
	private final Set<Long> coarseOnly = ConcurrentHashMap.newKeySet();
	private final Queue<Rendered> rendered = new ConcurrentLinkedQueue<>();
	private final Map<Long, int[]> coarse = new HashMap<>();
	private boolean closed;

	private record Rendered(long key, int @Nullable [] pixels) {
	}

	@Nullable Identifier get(int regionX, int regionZ) {
		return this.uploaded.get(ChunkPos.pack(regionX, regionZ));
	}

	int @Nullable [] coarse(int regionX, int regionZ) {
		return this.coarse.get(ChunkPos.pack(regionX, regionZ));
	}

	void request(Path file, int regionX, int regionZ, boolean fullSize) {
		long key = ChunkPos.pack(regionX, regionZ);
		if (!fullSize && this.coarse.containsKey(key)) {
			return;
		}

		if (this.closed || this.uploaded.containsKey(key) || this.failed.contains(key) || !this.pending.add(key)) {
			return;
		}

		if (fullSize) {
			this.coarseOnly.remove(key);
		} else {
			this.coarseOnly.add(key);
		}

		this.workers.execute(() -> {
			try {
				this.rendered.add(new Rendered(key, TerrainTile.render(file)));
			} catch (Exception e) {
				NBTEdit.LOGGER.warn("Failed to draw terrain of {}", file, e);
				this.rendered.add(new Rendered(key, null));
			}
		});
	}

	boolean upload() {
		boolean changed = false;
		for (int count = 0; count < UPLOADS_PER_FRAME; count++) {
			Rendered next = this.rendered.poll();
			if (next == null) {
				break;
			}

			this.pending.remove(next.key());
			if (next.pixels() == null) {
				this.failed.add(next.key());
				continue;
			}

			this.coarse.put(next.key(), shrink(next.pixels()));
			if (!this.coarseOnly.remove(next.key())) {
				this.upload(next.key(), next.pixels());
			}

			changed = true;
		}

		return changed;
	}

	private static int[] shrink(int[] pixels) {
		int[] small = new int[TerrainTile.REGION_CHUNKS * TerrainTile.REGION_CHUNKS];
		for (int chunkZ = 0; chunkZ < TerrainTile.REGION_CHUNKS; chunkZ++) {
			for (int chunkX = 0; chunkX < TerrainTile.REGION_CHUNKS; chunkX++) {
				int red = 0;
				int green = 0;
				int blue = 0;
				int counted = 0;
				for (int z = 0; z < CHUNK_PIXELS; z++) {
					for (int x = 0; x < CHUNK_PIXELS; x++) {
						int pixel = pixels[(chunkZ * CHUNK_PIXELS + z) * TerrainTile.PIXELS + chunkX * CHUNK_PIXELS + x];
						if (pixel != 0) {
							red += pixel >> 16 & 0xFF;
							green += pixel >> 8 & 0xFF;
							blue += pixel & 0xFF;
							counted++;
						}
					}
				}

				small[chunkZ * TerrainTile.REGION_CHUNKS + chunkX] = counted == 0
					? 0
					: 0xFF000000 | red / counted << 16 | green / counted << 8 | blue / counted;
			}
		}

		return small;
	}

	private void upload(long key, int[] pixels) {
		NativeImage image = new NativeImage(TerrainTile.PIXELS, TerrainTile.PIXELS, false);
		for (int z = 0; z < TerrainTile.PIXELS; z++) {
			for (int x = 0; x < TerrainTile.PIXELS; x++) {
				image.setPixel(x, z, pixels[z * TerrainTile.PIXELS + x]);
			}
		}

		Identifier id = Identifier.fromNamespaceAndPath(NBTEdit.MOD_ID, "terrain/" + ChunkPos.getX(key) + "_" + ChunkPos.getZ(key));
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "NBT Edit terrain", image));
		this.uploaded.put(key, id);
		this.evict();
	}

	private void evict() {
		List<Long> stale = new ArrayList<>();
		for (Long key : this.uploaded.keySet()) {
			if (this.uploaded.size() - stale.size() <= MAX_TILES) {
				break;
			}

			stale.add(key);
		}

		for (Long key : stale) {
			Minecraft.getInstance().getTextureManager().release(this.uploaded.remove(key));
		}
	}

	@Override
	public void close() {
		this.closed = true;
		this.workers.shutdownNow();
		this.rendered.clear();
		for (Identifier id : this.uploaded.values()) {
			Minecraft.getInstance().getTextureManager().release(id);
		}

		this.uploaded.clear();
		this.coarse.clear();
		this.coarseOnly.clear();
	}
}
