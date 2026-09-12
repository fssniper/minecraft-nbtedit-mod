package nbtedit.client.region;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.MapColor;

public final class TerrainTile {
	public static final int REGION_CHUNKS = 32;
	public static final int PIXELS = REGION_CHUNKS * ChunkSurface.SIZE;
	private static final int HIGH = MapColor.Brightness.HIGH.modifier;
	private static final int NORMAL = MapColor.Brightness.NORMAL.modifier;
	private static final int LOW = MapColor.Brightness.LOW.modifier;
	private static final double STEP_UP = 0.6;
	private static final double STEP_DOWN = -0.6;
	private static final double CHECKER = 0.4;
	private static final double WATER_STEP = 0.1;
	private static final double WATER_CHECKER = 0.2;
	private static final double WATER_SHALLOW = 0.5;
	private static final double WATER_DEEP = 0.9;

	private TerrainTile() {
	}

	public static int[] render(Path regionFile) throws IOException {
		int[] colors = new int[PIXELS * PIXELS];
		int[] heights = new int[PIXELS * PIXELS];
		int[] waterDepths = new int[PIXELS * PIXELS];
		Arrays.fill(heights, ChunkSurface.NO_HEIGHT);

		try (RegionFileView region = RegionFileView.open(regionFile)) {
			for (RegionFileView.Chunk chunk : region.chunks()) {
				CompoundTag tag;

				try {
					tag = region.read(chunk);
				} catch (IOException | RuntimeException e) {
					continue;
				}

				ChunkSurface surface = ChunkSurface.of(tag);
				if (surface == null) {
					continue;
				}

				int offsetX = Math.floorMod(chunk.x(), REGION_CHUNKS) * ChunkSurface.SIZE;
				int offsetZ = Math.floorMod(chunk.z(), REGION_CHUNKS) * ChunkSurface.SIZE;
				for (int z = 0; z < ChunkSurface.SIZE; z++) {
					for (int x = 0; x < ChunkSurface.SIZE; x++) {
						int pixel = (offsetZ + z) * PIXELS + offsetX + x;
						colors[pixel] = surface.color(x, z);
						heights[pixel] = surface.height(x, z);
						waterDepths[pixel] = surface.waterDepth(x, z);
					}
				}
			}
		}

		return shade(colors, heights, waterDepths);
	}

	private static int[] shade(int[] colors, int[] heights, int[] waterDepths) {
		int[] pixels = new int[colors.length];
		for (int z = 0; z < PIXELS; z++) {
			for (int x = 0; x < PIXELS; x++) {
				int pixel = z * PIXELS + x;
				if (heights[pixel] == ChunkSurface.NO_HEIGHT) {
					continue;
				}

				int checker = x + z & 1;
				int modifier;
				if (waterDepths[pixel] > 0) {
					double depth = waterDepths[pixel] * WATER_STEP + checker * WATER_CHECKER;
					modifier = depth < WATER_SHALLOW ? HIGH : depth > WATER_DEEP ? LOW : NORMAL;
				} else {
					int north = z > 0 ? heights[pixel - PIXELS] : heights[pixel];
					double slope = heights[pixel] - (north == ChunkSurface.NO_HEIGHT ? heights[pixel] : north) + (checker - 0.5) * CHECKER;
					modifier = slope > STEP_UP ? HIGH : slope < STEP_DOWN ? LOW : NORMAL;
				}

				pixels[pixel] = shade(colors[pixel], modifier);
			}
		}

		return pixels;
	}

	private static int shade(int color, int modifier) {
		int red = (color >> 16 & 0xFF) * modifier / 255;
		int green = (color >> 8 & 0xFF) * modifier / 255;
		int blue = (color & 0xFF) * modifier / 255;
		return 0xFF000000 | red << 16 | green << 8 | blue;
	}
}
