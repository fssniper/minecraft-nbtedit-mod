package nbtedit.client.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class SafeWrite {
	private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private SafeWrite() {
	}

	public static Path replace(Path path, Writer writer) throws IOException {
		Path backup = path.resolveSibling(path.getFileName() + "." + BACKUP_STAMP.format(LocalDateTime.now()) + ".bak");
		Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
		Path temporary = path.resolveSibling(path.getFileName() + ".nbtedit_tmp");
		writer.write(temporary);
		Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
		return backup;
	}

	@FunctionalInterface
	public interface Writer {
		void write(Path target) throws IOException;
	}
}
