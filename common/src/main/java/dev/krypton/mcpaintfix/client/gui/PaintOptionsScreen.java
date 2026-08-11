package dev.krypton.mcpaintfix.client.gui;

import dev.krypton.mcpaintfix.client.PaintCache;
import dev.krypton.mcpaintfix.client.PaintConfig;
import dev.krypton.mcpaintfix.client.PaintGeometry;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** The mod's settings, built out of vanilla's own option widgets so it looks like the rest of them. */
public class PaintOptionsScreen extends OptionsSubScreen {
	private static final int BUTTON_WIDTH = 150;
	private static final int BUTTON_SPACING = 8;
	private static final int STATUS_COLOUR = 0xFFA0A0A0;

	public PaintOptionsScreen(Screen lastScreen) {
		super(lastScreen, Minecraft.getInstance().options, Component.translatable("options.mcpaintfix.title"));
	}

	@Override
	protected void addOptions() {
		PaintConfig config = PaintConfig.get();

		this.list.addBig(option("enabled", config.enabled, config::setEnabled));
		this.list.addSmall(
				option("debug", config.debug, value -> config.debug = value),
				option("export_png", config.exportPng, value -> config.exportPng = value)
		);
		this.list.addSmall(option("skip_text_layout", config.skipTextLayout, value -> config.skipTextLayout = value));
	}

	@Override
	protected void addFooter() {
		// The footer frame stacks whatever it is given, so the two buttons go in a row of their own.
		LinearLayout row = this.layout.addToFooter(LinearLayout.horizontal().spacing(BUTTON_SPACING));
		row.addChild(Button.builder(Component.translatable("options.mcpaintfix.clear_cache"), button -> {
			PaintCache.clear();
			PaintGeometry.reset();
		}).width(BUTTON_WIDTH).build());
		row.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).width(BUTTON_WIDTH).build());
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		int y = this.height - this.layout.getFooterHeight() - this.font.lineHeight - HeaderAndFooterLayout.MAGIC_PADDING;
		graphics.drawCenteredString(this.font, status(), this.width / 2, y, STATUS_COLOUR);
	}

	@Override
	public void removed() {
		super.removed();
		PaintConfig.get().save();
	}

	private static OptionInstance<Boolean> option(String name, boolean value, Consumer<Boolean> onChange) {
		String key = "options.mcpaintfix." + name;

		return OptionInstance.createBoolean(
				key, OptionInstance.cachedConstantTooltip(Component.translatable(key + ".tooltip")), value, onChange
		);
	}

	private static Component status() {
		PaintConfig config = PaintConfig.get();

		if (!config.enabled) {
			return Component.translatable("options.mcpaintfix.status.off").withStyle(ChatFormatting.GRAY);
		}

		if (!PaintGeometry.isUsable()) {
			return Component.translatable("options.mcpaintfix.status.mismatch").withStyle(ChatFormatting.RED);
		}

		return Component.translatable(
				"options.mcpaintfix.status.on",
				PaintCache.textureCount(),
				PaintCache.decodedCount(),
				PaintCache.reusedCount(),
				PaintCache.failedCount()
		).withStyle(ChatFormatting.GRAY);
	}
}
