# MapPlus — Design

Status: draft. Target: Minecraft 26.3, Fabric.

## Goal

Tools for planning builds, structures and projects in Minecraft, through an
extended vanilla cartography: drawing on maps, atlases (map books) with writing,
sketches, zones and bookmarks, and an in-world projection of plans. It should
feel like vanilla.

## Core principle: removable

Anything vanilla can represent stays vanilla. Anything only MapPlus understands
lives somewhere vanilla ignores. Removing the mod removes only what it added.

| Don't add | Why | Instead |
|---|---|---|
| New blocks or items | Turn to air on removal; registry sync locks out vanilla clients | Vanilla items (map, book and quill, ink sac, dyes) with data in `minecraft:custom_data` |
| Custom item component types | Unknown components can make an item stack fail to load | `minecraft:custom_data` (kept and ignored by vanilla) |
| New fields in `map_N.dat`, new map decoration types | Dropped or fail to decode | MapPlus saved data under `world/data/mapplus/`, keyed by map id |
| Synced static registries (menu types, recipe types) | Vanilla clients can't join | Custom payload packets, sent only to clients that registered the channel |
| Changes to a map's pixel colors | Removal wouldn't undo them | Ink lives on a separate overlay layer |
| Block entities on vanilla blocks | Persisted in chunks | Menu-session state or player attachments |

What removal leaves behind:

