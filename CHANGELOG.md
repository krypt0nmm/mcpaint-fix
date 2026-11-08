# Changelog

## 1.0.0+mc26.3

Port to Minecraft 26.3 (Fabric and NeoForge, Java 25). Behaviour is the same as 1.0.0, except:

- The canvas quad is queued in the render phase vanilla would have used for the text (solid for
  opaque text), so it draws in the same pass relative to glass, water and other translucent geometry.
- The background goes through vanilla's own text background call.
- See-through displays are left to vanilla: 26.3 draws see-through text in a distance-sorted pass that
  a quad cannot join. MC Paint never sets the flag.

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
