package dev.krypton.mcpaintfix.client;

import dev.krypton.mcpaintfix.paint.PaintFont;

/**
 * What the mod has decided to do about one text display.
 *
 * <p>Three states, distinguished by which fields are set: no font means hands off, a font with no
 * texture means the canvas is recognised but not baked yet, and both set means draw the texture
 * instead of the text.
 *
 * <p>{@code generation} is what lets a decision be un-made. A binding is only trusted while it
 * matches {@link PaintCache#generation()}, so dropping the caches or flipping the switch retires
 * every decision in the world at once, including the ones that said to leave a display alone.
 */
public record PaintBinding(PaintFont font, BakedPaint paint, long generation) {
	/** Stand-in for a render state nothing has been extracted into yet; never matches a generation. */
	public static final PaintBinding NONE = new PaintBinding(null, null, Long.MIN_VALUE);

	/** True once we have committed to drawing this display ourselves, baked or not. */
	public boolean replacesRendering() {
		return this.font != null;
	}

	public boolean isReady() {
		return this.paint != null && this.paint.isValid();
	}

	public boolean isCurrent() {
		return this.generation == PaintCache.generation();
	}
}
