package dev.krypton.mcpaintfix.mixin;

import dev.krypton.mcpaintfix.client.PaintKeys;
import java.util.Arrays;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the settings keybind to the controls list.
 *
 * <p>Appending to the array is how both loaders end up with the same list; Fabric API and NeoForge
 * each have their own registration event, and neither is available to the other.
 */
@Mixin(Options.class)
public class OptionsMixin {
	@Shadow
	@Final
	@Mutable
	public KeyMapping[] keyMappings;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void mcpaintfix$registerKeys(CallbackInfo ci) {
		KeyMapping[] extended = Arrays.copyOf(this.keyMappings, this.keyMappings.length + 1);
		extended[extended.length - 1] = PaintKeys.OPEN_SETTINGS;
		this.keyMappings = extended;
	}
}
