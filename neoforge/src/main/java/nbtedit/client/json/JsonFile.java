package nbtedit.client.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import nbtedit.client.config.NbtEditConfig;
import nbtedit.client.io.SafeWrite;
import org.jspecify.annotations.Nullable;

public final class JsonFile {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().disableHtmlEscaping().create();

	private final Path path;
	private JsonElement root;

	private JsonFile(Path path, JsonElement root) {
		this.path = path;
		this.root = root;
	}

	public static JsonFile load(Path path) throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonElement root = JsonParser.parseReader(reader);
			if (root.isJsonNull()) {
				throw new IOException("File holds no JSON value: " + path);
			}

			return new JsonFile(path, root);
		}
	}

	public Path path() {
		return this.path;
	}

	public JsonElement root() {
		return this.root;
	}

	public void setRoot(JsonElement root) {
		this.root = root;
	}

	public static String toPrettyString(JsonElement element) {
		return GSON.toJson(element);
	}

	public @Nullable Path save() throws IOException {
		return SafeWrite.replace(this.path, NbtEditConfig.get().keptBackups(), target -> {
			try (Writer writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
				GSON.toJson(this.root, writer);
				writer.write('\n');
			}
		});
	}
}
