package nbtedit.region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import nbtedit.client.region.RegionFileView;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegionFileViewTest {
	private static final int SECTOR_BYTES = 4096;

	@TempDir
	Path directory;

	@Test
	void listsOnlyTheChunksThatArePresent() throws IOException {
		Path file = this.region(0, 0, Map.of(0, Regions.named("first"), 33, Regions.named("second")));

		try (RegionFileView region = RegionFileView.open(file)) {
			assertEquals(List.of("0, 0", "1, 1"), region.chunks().stream().map(chunk -> chunk.x() + ", " + chunk.z()).toList());
		}
	}

	@Test
	void chunkCoordinatesFollowTheFileName() throws IOException {
		Path file = this.region(-2, 3, Map.of(0, Regions.named("corner"), 1023, Regions.named("far")));

		try (RegionFileView region = RegionFileView.open(file)) {
			assertEquals(List.of("-64, 96", "-33, 127"), region.chunks().stream().map(chunk -> chunk.x() + ", " + chunk.z()).toList());
		}
	}

	@Test
	void readsBackWhatWasStored() throws IOException {
		Path file = this.region(0, 0, Map.of(5, Regions.named("hello")));

		try (RegionFileView region = RegionFileView.open(file)) {
			CompoundTag tag = region.read(region.chunks().getFirst());
			assertEquals("hello", tag.getString("name").orElseThrow());
		}
	}

	@Test
	void readsByCoordinate() throws IOException {
		Path file = this.region(1, 1, Map.of(2, Regions.named("by coordinate")));

		try (RegionFileView region = RegionFileView.open(file)) {
			assertEquals("by coordinate", region.read(34, 32).getString("name").orElseThrow());
		}
	}

	@Test
	void theAllocatedSizeCoversThePayload() throws IOException {
		Path file = this.region(0, 0, Map.of(0, Regions.named("small")));

		try (RegionFileView region = RegionFileView.open(file)) {
			RegionFileView.Chunk chunk = region.chunks().getFirst();
			assertEquals(SECTOR_BYTES, chunk.allocatedBytes());
			assertTrue(chunk.timestamp() > 0);
		}
	}

	@Test
	void aChunkOutsideTheRegionIsRefused() throws IOException {
		Path file = this.region(0, 0, Map.of(0, Regions.named("only one")));

		try (RegionFileView region = RegionFileView.open(file)) {
			assertThrows(IOException.class, () -> region.read(100, 100));
		}
	}

	@Test
	void anAbsentChunkIsRefused() throws IOException {
		Path file = this.region(0, 0, Map.of(0, Regions.named("only one")));

		try (RegionFileView region = RegionFileView.open(file)) {
			assertThrows(IOException.class, () -> region.read(31, 31));
		}
	}

	@Test
	void anEntryPointingPastTheEndOfTheFileIsIgnored() throws IOException {
		Path file = this.region(0, 0, Map.of(0, Regions.named("real")));
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
		Path file = this.region(0, 0, Map.of(0, Regions.named("real")));
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
		Path file = this.region(0, 0, Map.of(0, Regions.named("keep me")));
		byte[] before = Files.readAllBytes(file);

		try (RegionFileView region = RegionFileView.open(file)) {
			assertNotNull(region.read(0, 0));
		}

		assertEquals(-1, java.util.Arrays.mismatch(before, Files.readAllBytes(file)));
	}

	private Path region(int regionX, int regionZ, Map<Integer, CompoundTag> chunks) throws IOException {
		return Regions.write(this.directory, regionX, regionZ, chunks);
	}
}
