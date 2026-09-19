package nbtedit.client.region;

import java.io.IOException;
import java.nio.file.Path;
import nbtedit.client.config.NbtEditConfig;
import nbtedit.client.nbt.NbtDocument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public final class ChunkDocument implements NbtDocument {
	private final Path regionFile;
	private final int chunkX;
	private final int chunkZ;
	private CompoundTag root;

	private ChunkDocument(Path regionFile, int chunkX, int chunkZ, CompoundTag root) {
		this.regionFile = regionFile;
		this.chunkX = chunkX;
		this.chunkZ = chunkZ;
		this.root = root;
	}

	public static ChunkDocument load(Path regionFile, int chunkX, int chunkZ) throws IOException {
		try (RegionFileView region = RegionFileView.open(regionFile)) {
			return new ChunkDocument(regionFile, chunkX, chunkZ, region.read(chunkX, chunkZ));
		}
	}

	public int chunkX() {
		return this.chunkX;
	}

	public int chunkZ() {
		return this.chunkZ;
	}

	@Override
	public Path path() {
		return this.regionFile;
	}

	@Override
	public String name() {
		return this.chunkX + ", " + this.chunkZ;
	}

	@Override
	public Component title() {
		return Component.translatable("nbtedit.chunk.title", this.chunkX, this.chunkZ);
	}

	@Override
	public CompoundTag root() {
		return this.root;
	}

	@Override
	public void setRoot(CompoundTag root) {
		this.root = root;
	}

	@Override
	public @Nullable Path save() throws IOException {
		return RegionFileWriter.replaceChunk(this.regionFile, this.chunkX, this.chunkZ, this.root, NbtEditConfig.get().keptBackups());
	}
}
