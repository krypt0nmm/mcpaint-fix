package dev.krypton.mcpaintfix;

import dev.krypton.mcpaintfix.client.PaintConfig;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Entry point shared by both loaders. */
public final class McPaintFix {
	public static final String MOD_ID = "mcpaintfix";
	public static final String MOD_NAME = "MC Paint Fix";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

	private static Path gameDir = Path.of(".");

	private McPaintFix() {
	}

	/**
	 * Called by each loader's client entry point with the directories it resolves.
	 *
	 * <p>Both are passed in rather than derived from Minecraft, because a loader constructs its mods
	 * before the client exists.
	 */
	public static void init(Path gameDirectory, Path configDirectory) {
		gameDir = gameDirectory;
		PaintConfig.init(configDirectory);
	}

	public static Path gameDir() {
		return gameDir;
	}
}
