package dev.krypton.mcpaintfix.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.krypton.mcpaintfix.McPaintFix;
import dev.krypton.mcpaintfix.paint.PaintCanvas;
import dev.krypton.mcpaintfix.paint.PaintDecoder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;

/**
 * Decoded canvases, keyed by content, shared by every entity that shows the same picture.
 *
 * <p>A city full of paintings is mostly the same few pictures repeated, so keying on content rather
 * than on entity collapses hundreds of copies onto a handful of textures.
 *
 * <p>Decoding and uploading happen inline on the render thread rather than on a worker: a canvas is
 * a few thousand pixels, so the work is measured in tens of microseconds, and a per-frame budget
 * keeps a whole district streaming in at once from turning into one long frame. Anything that does
 * not fit in this frame's budget waits for the next one, during which the painting is not drawn —
 * a couple of frames, not something a player can see.
 */
public final class PaintCache {
	/** Frames a texture must go untouched before it may be evicted, so nothing dies mid-frame. */
	private static final int EVICTION_GRACE_FRAMES = 600;

	private static final Map<String, BakedPaint> BY_HASH = new HashMap<>();

	/** Free texture slots. Bounded, because a render type per identifier is memoised forever. */
	private static final Deque<Integer> FREE_SLOTS = new ArrayDeque<>();

	private static int slotsHandedOut;

	private static long frame;
	private static long frameSpentNs;
	private static long generation;

	private static int decoded;
	private static int failed;
	private static int reused;
	private static boolean warnedUnsupported;
	private static volatile boolean clearRequested;

	private PaintCache() {
	}

	/** Called once per rendered frame, before anything is drawn, to reset the bake budget. */
	public static void onFrameStart() {
		frame++;
		frameSpentNs = 0;

		if (clearRequested) {
			clearRequested = false;
			releaseAll();
		}
	}

	/**
	 * Bumped whenever every decision made so far has to be reconsidered.
	 *
	 * <p>Entities cache what the mod decided about them, including the decision to leave them alone,
	 * and there is no list of those entities to walk. Comparing against this counter retires all of
	 * them at once instead.
	 */
	public static long generation() {
		return generation;
	}

	/** Retires every binding without dropping the textures; for a change of settings. */
	public static void invalidateBindings() {
		generation++;
	}

	/** What came of a {@link #resolve} call. */
	public enum Status {
		/** {@link Result#paint()} is ready to draw. */
		READY,
		/** Out of budget this frame; try again next frame. */
		PENDING,
		/** Not something we can reproduce exactly. Render it the vanilla way, and stop asking. */
		UNSUPPORTED
	}

	public record Result(Status status, BakedPaint paint) {
		static final Result PENDING = new Result(Status.PENDING, null);
		static final Result UNSUPPORTED = new Result(Status.UNSUPPORTED, null);
	}

	/**
	 * The texture for {@code text}, decoding and uploading it if this is the first time we see it.
	 */
	public static Result resolve(Component text) {
		PaintConfig config = PaintConfig.get();
		long start = System.nanoTime();

		if (frameSpentNs >= config.bakeBudgetMicros * 1000L) {
			return Result.PENDING;
		}

		PaintCanvas canvas = PaintDecoder.decode(text, config.maxCanvasPixels);

		if (canvas == null) {
			failed++;
			frameSpentNs += System.nanoTime() - start;

			return Result.UNSUPPORTED;
		}

		String hash = canvas.hash();
		BakedPaint cached = BY_HASH.get(hash);

		if (cached != null && cached.isValid()) {
			reused++;
			cached.lastUsedFrame = frame;
			frameSpentNs += System.nanoTime() - start;

			return new Result(Status.READY, cached);
		}

		int slot = acquireSlot(config.maxCachedCanvases);

		if (slot < 0) {
			// Every slot is held by a painting drawn too recently to recycle. Waiting a frame is a
			// better answer than pulling a texture out from under something still on screen.
			frameSpentNs += System.nanoTime() - start;

			return Result.PENDING;
		}

		BakedPaint baked;

		try {
			baked = BakedPaint.upload(hash, slot, canvas);
		} catch (Exception e) {
			failed++;
			FREE_SLOTS.addLast(slot);
			McPaintFix.LOGGER.warn("Could not upload a {}x{} canvas", canvas.width(), canvas.height(), e);
			frameSpentNs += System.nanoTime() - start;

			return Result.UNSUPPORTED;
		}

		baked.lastUsedFrame = frame;
		BY_HASH.put(hash, baked);
		decoded++;

		Notifications.debug("Baked canvas {} ({}x{})", hash, canvas.width(), canvas.height());

		if (config.exportPng) {
			PaintStore.store(hash, canvas);
		}

		frameSpentNs += System.nanoTime() - start;

		return new Result(Status.READY, baked);
	}

