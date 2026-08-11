# Changelog

## 1.0.0

First release.

- Replaces MC Paint canvases with a single cached texture, keyed by the pixels so identical canvases
  share one.
- Skips vanilla's line splitting for canvases being replaced.
- Refuses anything it cannot reproduce exactly, including canvases with a text shadow, a line width
  narrow enough to wrap, and any encoding the decoder does not fully recognise.
- Verifies its geometry against vanilla's layout once per session, and switches itself off for that
  session if a resource pack measures canvases differently.
- Leaves a canvas alone while its text keeps changing, so editing in MC Paint's Studio does not
  thrash the texture cache.
- Settings screen reachable from Mod Menu, from NeoForge's mod list, or from a keybind.
- Optional PNG export of every decoded canvas to `.paints/<server>/`.
- Fabric and NeoForge, Minecraft 1.21.11, client only, no Fabric API required.
