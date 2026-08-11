package dev.krypton.mcpaintfix.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.krypton.mcpaintfix.McPaintFix;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Settings, stored in {@code config/mcpaint-fix.json}. Everything here is also in the mod's menu. */
public final class PaintConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String FILE_NAME = "mcpaint-fix.json";

	/** Replace MC Paint canvases with a cached texture. Off means plain vanilla rendering. */
	public boolean enabled = true;

	/** Print what the mod is doing to chat and to the log. Off, the mod says nothing. */
	public boolean debug = false;

	/** Write each decoded canvas to {@code .paints/<server>/} as a PNG plus an index entry. */
	public boolean exportPng = true;

	/**
	 * Skip vanilla's line splitting for canvases we are going to replace.
	 *
	 * <p>The split walks every glyph of the component and runs once per canvas per text change, so it
	 * is a burst of work exactly when a city's worth of paintings streams in.
	 */
	public boolean skipTextLayout = true;

	/** Textures kept resident. Each canvas is small, so this is generous by design. */
	public int maxCachedCanvases = 1024;

	/**
	 * Refuse to decode anything larger than this.
	 *
	 * <p>MC Paint caps a canvas at 64 by 64 pixels, so this leaves sixteen times the headroom a real
	 * painting needs while still bounding what a malformed component can make us allocate.
	 */
	public int maxCanvasPixels = 1 << 16;

	/** How long one frame may spend decoding and uploading new canvases, in microseconds. */
	public long bakeBudgetMicros = 2000;

	/** Text changes within {@link #volatileWindowMillis} before a display is left to vanilla. */
	public int volatileFlipThreshold = 3;

	/** How long a display must hold still before we are willing to bake it again. */
	public long volatileWindowMillis = 5000;

	private static Path directory = Path.of("config");
	private static PaintConfig instance;

	public static void init(Path configDirectory) {
		directory = configDirectory;
		instance = null;
	}

	public static PaintConfig get() {
		if (instance == null) {
			instance = load();
		}

		return instance;
	}

	private static PaintConfig load() {
		Path path = directory.resolve(FILE_NAME);

		if (Files.exists(path)) {
			try {
				PaintConfig loaded = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), PaintConfig.class);

				if (loaded != null) {
					return loaded;
				}
			} catch (Exception e) {
				McPaintFix.LOGGER.warn("Could not read {}, using defaults", path, e);
			}
		}

		PaintConfig config = new PaintConfig();
		config.save();

		return config;
	}

	public void save() {
		Path path = directory.resolve(FILE_NAME);

		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (IOException e) {
			McPaintFix.LOGGER.warn("Could not write {}", path, e);
		}
	}

	/** Applies a change to {@link #enabled}, dropping whatever the old setting had cached. */
	public void setEnabled(boolean value) {
		if (this.enabled == value) {
			return;
		}

		this.enabled = value;

		if (value) {
			PaintCache.invalidateBindings();
		} else {
			PaintCache.clear();
		}

		Notifications.chat(value ? "notification.mcpaintfix.enabled" : "notification.mcpaintfix.disabled");
	}
}
