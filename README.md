# MapPlus

A Fabric mod for Minecraft 26.3 that extends vanilla cartography for planning builds and projects. It adds no new blocks or items, and removing it leaves your world as it was.

## Features

### Drawing at the cartography table
With the mod on both sides, the cartography table window gets a drawing desk on its right: the map in the top slot, at full size, to draw on directly.

- **What draws is what's in the second slot:** a dye draws in its color (an ink sac draws black), a glow ink sac makes ink glow, a water bottle erases. One item covers 256 pixels; the bar on the item shows what's left of it.
- **Empty map:** put an empty map in the top slot to draw on a blank canvas. The drawing is the result; taking it uses up one empty map. Closing the table gives you the drawing in place of that empty map, so nothing you paid ink for is lost.
- **Tools:** pen, line, box and fill, under the map. Scroll over the map, or use the buttons on the right, to change the brush size (1 to 3).
- **Seal:** honeycomb in the second slot seals a map's ink against drawing and washing; an axe scrapes the seal off (and takes damage).
- Shift-click dyes, ink sacs, water bottles, honeycomb, axes and books into the second slot.
- Locking a map (glass pane) keeps its ink; zooming it out (paper) shrinks the ink onto the bigger map.
- Use a map on a water cauldron to wash all its ink off.
- Every copy of a map shows the same ink, including maps in item frames, and players without the mod see it too.

### Atlas
Put a filled map in the cartography table's top slot and a book and quill in the second slot: the result is an **Atlas** with the map stitched in as its next page. Stitch more maps the same way, with the atlas in the second slot. The maps are used up and can't be taken back out.

The atlas opens in the vanilla book and quill screen. Each stitched map shows on the page facing its notes, on the left or the right depending on where it was stitched: a map stitched after a left page faces it from the right; otherwise it starts a new spread on the left, with an empty page for notes on the right.

- **Notes:** write as in any book. Sign it to make it read-only; signed atlases can be copied like written books and read on lecterns.
- **Explore:** held, the atlas keeps filling in the map of the spread it was last closed on, like a held map. Copies show the maps but don't fill them in.

Removing the mod leaves the atlas as an ordinary book: notes intact, map pages showing the map's name.

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
