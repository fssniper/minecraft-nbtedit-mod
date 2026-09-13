package nbtedit.client;

import nbtedit.client.command.NbtEditCommand;
import net.fabricmc.api.ClientModInitializer;

public class NBTEditClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		NbtEditCommand.register();
	}
}
