package nbtedit.client.search;

import java.nio.file.Path;
import java.util.List;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

public record SearchHit(Path file, @Nullable ChunkPos chunk, List<String> path, String value) {
	public String where() {
		return this.chunk == null ? this.file.getFileName().toString() : this.file.getFileName() + " " + this.chunk.x() + ", " + this.chunk.z();
	}

	public String label() {
		return String.join(" / ", this.path);
	}
}
