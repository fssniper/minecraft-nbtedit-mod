package nbtedit.client;

import nbtedit.client.command.NbtEditCommand;
import nbtedit.client.input.NbtEditKey;
import net.fabricmc.api.ClientModInitializer;

public class NBTEditClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		NbtEditCommand.register();
		NbtEditKey.register();
	}
}
