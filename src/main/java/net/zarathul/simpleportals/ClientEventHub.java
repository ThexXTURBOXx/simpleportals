package net.zarathul.simpleportals;

import net.minecraftforge.fml.client.event.ConfigChangedEvent.OnConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.zarathul.simpleportals.configuration.Config;

/**
 * Hosts Forge event handlers on the client side.
 */
public final class ClientEventHub {
	@SubscribeEvent
	public void OnConfigChanged(OnConfigChangedEvent event) {
		if (SimplePortals.MOD_ID.equals(event.getModID())) {
			Config.sync();
		}
	}
}
