package nbtedit.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import nbtedit.client.search.SearchHit;
import nbtedit.client.search.WorldSearch;
import nbtedit.region.Regions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldSearchTest {
	private static final long TIMEOUT_MILLIS = 30_000L;

	@TempDir
	Path world;

	@Test
	void findsAValueInAnNbtFile() throws IOException {
		CompoundTag data = new CompoundTag();
		data.putString("LevelName", "a needle here");
		this.nbt("level.dat", data);

		List<SearchHit> hits = this.run("needle", true);

		assertEquals(1, hits.size());
		assertEquals(List.of("LevelName"), hits.getFirst().path());
		assertEquals("a needle here", hits.getFirst().value());
		assertEquals("level.dat", hits.getFirst().file().getFileName().toString());
	}

	@Test
	void findsAKeyAsWellAsAValue() throws IOException {
		CompoundTag data = new CompoundTag();
		data.putString("needle", "unrelated");
		this.nbt("level.dat", data);

		assertEquals(List.of("needle"), this.run("needle", true).getFirst().path());
	}

	@Test
	void listElementsAreNumberedInThePath() throws IOException {
		ListTag list = new ListTag();
		list.add(StringTag.valueOf("nothing"));
		list.add(StringTag.valueOf("the needle"));
		CompoundTag data = new CompoundTag();
		data.put("Inventory", list);
		this.nbt("player.dat", data);

		assertEquals(List.of("Inventory", "[1]"), this.run("needle", true).getFirst().path());
	}

	@Test
	void findsMatchesInsideJson() throws IOException {
		Files.write(this.world.resolve("stats.json"), "{\"stats\":{\"needle\":7}}".getBytes(StandardCharsets.UTF_8));

		List<SearchHit> hits = this.run("needle", true);

		assertEquals(1, hits.size());
		assertEquals(List.of("stats", "needle"), hits.getFirst().path());
	}

	@Test
	void findsMatchesInsideChunks() throws IOException {
		Path region = this.world.resolve("region");
		Files.createDirectories(region);
		CompoundTag chunk = new CompoundTag();
		chunk.putString("Status", "needle chunk");
		Regions.write(region, 0, 0, Map.of(Regions.index(3, 4), chunk));

		List<SearchHit> hits = this.run("needle", true);

		assertEquals(1, hits.size());
		assertEquals(3, hits.getFirst().chunk().x());
		assertEquals(4, hits.getFirst().chunk().z());
		assertEquals(List.of("Status"), hits.getFirst().path());
	}

	@Test
	void chunksAreSkippedWhenTheyAreTurnedOff() throws IOException {
		Path region = this.world.resolve("region");
		Files.createDirectories(region);
		CompoundTag chunk = new CompoundTag();
		chunk.putString("Status", "needle chunk");
		Regions.write(region, 0, 0, Map.of(Regions.index(0, 0), chunk));

		assertTrue(this.run("needle", false).isEmpty());
	}

	@Test
	void theSearchIsCaseInsensitive() throws IOException {
		CompoundTag data = new CompoundTag();
		data.putString("name", "A Needle");
		this.nbt("level.dat", data);

		assertEquals(1, this.run("nEeDlE", true).size());
	}

	@Test
	void backupsAndUnknownFilesAreLeftOut() throws IOException {
		CompoundTag data = new CompoundTag();
		data.putString("name", "needle");
		this.nbt("level.dat.20260101-000001.bak", data);
		Files.write(this.world.resolve("notes.txt"), "needle".getBytes(StandardCharsets.UTF_8));

		assertTrue(this.run("needle", true).isEmpty());
	}

	@Test
	void aNodeThatMatchesByNameAndValueIsReportedOnce() throws IOException {
		CompoundTag inner = new CompoundTag();
		inner.putString("needle", "needle");
		CompoundTag data = new CompoundTag();
		data.put("needle", inner);
		this.nbt("level.dat", data);

		List<SearchHit> hits = this.run("needle", true);

		assertEquals(2, hits.size());
		assertEquals(List.of(List.of("needle"), List.of("needle", "needle")), hits.stream().map(SearchHit::path).sorted(Comparator.comparingInt(List::size)).toList());
	}

	@Test
	void theSearchStopsAtTheLimit() throws IOException {
		CompoundTag data = new CompoundTag();
		for (int index = 0; index < 50; index++) {
			data.putString("needle" + index, "value");
		}

		this.nbt("level.dat", data);

		try (WorldSearch search = WorldSearch.start(this.world, "needle", true, 10)) {
			List<SearchHit> hits = drain(search);
			assertTrue(search.reachedLimit());
			assertTrue(hits.size() >= 10, "expected at least the limit, got " + hits.size());
		}
	}

	@Test
	void aBrokenFileDoesNotStopTheSearch() throws IOException {
		Files.write(this.world.resolve("broken.dat"), new byte[]{1, 2, 3, 4});
		CompoundTag data = new CompoundTag();
		data.putString("name", "needle");
		this.nbt("level.dat", data);

		assertEquals(1, this.run("needle", true).size());
	}

	@Test
	void nothingIsFoundForAnAbsentQuery() throws IOException {
		CompoundTag data = new CompoundTag();
		data.putString("name", "hay");
		this.nbt("level.dat", data);

		assertTrue(this.run("needle", true).isEmpty());
	}

	private void nbt(String name, CompoundTag tag) throws IOException {
		NbtIo.write(tag, this.world.resolve(name));
	}

	private List<SearchHit> run(String query, boolean regions) {
		try (WorldSearch search = WorldSearch.start(this.world, query, regions, WorldSearch.DEFAULT_LIMIT)) {
			return drain(search);
		}
	}

	private static List<SearchHit> drain(WorldSearch search) {
		List<SearchHit> hits = new ArrayList<>();
		long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
		while (!search.isDone() && System.currentTimeMillis() < deadline) {
			hits.addAll(search.drain());
			Thread.onSpinWait();
		}

		hits.addAll(search.drain());
		return hits;
	}
}
