package nbtedit.client.region;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;

public final class RegionFileView implements AutoCloseable {
	private static final Pattern FILE_NAME = Pattern.compile("^r\\.(-?\\d+)\\.(-?\\d+)\\.mca$");
	private static final int SECTOR_BYTES = 4096;
	private static final int HEADER_BYTES = 8192;
	private static final int CHUNK_COUNT = 1024;
	private static final int ROW_SIZE = 32;
	private static final int CHUNK_HEADER_BYTES = 5;
	private static final int EXTERNAL_FLAG = 128;
	private static final int MAX_PAYLOAD_BYTES = 128 * 1024 * 1024;

	private final Path path;
	private final FileChannel channel;
	private final int regionX;
	private final int regionZ;
	private final int[] offsets = new int[CHUNK_COUNT];
	private final List<Chunk> chunks = new ArrayList<>();

	public record Chunk(int x, int z, int timestamp, int allocatedBytes) {
	}

	private RegionFileView(Path path, FileChannel channel, int regionX, int regionZ) throws IOException {
		this.path = path;
		this.channel = channel;
		this.regionX = regionX;
		this.regionZ = regionZ;
		this.readHeader();
	}

	public static RegionFileView open(Path path) throws IOException {
		Matcher name = FILE_NAME.matcher(path.getFileName().toString());
		boolean named = name.matches();
		int regionX = named ? Integer.parseInt(name.group(1)) : 0;
		int regionZ = named ? Integer.parseInt(name.group(2)) : 0;
		FileChannel channel = FileChannel.open(path, StandardOpenOption.READ);

		try {
			return new RegionFileView(path, channel, regionX, regionZ);
		} catch (IOException | RuntimeException e) {
			channel.close();
			throw e;
		}
	}

	public Path path() {
		return this.path;
	}

	public List<Chunk> chunks() {
		return this.chunks;
	}

	public CompoundTag read(Chunk chunk) throws IOException {
		int offset = this.offsets[this.index(chunk)];
		if (offset == 0) {
			throw new IOException("Chunk " + chunk.x() + ", " + chunk.z() + " is not stored in " + this.path.getFileName());
		}

		long position = (long) (offset >> 8 & 0xFFFFFF) * SECTOR_BYTES;
		ByteBuffer header = ByteBuffer.allocate(CHUNK_HEADER_BYTES);
		this.readAvailable(header, position);
		if (header.hasRemaining()) {
			throw new IOException("Chunk header is truncated");
		}

		header.rewind();
		int length = header.getInt();
		int versionId = header.get() & 0xFF;
		boolean external = (versionId & EXTERNAL_FLAG) != 0;
		RegionFileVersion version = RegionFileVersion.fromId(versionId & ~EXTERNAL_FLAG);
		if (version == null) {
			throw new IOException("Unknown chunk compression " + (versionId & ~EXTERNAL_FLAG));
		}

		try (
			InputStream payload = external ? Files.newInputStream(this.externalPath(chunk)) : this.payload(position + CHUNK_HEADER_BYTES, length - 1);
			DataInputStream in = new DataInputStream(version.wrap(payload))
		) {
			return NbtIo.read(in, NbtAccounter.unlimitedHeap());
		}
	}

	@Override
	public void close() throws IOException {
		this.channel.close();
	}

	private void readHeader() throws IOException {
		long size = this.channel.size();
		ByteBuffer header = ByteBuffer.allocate(HEADER_BYTES);
		this.readAvailable(header, 0);
		header.rewind();
		IntBuffer values = header.asIntBuffer();
		for (int index = 0; index < CHUNK_COUNT; index++) {
			int offset = values.get(index);
			int sector = offset >> 8 & 0xFFFFFF;
			int sectors = offset & 0xFF;
			if (sector < 2 || sectors == 0 || (long) sector * SECTOR_BYTES >= size) {
				continue;
			}

			this.offsets[index] = offset;
			this.chunks
				.add(
					new Chunk(
						this.regionX * ROW_SIZE + index % ROW_SIZE,
						this.regionZ * ROW_SIZE + index / ROW_SIZE,
						values.get(CHUNK_COUNT + index),
						sectors * SECTOR_BYTES
					)
				);
		}
	}

	private int index(Chunk chunk) {
		return chunk.x() - this.regionX * ROW_SIZE + (chunk.z() - this.regionZ * ROW_SIZE) * ROW_SIZE;
	}

	private Path externalPath(Chunk chunk) {
		return this.path.resolveSibling("c." + chunk.x() + "." + chunk.z() + ".mcc");
	}

	private InputStream payload(long position, int length) throws IOException {
		if (length <= 0 || length > MAX_PAYLOAD_BYTES) {
			throw new IOException("Chunk length is out of range: " + length);
		}

		ByteBuffer buffer = ByteBuffer.allocate(length);
		this.readAvailable(buffer, position);
		if (buffer.hasRemaining()) {
			throw new IOException("Chunk payload is truncated");
		}

		return new ByteArrayInputStream(buffer.array());
	}

	private void readAvailable(ByteBuffer buffer, long position) throws IOException {
		long at = position;
		while (buffer.hasRemaining()) {
			int read = this.channel.read(buffer, at);
			if (read <= 0) {
				return;
			}

			at += read;
		}
	}
}
