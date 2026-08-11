package dev.krypton.mcpaintfix.paint;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * One decoded MC Paint canvas: straight ARGB pixels, row major, origin top left.
 *
 * <p>Colours are the unshaded palette values, because {@link PaintFont} shading is applied at draw
 * time. A pixel of 0 is fully transparent, which is what MC Paint's spacing glyphs mean.
 */
public record PaintCanvas(int width, int height, int[] pixels) {
	/** Font advance units one canvas pixel occupies, both horizontally and vertically. */
	public static final int PIXEL_SIZE = 10;

	public int pixelCount() {
		return this.width * this.height;
	}

	/** Width of the equivalent text, in the font space {@code TextDisplayRenderer} lays out in. */
	public float textWidth() {
		return this.width * PIXEL_SIZE;
	}

	/**
	 * Bottom edge of the equivalent text in font space.
	 *
	 * <p>Vanilla stacks lines every {@code 9 + 1} units and subtracts one for the trailing gap, and
	 * the glyphs themselves run from {@code -1} to {@code lines * 10 - 1}. Matching that exactly is
	 * what keeps a replaced painting in the same place as the one it replaced.
	 */
	public float textBottom() {
		return this.height * PIXEL_SIZE - 1;
	}

	/** Top edge of the equivalent text in font space; the glyphs start one unit above the baseline. */
	public float textTop() {
		return -1.0F;
	}

	/**
	 * Content hash, used to key both the texture cache and the files on disk.
	 *
	 * <p>Two entities showing the same picture share one texture, and the same picture keeps the
	 * same file name across sessions, which is what makes the folder on disk browsable.
	 */
	public String hash() {
		MessageDigest digest;

		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is required by the Java platform", e);
		}

		digest.update(("mcpaint:" + this.width + "x" + this.height + ":").getBytes(StandardCharsets.US_ASCII));
		byte[] bytes = new byte[this.pixels.length * 4];

		for (int i = 0; i < this.pixels.length; i++) {
			int argb = this.pixels[i];
			bytes[i * 4] = (byte)(argb >>> 24);
			bytes[i * 4 + 1] = (byte)(argb >>> 16);
			bytes[i * 4 + 2] = (byte)(argb >>> 8);
			bytes[i * 4 + 3] = (byte)argb;
		}

		digest.update(bytes);
		byte[] full = digest.digest();
		byte[] truncated = new byte[16];
		System.arraycopy(full, 0, truncated, 0, truncated.length);

		return HexFormat.of().formatHex(truncated);
	}
}
