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

### Ink

- Materials: ink sac (black), the 16 dyes (colors), glow ink sac (glowing ink).
- Drawing uses ink in proportion to what is drawn. One ink item loaded into the
  inkwell gives 256 ink (configurable). Costs:
  - Pixel layer: 1 ink per pixel painted.
  - Lines, arrows and shape outlines: 1 ink per pixel of length.
  - Zone fills: the outline cost plus a small flat fee, not the area, so large
    zones stay affordable.
  - Text labels: 1 ink per character.
  - Glow ink sac: makes strokes glow, at the same rate.
- Leftover ink is kept on the player as a Fabric attachment, which vanilla drops
  silently if the mod is removed.
- **Eraser:** a water bottle, matching vanilla's cauldron washing of dye. One
  bottle erases 256 pixels and leaves a glass bottle. Dipping a map into a water
  cauldron clears all its ink and uses one cauldron level.
- **Sealing:** honeycomb seals a map's or sketch's ink against edits, like waxing
  a sign. An axe scrapes the seal off, like scraping wax off copper.
- Two layers per surface:
  - **Pixel layer:** 128×128, 16 dye colors plus a glow flag.
  - **Vector layer:** lines, arrows, rectangles, polygons, text labels, pins.
- Surfaces: a map (keyed by map id) or a sketch page (keyed by sketch id).
- Ink on a map is shared by every copy of that map id, like vanilla map content.

### Cartography table

- Vanilla functions (zoom, clone, lock) unchanged.
- **Ink tab:** draw on a map.
- **Bind:** add maps to an atlas.

### Atlas

An atlas is a vanilla book and quill (later a written book) carrying an atlas id
and its page order in `custom_data`.

- **Making one:** book and quill + map in the cartography table. The map item is
  consumed; the atlas references its map id.
- **Pages:**
  - Text pages: stored in the vanilla `writable_book_content` /
    `written_book_content` component, so they survive removal.
  - Map pages: a bound map, with its ink, zones and pins.
  - Sketch pages: a blank page drawn on with the same ink mechanism.
- **Adding pages:** maps through the cartography table; text and sketch pages in
  the atlas screen.
- **Signing:** like a book and quill. Becomes a written book with vanilla title,
  author and generation. Map and sketch pages become read-only.
- **Copying:** vanilla book cloning (written atlas + book and quill), with the
  same generation limits. Copies reference the same map ids and sketches; this
  is safe because signed atlases are read-only.
- **Bookmarks:** dye-colored ribbons on any page.
- **Table of contents:** built from zone and pin names.
- **Overview:** all map pages stitched together.
- **Lectern:** works like a book; players with the mod see the full atlas.

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

While looking through a spyglass:

- **Far view:** the server sends chunks beyond the normal view distance, but
  only in a narrow cone along the line of sight, so you actually see distant
  terrain instead of a magnified image. Range (default 2× the server view
  distance) and cone width are configurable and rate-limited. Needs the mod on
  the client; vanilla clients see the vanilla spyglass.
- **Chunk generation:** looking at land nobody has visited generates those
  chunks, rate-limited and capped. On by default; a server setting turns it off
  so only existing chunks are shown. This is the one feature that can leave
  something behind after removal: normal vanilla terrain, as exploring would.
- **Surveying:** with a map in the other hand, the map fills in from what you
  see, using vanilla's own map coloring and height shading. Only the part of the
  view inside the map's area fills in, and it fills progressively as you scan.
  This runs on the server, so it also works for vanilla clients.
- Far chunks are loaded read-only and are never ticked: no mobs, redstone or
  crops run in them.

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
2. Ink on maps at the cartography table (with ink cost); rendering on held and framed maps
3. Spyglass survey: map filling from the spyglass, then far view
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
- Water erases; honeycomb seals; an axe removes the seal.
- Foresight is treasure-only.
- v1 spyglass: far view and map surveying only; zoom control and spyglass
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
