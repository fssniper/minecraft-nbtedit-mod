package nbtedit.client.region;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import nbtedit.client.io.SafeWrite;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import org.jspecify.annotations.Nullable;

public final class RegionFileWriter {
	private static final int SECTOR_BYTES = 4096;
	private static final int HEADER_BYTES = 8192;
	private static final int HEADER_SECTORS = HEADER_BYTES / SECTOR_BYTES;
	private static final int ROW_SIZE = 32;
	private static final int CHUNK_HEADER_BYTES = 5;
	private static final int EXTERNAL_FLAG = 128;
	private static final int MAX_SECTORS = 255;
	private static final byte[] PADDING = new byte[SECTOR_BYTES];

	private RegionFileWriter() {
	}

	private record Entry(int index, int timestamp, byte[] record) {
	}

	public static @Nullable Path replaceChunk(Path regionFile, int chunkX, int chunkZ, CompoundTag chunk, int keptBackups) throws IOException {
		List<Entry> entries = new ArrayList<>();
		byte @Nullable [] external;
		Path externalFile;

		try (RegionFileView region = RegionFileView.open(regionFile)) {
			int target = index(region, chunkX, chunkZ);
			if (target < 0) {
				throw new IOException("Chunk " + chunkX + ", " + chunkZ + " is outside " + regionFile.getFileName());
			}

			byte[] payload = compress(chunk, versionOf(region.record(chunkX, chunkZ)));
			external = sectorsFor(payload.length) > MAX_SECTORS ? payload : null;
			externalFile = regionFile.resolveSibling("c." + chunkX + "." + chunkZ + ".mcc");
			int now = (int) (System.currentTimeMillis() / 1000L);
			for (RegionFileView.Chunk existing : region.chunks()) {
				int index = index(region, existing.x(), existing.z());
				boolean edited = index == target;
				entries.add(new Entry(index, edited ? now : existing.timestamp(), edited ? record(payload, external != null) : region.record(existing.x(), existing.z())));
			}
		}

		if (external != null) {
			writeExternal(externalFile, external);
		}

		Path backup = SafeWrite.replace(regionFile, Math.max(1, keptBackups), target -> {
			writeRegion(target, entries);
			verify(target, entries.size());
		});
		if (external == null) {
			Files.deleteIfExists(externalFile);
		}

		return backup;
	}

	private static int index(RegionFileView region, int chunkX, int chunkZ) {
		int localX = chunkX - region.regionX() * ROW_SIZE;
		int localZ = chunkZ - region.regionZ() * ROW_SIZE;
		if (localX < 0 || localX >= ROW_SIZE || localZ < 0 || localZ >= ROW_SIZE) {
			return -1;
		}

		return localX + localZ * ROW_SIZE;
	}

	private static RegionFileVersion versionOf(byte[] record) {
		RegionFileVersion version = RegionFileVersion.fromId(record[4] & 0xFF & ~EXTERNAL_FLAG);
		return version == null || version == RegionFileVersion.VERSION_CUSTOM ? RegionFileVersion.DEFAULT : version;
	}

	private static byte[] compress(CompoundTag chunk, RegionFileVersion version) throws IOException {
		ByteArrayOutputStream compressed = new ByteArrayOutputStream();
		compressed.write(new byte[CHUNK_HEADER_BYTES]);
		try (DataOutputStream out = new DataOutputStream(version.wrap(compressed))) {
			NbtIo.write(chunk, out);
		}

		byte[] payload = compressed.toByteArray();
		ByteBuffer.wrap(payload).putInt(0, payload.length - CHUNK_HEADER_BYTES + 1).put(4, (byte) version.getId());
		return payload;
	}

	private static byte[] record(byte[] payload, boolean external) {
		if (!external) {
			return payload;
		}

		ByteBuffer stub = ByteBuffer.allocate(CHUNK_HEADER_BYTES);
		stub.putInt(1);
		stub.put((byte) (payload[4] | EXTERNAL_FLAG));
		return stub.array();
	}

	private static void writeExternal(Path file, byte[] payload) throws IOException {
		Path temporary = file.resolveSibling(file.getFileName() + ".nbtedit_tmp");
		Files.write(temporary, Arrays.copyOfRange(payload, CHUNK_HEADER_BYTES, payload.length));
		Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
	}

	private static void writeRegion(Path target, List<Entry> entries) throws IOException {
		ByteBuffer header = ByteBuffer.allocate(HEADER_BYTES);
		int sector = HEADER_SECTORS;
		for (Entry entry : entries) {
			int sectors = sectorsFor(entry.record().length);
			header.putInt(entry.index() * 4, sector << 8 | sectors);
			header.putInt(HEADER_BYTES / 2 + entry.index() * 4, entry.timestamp());
			sector += sectors;
		}

		try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(target))) {
			out.write(header.array());
			for (Entry entry : entries) {
				out.write(entry.record());
				out.write(PADDING, 0, sectorsFor(entry.record().length) * SECTOR_BYTES - entry.record().length);
			}
		}
	}

	private static void verify(Path target, int expected) throws IOException {
		try (RegionFileView written = RegionFileView.open(target)) {
			if (written.chunks().size() != expected) {
				throw new IOException("Rewritten region holds " + written.chunks().size() + " chunks instead of " + expected);
			}

			for (RegionFileView.Chunk chunk : written.chunks()) {
				if (written.read(chunk).isEmpty()) {
					throw new IOException("Chunk " + chunk.x() + ", " + chunk.z() + " came back empty");
				}
			}
		}
	}

	private static int sectorsFor(int bytes) {
		return (bytes + SECTOR_BYTES - 1) / SECTOR_BYTES;
	}
}
