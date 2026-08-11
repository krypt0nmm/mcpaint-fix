package dev.krypton.mcpaintfix.mixin;

import dev.krypton.mcpaintfix.client.ClientHooks;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The mod's whole client lifecycle: one hook per frame, plus reload and shutdown. */
@Mixin(Minecraft.class)
public class MinecraftMixin {
	@Inject(method = "runTick(Z)V", at = @At("HEAD"))
	private void mcpaintfix$onFrameStart(boolean renderLevel, CallbackInfo ci) {
		ClientHooks.onFrameStart();
	}

	@Inject(method = "reloadResourcePacks()Ljava/util/concurrent/CompletableFuture;", at = @At("HEAD"))
	private void mcpaintfix$onResourceReload(CallbackInfoReturnable<CompletableFuture<Void>> cir) {
		ClientHooks.onResourceReload();
	}

	@Inject(method = "close", at = @At("HEAD"))
	private void mcpaintfix$onShutdown(CallbackInfo ci) {
		ClientHooks.onShutdown();
	}
}
