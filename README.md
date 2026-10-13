# MapPlus

A Fabric mod for Minecraft 26.3 that extends vanilla cartography for planning builds and projects. It adds no new blocks or items, and removing it leaves your world as it was.

## Features

### Drawing at the cartography table
With the mod on both sides, the cartography table window gets a drawing desk on its right: the map in the top slot, at full size, to draw on directly.

- **What draws is what's in the second slot:** a dye draws in its color (an ink sac draws black), a glow ink sac makes ink glow, a water bottle erases. One item covers 256 pixels; the bar on the item shows what's left of it.
- **Blank canvas:** put an empty map or a sheet of paper in the top slot to draw on a blank canvas. The drawing is the result; taking it uses up one empty map or paper. Closing the table gives you the drawing in its place, so nothing you paid ink for is lost.
- **Water the ink down:** scroll over the map with a drawing tool to draw at full, ¾, ½ or ¼ strength: watered ink tints the map instead of covering it, and costs that share of a pixel's ink. Fill a fenced plot at ¼ to tint it as a zone. Watered ink never weakens ink of its own color; a water bottle at a lower strength fades ink a step instead of erasing it. Players without the mod see tints as a fine pattern over the map.
- **Tools are items:** pick one up and draw on the map with it. The rack under the map picks your tools up from your inventory in one click.

  | On the cursor | Tool |
  |---|---|
  | Feather | Pen, 1 pixel |
  | Brush | Pen, 3 pixels |
  | String | Line: drag from one end to the other |
  | Item frame | Box: drag from corner to corner |
  | Bucket | Fill the area around the click, up to the ink lines around it (dashed borders hold it too) |
- **Seal:** honeycomb in the second slot seals a map's ink against drawing and washing; an axe scrapes the seal off (and takes damage). On servers, only whoever sealed the map, the map's maker or an operator can scrape it off (for maps made since MapPlus was installed).
- Shift-click dyes, ink sacs, water bottles, honeycomb, axes and books into the second slot.
- Locking a map (glass pane) keeps its ink; zooming it out (paper) shrinks the ink onto the bigger map.
- Use a map on a water cauldron to water all its ink, labels and icons down a step; four dips wash full ink off.
- Every copy of a map shows the same ink, including maps in item frames, and players without the mod see it too.

### Planning: labels, pins, icons and borders
Place a **loom next to the cartography table** to mark maps up for planning: your turf, your city's districts, its roads.

- **Sign:** click the map and type a label, in the color of the dye in the second slot. Scroll or press ↑ ↓ while typing to size it (small, medium or large), Enter to place it. Letters cost ink.
- **Banner:** pin it to the map. It shows as the banner itself, with its patterns, named with the banner's anvil name if it has one.
- **Pottery sherd:** stamp its picture as an icon, in the dye's color: the skull sherd for danger, the miner's pickaxe for a mine, the sheaf for farms, plenty's chest for storage. Named with the sherd's anvil name if it has one. Icons cost a little ink.
- **Iron chain:** draw a dashed border.
- Drag a mark to move it, scroll over it to size it, or drag it off the map to remove it. Click a label with a sign to rewrite it.
- Marks show on every copy of the map, held, framed and in atlases. Players without the mod see them as named banner markers.

For example, to mark your turf: draw its border with an iron chain, tint it by filling it with the bucket at ¼ strength, write its name with a sign, and pin your banner.

### Atlas
Put a filled map in the cartography table's top slot and a book and quill in the second slot: the result is an **Atlas** with the map stitched in as its next page. Stitch more maps the same way, with the atlas in the second slot. The maps are used up and can't be taken back out.

The atlas opens in the vanilla book and quill screen. Each stitched map shows on the page facing its notes, on the left or the right depending on where it was stitched: a map stitched after a left page faces it from the right; otherwise it starts a new spread on the left, with an empty page for notes on the right.

- **Notes:** write as in any book. Sign it to make it read-only; signed atlases can be copied like written books and read on lecterns.
- **Explore:** held, the atlas keeps filling in the map of the spread it was last closed on, like a held map. Copies show the maps but don't fill them in.