	public static void touch(BakedPaint paint) {
		paint.lastUsedFrame = frame;
	}

	/**
	 * Warns once if a canvas font turns up wrapped around something we cannot read.
	 *
	 * <p>The encoding is MC Paint's to change. If it ever does, this line in the log is the
	 * difference between "the mod quietly stopped helping" and a bug report.
	 */
	public static void noteUnsupportedCanvas(Component text) {
		if (warnedUnsupported) {
			return;
		}

		warnedUnsupported = true;
		String sample = text.getString();
		Notifications.problem(
				"notification.mcpaintfix.undecodable",
				"A display using an MC Paint canvas font did not decode; falling back to vanilla for it. "
						+ "If every canvas does this, the encoding has changed. First 80 characters: {}",
				sample.length() > 80 ? sample.substring(0, 80) : sample
		);
	}

	private static int acquireSlot(int limit) {
		if (!FREE_SLOTS.isEmpty()) {
			return FREE_SLOTS.removeFirst();
		}

		if (slotsHandedOut < limit) {
			return slotsHandedOut++;
		}

		evictDownTo(limit - 1);

		return FREE_SLOTS.isEmpty() ? -1 : FREE_SLOTS.removeFirst();
	}

	private static void evictDownTo(int target) {
		if (BY_HASH.size() <= target) {
			return;
		}

		List<BakedPaint> candidates = new ArrayList<>();

		for (BakedPaint paint : BY_HASH.values()) {
			if (frame - paint.lastUsedFrame > EVICTION_GRACE_FRAMES) {
				candidates.add(paint);
			}
		}

		candidates.sort((a, b) -> Long.compare(a.lastUsedFrame, b.lastUsedFrame));
		int excess = BY_HASH.size() - target;

		for (int i = 0; i < Math.min(excess, candidates.size()); i++) {
			BakedPaint paint = candidates.get(i);
			BY_HASH.remove(paint.hash());
			paint.release();
			FREE_SLOTS.addLast(paint.slot());
		}
	}

	/**
	 * Drops every texture; used when leaving a world and by the button in the settings.
	 *
	 * <p>Freeing a texture talks to the GPU, so a call from anywhere but the render thread only
	 * retires the bindings and leaves the release to the next frame. Nothing new is drawn in the
	 * meantime either way.
	 */
	public static void clear() {
		invalidateBindings();

		if (RenderSystem.isOnRenderThread()) {
			releaseAll();
		} else {
			clearRequested = true;
		}
	}

	private static void releaseAll() {
		for (BakedPaint paint : BY_HASH.values()) {
			paint.release();
		}

		BY_HASH.clear();
		FREE_SLOTS.clear();
		slotsHandedOut = 0;
		decoded = 0;
		failed = 0;
		reused = 0;
	}

	public static int textureCount() {
		return BY_HASH.size();
	}

	public static int decodedCount() {
		return decoded;
	}

	public static int failedCount() {
		return failed;
	}

	public static int reusedCount() {
		return reused;
	}
}
