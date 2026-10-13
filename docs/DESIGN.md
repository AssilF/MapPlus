# MapPlus — Design

Status: draft. Target: Minecraft 26.3, Fabric.

## Goal

Tools for planning builds, structures and projects in Minecraft, through an
extended vanilla cartography: drawing on maps, atlases (map books) with notes,
zones and pins, and an in-world projection of plans. It should
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
| Atlases | Vanilla book and quill / written book with their text pages intact; map pages show the map's name |
| Maps stitched into an atlas | Their `map_N.dat` stays in the world, but no item points to it any more |
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
  Closing the table gives the drawing back in place of that empty map (or, with
  no empty map left in the slot, keeps it for the player's next one).
- Locking and zooming at the table carry the ink over to the new map.
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
  | Book and quill / atlas | Result: the map stitched into the book as its next page |
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

An atlas is a vanilla book and quill (or, signed, a written book) with maps
stitched in as pages. It opens in the vanilla book screens; nothing about
writing, signing or copying changes.

- **Stitching:** map + book and quill in the cartography table's second slot.
  The map item is used up (its markers, name and color are kept in the atlas)
  and becomes the book's next page. The cartography table is the only place
  this happens; maps can't be taken back out.
- **Spreads:** pages pair up as in an open book. A map page is shown facing the
  other page of its spread, which holds its notes:
  - if the book ends on a left page, the map goes on the right, facing it;
  - otherwise the map starts a new spread on the left, with an empty notes
    page on the right.
- **Reading:** the vanilla page is shown as usual, with the map page drawn
  beside it on its side of the spread. Turning pages steps over map pages,
  since each is always shown with its notes. Signed atlases and atlases on a
  lectern read the same way.
- **Notes:** ordinary book pages, written with the vanilla editor. A map page
  also carries the map's name as text, which is what players without the mod
  (or after removal) see.
- **Exploring:** a held atlas explores with the map of the spread it was last
  closed on, as if that map were held. Closing it on a spread without a map
  stops that. Copies of a signed atlas show the maps but don't explore.
- No map making or drawing on maps inside the atlas: that's the cartography
  table's job. (Planned: sketches on empty pages, which are drawings, never
  maps; see [Sketches](#sketches-in-a-book-and-quill).)
- **Planned:** choosing the page a map is stitched onto, and cutting maps back
  out of an unsigned atlas with shears; see
  [Choosing the page](#choosing-the-page-and-cutting-maps-out).
- **Later:** table of contents from zone and pin names; stitched overview.

### Planning

- **Zones:** filled regions with a color, hatch pattern, name and status
  (planned, in progress, done), built on [labels](#labels-text-on-maps).
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
- Atlases appear as plain books; map pages show the map's name.
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

## Next: tools, painting and writing

Planned from playtesting feedback; none of this is built yet. Each part starts
with what was asked and what changed after review, then the design.

One rule ties it together: **the tool says how, the ink says what.** The ink is
the item in the table's second slot, or in your other hand: a dye or ink sac
draws its color, a glow ink sac makes ink glow, a water bottle erases. The tool
is the item on your cursor at the table, or in your pen hand at a wall. An item
has at most one MapPlus meaning, and it's the same everywhere.

### Item vocabulary

| Item | At the cartography table | On a framed map | In a book and quill |
|---|---|---|---|
| Feather | On the cursor: pen, 1 px | In one hand: pen, 1 px | (The book's own quill is the pen) |
| Brush | On the cursor: pen, 3×3 px | In one hand: pen, 3×3 px | — |
| String | On the cursor: line | Later: line | — |
| Item frame | On the cursor: box | Holds the map (vanilla) | — |
| Bucket (empty) | On the cursor: fill; on a label, applies the ink to the whole label | — | — |
| Sign | On the cursor: write or edit a label | Later: write a label | — |
| Shears | Second slot, with an atlas on top: cut a map out | — | — |
| Dye / ink sac | Second slot: its color (Shift: light coat) | Other hand: its color (sneak: light coat) | Other hand: sketch in its color |
| Glow ink sac | Second slot: makes ink and labels glow | Other hand: makes ink glow | Other hand: makes sketch ink glow |
| Water bottle | Second slot: erases (Shift: waters down one step) | Other hand: erases (sneak: waters down) | Other hand: erases |
| Water cauldron | Dip a map: waters all its ink and labels down one step | (Take the map down first) | — |
| Honeycomb | Second slot: seals ink and labels | Sealed maps refuse paint | — |
| Axe | Second slot: scrapes the seal off | — | — |
| Book and quill | Second slot: stitch the map in at the book's bookmark | — | Held with ink: sketch on an empty page |
| Written book | Can't be stitched into or cut | — | Read only |
| Empty map / paper / glass pane | Top slot: blank canvas. Second slot: copy / zoom out / lock (vanilla) | Never a canvas | — |

Not used, on purpose: the stick (string already means line), the water bucket
(water erases, and a bucket would place water when a click misses), the name
tag (it names mobs).

### Tools on the cursor

**As proposed:** instead of buttons, place tools at the table (quill, stick,
brush, bucket, shears, glow ink sac, string).

**What changes and why:**
- The table has no free slot. A third slot would change the menu's slot layout
  for players with the mod only, and it would hold an item for nothing. The
  vanilla way to hold something in a container window is the cursor, which the
  server already tracks. So the tool is the item you pick up and draw with.
- A glow ink sac is ink, not a tool: it stays in the second slot. Shears cut maps
  out of atlases (see below). Minecraft's quill is the feather.

**Design:**

| On the cursor | Tool |
|---|---|
| Feather | Pen, 1 px |
| Brush | Pen, 3×3 px |
| String | Line: drag from one end to the other |
| Item frame | Box: drag from corner to corner |
| Bucket (empty) | Fill an area of one color |
| Sign | Write a label (see [Labels](#labels-text-on-maps)) |

- Pick a tool up and draw on the map with it. Over the map, the item is drawn
  with its working end on the pointer (the quill's tip, the brush's bristles),
  so it never hides the pixel being drawn.
- The row under the map becomes a rack of these six tools. Clicking one picks it
  up from your inventory (an ordinary vanilla click on the slot holding it) and
  puts what you were carrying there, so switching stays one click. Tools you
  don't have are faded, with a tooltip such as "Needs a brush".
- The brush-size buttons and scroll-to-resize go: the feather is fine, the brush
  is wide.
- Tools are never used up. Ink pays per pixel, as now.
- A press that starts on the map owns its drag and release, even if the release
  lands outside the window, so a carried tool is never thrown and vanilla never
  starts splitting it across slots.
- Shift-clicking an ink in the inventory while carrying a tool swaps it with the
  ink in the second slot.
- The server accepts strokes only while the carried item is a tool, and at most
  4 stroke packets per player per tick.
- Players without the mod keep the plain vanilla table.

**Implementation:** a common `ink/InkTool` (item to kind and size) replaces the
client's `DeskTool`; `DeskWing` reads the tool from `menu.getCarried()`; a mixin
on `AbstractContainerScreen.extractCarriedItem` offsets the item over the map;
`InkManager.draw` checks the carried tool. Effort: small.

### Painting framed maps

**As proposed:** paint unlocked maps in item frames with a feather in one hand
and an ink sac in the other; a brush paints wider; a water bucket fills; shears
cut a line.

**What changes and why:**
- The condition is "not sealed", not "unlocked". Locking only freezes terrain,
  and blank canvases are locked maps: exactly what you'd hang up and paint.
  Honeycomb already means "no drawing".
- No water bucket: water erases everywhere else, a click that misses the frame
  would place water, and a one-click fill on a wall has no preview and could
  spend a stack of dye on someone else's map. Fill stays at the table.
- No shears: shears never draw in vanilla, and a two-point line needs a preview
  that vanilla clients can't show. A string line may come later.
- The brush as a wider pen is right. It isn't worn down; the ink is the cost.

**Design:**
- Hold a pen (feather, or brush) in one hand and ink in the other, and
  right-click a filled map in an item frame. Hold right-click to keep painting.
  Either hand can hold either; a handy setup is the pen in the off hand and dyes
  along the hotbar, scrolling to change color.
- On 26.3 the client sends the exact point it clicked on the frame. The server
  turns it into a map pixel using the frame's facing and the map's rotation.
- Holding use repeats every 4 ticks, so a dab within 5 ticks of the previous one
  on the same frame is joined to it with a line: sweeping paints a stroke.
- Painting replaces rotating. If the ink runs out mid-hold, clicks are
  swallowed for 10 ticks so the map doesn't start spinning.
- Cost as at the table: changed pixels only, from the ink in hand, with the same
  wear model and the same leftover charge. A used-up water bottle becomes a glass
  bottle in that hand. Free in creative.
- Vanilla behavior runs instead for fixed or invulnerable frames, frames holding
  anything but a filled map, adventure and spectator mode (`mayBuild`) and spawn
  protection. The hook sits inside vanilla's frame interaction, so claim mods
  that stop a player using a frame stop painting too.
- A sealed map refuses with an action bar message and doesn't rotate either.
- Against hacked clients: the point must be within entity reach of the eyes and
  in clear line of sight, and at most one dab is applied per tick.
- For 10 ticks after a dab, the other hand's item can't be used on its own, so
  missing the frame mid-stroke doesn't drink the water bottle or dye a sign.
- Feedback: the arm swings; the painter sees the change at once, others at the
  usual framed-map refresh; a quiet, rate-limited sound for the painter.
- Players without the mod can paint too: it all runs on the server.
- Server setting `framePainting` (on). One log line per painting session: who,
  how many pixels, which map, where.
- Not on frames: fill, box, labels (later, with a sign) and blank canvases.

**Implementation:** a server-side mixin at the head of
`ItemFrame.interact(Player, InteractionHand, Vec3)`; `FramePixels` maps a point
to a pixel (tested for 6 facings × 4 rotations); `Strokes` moves to common code;
`InkManager.draw` splits into the desk checks and a shared `apply`; the wallet
takes its ink from a slot or a hand. Effort: medium.

### Protecting maps

Ink belongs to the map id, so anyone holding a copy can draw on every copy,
including a framed mural. Framed-map painting, watering down and labels make
that easier, so:

- Sealing is the owner's lock, and seals remember who sealed them. Only that
  player or an operator can scrape a seal off (server setting `sealsAreOwned`,
  on).
- Painting a framed map is only possible where taking the map down would be.

### Watering ink down

**As proposed:** water down the colors, to make them more or less transparent.

**What changes and why:** map pixels are palette colors with no transparency,
and vanilla clients can only show those. Blending the ink with the terrain and
picking the nearest palette color changes the hue: half red over grass comes
out dirt brown. So watered ink is shown the way vanilla maps already shade
water and slopes: a fixed checkerboard of ink and terrain.

**Design:**
- Four strengths: full, ¾, ½ and ¼. Watered ink shows on its share of a fixed
  2×2 pattern and lets the terrain show on the rest. The pattern follows the
  map's own pixel grid, so strokes and neighboring wall maps line up. Where the
  ink shows, it takes the terrain's shade, so slopes show through.
- Shift while drawing lays a light coat: ¼ of the color on bare map or another
  color, or one step stronger on the same color (¼ → ½ → ¾ → full), like going
  over a highlighter again.
- A water bottle still erases in one pass. With Shift it waters ink down one
  step: full → ¾ → ½ → ¼ → gone.
- The fill tool with Shift tints or fades a whole area by one step: the quick
  way to shade a planned area.
- On a framed map, Shift is sneaking. In a sketch, Shift.
- Water cauldron: each dip waters all of a map's ink and labels down one step,
  for one water level. Four dips leave it clean. (Today one dip wipes it all.)
- Glow keeps the strength. A sealed map refuses all of it.
- **Cost:** you pay for each pixel whose look changes: it starts showing ink,
  changes color or glow, or stops showing ink (paid in water). A ¼ coat over 400
  pixels costs 100, and building it up to full costs 300 more. Lines drawn only
  on the pattern's visible cells still pay for every pixel you can see.
- Locking keeps strengths. Zooming out turns each 2×2 block into one pixel whose
  strength is how many of the four had ink.
- Fill floods by what's drawn, not by the colors shown: ink by its dye and glow
  at any strength, bare map by terrain color ignoring shade. This also fixes
  today's patchy fills on shaded terrain.
- Everyone sees the same pixels, since it's all palette colors in the packet.

**Implementation:** bits 5-6 of the ink byte hold how watered it is (0 = full,
so existing ink reads as full); `InkLayer.overlay` applies the pattern and the
terrain's shade; the cost rule lives in common code so the desk's estimate
matches the server; `InkStrokePayload` gets a flags byte (shape, light); the
desk receives the shown map's ink layer (all of it when shown, then changed
rectangles) so fills, previews and estimates work on watered ink;
`CauldronWashing` fades one step. Ink storage moves to one file per map first.
Effort: medium.

### Labels: text on maps

**As proposed:** a seamless way to add text to maps, as the base for zones,
borders and the rest.

**What changes and why:**
- Text drawn as ink pixels isn't text: it can't be edited, listed or searched,
  so zones and a table of contents couldn't use it, and a pixel font costs 4
  pixels a letter. Labels are stored as text.
- Vanilla already has the model: the sign. You type on it, a dye colors the
  text, a glow ink sac makes it glow, honeycomb waxes it and an axe scrapes the
  wax off. MapPlus's inks already mean exactly that. So labels are written with
  a sign.
- Vanilla can only show text on a map as a marker's name (a named banner). That
  is what players without the mod see.

**Design:**
- At the table, carry a sign (any wood) over the map and click: a caret appears
  at that spot. Type, and the text shows on the map in the ink's color. Enter
  places it, Esc cancels. While typing, E and the number keys type letters.
- Scroll while typing to pick the size: small, medium or large.
- The second slot colors it: a dye or ink sac its color, a glow ink sac makes it
  glow (bright and outlined, readable at night, like glowing sign text).
- Click a label with the sign to edit it; drag it to move it.
- The bucket applies the second slot's ink to a whole label: a dye recolors it,
  a glow ink sac makes it glow, a water bottle erases it (with Shift, waters it
  down).
- One line, at most 32 characters and never wider than the map. At most 32
  labels per map (server setting).
- **Cost:** letters are ink: 4, 9 or 16 pixels' worth per letter (small, medium,
  large), paid when placed, all or nothing. Edits pay only for added letters.
  Spaces are free. Free in creative.
- Sealed maps can't get, change, move or lose labels.
- **Players with the mod** see crisp vanilla text on the map, in the ink's map
  color, wherever the map is drawn: held, framed, on the desk, in an atlas.
- **Players without the mod** see each label as a banner marker in its dye's
  color with the text as its name, exactly as vanilla shows a named banner. Long
  names shrink, and there's no glow or fading.
- **Safety:** clients send plain text only. The server strips formatting codes,
  control and invisible characters, runs the server's text filter as it does
  for sign text (players with filtering on get the filtered text), stores who
  wrote it and when, and logs each label. At most 4 label edits a second.
- Removing the mod removes labels along with `world/data/mapplus/`.

**What builds on labels:**
- A label grows into a **mark**: text, position, color, glow, strength, size,
  and later an icon and a link.
- **Pins:** marks with an icon. The tool is a banner: its color, and its anvil
  name if it has one. Players without the mod see that banner's own marker.
- **Zones:** a filled or hatched area (hatches are more patterns for the same
  engine that waters ink down) with a name mark and a status.
- **Borders:** dashed ink lines with names along them.
- **Table of contents:** every named mark on an atlas's maps.
- **On framed maps:** later, using a sign on a framed map opens a sign-style
  prompt (needs the mod on the client).

**Implementation:** `InkLayer` gains labels (id, text, filtered text, position
in half pixels, dye, glow, strength, size, writer, time). A `LabelEditPayload`
is checked like a stroke and filtered like a sign update. Holders with the mod
get the map's labels when they change; holders without it get banner markers
added to their map packet in `HoldingPlayerMixin`. The client draws labels in
one `MapRenderer` hook, which covers held maps, frames, the desk and atlas
pages. Locking copies labels; zooming out moves them. Effort: large.

### Choosing the page, and cutting maps out

**As proposed:** choose which page a map is stitched onto, and unstitch maps
from a book that isn't signed.

**What changes and why:**
- "Which page" really means "which spread": a map faces its notes, and its own
  page's text becomes the map's name, so a map must never land on a written page
  or face another map.
- Inserting pages has to move whole spreads, two pages at a time. Otherwise
  every later map would swap sides and face the wrong notes. For the same reason
  a single page is never removed.
- An unsigned atlas is one of a kind (a book and quill can't be copied), so
  taking one map out of it can't duplicate anything. Signed atlases can be
  copied, so they never give maps back.
- The table has one result slot, so cutting changes the atlas in place, as an
  axe takes damage in place today.
- Shears were meant to "cut a line" on a framed map. Cutting a map out of a
  book is where they fit.

**Design: the bookmark.**
- The atlas remembers the page it was left open on, and opens there again. A
  held atlas explores with the map of that spread, as now.
- Stitching puts the map in the bookmarked spread:
  1. If the spread already holds a map, a new spread is inserted after it: the
     map on the left, an empty notes page on the right.
  2. Otherwise, if the bookmarked page is empty, the map goes there.
  3. Otherwise, if the facing page is empty, the map goes there, facing the
     notes you were writing.
  4. Otherwise a new spread is inserted after this one.

  With no bookmark, the map goes at the end of the book, as now.
- The result is bookmarked on the new map, so stitching several maps in a row
  keeps them in order.
- For example: write about your village on page 7, close the book, stitch the
  village map at the table, and it lands on page 8 facing your notes.

**Design: the book on the desk.**
- With a book in the second slot (stitching), or an atlas in the top slot
  (cutting), the wing shows the book lying open instead of the canvas, with
  vanilla's page arrows and "Page X of Y". Hovering a page shows its first
  lines, or the map's name.
- The arrows turn the target spread, as on a lectern. The page belongs to the
  table; only the result carries the new bookmark.
- While stitching, the wing shows the result: exactly where the map lands and
  what it faces.

**Design: cutting a map out.**
- An unsigned atlas in the top slot and shears in the second slot. Written books
  aren't accepted.
- The result is the map in the spread shown (or the next map after it), exactly
  as it was stitched, with its name and markers. Its terrain and ink come along,
  since they belong to the map id.
- Taking it removes that map from the atlas in the top slot, the shears take 1
  damage, and they snip. The map's page becomes empty (its text is cleared only
  if it's still the map's name), its notes stay, and no other page moves. The
  bookmark stays put, so the next map stitched in fills the gap and faces the
  old notes.
- The result doesn't move on to the next map, so a shift-click takes exactly
  one.
- When the last map comes out, the book goes back to being a plain book and
  quill with its notes (a custom name stays).
- To move a map within the atlas: cut it out, turn to the new spread, stitch it
  back.

**Implementation:** the atlas's `selected` becomes `open` (data version 3; old
atlases read `selected` as `open`); `Atlas.stitch` takes a page and
`Atlas.cut` is new; the spread-stepping helpers live in common code; the table
keeps its own page and answers vanilla's menu button packet (1 and 2) for desk
players only; the result slot works out the cut again from the slots and checks
the map id before changing anything; the slots accept an atlas on top and shears
in the second slot, for desk players. Effort: medium.

### Sketches in a book and quill

**As proposed:** draw a sketch on an empty page of a book and quill while
holding ink or dye in the other hand.

**What changes and why:**
- This reverses "no sketching inside the atlas", so it must never become map
  making. A sketch is a drawing on a page, never a map: it has no map id and
  can't be taken out, framed, copied at the table or explored. Maps still come
  only from the table.
- Only an empty page: in the book editor a click places the text cursor, so a
  page is either text or a sketch.
- The book's own quill is the pen: 1 px, freehand. No brush, line or fill in the
  book; precise drawing is the table's job.
- Sketches don't use map ids: one pixel would cost a map file and 16 KB of saved
  ink. They're stored small, in the book itself, like its text.

**Design:**
- Hold the book and quill and, in the other hand, ink: a dye or ink sac draws, a
  glow ink sac makes sketch ink glow, a water bottle erases (keep it in the off
  hand, or it gets drunk). Shift lays a light coat. Open the book as usual.
- An empty page (no text, not a map page) becomes a sketch: 60×60, the size of a
  map pasted on a page.
- A sketch page acts like a map page: it faces its notes page, the text box is
  hidden, and turning pages steps over it. Erased completely, it's an empty page
  again.
- **Cost:** changed pixels, from the ink in hand, with the same wear model and
  leftover charge. A full page is 3,600 pixels; a line drawing is an ink sac or
  two. The page shows an ink bar. Free in creative.
- Saved like book text: when the book closes, one small message per changed
  page goes to the server before vanilla's own. The server checks the book, the
  ink in hand and the page (empty or already a sketch, not a map), charges,
  applies pixels until the ink runs out and writes the book once.
- Signing freezes sketches. Copies of a signed book and lecterns show them.
- At most 16 sketch pages per book (server setting), stored compressed: well
  within what vanilla lets a book's text hold.
- Players without the mod, and everyone after removal, see an empty page; text
  pages are untouched.

**Implementation:** the atlas data gains sketches (page, compressed pixels); a
book with only sketches isn't renamed "Atlas"; `AtlasSpread` draws a sketch as
pixel runs at twice its size; `BookEditScreenMixin` draws on empty pages while
the other hand holds ink; a `SketchPayload` per changed page; the shared wallet
takes ink from a hand. Effort: medium.

### Order

1. Tools on the cursor (small): adds the shared `InkTool`.
2. Choosing the page and cutting maps out (medium).
3. Painting framed maps (medium): moves `Strokes` and the wallet to common code.
4. One ink file per map, then watering down (medium).
5. Labels (large), then pins, zones and borders on top of them.
6. Sketches (medium).

## Data layout

- `world/data/mapplus/maps/<id>.dat`: ink pixels, labels, vector shapes, zones, pins for map `<id>`.
  (Today all ink is in one `ink.dat`; it moves to one file per map before
  watering down and labels add data.)
- Atlas item `custom_data`: which pages hold which map, the bookmarked page, and
  (planned) sketch pages.
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
4. Atlas: stitching maps, map spreads in the vanilla book screens, signing, copying, lectern
5. [Next](#next-tools-painting-and-writing): tools on the cursor, choosing the page and cutting maps out, painting framed maps, watering down, labels, sketches
6. Zones, pins, borders, table of contents (on labels)
7. Enchantment and in-world projection; vanilla client fallbacks
8. Client-only mode, PNG export, uninstall command

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

- A map is consumed when stitched into an atlas. (Planned: it can be cut back
  out with shears at the table while the atlas is unsigned; signing makes its
  maps permanent.)
- Drawing uses ink and dyes.
- Client-only mode comes later.
- In-world projection requires a helmet enchanted with Foresight, and the map
  (or the atlas, on a selected map page) in hand.
- Ink cost is proportional to what is drawn.
- Water erases; honeycomb seals; an axe removes the seal. All of these go in
  the cartography table's second slot.
- The cartography table itself is the drawing UI; there is no separate screen.
- The tool says how, the ink says what: one meaning per item, the same at the
  table, on a wall and in a book. Planned: tools are items on the cursor instead
  of buttons.
- Framed maps can be painted where they hang (planned), but only freehand: fill,
  box, sealing, stitching and canvases stay at the table.
- Watered-down ink is a fixed checkerboard pattern, never a blended color.
- Text on maps is stored as text (labels), written with a sign.
- Sketches are page drawings in the book, never maps.
- Foresight is treasure-only.
- v1 spyglass: map surveying only. Far view, zoom control and spyglass
  enchantments come later.
- Spyglass chunk generation: on by default, server setting to turn it off.
- Territory is visual only (future version); the political view is its own
  kind of map.

## Open questions

- Exact ink amounts after playtesting.
- Must a feather (or another tool) be on the cursor to draw, or should an empty
  cursor draw as the pen? Planned: a tool is needed; the rack makes picking one
  up a single click.
- Should only the player who sealed a map (or an operator) be able to scrape the
  seal off? Planned: yes on servers (`sealsAreOwned`), unlike vanilla wax.
- A cauldron dip waters a map down one step instead of washing it clean in one
  go. Planned: yes, four dips to clean.
- Framed-map painting on by default, and open to players without the mod?
  Planned: yes to both.
- Sketch size (60×60) and cap (16 pages per book).

## To verify on 26.3

- Book cloning keeps `custom_data` on the copy.
- Saving a book and quill (edit and sign packets) keeps `custom_data`.
- The point sent with an item frame interaction: relative to the frame or
  absolute, and precise to well under 1/128 of a block. A framed map's hitbox
  covers the whole map face.
- How often framed maps send changes to other players.
- The name of the spawn-protection check.
- Map marker names: how they're scaled, whether their color is kept, and that
  banner markers show on framed maps.
- Whether 26.3's map render state can carry text drawn by the mod.
- The server text filter API used for sign text.
- Vanilla's checkerboard shading in `MapItem.update`.
- What happens to an item whose enchantment is no longer registered.
- Fabric registry sync lets vanilla clients join when the mod ships a
  data-driven enchantment.
