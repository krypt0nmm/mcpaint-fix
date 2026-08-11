# MC Paint Fix

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-brightgreen)](https://modrinth.com/mod/mcpaint-fix)
[![Loaders](https://img.shields.io/badge/loaders-Fabric%20%7C%20NeoForge-blue)](https://modrinth.com/mod/mcpaint-fix)
[![Client only](https://img.shields.io/badge/side-client%20only-orange)](https://modrinth.com/mod/mcpaint-fix)
[![License](https://img.shields.io/badge/license-MIT-lightgrey)](LICENSE)

**Client-side fix for the frame rate collapse around [MC Paint](https://modrinth.com/datapack/mc-paint)
canvases.** Install it and walk into the gallery; there is nothing to configure and nothing to run.

## The problem

MC Paint draws a custom painting as a `text_display` whose text is one glyph per run of
same-coloured pixels, in a font its resource pack supplies. Canvases go up to 64×64, so a detailed
one is a few thousand glyphs — rebuilt into vertices every single frame.

Nothing caches that. The entity caches only the line *split*; the glyph geometry is re-emitted per
frame, and the render type text displays use sorts its quads on every upload. A street of canvases
is enough to halve the frame rate on its own.

Sodium does not help here. It has no mixin on the display renderer at all, and its glyph mixin means
these canvases already run its fastest per-glyph path — which is the evidence that the problem is the
*number* of quads, not the cost of each one.

## What it does

Decodes that text back into the picture it encodes, uploads it as one texture, and draws one quad in
its place. A canvas that cost four thousand quads costs one.

**Decoding, not screenshotting.** The encoding is recoverable: `a`–`p` are transparent runs of 1 to
16 pixels, `A`–`P` are opaque runs in the segment's colour, `.` is a −1 advance that makes one pixel
exactly ten units wide, and a lone pixel is a codepoint indexing a 15-bit palette. The picture is
reconstructed exactly, off the GPU, before the canvas is ever on screen.

**Same place, same size, same pixels.** The quad occupies the font space the text did — `width × 10`
units across, `−1` to `height × 10 − 1` down — and goes through vanilla's own transform. It uses the
text render type, so it keeps the depth bias that wins the z-fight against the item frame behind it.

**Shading stays a property of the wall.** MC Paint ships four copies of its font, pre-multiplied by
Minecraft's block face shading and picked by the direction a canvas faces. That belongs to the
entity, not to the picture, so it is applied as a vertex tint instead of being baked in — one texture
then serves every copy of a canvas regardless of orientation.

**One texture per picture, not per entity.** Textures are keyed by a hash of the pixels, so a hundred
copies of the same canvas cost one texture.

**It refuses rather than guesses.** Anything the decoder does not fully recognise goes back to
vanilla rendering — a wrong picture on a wall would be worse than a slow one. So do displays that
would not survive the substitution: a text shadow, or a line width narrow enough that vanilla would
wrap the rows. The ten-units-per-pixel geometry rests on advances declared in a resource pack MC
Paint can change, so the first canvas of a session is also laid out by vanilla and the two answers
compared; a disagreement switches the mod off for that session.

**It will not make things worse.** A canvas being edited in MC Paint's Studio is rewritten on every
brush stroke. Baking each of those would upload throwaway textures many times a second and evict the
real canvases to make room, so a display whose text keeps changing is left to vanilla until it holds
still, then picked back up.

## Compatibility

| | |
|---|---|
| Minecraft | 1.21.11 |
| Loaders | Fabric, NeoForge |
| Side | client only — the server does not need it, and does not know it is there |
| Fabric API | not required |
| Sodium, Iris, ImmediatelyFast | yes |

The only mixins touch the text display renderer, `Minecraft` and `Options` — a corner of the engine
no performance mod competes for. Sodium rewrites terrain and chunk building, not entity renderers.

### Other Minecraft versions

Every method this mod hooks is unchanged back to 1.21.9, but two symbols it names were renamed in
1.21.11: `ResourceLocation` became `Identifier`, and the static render type factories moved from
`RenderType` to `rendertype.RenderTypes`. One jar therefore cannot cover both, and 1.21.9 – 1.21.10
belong on their own branch. Anything older than 1.21.9 predates the render pipeline this is built on
and needs a different renderer entirely.

## Installation

Drop the jar for your loader in `mods/`. It is on by default and starts working the moment a canvas
comes into view.

## Settings

Reachable from **Mod Menu** on Fabric, from the **mod list** on NeoForge, or by binding a key under
*Options → Controls → Miscellaneous → MC Paint Fix settings*. Stored in `config/mcpaint-fix.json`.

| | Default | |
|---|---|---|
| Replace MC Paint canvases | on | the fix itself |
| Debug messages | **off** | chat and log output; off, the mod only logs genuine faults |
| Export canvases as PNG | on | see below |
| Skip text layout | on | also skip vanilla's line splitting for replaced canvases |

The screen also shows how many textures are resident, and how many canvases were decoded, shared and
refused.

Some settings in the file are not in the menu because they are rarely worth touching:
`maxCachedCanvases`, `maxCanvasPixels`, `bakeBudgetMicros`, `volatileFlipThreshold`,
`volatileWindowMillis`.

## The `.paints` folder

With PNG export on, every decoded canvas is written to `.minecraft/.paints/<server>/<hash>.png`, with
an `index.json` recording its size, when it was first and last seen, and where it hangs.

This is an archive, not a speed-up: identifying a canvas means decoding its component anyway, and
decoding is faster than reading the PNG back, so nothing here is ever on the hot path. What it buys
is a browsable copy of a server's art and a record of what the mod actually saw, which is what makes
a mismatch debuggable. Turn it off if you would rather it wrote nothing.

## Building

```sh
./gradlew build            # both jars land in fabric/build/libs and neoforge/build/libs
./gradlew :fabric:test     # decoder round-trip tests
./gradlew :fabric:runClient
./gradlew :neoforge:runClient
```

The mod itself lives in `common/`, which is a plain source folder rather than a Gradle project: both
loaders compile it directly against their own Minecraft. Official Mojang mappings are used on both
sides, so the same source resolves to the same names without a translation layer.

Java 21 is required. Gradle picks it up through `gradle/gradle-daemon-jvm.properties` and does not
care what the system default is.

## License

MIT. See [LICENSE](LICENSE).

MC Paint is a separate project by its own authors; this mod only reads what it draws.
