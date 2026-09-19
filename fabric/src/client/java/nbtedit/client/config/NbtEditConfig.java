package nbtedit.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import nbtedit.NBTEdit;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

public final class NbtEditConfig {
	public static final List<Integer> BACKUP_CHOICES = List.of(0, 1, 3, 5, 10);
	private static final int DEFAULT_BACKUPS = 0;
	private static final String BACKUPS_KEY = "kept_backups";
	private static final String DISCLAIMER_KEY = "disclaimer_dismissed";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static NbtEditConfig instance;

	private final Path path;
	private int keptBackups = DEFAULT_BACKUPS;
	private boolean disclaimerDismissed;

	private NbtEditConfig(Path path) {
		this.path = path;
	}

	public static NbtEditConfig get() {
		if (instance == null) {
			instance = load();
		}

		return instance;
	}

	public static Component backupChoiceName(int value) {
		return value == 0 ? Component.translatable("nbtedit.backups.off") : Component.literal(String.valueOf(value));
	}

	public int keptBackups() {
		return this.keptBackups;
	}

	public void setKeptBackups(int keptBackups) {
		if (this.keptBackups == keptBackups) {
			return;
		}

		this.keptBackups = Math.max(0, keptBackups);
		this.save();
	}

	public boolean disclaimerDismissed() {
		return this.disclaimerDismissed;
	}

	public void dismissDisclaimer() {
		if (this.disclaimerDismissed) {
			return;
		}

		this.disclaimerDismissed = true;
		this.save();
	}

	private static NbtEditConfig load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve(NBTEdit.MOD_ID + ".json");
		NbtEditConfig config = new NbtEditConfig(path);
		if (!Files.isRegularFile(path)) {
			return config;
		}

		try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
			if (json.has(BACKUPS_KEY)) {
				config.keptBackups = Math.max(0, json.get(BACKUPS_KEY).getAsInt());
			}

			if (json.has(DISCLAIMER_KEY)) {
				config.disclaimerDismissed = json.get(DISCLAIMER_KEY).getAsBoolean();
			}
		} catch (IOException | RuntimeException e) {
			NBTEdit.LOGGER.error("Failed to read config {}, falling back to defaults", path, e);
		}

		return config;
	}

	private void save() {
		JsonObject json = new JsonObject();
		json.addProperty(BACKUPS_KEY, this.keptBackups);
		json.addProperty(DISCLAIMER_KEY, this.disclaimerDismissed);

		try {
			Files.createDirectories(this.path.getParent());
			try (Writer writer = Files.newBufferedWriter(this.path, StandardCharsets.UTF_8)) {
				GSON.toJson(json, writer);
				writer.write('\n');
			}
		} catch (IOException e) {
			NBTEdit.LOGGER.error("Failed to write config {}", this.path, e);
		}
	}
}
