package dev.krypton.mcpaintfix.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.krypton.mcpaintfix.paint.PaintCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.TextDisplayEntityRenderState;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import net.minecraft.client.renderer.feature.phase.SimpleFeatureRenderPhase;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.entity.Display;
import org.joml.Matrix4f;

/**
 * Draws a baked canvas in place of the text it was decoded from.
 *
 * <p>The point of the whole mod is here: one quad instead of one glyph per run of pixels. The
 * transform below is vanilla's, line for line, because the replacement has to land in exactly the
 * same place as the text — a painting that shifts by a pixel is worse than a slow one.
 *
 * <p>It also uses the same render type the font would have (the text pipeline, with its depth bias),
 * so the canvas keeps winning the depth fight against the item display frame behind it, and shader
 * packs treat it as the text it replaces.
 */
public final class PaintRenderer {
	/** The scale vanilla shrinks font space by before drawing display text into the world. */
	private static final float TEXT_SCALE = 0.025F;

	private PaintRenderer() {
	}

	/**
	 * Whether a display with these flags can be drawn by {@link #submit}.
	 *
	 * <p>See-through text goes into a phase of its own, sorted by distance, which only text and other
	 * distance-sorted submits can join; a quad of custom geometry would be drawn in the wrong pass.
	 * MC Paint never sets the flag, so such a display is simply left to vanilla.
	 */
	public static boolean canDraw(byte flags) {
		return (flags & Display.TextDisplay.FLAG_SEE_THROUGH) == 0;
	}

	public static void submit(
			PaintBinding binding, TextDisplayEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, int light, float partialTick
	) {
		BakedPaint paint = binding.paint();
		Display.TextDisplay.TextRenderState text = state.textRenderState;
		byte flags = text.flags();
		boolean defaultBackground = (flags & Display.TextDisplay.FLAG_USE_DEFAULT_BACKGROUND) != 0;

		int background;

		if (defaultBackground) {
			float opacity = Minecraft.getInstance().gameRenderer.gameRenderState().optionsRenderState.getBackgroundOpacity(0.25F);
			background = (int)(opacity * 255.0F) << 24;
		} else {
			background = text.backgroundColor().get(partialTick);
		}

		Matrix4f matrix = poseStack.last().pose();
		matrix.rotate((float)Math.PI, 0.0F, 1.0F, 0.0F);
		matrix.scale(-TEXT_SCALE, -TEXT_SCALE, -TEXT_SCALE);

		PaintCanvas canvas = paint.canvas();
		float width = canvas.textWidth();
		float top = canvas.textTop();
		float bottom = canvas.textBottom();
		matrix.translate(1.0F - width / 2.0F, -bottom, 0.0F);

		if (background != 0) {
			// Exactly vanilla's call: the background is a glyph of the font's own, set a hair behind the
			// text, so the canvas covers it by depth whatever order the two end up drawn in.
			collector.submitTextBackground(poseStack, -1.0F, -1.0F, width, bottom, background, Font.DisplayMode.POLYGON_OFFSET, light);
		}

		// The glyphs carried their shading in the atlas and their colour in the vertex; our texture
		// carries the colour, so shading and opacity go in the vertex instead. The shade values are
		// the exact greys of the font and the shader still does one float multiply, so a run of
		// pixels comes out bit-identical; an isolated pixel, whose glyph the pack pre-rounded, can
		// differ by one 255th.
		int shade = binding.font().shade();
		int alpha = text.textOpacity().get(partialTick) & 0xFF;
		int tint = alpha << 24 | shade << 16 | shade << 8 | shade;

		SubmitNodeCollector.CustomGeometryRenderer quad = (pose, vertices) -> {
			// Wound like a glyph quad — top left, bottom left, bottom right, top right — so back-face
			// culling treats the replacement the same way it treated the text.
			vertices.addVertex(pose, 0.0F, top, 0.0F).setColor(tint).setUv(0.0F, 0.0F).setLight(light);
			vertices.addVertex(pose, 0.0F, bottom, 0.0F).setColor(tint).setUv(0.0F, 1.0F).setLight(light);
			vertices.addVertex(pose, width, bottom, 0.0F).setColor(tint).setUv(1.0F, 1.0F).setLight(light);
			vertices.addVertex(pose, width, top, 0.0F).setColor(tint).setUv(1.0F, 0.0F).setLight(light);
		};

		submitLikeText(collector.order(0), poseStack, paint.renderType(), alpha, quad);
	}

	/**
	 * Queues the quad in the phase vanilla would have queued the text in.
	 *
	 * <p>Custom geometry with a blending render type always lands in the translucent pass, after
	 * translucent terrain. Opaque text does not: {@code SubmitNodeCollection.submitTextPart} files it
	 * with the solid geometry, so a painting behind stained glass or water shows through it. Doing
	 * the same keeps the canvas in the pass the text was in. A collector that is not vanilla's — some
	 * other mod's wrapper — gets the plain call, which still draws the right picture, only in the
	 * translucent pass.
	 *
	 * <p>Skipping {@code submitCustomGeometry} loses nothing with Iris 1.11.7: it records the entity
	 * a submit belongs to in the constructor of {@code CustomFeatureRenderer.Submit} itself, and the
	 * render type it swaps in that method applies to block entities only.
	 */
	private static void submitLikeText(
			OrderedSubmitNodeCollector collector, PoseStack poseStack, RenderType renderType,
			int alpha, SubmitNodeCollector.CustomGeometryRenderer quad
	) {
		if (collector instanceof SubmitNodeCollection collection) {
			SimpleFeatureRenderPhase phase = alpha == 0xFF && RenderSystem.isRenderingLevel ? collection.solid : collection.texts;
			phase.submit(new CustomFeatureRenderer.Submit(poseStack.last().copy(), renderType, quad));
		} else {
			collector.submitCustomGeometry(poseStack, renderType, quad);
		}
	}
}
