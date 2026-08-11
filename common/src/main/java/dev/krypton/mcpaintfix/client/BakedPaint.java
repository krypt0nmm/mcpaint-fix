package dev.krypton.mcpaintfix.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.krypton.mcpaintfix.McPaintFix;
import dev.krypton.mcpaintfix.paint.PaintCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * A decoded canvas that lives on the GPU: one texture, drawn as one quad.
 *
 * <p>{@link DynamicTexture} already samples with {@code NEAREST} and a single mip level, which is
 * what the font atlas does too, so a stretched texture reproduces the blocky pixels of the original
 * glyphs rather than smearing them.
 *
 * <p>Textures are registered under a recycled slot identifier rather than one named after the
 * picture. {@code RenderTypes.text*} memoises a render type per identifier and never forgets one, so
 * naming them after content would grow a map for the rest of the session; a fixed pool bounds it,
 * and lets each baked painting hold its two render types instead of looking them up per frame.
 */
public final class BakedPaint {
	private final String hash;
	private final int slot;
	private final Identifier textureId;
	private final PaintCanvas canvas;
	private final RenderType polygonOffset;
	private final RenderType seeThrough;

	private boolean released;
	long lastUsedFrame;

	private BakedPaint(String hash, int slot, Identifier textureId, PaintCanvas canvas) {
		this.hash = hash;
		this.slot = slot;
		this.textureId = textureId;
		this.canvas = canvas;
		this.polygonOffset = RenderTypes.textPolygonOffset(textureId);
		this.seeThrough = RenderTypes.textSeeThrough(textureId);
	}

	/** Uploads {@code canvas}. Must run on the render thread, since it talks to the GPU device. */
	static BakedPaint upload(String hash, int slot, PaintCanvas canvas) {
		NativeImage image = new NativeImage(canvas.width(), canvas.height(), false);

		for (int y = 0; y < canvas.height(); y++) {
			for (int x = 0; x < canvas.width(); x++) {
				image.setPixel(x, y, canvas.pixels()[y * canvas.width() + x]);
			}
		}

		Identifier id = Identifier.fromNamespaceAndPath(McPaintFix.MOD_ID, "paint/slot_" + slot);
		// register() closes whatever was in this slot before, so recycling a slot frees its texture.
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "mcpaintfix canvas " + hash, image));

		return new BakedPaint(hash, slot, id, canvas);
	}

	public String hash() {
		return this.hash;
	}

	public PaintCanvas canvas() {
		return this.canvas;
	}

	public RenderType renderType(boolean throughWalls) {
		return throughWalls ? this.seeThrough : this.polygonOffset;
	}

	/**
	 * False once the texture has been evicted.
	 *
	 * <p>An entity can outlive its texture, and drawing a released one would show whatever took over
	 * its slot, so holders re-resolve when they see this go false.
	 */
	public boolean isValid() {
		return !this.released;
	}

	int slot() {
		return this.slot;
	}

	void release() {
		if (!this.released) {
			this.released = true;
			// release() unregisters and closes; closing the texture ourselves as well would be a
			// double free.
			Minecraft.getInstance().getTextureManager().release(this.textureId);
		}
	}
}
