package dev.krypton.mcpaintfix.fabric;

import dev.krypton.mcpaintfix.McPaintFix;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Everything Fabric has to know about this mod.
 *
 * <p>There is nothing to register: the frame hook and the keybind come from mixins, so the mod works
 * the same with or without Fabric API installed.
 */
public final class FabricClientEntry implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		FabricLoader loader = FabricLoader.getInstance();
		McPaintFix.init(loader.getGameDir(), loader.getConfigDir());
	}
}
