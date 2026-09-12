package nbtedit.region;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;

final class Regions {
	static final int SECTOR_BYTES = 4096;
	private static final int HEADER_SECTORS = 2;

	private Regions() {
	}

	static CompoundTag named(String name) {
		CompoundTag tag = new CompoundTag();
		tag.putString("name", name);
		return tag;
	}

	static int index(int localX, int localZ) {
		return localZ * 32 + localX;
	}

	static Path write(Path directory, int regionX, int regionZ, Map<Integer, CompoundTag> chunks) throws IOException {
		return write(directory, regionX, regionZ, chunks, RegionFileVersion.VERSION_DEFLATE);
	}

	static Path write(Path directory, int regionX, int regionZ, Map<Integer, CompoundTag> chunks, RegionFileVersion version) throws IOException {
		Path file = directory.resolve("r." + regionX + "." + regionZ + ".mca");
		ByteBuffer header = ByteBuffer.allocate(SECTOR_BYTES * HEADER_SECTORS);
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		int sector = HEADER_SECTORS;
		int timestamp = 1000;
		for (Map.Entry<Integer, CompoundTag> entry : new TreeMap<>(chunks).entrySet()) {
			byte[] record = record(entry.getValue(), version);
			int sectors = (record.length + SECTOR_BYTES - 1) / SECTOR_BYTES;
			header.putInt(entry.getKey() * 4, sector << 8 | sectors);
			header.putInt(SECTOR_BYTES + entry.getKey() * 4, timestamp++);
			body.write(record);
			body.write(new byte[sectors * SECTOR_BYTES - record.length]);
			sector += sectors;
		}

		try (OutputStream out = Files.newOutputStream(file)) {
			out.write(header.array());
			body.writeTo(out);
		}

		return file;
	}

	static byte[] record(CompoundTag tag, RegionFileVersion version) throws IOException {
		ByteArrayOutputStream compressed = new ByteArrayOutputStream();
		try (DataOutputStream out = new DataOutputStream(version.wrap(compressed))) {
			NbtIo.write(tag, out);
		}

		ByteArrayOutputStream record = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(record);
		out.writeInt(compressed.size() + 1);
		out.writeByte(version.getId());
		compressed.writeTo(out);
		out.flush();
		return record.toByteArray();
	}
}
