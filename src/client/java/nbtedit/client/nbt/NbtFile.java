package nbtedit.client.nbt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import nbtedit.client.config.NbtEditConfig;
import nbtedit.client.io.SafeWrite;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.jspecify.annotations.Nullable;

public final class NbtFile {
	private final Path path;
	private final boolean compressed;
	private CompoundTag root;

	private NbtFile(Path path, boolean compressed, CompoundTag root) {
		this.path = path;
		this.compressed = compressed;
		this.root = root;
	}

	public static NbtFile load(Path path) throws IOException {
		boolean compressed = isGzipped(path);
		CompoundTag root = compressed ? NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap()) : NbtIo.read(path);
		if (root == null) {
			throw new IOException("File is not readable as NBT: " + path);
		}

		return new NbtFile(path, compressed, root);
	}

	public Path path() {
		return this.path;
	}

	public CompoundTag root() {
		return this.root;
	}

	public void setRoot(CompoundTag root) {
		this.root = root;
	}

	public boolean compressed() {
		return this.compressed;
	}

	public @Nullable Path save() throws IOException {
		return SafeWrite.replace(this.path, NbtEditConfig.get().keptBackups(), target -> {
			if (this.compressed) {
				NbtIo.writeCompressed(this.root, target);
			} else {
				NbtIo.write(this.root, target);
			}
		});
	}

	private static boolean isGzipped(Path path) throws IOException {
		try (InputStream in = Files.newInputStream(path)) {
			int first = in.read();
			int second = in.read();
			return first == 0x1F && second == 0x8B;
		}
	}
}
