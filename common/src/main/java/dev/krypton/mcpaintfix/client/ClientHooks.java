package dev.krypton.mcpaintfix.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

/**
 * The mod's client lifecycle, driven entirely from the frame hook.
 *
 * <p>Joining and leaving are read off the connection object rather than subscribed to: it is one
 * reference compare per frame, it needs no loader API, and it puts every cache change on the render
 * thread, where freeing a texture is legal. Dimension changes keep the same connection, so a trip to
 * the Nether does not throw away the textures of the overworld.
 */
public final class ClientHooks {
	/** Frames between index writes; the index is small and losing half a minute of it costs nothing. */
	private static final int FLUSH_INTERVAL_FRAMES = 1200;

	private static ClientPacketListener connection;
	private static int framesToFlush = FLUSH_INTERVAL_FRAMES;

	private ClientHooks() {
	}

	public static void onFrameStart() {
		Minecraft minecraft = Minecraft.getInstance();
		PaintCache.onFrameStart();

		ClientPacketListener current = minecraft.getConnection();

		if (current != connection) {
			if (connection != null) {
				onLeave();
			}

			connection = current;

			if (current != null) {
				onJoin();
			}
		}

		PaintKeys.poll(minecraft);

		if (--framesToFlush <= 0) {
			framesToFlush = FLUSH_INTERVAL_FRAMES;
			PaintStore.flush();
		}
	}

	/** A resource pack swap can change the advances the geometry check was verified against. */
	public static void onResourceReload() {
		PaintGeometry.reset();
	}

	public static void onShutdown() {
		PaintStore.flush();
	}

	private static void onJoin() {
		if (PaintConfig.get().exportPng) {
			PaintStore.onJoin();
		}
	}

	private static void onLeave() {
		// Textures belong to a world: a canvas on the next server is a different canvas even if the
		// pixels happen to match. That server also means a different resource pack, so the geometry
		// check has to be earned again rather than inherited.
		PaintCache.clear();
		PaintGeometry.reset();
		PaintStore.onLeave();
	}
}
