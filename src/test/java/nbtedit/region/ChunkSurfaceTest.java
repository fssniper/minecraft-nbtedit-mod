package nbtedit.region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import nbtedit.client.region.ChunkSurface;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ChunkSurfaceTest {
	private static final int SECTION_BLOCKS = 4096;
	private static final String AIR = "minecraft:air";
	private static final String STONE = "minecraft:stone";
	private static final String WATER = "minecraft:water";
	private static final String DIRT = "minecraft:dirt";

	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void aSectionWithoutDataIsOneBlockThroughout() {
		ChunkSurface surface = ChunkSurface.of(chunk(section(0, List.of(STONE), null)));

		assertNotNull(surface);
		assertEquals(color(Blocks.STONE), surface.color(0, 0));
		assertEquals(15, surface.height(9, 9));
	}

	@Test
	void airIsSkippedUntilASolidBlockIsFound() {
		int[] blocks = new int[SECTION_BLOCKS];
		for (int y = 0; y <= 7; y++) {
			blocks[at(3, y, 4)] = 1;
		}

		ChunkSurface surface = ChunkSurface.of(chunk(section(0, List.of(AIR, STONE), blocks)));

		assertNotNull(surface);
		assertEquals(7, surface.height(3, 4));
		assertEquals(color(Blocks.STONE), surface.color(3, 4));
	}

	@Test
	void aColumnOfOnlyAirStaysEmpty() {
		int[] blocks = new int[SECTION_BLOCKS];
		blocks[at(0, 0, 0)] = 1;
		ChunkSurface surface = ChunkSurface.of(chunk(section(0, List.of(AIR, STONE), blocks)));

		assertNotNull(surface);
		assertEquals(ChunkSurface.NO_HEIGHT, surface.height(5, 5));
	}

	@Test
	void higherSectionsWinOverLowerOnes() {
		int[] lower = new int[SECTION_BLOCKS];
		int[] upper = new int[SECTION_BLOCKS];
		for (int y = 0; y < 16; y++) {
			lower[at(1, y, 1)] = 1;
		}

		upper[at(1, 0, 1)] = 1;
		ChunkSurface surface = ChunkSurface.of(chunk(section(0, List.of(AIR, STONE), lower), section(1, List.of(AIR, DIRT), upper)));

		assertNotNull(surface);
		assertEquals(16, surface.height(1, 1));
		assertEquals(color(Blocks.DIRT), surface.color(1, 1));
	}

	@Test
	void sectionsBelowZeroKeepTheirHeight() {
		ChunkSurface surface = ChunkSurface.of(chunk(section(-4, List.of(STONE), null)));

		assertNotNull(surface);
		assertEquals(-49, surface.height(0, 0));
	}

	@Test
	void waterIsMeasuredDownToTheFloor() {
		int[] blocks = new int[SECTION_BLOCKS];
		for (int y = 0; y <= 3; y++) {
			blocks[at(2, y, 2)] = 1;
		}

		for (int y = 4; y <= 9; y++) {
			blocks[at(2, y, 2)] = 2;
		}

		ChunkSurface surface = ChunkSurface.of(chunk(section(0, List.of(AIR, STONE, WATER), blocks)));

		assertNotNull(surface);
		assertEquals(9, surface.height(2, 2));
		assertEquals(color(Blocks.WATER), surface.color(2, 2));
		assertEquals(6, surface.waterDepth(2, 2));
		assertEquals(0, surface.waterDepth(0, 0));
	}

	@Test
	void paletteWiderThanFourBitsIsUnpacked() {
		List<String> palette = new java.util.ArrayList<>(List.of(AIR));
		for (int index = 1; index <= 16; index++) {
			palette.add("minecraft:" + NAMES.get(index - 1));
		}

		int[] blocks = new int[SECTION_BLOCKS];
		blocks[at(0, 0, 0)] = 16;
		blocks[at(1, 0, 0)] = 9;

		ChunkSurface surface = ChunkSurface.of(chunk(section(0, palette, blocks)));

		assertNotNull(surface);
		assertEquals(0, surface.height(0, 0));
		assertEquals(color(block(NAMES.get(15))), surface.color(0, 0));
		assertEquals(color(block(NAMES.get(8))), surface.color(1, 0));
	}

	@Test
	void aChunkWithoutSectionsIsRejected() {
		assertNull(ChunkSurface.of(new CompoundTag()));
	}

	@Test
	void anEmptyPaletteIsIgnored() {
		CompoundTag chunk = chunk(section(0, List.of(), null));
		assertNull(ChunkSurface.of(chunk));
	}

	private static final List<String> NAMES = List.of(
		"stone",
		"dirt",
		"sand",
		"gravel",
		"clay",
		"grass_block",
		"oak_log",
		"oak_planks",
		"cobblestone",
		"andesite",
		"granite",
		"diorite",
		"sandstone",
		"terracotta",
		"obsidian",
		"bedrock"
	);

	private static int at(int x, int y, int z) {
		return y * 256 + z * 16 + x;
	}

	private static int color(Block block) {
		return block.defaultBlockState().getMapColor(null, null).col;
	}

	private static Block block(String name) {
		return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(name));
	}

	private static CompoundTag chunk(CompoundTag... sections) {
		CompoundTag chunk = new CompoundTag();
		ListTag list = new ListTag();
		list.addAll(List.of(sections));
		chunk.put("sections", list);
		return chunk;
	}

	private static CompoundTag section(int sectionY, List<String> palette, int @org.jspecify.annotations.Nullable [] blocks) {
		ListTag entries = new ListTag();
		for (String name : palette) {
			CompoundTag entry = new CompoundTag();
			entry.putString("Name", name);
			entries.add(entry);
		}

		CompoundTag states = new CompoundTag();
		states.put("palette", entries);
		if (blocks != null) {
			states.putLongArray("data", pack(blocks, Math.max(4, bitsFor(palette.size()))));
		}

		CompoundTag section = new CompoundTag();
		section.putByte("Y", (byte) sectionY);
		section.put("block_states", states);
		return section;
	}

	private static int bitsFor(int paletteSize) {
		int bits = 1;
		while (1 << bits < paletteSize) {
			bits++;
		}

		return bits;
	}

	private static long[] pack(int[] blocks, int bits) {
		int perLong = 64 / bits;
		long[] packed = new long[(blocks.length + perLong - 1) / perLong];
		for (int index = 0; index < blocks.length; index++) {
			packed[index / perLong] |= (long) blocks[index] << index % perLong * bits;
		}

		return packed;
	}
}