Removing the mod leaves the atlas as an ordinary book: notes intact, map pages showing the map's name.

### Spyglass survey
Look through a spyglass with a filled map in your other hand: the map fills in with what you see, out to twice the server's view distance. Works for players without the mod too. With the mod, the map shows in the top right corner while you look, and scrolling zooms the spyglass in and out.

### Nations
Name a banner in an anvil and put it up: it's a border post of your nation. A nation is a banner design (its color and patterns); its name is the banner's name. The land around each post, out to 48 blocks, is the nation's on every map: borders in the nation's color with a light tint inside. Land floods out from the posts, so neighbors meet halfway, and rivers and coasts make natural borders. Crossing a border shows "Entering …" in the action bar. Take the banner down and the land goes with it. Borders are maps only: nothing is protected.

### Caves and the echo
- **Cave maps:** use an empty map deep underground and it maps the level you're standing on, as a floor plan: open floor in its own colors, darker the deeper it drops, and rock as the darkest shade of itself. It fills in around you while you're on that level, and is named for it.
- **The echo:** use an amethyst shard with a map in your other hand: it rings, and the land around you fills in at once, even through rock: the caves on a cave map, the surface on any other. An echo shard rings twice as far.

### Foresight
A treasure helmet enchantment, found in loot and sold by librarians. Wearing it, the map in your hand (or the open map of an atlas) is projected onto the land around you as a ghostly blueprint: its ink on the ground, its labels written across it, and a pillar of light at each pin and icon. Only you see it; nothing is placed in the world.

## Installation
Requires Fabric Loader 0.19.5+ and Fabric API. Install on the server, the client, or both:

- **Server and client:** everything works.
- **Server only:** vanilla players can join and see ink, pins, labels (as named markers) and surveyed maps; drawing needs the mod on the client.

## Configuration
`config/mapplus.json`:

| Setting | Default | |
|---|---|---|
| `inkPerItem` | 256 | Pixels drawn per dye or ink sac |
| `waterPerBottle` | 256 | Pixels erased per water bottle |
| `creativeInkIsFree` | true | Drawing costs nothing in creative |
| `markingNeedsLoom` | true | Labels, pins, icons and borders need a loom next to the table |
| `maxLabelsPerMap` | 32 | Labels one map can carry |
| `maxPinsPerMap` | 32 | Pins and icons one map can carry |
| `sealsAreOwned` | true | Only the sealer, the map's maker or an operator can scrape a seal off |
| `logMarks` | true | Log each label, pin and icon placed or removed, and who did it |
| `nations` | true | Named banners put up in the world are nations' border posts |
| `postReach` | 48 | How far a border post holds land, in blocks |
| `nationsOnMaps` | true | Maps show nations' borders and land |
| `nationMessages` | true | Say "Entering …" in the action bar when crossing a border |
| `caveMaps` | true | Empty maps used deep underground map that level |
| `caveMapReach` | 16 | How far around you a held cave map fills in, in blocks |
| `echoReach` | 24 | How far an amethyst shard's ring fills in a map, in blocks (an echo shard: twice) |
| `echoCooldown` | 40 | Ticks before a shard can ring again |
| `spyglassSurvey` | true | Spyglass fills held maps |
| `surveyPixelsPerTick` | 48 | Map pixels surveyed per tick per player |
| `surveyRangeMultiplier` | 2.0 | Survey range, as a multiple of the view distance |
| `spyglassGeneratesChunks` | true | Surveying may generate unvisited chunks |
| `surveyChunksPerTick` | 2 | Chunks loaded per tick per player while surveying |

## Removing the mod
Maps keep their own colors; ink, marks and nations are stored separately in `world/data/mapplus/` and simply stop showing. Cave maps keep what they've mapped, but fill in the surface from then on. The only lasting change is terrain the spyglass generated, which is ordinary vanilla terrain. Set `spyglassGeneratesChunks` to `false` to avoid that.

## Building
```
./gradlew build
```
The jar is written to `build/libs/`. Every push also builds it on GitHub Actions.

## License
MIT
