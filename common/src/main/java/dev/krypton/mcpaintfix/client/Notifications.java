package dev.krypton.mcpaintfix.client;

import dev.krypton.mcpaintfix.McPaintFix;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Everything the mod says.
 *
 * <p>Silence is the default. Chat and progress logging are behind {@link PaintConfig#debug}; only a
 * genuine fault — a canvas that will not decode, a resource pack that measures canvases differently,
 * a file that will not write — reaches the log unconditionally, and each of those warns once.
 */
public final class Notifications {
	private static final Component PREFIX = Component.literal("[MC Paint Fix] ").withStyle(ChatFormatting.GRAY);

	private Notifications() {
	}

	/** A line in chat, if the player asked for them. */
	public static void chat(String key, Object... args) {
		if (!PaintConfig.get().debug) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft.player != null) {
			minecraft.player.displayClientMessage(Component.empty().append(PREFIX).append(Component.translatable(key, args)), false);
		}
	}

	/** A line in the log, if the player asked for them. */
	public static void debug(String format, Object... args) {
		if (PaintConfig.get().debug) {
			McPaintFix.LOGGER.info(format, args);
		}
	}

	/** A fault worth a bug report. Always logged, and mirrored to chat in debug mode. */
	public static void problem(String chatKey, String format, Object... args) {
		McPaintFix.LOGGER.warn(format, args);
		chat(chatKey);
	}
}
