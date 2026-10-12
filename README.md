# MapPlus

A Fabric mod for Minecraft 26.3 that extends vanilla cartography for planning builds and projects. It adds no new blocks or items, and removing it leaves your world as it was.

## Features

### Ink on maps
Put a filled map in a cartography table and press **Ink**.

- Tools: pen, line, box, filled box, fill and eraser, with brush sizes 1–3. Right-click erases.
- Ink comes from dyes (ink sacs count as black): one item draws 256 pixels. Glow ink sacs make glowing ink.
- Erasing uses water bottles: one bottle erases 256 pixels and gives back a glass bottle.
- **Seal** with honeycomb to protect a map's ink; **scrape** the seal off with an axe.
- Use a map on a water cauldron to wash all its ink off.
- Every copy of a map shows the same ink, including maps in item frames, and players without the mod see it too.

### Spyglass survey
Look through a spyglass with a filled map in your other hand: the map fills in with what you see, out to twice the server's view distance. Works for players without the mod too.

## Installation
Requires Fabric Loader 0.19.5+ and Fabric API. Install on the server, the client, or both:

- **Server and client:** everything works.
- **Server only:** vanilla players can join and see ink and surveyed maps; drawing needs the mod on the client.

## Configuration
`config/mapplus.json`:

| Setting | Default | |
|---|---|---|
| `inkPerItem` | 256 | Pixels drawn per dye or ink sac |
| `waterPerBottle` | 256 | Pixels erased per water bottle |
| `creativeInkIsFree` | true | Drawing costs nothing in creative |
| `spyglassSurvey` | true | Spyglass fills held maps |
| `surveyPixelsPerTick` | 48 | Map pixels surveyed per tick per player |
| `surveyRangeMultiplier` | 2.0 | Survey range, as a multiple of the view distance |
| `spyglassGeneratesChunks` | true | Surveying may generate unvisited chunks |
| `surveyChunksPerTick` | 2 | Chunks loaded per tick per player while surveying |

## Removing the mod
Maps keep their own colors; ink is stored separately in `world/data/mapplus/` and simply stops showing. The only lasting change is terrain the spyglass generated, which is ordinary vanilla terrain. Set `spyglassGeneratesChunks` to `false` to avoid that.

## Building
```
./gradlew build
```
The jar is written to `build/libs/`. Every push also builds it on GitHub Actions.

## License
MIT
