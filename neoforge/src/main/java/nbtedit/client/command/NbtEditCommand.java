package nbtedit.client.command;

import nbtedit.client.screen.ReadOnlyBrowser;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

public final class NbtEditCommand {
	private static final String NAME = "nbtedit";

	private NbtEditCommand() {
	}

	public static void register(RegisterClientCommandsEvent event) {
		event.getDispatcher().register(Commands.literal(NAME).executes(context -> open(context.getSource())));
	}

	private static int open(CommandSourceStack source) {
		if (ReadOnlyBrowser.open(Minecraft.getInstance())) {
			return 1;
		}

		source.sendFailure(Component.translatable("nbtedit.command.remote"));
		return 0;
	}
}
