package dev.krypton.mcpaintfix.mixin;

import dev.krypton.mcpaintfix.client.PaintBinding;
import dev.krypton.mcpaintfix.client.PaintHolder;
import dev.krypton.mcpaintfix.client.PaintVolatility;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/** Gives every text display somewhere to remember what the mod decided about it. */
@Mixin(Display.TextDisplay.class)
public abstract class TextDisplayMixin implements PaintHolder {
	@Shadow
	private Display.TextDisplay.CachedInfo clientDisplayCache;

	@Unique
	private PaintBinding mcpaintfix$binding;

	@Unique
	private Component mcpaintfix$bindingSource;

	@Unique
	private PaintVolatility mcpaintfix$volatility;

	@Override
	public PaintBinding mcpaintfix$binding() {
		return this.mcpaintfix$binding;
	}

	@Override
	public Component mcpaintfix$bindingSource() {
		return this.mcpaintfix$bindingSource;
	}

	@Override
	public void mcpaintfix$setBinding(Component source, PaintBinding binding) {
		// While the mod draws a display it also suppresses vanilla's line splitting, leaving an empty
		// layout behind. Handing the display back — because the picture turned out to be one we
		// cannot reproduce, or because the optimisation was switched off — has to drop that empty
		// layout, or vanilla would faithfully render nothing.
		if (this.mcpaintfix$binding != null && this.mcpaintfix$binding.replacesRendering() && !binding.replacesRendering()) {
			this.clientDisplayCache = null;
		}

		this.mcpaintfix$bindingSource = source;
		this.mcpaintfix$binding = binding;
	}

	@Override
	public PaintVolatility mcpaintfix$volatility() {
		if (this.mcpaintfix$volatility == null) {
			this.mcpaintfix$volatility = new PaintVolatility();
		}

		return this.mcpaintfix$volatility;
	}

	@Override
	public void mcpaintfix$releaseLayout() {
		this.clientDisplayCache = null;
	}
}
