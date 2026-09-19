package nbtedit.region;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Random;
import nbtedit.client.region.RegionFileView;
import nbtedit.client.region.RegionFileWriter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegionFileWriterTest {
	private static final int OVERSIZED_BYTES = 1_200_000;

	@TempDir
	Path directory;

	@Test
	void theEditedChunkComesBackChanged() throws IOException {
		Path file = this.region(Map.of(Regions.index(0, 0), Regions.named("before")));
		RegionFileWriter.replaceChunk(file, 0, 0, Regions.named("after"), 0);
		assertEquals("after", this.read(file, 0, 0));
	}

	@Test
	void theOtherChunksAreLeftAsTheyWere() throws IOException {
		Path file = this.region(
			Map.of(Regions.index(0, 0), Regions.named("first"), Regions.index(5, 2), Regions.named("second"), Regions.index(31, 31), Regions.named("last"))
		);
		RegionFileWriter.replaceChunk(file, 5, 2, Regions.named("edited"), 0);

		assertEquals("first", this.read(file, 0, 0));
		assertEquals("edited", this.read(file, 5, 2));
		assertEquals("last", this.read(file, 31, 31));
	}

	@Test
	void aChunkThatOutgrowsItsSectorsMovesWithoutTouchingTheRest() throws IOException {
		Path file = this.region(Map.of(Regions.index(0, 0), Regions.named("small"), Regions.index(1, 0), Regions.named("neighbour")));
		RegionFileWriter.replaceChunk(file, 0, 0, bulky(60_000), 0);

		assertEquals("neighbour", this.read(file, 1, 0));

		try (RegionFileView region = RegionFileView.open(file)) {
			assertEquals(60_000, region.read(0, 0).getByteArray("bulk").orElseThrow().length);
			assertTrue(region.chunks().getFirst().allocatedBytes() > Regions.SECTOR_BYTES);
		}
	}

	@Test
	void aChunkThatShrinksReleasesItsSpace() throws IOException {
		Path file = this.region(Map.of(Regions.index(0, 0), bulky(60_000), Regions.index(1, 0), Regions.named("neighbour")));
		long before = Files.size(file);
		RegionFileWriter.replaceChunk(file, 0, 0, Regions.named("small"), 0);

		assertEquals("small", this.read(file, 0, 0));
		assertEquals("neighbour", this.read(file, 1, 0));
		assertTrue(Files.size(file) < before);
	}

	@Test
	void timestampsOfUntouchedChunksSurvive() throws IOException {
		Path file = this.region(Map.of(Regions.index(0, 0), Regions.named("first"), Regions.index(1, 0), Regions.named("second")));
		int kept;

		try (RegionFileView region = RegionFileView.open(file)) {
			kept = region.chunks().getLast().timestamp();
		}

		RegionFileWriter.replaceChunk(file, 0, 0, Regions.named("edited"), 0);

		try (RegionFileView region = RegionFileView.open(file)) {
			assertEquals(kept, region.chunks().getLast().timestamp());
			assertTrue(region.chunks().getFirst().timestamp() > kept);
		}
	}

	@Test
	void theCompressionOfTheChunkIsKept() throws IOException {
		Path file = Regions.write(
			this.directory, 0, 0, Map.of(Regions.index(0, 0), Regions.named("gzipped")), RegionFileVersion.VERSION_GZIP
		);
		RegionFileWriter.replaceChunk(file, 0, 0, Regions.named("still gzipped"), 0);

		try (RegionFileView region = RegionFileView.open(file)) {
			assertEquals(RegionFileVersion.VERSION_GZIP.getId(), region.record(0, 0)[4]);
			assertEquals("still gzipped", region.read(0, 0).getString("name").orElseThrow());
		}
	}

	@Test
	void aCopyHoldsTheRegionFromBeforeTheWrite() throws IOException {
		Path file = this.region(Map.of(Regions.index(0, 0), Regions.named("before")));
		byte[] original = Files.readAllBytes(file);
		Path backup = RegionFileWriter.replaceChunk(file, 0, 0, Regions.named("after"), 1);

		assertNotNull(backup);
		assertArrayEquals(original, Files.readAllBytes(backup));
	}

	@Test
	void noCopyIsKeptWithBackupsTurnedOff() throws IOException {
		Path file = this.region(Map.of(Regions.index(0, 0), Regions.named("before")));

		assertNull(RegionFileWriter.replaceChunk(file, 0, 0, Regions.named("after"), 0));
		try (var entries = Files.list(this.directory)) {
			assertTrue(entries.noneMatch(entry -> entry.getFileName().toString().endsWith(".bak")));
		}
	}

	@Test
	void noTemporaryFileIsLeftBehind() throws IOException {
		Path file = this.region(Map.of(Regions.index(0, 0), Regions.named("before")));
		RegionFileWriter.replaceChunk(file, 0, 0, Regions.named("after"), 0);

		try (var entries = Files.list(this.directory)) {
			assertTrue(entries.noneMatch(entry -> entry.getFileName().toString().contains("nbtedit_tmp")));
		}
	}

	@Test
	void aChunkOutsideTheRegionIsRefused() throws IOException {
		Path file = this.region(Map.of(Regions.index(0, 0), Regions.named("only")));
		byte[] original = Files.readAllBytes(file);

		assertThrows(IOException.class, () -> RegionFileWriter.replaceChunk(file, 100, 100, Regions.named("nowhere"), 0));
		assertArrayEquals(original, Files.readAllBytes(file));
	}

	@Test
	void aChunkThatIsNotStoredIsRefused() throws IOException {
		Path file = this.region(Map.of(Regions.index(0, 0), Regions.named("only")));
		byte[] original = Files.readAllBytes(file);

		assertThrows(IOException.class, () -> RegionFileWriter.replaceChunk(file, 7, 7, Regions.named("absent"), 0));
		assertArrayEquals(original, Files.readAllBytes(file));
	}

	@Test
	void anOversizedChunkGoesIntoItsOwnFile() throws IOException {
		Path file = this.region(Map.of(Regions.index(2, 1), Regions.named("small"), Regions.index(3, 1), Regions.named("neighbour")));
		RegionFileWriter.replaceChunk(file, 2, 1, bulky(OVERSIZED_BYTES), 0);
		Path external = this.directory.resolve("c.2.1.mcc");

		assertTrue(Files.exists(external));

		try (RegionFileView region = RegionFileView.open(file)) {
			assertEquals(Regions.SECTOR_BYTES, region.chunks().getFirst().allocatedBytes());
			assertEquals(OVERSIZED_BYTES, region.read(2, 1).getByteArray("bulk").orElseThrow().length);
			assertEquals("neighbour", region.read(3, 1).getString("name").orElseThrow());
		}
	}

	@Test
	void aChunkThatStopsBeingOversizedDropsItsOwnFile() throws IOException {
		Path file = this.region(Map.of(Regions.index(2, 1), Regions.named("small")));
		RegionFileWriter.replaceChunk(file, 2, 1, bulky(OVERSIZED_BYTES), 0);
		Path external = this.directory.resolve("c.2.1.mcc");
		assertTrue(Files.exists(external));

		RegionFileWriter.replaceChunk(file, 2, 1, Regions.named("small again"), 0);

		assertFalse(Files.exists(external));
		assertEquals("small again", this.read(file, 2, 1));
	}

	private Path region(Map<Integer, CompoundTag> chunks) throws IOException {
		return Regions.write(this.directory, 0, 0, chunks);
	}

	private String read(Path file, int x, int z) throws IOException {
		try (RegionFileView region = RegionFileView.open(file)) {
			return region.read(x, z).getString("name").orElseThrow();
		}
	}

	private static CompoundTag bulky(int bytes) {
		byte[] noise = new byte[bytes];
		new Random(7).nextBytes(noise);
		CompoundTag tag = Regions.named("bulky");
		tag.putByteArray("bulk", noise);
		return tag;
	}
}
