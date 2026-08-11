package dev.krypton.mcpaintfix.neoforge;

import dev.krypton.mcpaintfix.McPaintFix;
import dev.krypton.mcpaintfix.client.gui.PaintOptionsScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Everything NeoForge has to know about this mod.
 *
 * <p>Like the Fabric side there is nothing to register but the settings screen: the frame hook and
 * the keybind come from mixins, which are the same on both loaders.
 */
@Mod(value = McPaintFix.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeClientEntry {
	public NeoForgeClientEntry(ModContainer container) {
		McPaintFix.init(FMLPaths.GAMEDIR.get(), FMLPaths.CONFIGDIR.get());
		container.registerExtensionPoint(IConfigScreenFactory.class, (owner, parent) -> new PaintOptionsScreen(parent));
	}
}
