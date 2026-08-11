package dev.krypton.mcpaintfix.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.krypton.mcpaintfix.client.gui.PaintOptionsScreen;

/**
 * The "Config" button in Mod Menu's mod list.
 *
 * <p>Mod Menu only loads this entry point when it is itself installed, so the class never resolves
 * without it and the mod carries no runtime dependency on it.
 */
public final class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return PaintOptionsScreen::new;
	}
}
