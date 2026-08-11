package dev.krypton.mcpaintfix.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import dev.krypton.mcpaintfix.McPaintFix;
import dev.krypton.mcpaintfix.paint.PaintCanvas;
import dev.krypton.mcpaintfix.paint.PaintFont;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Display;

/**
 * The on-disk copy of everything we have decoded, under {@code .minecraft/.paints/<server>/}.
 *
 * <p>This is an archive, not a cache in the performance sense: identifying a painting means decoding
 * its component anyway, and decoding is faster than reading a PNG back, so nothing here is ever on
 * the hot path. What it buys is a browsable folder of the art on a server and a paper trail of what
 * the mod actually saw, which is what makes a mismatch debuggable.
 *
 * <p>All file work happens on one background thread, so a slow disk cannot stall a frame.
 */
public final class PaintStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String INDEX_FILE = "index.json";
	private static final int MAX_LOCATIONS = 8;

	private static final ExecutorService IO = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "MC Paint Fix store");
		thread.setDaemon(true);

		return thread;
	});

	private static final AtomicBoolean INDEX_DIRTY = new AtomicBoolean();

	private static String server;
	private static Path directory;
	private static Index index = new Index();

	private PaintStore() {
	}

	/** One painting as it is recorded in {@code index.json}. */
	public static final class PaintingRecord {
		public String file;
		public int width;
		public int height;
		public String firstSeen;
		public String lastSeen;
		public List<Sighting> locations = new ArrayList<>();
	}

	public static final class Sighting {
		public String dimension;
		public int x;
		public int y;
		public int z;
		public String font;
	}

	private static final class Index {
		int version = 1;
		String server = "";
		Map<String, PaintingRecord> paintings = new LinkedHashMap<>();
	}

	/**
	 * Points the store at the server we just joined and reads back whatever it already knows.
	 *
	 * <p>Must run on the client thread, because it asks Minecraft which server this is.
	 */
	public static void onJoin() {
		String name = currentServerName();

		if (name.equals(server)) {
			return;
		}

		flush();
		server = name;
		directory = McPaintFix.gameDir().resolve(".paints").resolve(name);
		Index loaded = new Index();
		loaded.server = name;
		index = loaded;

		Path path = directory.resolve(INDEX_FILE);

		IO.execute(() -> {
			try {
				if (Files.exists(path)) {
					Index read = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), Index.class);

					if (read != null && read.paintings != null) {
						synchronized (PaintStore.class) {
							index.paintings.putAll(read.paintings);
						}
					}
				}
			} catch (Exception e) {
				McPaintFix.LOGGER.warn("Could not read {}", path, e);
			}
		});
	}

	public static void onLeave() {
		flush();
		server = null;
		directory = null;
	}

	/** Writes {@code canvas} out as {@code <hash>.png} the first time we decode it. */
	public static void store(String hash, PaintCanvas canvas) {
		Path dir = directory;

		if (dir == null) {
			return;
		}

		String stamp = Instant.now().toString();

		synchronized (PaintStore.class) {
			PaintingRecord record = index.paintings.get(hash);

			if (record != null) {
				record.lastSeen = stamp;
				INDEX_DIRTY.set(true);

				if (Files.exists(dir.resolve(record.file))) {
					return;
				}
			} else {
				record = new PaintingRecord();
				record.file = hash + ".png";
				record.width = canvas.width();
				record.height = canvas.height();
				record.firstSeen = stamp;
				record.lastSeen = stamp;
				index.paintings.put(hash, record);
				INDEX_DIRTY.set(true);
			}
		}

		int[] pixels = canvas.pixels().clone();
		int width = canvas.width();
		int height = canvas.height();

		IO.execute(() -> {
			try (NativeImage image = new NativeImage(width, height, false)) {
				for (int y = 0; y < height; y++) {
					for (int x = 0; x < width; x++) {
						image.setPixel(x, y, pixels[y * width + x]);
					}
				}

				Files.createDirectories(dir);
				image.writeToFile(dir.resolve(hash + ".png"));
			} catch (Exception e) {
				McPaintFix.LOGGER.warn("Could not write {}.png", hash, e);
			}
		});
	}

	/** Records where a painting was seen, so the PNG on disk can be traced back to a wall. */
	public static void sighted(String hash, Display.TextDisplay display, PaintFont font) {
		if (directory == null) {
			return;
		}

		BlockPos pos = display.blockPosition();
		String dimension = display.level().dimension().identifier().toString();

		synchronized (PaintStore.class) {
			PaintingRecord record = index.paintings.get(hash);

			if (record == null) {
				return;
			}

			for (Sighting sighting : record.locations) {
				if (sighting.x == pos.getX() && sighting.y == pos.getY() && sighting.z == pos.getZ() && sighting.dimension.equals(dimension)) {
					return;
				}
			}

			if (record.locations.size() >= MAX_LOCATIONS) {
				return;
			}

			Sighting sighting = new Sighting();
			sighting.dimension = dimension;
			sighting.x = pos.getX();
			sighting.y = pos.getY();
			sighting.z = pos.getZ();
			sighting.font = font.fontId().toString();
			record.locations.add(sighting);
			INDEX_DIRTY.set(true);
		}
	}

	/** Writes {@code index.json} if anything changed since the last flush. */
	public static void flush() {
		Path dir = directory;

		if (dir == null || !INDEX_DIRTY.getAndSet(false)) {
			return;
		}

		String json;

		synchronized (PaintStore.class) {
			json = GSON.toJson(index);
		}

		IO.execute(() -> {
			try {
				Files.createDirectories(dir);
				// Write beside the target and move it into place, so a crash mid-write cannot leave
				// behind an index that no longer parses.
				Path temp = dir.resolve(INDEX_FILE + ".tmp");
				Files.writeString(temp, json, StandardCharsets.UTF_8);
				Files.move(temp, dir.resolve(INDEX_FILE), StandardCopyOption.REPLACE_EXISTING);
			} catch (Exception e) {
				McPaintFix.LOGGER.warn("Could not write {}", dir.resolve(INDEX_FILE), e);
			}
		});
	}

	/**
	 * A directory name for the server we are on.
	 *
	 * <p>Addresses become path segments here, so anything that is not plainly safe is replaced rather
	 * than trusted — a hostname is server-controlled input, and it is not worth finding out what a
	 * creative one does to a path.
	 */
	private static String currentServerName() {
		Minecraft minecraft = Minecraft.getInstance();
		ServerData data = minecraft.getCurrentServer();

		if (data != null && data.ip != null && !data.ip.isBlank()) {
			return sanitise(data.ip);
		}

		return minecraft.hasSingleplayerServer() ? "singleplayer" : "unknown";
	}

	private static String sanitise(String address) {
		StringBuilder builder = new StringBuilder(address.length());

		for (int i = 0; i < address.length() && builder.length() < 64; i++) {
			char c = Character.toLowerCase(address.charAt(i));
			builder.append(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '.' || c == '-' ? c : '_');
		}

		String name = builder.toString();

		return name.isBlank() || name.equals(".") || name.equals("..") ? "unknown" : name;
	}
}
