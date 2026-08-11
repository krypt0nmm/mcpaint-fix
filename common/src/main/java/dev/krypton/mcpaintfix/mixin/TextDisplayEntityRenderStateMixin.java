package dev.krypton.mcpaintfix.mixin;

import dev.krypton.mcpaintfix.client.PaintBinding;
import dev.krypton.mcpaintfix.client.PaintStateHolder;
import net.minecraft.client.renderer.entity.state.TextDisplayEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Carries the binding from render state extraction, which sees the entity, to submission, which
 * only sees the render state.
 */
@Mixin(TextDisplayEntityRenderState.class)
public abstract class TextDisplayEntityRenderStateMixin implements PaintStateHolder {
	@Unique
	private PaintBinding mcpaintfix$binding = PaintBinding.NONE;

	@Override
	public PaintBinding mcpaintfix$binding() {
		return this.mcpaintfix$binding;
	}

	@Override
	public void mcpaintfix$setBinding(PaintBinding binding) {
		this.mcpaintfix$binding = binding;
	}
}
