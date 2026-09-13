package nbtedit.client.command;

import com.mojang.brigadier.CommandDispatcher;
import java.nio.file.Path;
import nbtedit.client.screen.WorldBrowserScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;

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
		Minecraft minecraft = source.getClient();
		IntegratedServer server = minecraft.getSingleplayerServer();
		if (server == null || !minecraft.isLocalServer()) {
			source.sendError(Component.translatable("nbtedit.command.remote"));
			return 0;
		}

		Path worldRoot = server.getWorldPath(LevelResource.ROOT);
		String levelId = worldRoot.getFileName().toString();
		minecraft.schedule(() -> minecraft.gui.setScreen(new WorldBrowserScreen(worldRoot, levelId, true, () -> minecraft.gui.setScreen(null))));
		return 1;
	}
}
