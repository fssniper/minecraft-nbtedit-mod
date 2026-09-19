package nbtedit.client;

import nbtedit.NBTEdit;
import nbtedit.client.command.NbtEditCommand;
import nbtedit.client.input.NbtEditKey;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = NBTEdit.MOD_ID, dist = Dist.CLIENT)
public class NBTEditClient {
	public NBTEditClient(IEventBus modBus) {
		modBus.addListener(NbtEditKey::register);
		NeoForge.EVENT_BUS.addListener(NbtEditKey::tick);
		NeoForge.EVENT_BUS.addListener(NbtEditCommand::register);
	}
}
