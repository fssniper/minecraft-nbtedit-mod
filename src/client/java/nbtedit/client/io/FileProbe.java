package nbtedit.client.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FileProbe {
	private static final int SAMPLE_SIZE = 4096;
	private static final int MAX_SUSPICIOUS_PERCENT = 5;

	private FileProbe() {
	}

	public static boolean looksLikeText(Path path) {
		try (InputStream in = Files.newInputStream(path)) {
			byte[] sample = in.readNBytes(SAMPLE_SIZE);
			if (sample.length == 0) {
				return true;
			}

			int suspicious = 0;
			for (byte value : sample) {
				int unsigned = value & 0xFF;
				if (unsigned == 0) {
					return false;
				}

				if (unsigned < 0x20 && unsigned != '\t' && unsigned != '\n' && unsigned != '\r') {
					suspicious++;
				}
			}

			return suspicious * 100 / sample.length < MAX_SUSPICIOUS_PERCENT;
		} catch (IOException e) {
			return false;
		}
	}
}
