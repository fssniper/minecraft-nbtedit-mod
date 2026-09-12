package nbtedit.client.region;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import org.jspecify.annotations.Nullable;

public final class ChunkSurface {
	public static final int SIZE = 16;
	public static final int NO_HEIGHT = Integer.MIN_VALUE;
	private static final int COLUMNS = SIZE * SIZE;
	private static final int SECTION_HEIGHT = 16;
	private static final int MIN_BITS = 4;
	private static final int WATER_LIMIT = 32;
	private static final Paint BLANK = new Paint(0, false, true);
	private static final Map<CompoundTag, Paint> PAINTS = new ConcurrentHashMap<>();

	private final int[] colors = new int[COLUMNS];
	private final int[] heights = new int[COLUMNS];
	private final int[] waterDepths = new int[COLUMNS];

	private record Paint(int color, boolean water, boolean blank) {
	}

	private record Section(int minY, Paint[] palette, @Nullable long[] data, int bits) {
	}

	private ChunkSurface() {
		Arrays.fill(this.heights, NO_HEIGHT);
	}

	public static @Nullable ChunkSurface of(CompoundTag chunk) {
		List<Section> sections = readSections(chunk);
		if (sections.isEmpty()) {
			return null;
		}

		ChunkSurface surface = new ChunkSurface();
		int resolved = 0;
		for (int index = sections.size() - 1; index >= 0 && resolved < COLUMNS; index--) {
			resolved += surface.collect(sections.get(index));
		}

		return resolved == 0 ? null : surface;
	}

	public int color(int x, int z) {
		return this.colors[index(x, z)];
	}

	public int height(int x, int z) {
		return this.heights[index(x, z)];
	}

	public int waterDepth(int x, int z) {
		return this.waterDepths[index(x, z)];
	}

	private int collect(Section section) {
		int found = 0;
		for (int column = 0; column < COLUMNS; column++) {
			if (this.heights[column] != NO_HEIGHT) {
				continue;
			}

			for (int y = SECTION_HEIGHT - 1; y >= 0; y--) {
				Paint paint = paintAt(section, column, y);
				if (paint.blank()) {
					continue;
				}

				this.colors[column] = paint.color();
				this.heights[column] = section.minY() + y;
				this.waterDepths[column] = paint.water() ? measureWater(section, column, y) : 0;
				found++;
				break;
			}
		}

		return found;
	}

	private static int measureWater(Section section, int column, int surfaceY) {
		int depth = 0;
		for (int y = surfaceY; y >= 0 && depth < WATER_LIMIT; y--) {
			if (!paintAt(section, column, y).water()) {
				break;
			}

			depth++;
		}

		return depth;
	}

	private static Paint paintAt(Section section, int column, int y) {
		int index = y * COLUMNS + column;
		long[] data = section.data();
		if (data == null) {
			return section.palette()[0];
		}

		int perLong = 64 / section.bits();
		int slot = index / perLong;
		if (slot >= data.length) {
			return BLANK;
		}

		int value = (int) (data[slot] >>> index % perLong * section.bits() & (1L << section.bits()) - 1L);
		return value < section.palette().length ? section.palette()[value] : BLANK;
	}

	private static int index(int x, int z) {
		return z * SIZE + x;
	}

	private static List<Section> readSections(CompoundTag chunk) {
		ListTag list = chunk.getListOrEmpty("sections");
		List<Section> sections = new ArrayList<>(list.size());
		for (int index = 0; index < list.size(); index++) {
			CompoundTag tag = list.getCompoundOrEmpty(index);
			CompoundTag states = tag.getCompound("block_states").orElse(null);
			ListTag palette = states == null ? null : states.getList("palette").orElse(null);
			if (palette == null || palette.isEmpty()) {
				continue;
			}

			Paint[] paints = new Paint[palette.size()];
			for (int entry = 0; entry < palette.size(); entry++) {
				paints[entry] = PAINTS.computeIfAbsent(palette.getCompoundOrEmpty(entry), ChunkSurface::readPaint);
			}

			sections.add(
				new Section(
					tag.getByteOr("Y", (byte) 0) * SECTION_HEIGHT,
					paints,
					states.getLongArray("data").orElse(null),
					Math.max(MIN_BITS, bitsFor(palette.size()))
				)
			);
		}

		sections.sort(Comparator.comparingInt(Section::minY));
		return sections;
	}

	private static int bitsFor(int paletteSize) {
		int bits = 1;
		while (1 << bits < paletteSize) {
			bits++;
		}

		return bits;
	}

	private static Paint readPaint(CompoundTag entry) {
		BlockState state = BlockState.CODEC.parse(NbtOps.INSTANCE, entry).result().orElse(null);
		if (state == null) {
			return BLANK;
		}

		MapColor color = state.getMapColor(null, null);
		return new Paint(color.col, color == MapColor.WATER, color == MapColor.NONE);
	}
}
