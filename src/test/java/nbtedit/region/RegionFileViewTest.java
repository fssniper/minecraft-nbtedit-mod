package nbtedit.region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import nbtedit.client.region.RegionFileView;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegionFileViewTest {
	private static final int SECTOR_BYTES = 4096;
	private static final int HEADER_SECTORS = 2;

	@TempDir
	Path directory;

	@Test
	void listsOnlyTheChunksThatArePresent() throws IOException {
		Path file = this.region(0, 0, Map.of(0, chunk("first"), 33, chunk("second")));

		try (RegionFileView region = RegionFileView.open(file)) {
			assertEquals(List.of("0, 0", "1, 1"), region.chunks().stream().map(chunk -> chunk.x() + ", " + chunk.z()).toList());
		}
	}

	@Test
	void chunkCoordinatesFollowTheFileName() throws IOException {
		Path file = this.region(-2, 3, Map.of(0, chunk("corner"), 1023, chunk("far")));

		try (RegionFileView region = RegionFileView.open(file)) {
			assertEquals(List.of("-64, 96", "-33, 127"), region.chunks().stream().map(chunk -> chunk.x() + ", " + chunk.z()).toList());
		}
	}

	@Test
	void readsBackWhatWasStored() throws IOException {
		Path file = this.region(0, 0, Map.of(5, chunk("hello")));

		try (RegionFileView region = RegionFileView.open(file)) {
			CompoundTag tag = region.read(region.chunks().getFirst());
			assertEquals("hello", tag.getString("name").orElseThrow());
		}
	}

	@Test
	void readsByCoordinate() throws IOException {
		Path file = this.region(1, 1, Map.of(2, chunk("by coordinate")));

		try (RegionFileView region = RegionFileView.open(file)) {
			assertEquals("by coordinate", region.read(34, 32).getString("name").orElseThrow());
		}
	}

	@Test
	void theAllocatedSizeCoversThePayload() throws IOException {
		Path file = this.region(0, 0, Map.of(0, chunk("small")));

		try (RegionFileView region = RegionFileView.open(file)) {
			RegionFileView.Chunk chunk = region.chunks().getFirst();
			assertEquals(SECTOR_BYTES, chunk.allocatedBytes());
			assertTrue(chunk.timestamp() > 0);
		}
	}

	@Test
	void aChunkOutsideTheRegionIsRefused() throws IOException {
		Path file = this.region(0, 0, Map.of(0, chunk("only one")));

		try (RegionFileView region = RegionFileView.open(file)) {
			assertThrows(IOException.class, () -> region.read(100, 100));
		}
	}

	@Test
	void anAbsentChunkIsRefused() throws IOException {
		Path file = this.region(0, 0, Map.of(0, chunk("only one")));

		try (RegionFileView region = RegionFileView.open(file)) {
			assertThrows(IOException.class, () -> region.read(31, 31));
		}
	}

	@Test
	void anEntryPointingPastTheEndOfTheFileIsIgnored() throws IOException {
		Path file = this.region(0, 0, Map.of(0, chunk("real")));
		byte[] bytes = Files.readAllBytes(file);
		ByteBuffer.wrap(bytes).putInt(4, 900 << 8 | 1);
		Files.write(file, bytes);

		try (RegionFileView region = RegionFileView.open(file)) {
			assertEquals(1, region.chunks().size());
			assertEquals(0, region.chunks().getFirst().x());
		}
	}

	@Test
	void anEntryOverlappingTheHeaderIsIgnored() throws IOException {
		Path file = this.region(0, 0, Map.of(0, chunk("real")));
		byte[] bytes = Files.readAllBytes(file);
		ByteBuffer.wrap(bytes).putInt(4, 1 << 8 | 1);
		Files.write(file, bytes);

		try (RegionFileView region = RegionFileView.open(file)) {
			assertEquals(1, region.chunks().size());
		}
	}

	@Test
	void anEmptyFileHoldsNoChunks() throws IOException {
		Path file = this.directory.resolve("r.0.0.mca");
		Files.write(file, new byte[0]);

		try (RegionFileView region = RegionFileView.open(file)) {
			assertTrue(region.chunks().isEmpty());
		}
	}

	@Test
	void readingIsLeftTheFileUntouched() throws IOException {
		Path file = this.region(0, 0, Map.of(0, chunk("keep me")));
		byte[] before = Files.readAllBytes(file);

		try (RegionFileView region = RegionFileView.open(file)) {
			assertNotNull(region.read(0, 0));
		}

		assertEquals(-1, java.util.Arrays.mismatch(before, Files.readAllBytes(file)));
	}

	private static CompoundTag chunk(String name) {
		CompoundTag tag = new CompoundTag();
		tag.putString("name", name);
		return tag;
	}

	private Path region(int regionX, int regionZ, Map<Integer, CompoundTag> chunks) throws IOException {
		Path file = this.directory.resolve("r." + regionX + "." + regionZ + ".mca");
		ByteBuffer header = ByteBuffer.allocate(SECTOR_BYTES * HEADER_SECTORS);
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		int sector = HEADER_SECTORS;
		for (Map.Entry<Integer, CompoundTag> entry : new TreeMap<>(chunks).entrySet()) {
			byte[] payload = compress(entry.getValue());
			int sectors = (payload.length + SECTOR_BYTES - 1) / SECTOR_BYTES;
			header.putInt(entry.getKey() * 4, sector << 8 | sectors);
			header.putInt(SECTOR_BYTES + entry.getKey() * 4, (int) (System.currentTimeMillis() / 1000L));
			body.write(payload);
			body.write(new byte[sectors * SECTOR_BYTES - payload.length]);
			sector += sectors;
		}

		try (OutputStream out = Files.newOutputStream(file)) {
			out.write(header.array());
			body.writeTo(out);
		}

		return file;
	}

	private static byte[] compress(CompoundTag tag) throws IOException {
		ByteArrayOutputStream compressed = new ByteArrayOutputStream();
		try (DataOutputStream out = new DataOutputStream(RegionFileVersion.VERSION_DEFLATE.wrap(compressed))) {
			NbtIo.write(tag, out);
		}

		ByteArrayOutputStream chunk = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(chunk);
		out.writeInt(compressed.size() + 1);
		out.writeByte(RegionFileVersion.VERSION_DEFLATE.getId());
		compressed.writeTo(out);
		out.flush();
		return chunk.toByteArray();
	}
}
