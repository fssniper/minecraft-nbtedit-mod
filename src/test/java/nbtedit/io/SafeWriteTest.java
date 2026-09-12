package nbtedit.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import nbtedit.client.io.SafeWrite;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SafeWriteTest {
	@TempDir
	Path directory;

	@Test
	void replaceWritesTheNewContent() throws IOException {
		Path file = this.write("level.dat", "before");
		assertNull(SafeWrite.replace(file, 0, target -> Files.writeString(target, "after")));
		assertEquals("after", Files.readString(file));
	}

	@Test
	void replaceLeavesNoTemporaryFile() throws IOException {
		Path file = this.write("level.dat", "before");
		SafeWrite.replace(file, 0, target -> Files.writeString(target, "after"));
		assertEquals(List.of("level.dat"), this.names());
	}

	@Test
	void aFailedWriteKeepsTheOriginal() throws IOException {
		Path file = this.write("level.dat", "before");
		assertThrows(IOException.class, () -> SafeWrite.replace(file, 0, target -> {
			throw new IOException("no room");
		}));
		assertEquals("before", Files.readString(file));
	}

	@Test
	void backupHoldsTheContentFromBeforeTheWrite() throws IOException {
		Path file = this.write("level.dat", "before");
		Path backup = SafeWrite.replace(file, 3, target -> Files.writeString(target, "after"));
		assertNotNull(backup);
		assertEquals("before", Files.readString(backup));
		assertEquals("after", Files.readString(file));
	}

	@Test
	void backupsAreNotKeptWhenTheyAreTurnedOff() throws IOException {
		Path file = this.write("level.dat", "before");
		assertNull(SafeWrite.replace(file, 0, target -> Files.writeString(target, "after")));
		assertEquals(List.of("level.dat"), this.names());
	}

	@Test
	void onlyTheNewestBackupsSurvive() throws IOException {
		Path file = this.write("level.dat", "content");
		List<Path> stale = List.of(
			this.write("level.dat.20260101-000001.bak", "one"),
			this.write("level.dat.20260101-000002.bak", "two"),
			this.write("level.dat.20260101-000003.bak", "three")
		);
		Path backup = SafeWrite.replace(file, 2, target -> Files.writeString(target, "next"));

		assertNotNull(backup);
		assertTrue(Files.exists(backup));
		assertTrue(Files.exists(stale.get(2)));
		assertFalse(Files.exists(stale.get(1)));
		assertFalse(Files.exists(stale.get(0)));
	}

	@Test
	void backupsOfOtherFilesAreLeftAlone() throws IOException {
		Path file = this.write("level.dat", "content");
		Path other = this.write("stats.json.20260101-000001.bak", "other");
		SafeWrite.replace(file, 1, target -> Files.writeString(target, "next"));
		assertTrue(Files.exists(other));
	}

	@Test
	void removeDeletesTheFileAndCanKeepACopy() throws IOException {
		Path file = this.write("level.dat", "content");
		Path backup = SafeWrite.remove(file, 1);

		assertFalse(Files.exists(file));
		assertNotNull(backup);
		assertEquals("content", Files.readString(backup));
	}

	@Test
	void removeWithoutBackupsLeavesNothingBehind() throws IOException {
		Path file = this.write("level.dat", "content");
		assertNull(SafeWrite.remove(file, 0));
		assertEquals(List.of(), this.names());
	}

	private Path write(String name, String content) throws IOException {
		return Files.write(this.directory.resolve(name), content.getBytes(StandardCharsets.UTF_8));
	}

	private List<String> names() throws IOException {
		try (Stream<Path> entries = Files.list(this.directory)) {
			return entries.map(entry -> entry.getFileName().toString()).sorted().toList();
		}
	}
}
