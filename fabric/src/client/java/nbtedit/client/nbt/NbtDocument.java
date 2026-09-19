package nbtedit.client.nbt;

import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public interface NbtDocument {
	Path path();

	String name();

	Component title();

	CompoundTag root();

	void setRoot(CompoundTag root);

	@Nullable Path save() throws IOException;
}
