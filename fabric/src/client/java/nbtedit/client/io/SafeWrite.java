package nbtedit.client.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import nbtedit.NBTEdit;
import org.jspecify.annotations.Nullable;

public final class SafeWrite {
	private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final String BACKUP_SUFFIX = ".bak";

	private SafeWrite() {
	}

	public static @Nullable Path replace(Path path, int keptBackups, Writer writer) throws IOException {
		Path backup = keptBackups > 0 ? backUp(path, keptBackups) : null;
		Path temporary = path.resolveSibling(path.getFileName() + ".nbtedit_tmp");
		writer.write(temporary);
		Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
		return backup;
	}

	public static @Nullable Path remove(Path path, int keptBackups) throws IOException {
		Path backup = keptBackups > 0 ? backUp(path, keptBackups) : null;
		Files.delete(path);
		return backup;
	}

	private static Path backUp(Path path, int keptBackups) throws IOException {
		Path backup = path.resolveSibling(path.getFileName() + "." + BACKUP_STAMP.format(LocalDateTime.now()) + BACKUP_SUFFIX);
		Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
		prune(path, keptBackups);
		return backup;
	}

	private static void prune(Path path, int keptBackups) {
		Pattern pattern = Pattern.compile(Pattern.quote(path.getFileName().toString()) + "\\.\\d{8}-\\d{6}" + Pattern.quote(BACKUP_SUFFIX));
		Path directory = path.getParent();
		if (directory == null) {
			return;
		}

		try (Stream<Path> entries = Files.list(directory)) {
			List<Path> backups = entries.filter(entry -> pattern.matcher(entry.getFileName().toString()).matches())
				.sorted(Comparator.comparing((Path entry) -> entry.getFileName().toString()).reversed())
				.toList();
			for (Path stale : backups.subList(Math.min(keptBackups, backups.size()), backups.size())) {
				Files.deleteIfExists(stale);
			}
		} catch (IOException e) {
			NBTEdit.LOGGER.error("Failed to prune backups of {}", path, e);
		}
	}

	@FunctionalInterface
	public interface Writer {
		void write(Path target) throws IOException;
	}
}