| Thing | After removal |
|---|---|
| Maps | Unchanged vanilla maps |
| Atlases | Vanilla book and quill / written book with their text pages intact; map and sketch pages are not shown |
| Maps bound into an atlas | Their `map_N.dat` stays in the world, but no item points to it any more |
| Enchanted helmets | See [The enchantment](#the-enchantment-and-vanilla-players) |
| `world/data/mapplus/` | Orphaned and ignored |

## Run modes (one jar)

1. **Client and server:** everything works; annotations are shared.
2. **Server only (vanilla clients can join):** the server changes only what it
   sends, never what it saves. See [Vanilla client fallback](#vanilla-client-fallback).
3. **Client only:** later. Annotations stored locally in
   `.minecraft/mapplus/<server>/`, keyed by map id.

## Features

### The cartography table is the drawing desk

Players with the mod get the vanilla cartography table window with a drawing
wing attached on its right, in the same vanilla window style. Players without
the mod get the plain vanilla table.

- **Canvas:** with a map in the top slot, the wing shows it at full size and
  you draw on it directly. An **empty map** in the top slot is a blank canvas;
  the drawing appears in the result slot, and taking it uses one empty map.
- **The second slot is the material.** What you put there decides what the
  mouse does on the canvas:

  | Second slot | Effect |
  |---|---|
  | Dye | Draw in that color |
  | Ink sac | Draw in black |
  | Glow ink sac | Brush over ink to make it glow, like glow ink on a sign |
  | Water bottle | Erase; the bottle becomes a glass bottle when used up |
  | Honeycomb | Result: the same map, sealed against edits |
  | Axe | Result: the same map, seal scraped off (the axe takes damage) |
  | Book and quill / atlas | Result: the map bound into an atlas |
  | Paper / empty map / glass pane | Vanilla: zoom out, copy, lock |

- **Ink is used up from that slot:** one dye, ink sac, glow ink sac or water
  bottle covers 256 pixels. A bar in the wing shows what is left of the
  current item.
- **Tools:** pen, line, box and fill, as slot-style buttons with item icons
  under the canvas. Scroll over the canvas to change the brush size (1-3).
- **Performance:** the canvas is the map's own texture, drawn the way vanilla
  draws maps in item frames. Only the stroke being drawn is overlaid.
- Ink is still never written into the map's own colors; it is drawn into the
  map data sent to players, so vanilla clients see it too.
- Dipping a map into a water cauldron still washes all its ink off.

### Atlas

An atlas is a vanilla book and quill (later a written book) carrying an atlas id
and its page order in `custom_data`.

- **Making one:** book and quill + map in the cartography table. The map item is
  consumed; the atlas keeps the whole item (markers, name, color) and its id.
- **Looks like a book:** it opens as a vanilla open book. Each spread has the
  notes page on the left and the map on the right.
- **Notes:** the book's own text pages (vanilla `writable_book_content` /
  `written_book_content`), written with the vanilla book editor, so they survive
  if the mod is removed. Notes stay paired with their map page.
- **Drawing:** with a material (dye, ink sac, glow ink sac, water bottle) in the
  other hand, draw directly on the open page, with the same tools as the table.
- **Sketch pages:** blank pages to draw on freely, using one paper each. They
  belong to the book and can't be taken out.
- **Taking out:** a bound map can be taken back out as the original map item.
- **Signing:** like a book and quill. Becomes a written book with vanilla title,
  author and generation. Pages become read-only.
- **Copying:** vanilla book cloning (written atlas + book and quill), with the
  same generation limits.
- **Bookmarks:** dye-colored ribbons on any page.
- **Later:** table of contents from zone and pin names; stitched overview;
  lectern view.

### Planning

- **Zones:** filled regions with a color, hatch pattern, name and status
  (planned, in progress, done).
- **Blueprint mode:** a block-resolution footprint grid over zoom-0 maps.
- **Measure tool:** distance, area, block coordinates under the cursor.
- **Grid overlays:** block, chunk and region grids, view-only.
- **Wall maps:** framed maps show their ink; adjacent frames stitch together.
- **Waypoints:** a pin can become a compass-style HUD marker.

### In-world projection

While wearing a helmet enchanted with **Foresight**, zones and blueprint
footprints show as ghost outlines in the world. They come from either:

- a map held in the main hand or offhand, or
- an atlas held in hand, using its selected map page (stored in the atlas's
  `custom_data`).

Client rendering only; nothing is placed in the world.

### The enchantment and vanilla players

- Defined as a **data-driven enchantment** (plain JSON). The server syncs it to
  clients at login like vanilla's own enchantments, so vanilla players can still
  join, and can enchant, trade and wear it. Its name uses a translatable text
  with an English fallback, so vanilla clients show a readable name.
- Name: **Foresight**. Helmets only, one level.
- Treasure-only, like Mending: never offered by the enchanting table, found in
  loot chests and sold by librarians. Controlled by vanilla enchantment tags.
- Vanilla players can't render the projection client-side. Optional server
  fallback: packet-only block display entities (glowing outlines) sent to vanilla
  players wearing the helmet. They are never added to the world or saved, and
  are capped by count and distance.
- Removal risk: an item carrying an enchantment the registry no longer knows may
  fail to load (to verify on 26.3). Because the enchantment is plain vanilla
  data, `/mapplus uninstall` (or a config option) can write it into
  `world/datapacks/` as a standalone datapack. Enchanted helmets then stay valid
  and the enchantment remains as an inert name.

### Vanilla client fallback

In server-only mode, for clients without the mod:

- Map ink is merged into the colors in the map packet (the saved map is untouched).
- Pins are sent as vanilla map decorations in the map packet.
- Atlases appear as plain books (later: packet-only placeholder pages for map and
  sketch pages).
- Projection uses the display entity fallback above.

### Spyglass survey

While looking through a spyglass with a map in the other hand:

- **Surveying:** the map fills in from what you see, using vanilla's own map
  coloring and height shading, out to 2× the server view distance
  (configurable). Only the part of the view inside the map's area fills in, and
  it fills progressively as you scan. This runs on the server, so it also works
  for vanilla clients.
- **Chunk loading:** chunks the survey needs are loaded in the background, a
  few per tick, and are never ticked: no mobs, redstone or crops run in them.
- **Chunk generation:** surveying land nobody has visited generates those
  chunks, rate-limited and capped. On by default; a server setting turns it off
  so only existing chunks are used. This is the one feature that can leave
  something behind after removal: normal vanilla terrain, as exploring would.

### Server

- Storage, sync, per-atlas permissions (owner, editors, viewers), undo history.
- `/mapplus` admin commands, including `uninstall`.

### Client

- PNG export of an annotated map or atlas.
- Client-only mode (later).

## Data layout

- `world/data/mapplus/maps/<id>.dat`: ink pixels, vector shapes, zones, pins for map `<id>`.
- `world/data/mapplus/atlases/<uuid>.dat`: sketch pages, bookmarks, permissions.
- Atlas item `custom_data`: atlas id and page order (text / map id / sketch id).
- Every file carries a MapPlus data version for later migrations.

## Project layout

```
MapPlus/
├─ src/main/      common: data model, saved data, packets, cartography table and
│                 atlas logic, enchantment data, vanilla client fallback
├─ src/client/    map overlay rendering, ink and atlas screens, world projection,
│                 HUD, PNG export, client-only storage
└─ docs/DESIGN.md
```

Fabric split client/common source sets plus the Fabric API.

## Build order

1. Project setup, data model, storage, networking
2. Ink on maps at the cartography table (drawing desk, material slot, empty-map canvas); rendering on held and framed maps
3. Spyglass survey: map filling from the spyglass
4. Atlas: binding maps, text pages, sketch pages, signing, copying, lectern
5. Zones, pins, labels, bookmarks, table of contents
6. Enchantment and in-world projection; vanilla client fallbacks
7. Client-only mode, PNG export, uninstall command

## Future versions

### Territory and nations

Visual only: borders are cartography, never rules. Nothing is protected or
enforced.

- **Identity:** a nation (or turf, town, guild) is a name plus a banner design
  (base color and patterns, stored as vanilla stores them). Two nations can't
  register the same design.
- **Political map:** a kind of map, not a toggle. Made at the cartography table
  from a filled map and a banner. It shows the same area with faded terrain,
  borders in each nation's colors, names, and flags at capitals and along
  borders.
- **Held borders:** a nation's banner placed in the world is a border post and
  holds a radius around it. Neighboring borders meet halfway. Shown as solid
  borders.
- **Claimed borders:** territory drawn in an atlas and assigned to a nation.
  Shown as dashed borders.
- **Contested land:** overlapping claims are hatched in both colors.
- **Crossing a border:** "Entering <nation>" in the action bar, with the flag.
  Vanilla clients get this too.
- Banners stay vanilla blocks. MapPlus only remembers which banners count as
  posts, in its own data.

### Spyglass upgrades

- **Far view:** see past the render distance while scoping. Planned as a mix
  of two approaches: real chunks streamed along the line of sight just past the
  render distance (the client widens its chunk storage and render distance
  while scoping, which costs a short hitch), and a cheap map-style preview
  (heights and map colors only) further out.

- Zoom control: scroll while scoping.
- Treasure enchantments: more range; revealing entities (per-player glowing,
  which vanilla clients can see too); revealing structures (outlined bounds).
- Spyglass + compass + an enchantment: finds biomes you haven't discovered yet
  (as tracked by vanilla's Adventuring Time progress), e.g. the compass points
  to the nearest one and the spyglass shows its name on the horizon.

### Depth and cave mapping

- Maps that record a chosen Y level instead of the surface, filled by exploring
  underground.
- Cross-section and contour pages in the atlas.
- Amethyst resonance: using an amethyst shard sends out a chime that sketches
  nearby caves onto the held map.

### Variant to revisit: citizenship

Charters (an atlas bound to the nation's banner), citizenship papers as copies
of the charter, copy generations as ranks, and possibly "whoever holds the
original leads". Kept out of the main mod; may become a parallel variant.

## Decisions

- A map is consumed when bound into an atlas.
- Drawing uses ink and dyes.
- Client-only mode comes later.
- In-world projection requires a helmet enchanted with Foresight, and the map
  (or the atlas, on a selected map page) in hand.
- Ink cost is proportional to what is drawn.
- Water erases; honeycomb seals; an axe removes the seal. All of these go in
  the cartography table's second slot.
- The cartography table itself is the drawing UI; there is no separate screen.
- Foresight is treasure-only.
- v1 spyglass: map surveying only. Far view, zoom control and spyglass
  enchantments come later.
- Spyglass chunk generation: on by default, server setting to turn it off.
- Territory is visual only (future version); the political view is its own
  kind of map.

## Open questions

- Exact ink amounts after playtesting.

## To verify on 26.3

- Book cloning keeps `custom_data` on the copy.
- What happens to an item whose enchantment is no longer registered.
- Fabric registry sync lets vanilla clients join when the mod ships a
  data-driven enchantment.
