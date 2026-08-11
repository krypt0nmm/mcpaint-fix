package dev.krypton.mcpaintfix.client;

/** Mixed into {@code TextDisplayEntityRenderState} so submission can see what extraction decided. */
public interface PaintStateHolder {
	PaintBinding mcpaintfix$binding();

	void mcpaintfix$setBinding(PaintBinding binding);
}
