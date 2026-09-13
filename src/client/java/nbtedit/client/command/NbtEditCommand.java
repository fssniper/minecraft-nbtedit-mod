package nbtedit.client.command;

import com.mojang.brigadier.CommandDispatcher;
import nbtedit.client.screen.ReadOnlyBrowser;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

public final class NbtEditCommand {
	private static final String NAME = "nbtedit";

	private NbtEditCommand() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(ClientCommands.literal(NAME).executes(context -> open(context.getSource())));
	}

	private static int open(FabricClientCommandSource source) {
		if (ReadOnlyBrowser.open(source.getClient())) {
			return 1;
		}

		source.sendError(Component.translatable("nbtedit.command.remote"));
		return 0;
	}
}
