package dev.krypton.mcpaintfix.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.krypton.mcpaintfix.client.gui.PaintOptionsScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * The keybind that opens the mod's settings, unbound by default.
 *
 * <p>It is registered by appending to {@code Options.keyMappings}, which works the same on both
 * loaders and needs no loader API. Mod Menu and NeoForge's mod list reach the same screen.
 */
public final class PaintKeys {
	public static final KeyMapping OPEN_SETTINGS = new KeyMapping(
			"key.mcpaintfix.settings", InputConstants.UNKNOWN.getValue(), KeyMapping.Category.MISC
	);

	private PaintKeys() {
	}

	static void poll(Minecraft minecraft) {
		boolean pressed = false;

		while (OPEN_SETTINGS.consumeClick()) {
			pressed = true;
		}

		if (pressed && minecraft.gui.screen() == null) {
			minecraft.gui.setScreen(new PaintOptionsScreen(null));
		}
	}
}
