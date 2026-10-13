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
- **Tools are items on the cursor:** a feather (pen), brush (wide pen),
  string (line), item frame (box) or bucket (fill, up to the ink around the
  click). A rack under the canvas picks them up from the inventory. See
  [Tools on the cursor](#tools-on-the-cursor).
- **Planning, with a loom next to the table:** a sign writes labels, a banner
  pins its marker, an iron chain draws dashed borders. See
  [Labels](#labels-text-on-maps).
- **Performance:** the canvas is the map's own texture, drawn the way vanilla
  draws maps in item frames. Only the stroke being drawn is overlaid.
- Ink is still never written into the map's own colors; it is drawn into the
  map data sent to players, so vanilla clients see it too.
- Dipping a map into a water cauldron washes all its ink and labels off; pins stay.

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

Planned from playtesting feedback. Each part starts with what was asked and
what changed after review, then the design. **Built so far:** tools on the
cursor, labels, pins and borders (the planning desk), owned seals, watering
down and tints, sherd icons, banner designs, sizes and paper canvases; see
[Built: tints and icons](#built-tints-and-icons) and
[Built: the planning desk](#built-the-planning-desk) for where the build
differs from the plan.

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
  puts what you were carrying there, so switching stays one click. A tool in the
  off hand counts too: the rack first moves it into the inventory with vanilla's
  off-hand swap. Tools you don't have are faded, with a tooltip such as "Needs a
  brush".
- The brush-size buttons and scroll-to-resize go: the feather is fine, the brush
  is wide.
- Tools are never used up. Ink pays per pixel, as now.
- A press that starts on the map owns its drag and release, even if the release
  lands outside the window, so a carried tool is never thrown and vanilla never
  starts splitting it across slots.
- Shift-clicking an ink in the inventory while carrying a tool adds it to the
  second slot if it stacks with what's there, and otherwise swaps the two. Two
  of the same item that can't stack (two water bottles) are never swapped: the
  swap has to end vanilla's shift-click repeat, or it would never stop.
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
- Holding use repeats every 4 ticks, so a dab within 8 ticks (two repeats) of
  the previous one on the same frame is joined to it with a line: sweeping
  paints a stroke. One dab is applied per tick; a second one arriving in the
  same tick waits for the next (at most two wait), so lag doesn't leave gaps.
- Painting replaces rotating. A held right-click that runs out of ink, or hits a
  sealed map, keeps being swallowed for as long as it's held, so the map never
  starts spinning.
- Cost as at the table: changed pixels only, from the ink in hand, with the same
  wear model and the same leftover charge. A used-up water bottle becomes a glass
  bottle in that hand. Free in creative.
- Painting needs what taking the map down needs, not just what rotating it
  needs: build rights (`mayBuild`, so not in adventure or spectator mode), no
  spawn protection, a frame that isn't fixed or invulnerable, and a pass from
  Fabric's attack-entity event for that frame (asked without attacking it), which
  is how claim mods guard taking items out of frames. Where a player may rotate
  a frame but not break it, the frame only rotates.
- The hook replaces vanilla's rotate step, after vanilla's own checks and after
  other mods have had their say on the interaction.
- A sealed map refuses with an action bar message and doesn't rotate either.
- Against hacked clients: the point must be within entity reach of the eyes and
  in clear line of sight.
- For 10 ticks after a dab or a swallowed click (each one starts the 10 ticks
  again), the server refuses every other use by that player, in either hand:
  no drinking the water bottle, dyeing a sign, putting the feather in the next
  frame, rotating other frames or brushing a block when the crosshair slips off
  the map. It re-syncs the hands so a vanilla client drops a drink or brush it
  started on its own and keeps repeating.
- Feedback: the arm swings; the painter sees the change at once, others at the
  usual framed-map refresh; a quiet, rate-limited sound for the painter.
- Players without the mod can paint too: it all runs on the server.
- Server setting `framePainting` (on). One log line per painting session: who,
  how many pixels, which map, where.
- Not on frames: fill, box, labels (later, with a sign) and blank canvases.

**Implementation:** a server-side mixin around the rotate step of
`ItemFrame.interact(Player, InteractionHand, Vec3)`; `FramePixels` maps a point
to a pixel (tested for 6 facings × 4 rotations); `Strokes` moves to common code;
`InkManager.draw` splits into the desk checks and a shared `apply`; the wallet
takes its ink from a slot or a hand. Effort: medium.

### Protecting maps

Ink belongs to the map id, so anyone holding a copy can draw on every copy,
including a framed mural. Framed-map painting, watering down and labels make
that easier, so:

- Sealing is the owner's lock. MapPlus records each map's maker in its own data
  when the map is made (an empty map used, or a blank canvas made at the table);
  locking and zooming keep the maker. With `sealsAreOwned` (on), a seal can only
  be scraped off by whoever sealed it, the map's maker or an operator. Maps made
  before MapPlus have no maker, so their sealer owns the seal. A copy holder can
  seal the maker's map, but can't lock the maker out.
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
  map's own pixel grid, so strokes and neighboring wall maps line up.
- Where plain ink shows, it takes the terrain's shade but never brighter than
  normal, so slopes show through. Glow ink always shows at the bright shade, so
  it never looks like a sunny slope. Where there's no terrain (unexplored land,
  blank canvases), ink shows at the normal shade.
- Shift while drawing lays a light coat: ¼ of the color on bare map, or one step
  stronger on the same color (¼ → ½ → ¾ → full), like going over a highlighter
  again. On another color it recolors at that ink's strength; only water makes
  ink weaker.
- A light coat raises a pixel at most one step per stroke, however often the
  stroke crosses it.
- A water bottle still erases in one pass. With Shift it waters ink down one
  step: full → ¾ → ½ → ¼ → gone.
- The fill tool with Shift tints or fades a whole area by one step: the quick
  way to shade a planned area.
- On a framed map, Shift is sneaking. In a sketch, Shift.
- Water cauldron: each dip waters all of a map's ink and labels down one step,
  for one water level. Four dips leave it clean. (Today one dip wipes it all.)
- Glow keeps the strength, and only marks pixels that show ink: pixels that show
  later, as the ink gets stronger, don't glow until glowed. A sealed map refuses
  all of it.
- **Cost:** you pay for each pixel whose look changes, once per stroke: it starts
  showing ink, changes color or glow, or stops showing ink (paid in water). A ¼
  coat over 400 pixels costs 100, and building it up to full costs 300 more.
  Lines drawn only on the pattern's visible cells still pay for every pixel you
  can see.
- Locking keeps strengths. Zooming out turns each 2×2 block into one pixel whose
  strength is how many of the four showed ink, in the most common color (and
  glow) among them.
- Fill floods by what's drawn, not by the colors shown: ink by its dye and glow
  at any strength, bare map by terrain color ignoring shade. This also fixes
  today's patchy fills on shaded terrain.
- Everyone sees the same pixels, since it's all palette colors in the packet.

**Implementation:** bits 5-6 of the ink byte hold how watered it is (0 = full,
so existing ink reads as full); `InkLayer.overlay` applies the pattern and the
terrain's shade; the cost rule lives in common code so the desk's estimate
matches the server; `InkStrokePayload` gets a flags byte (shape, light) and a
stroke id, and the server remembers which pixels the current stroke has already
coated (a framed-map painting session is one stroke); the
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
- **Cost:** letters are ink: a label costs its visible letters times 4, 9 or 16
  pixels' worth (small, medium, large), paid when placed, all or nothing. An
  edit to the text or the size pays whatever it adds to that cost; making a
  label smaller or shorter is free. A label needs at least one visible letter.
  Recoloring or glowing a label costs its cost in that ink; erasing or watering
  it down costs its cost in water. Free in creative.
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
- An empty page here means no text, no map and no sketch. Stitching puts the
  map in the bookmarked spread:
  1. If the spread already holds a map or a sketch, a new spread is inserted
     after it: the map on the left, an empty notes page on the right.
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
  table, and the server keeps it: it starts at the book's bookmark when a book
  goes in, stays in range as things change, and is sent to the client the way a
  lectern sends its page. Only the result carries the new bookmark.
- While stitching, the wing shows the result: exactly where the map lands and
  what it faces.

**Design: cutting a map out.**
- An unsigned atlas in the top slot and shears in the second slot. Written books
  aren't accepted.
- The result is the map in the spread shown, and nothing if that spread has no
  map. When an atlas goes on the table, it opens at the first spread from its
  bookmark on that holds a map. The map comes out exactly as it was stitched,
  with its name and markers; its terrain and ink come along, since they belong
  to the map id.
- With an atlas on top, only shears give a result. Anything else in the second
  slot leaves the result empty.
- Taking it removes that map from the atlas in the top slot, the shears take 1
  damage, and they snip. The map's page becomes empty (its text is cleared only
  if it's still the map's name), its notes stay, and no other page moves. The
  atlas is bookmarked on the gap, so the next map stitched in fills it and faces
  the old notes.
- After a cut the result stays empty until the page is turned. Vanilla repeats
  a shift-click on the result while it holds the same item, so moving on by
  itself would cut every map out in one click.
- When the last map comes out, the book goes back to being a plain book and
  quill with its notes (a custom name stays), unless it still has sketches.
- To move a map within the atlas: cut it out, turn to the new spread, stitch it
  back.

**Implementation:** the atlas's `selected` becomes `open` (data version 3; old
atlases read `selected` as `open`); `Atlas.stitch` takes a page and
`Atlas.cut` is new; the spread-stepping helpers live in common code; the table
keeps its own page in a menu data slot that only desk menus have (both sides
build the menu knowing it's a desk), and answers vanilla's menu button packet
(1 and 2) for desk players only. The result slot checks the cut before the map
leaves it: it works the cut out again from the atlas, the shears and the page,
and refuses the take unless that gives the same map. Only then does taking it
remove the plate and wear the shears; vanilla's removal of the inputs never
runs. The slots accept an atlas on top and shears in the second slot, for desk
players. Effort: medium.

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
- An empty page (no text, map or sketch) whose facing page holds text, or is
  also empty, becomes a sketch: 60×60, the size of a map pasted on a page. The
  page facing a map or another sketch can't, since it's that one's notes.
- A sketch page acts like a map page everywhere: it faces its notes page, the
  text box is hidden, turning pages steps over it, maps aren't stitched onto it
  or facing it, and the book keeps its page even when it's the last one. Erased
  completely, it's an empty page again.
- **Cost:** changed pixels, from the ink in hand, with the same wear model and
  leftover charge. A full page is 3,600 pixels; a line drawing is an ink sac or
  two. The page shows an ink bar. Free in creative.
- Saved like book text: when the book closes, one small message per changed
  page goes to the server. The server applies vanilla's own edit for that close
  first (it can arrive later, after the text filter), then checks the book, the
  ink in hand and each page (empty or already a sketch, not a map), charges,
  applies pixels until the ink runs out and writes the book once. A page that
  ends up with text loses its sketch.
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

1. ~~Tools on the cursor~~ (built)
2. ~~Labels, pins and borders, unlocked by a loom~~ (built)
3. ~~Watering down, tints, sherd icons, banner designs, sizes, paper canvases~~
   (built; see [Built: tints and icons](#built-tints-and-icons))
4. Choosing the page and cutting maps out, with the book on the desk
5. Atlases shown like maps: held, the open spread's map shows as a held map
   does; in an item frame, the open map shows as a framed map does
6. Painting framed maps (on by default, open to players without the mod)
7. One ink file per map
8. Sketches: in the hand with the quill only; at the table, on the book on the
   desk, with all the table's tools. A sketch fills the page's writing area as
   a vanilla book lays it out, so it sits where text would.

### Built: tints and icons

From playtesting on a server, and where it differs from the plan above:

- **Typing labels works with a real keyboard.** Since 26.x, keyboard text only
  reaches the game while text input is on, as it is for a focused text box; the
  desk turns it on while a label is typed. (Tests type straight into the screen,
  which is why they passed.) While typing, the label's size shows under it, and
  ↑ ↓ size it as scrolling does.
- **Strength is scrolled, not Shift.** Scrolling over the map with a drawing
  tool picks full, ¾, ½ or ¼; a tooltip says which, and the preview under the
  cursor shows it. It's kept from table to table. A stroke sets pixels to that
  strength: over ink of the same color it only ever makes it stronger, over
  another color it recolors at that strength. With water, full erases and any
  lower strength fades a step. Watered dye costs that share of a pixel's ink.
- **True tints for players with the mod.** The server leaves watered ink out of
  the pixels it sends them and sends it on its own (`MapTintsPayload`,
  compressed, when the ink changes); their game blends it over the map's colors
  when it makes the map's picture, so terrain shows through in its own colors.
  Players without the mod get the 2×2 pattern. Ink keeps the normal shade
  (taking the terrain's shade is left for later).
- **The bucket floods by color and strength,** so a tinted plot fills apart
  from its border, and re-filling it at another strength changes the tint.
- **Cauldron dips fade** ink, labels and icons a step; pins stay.
- **Pottery sherds stamp icons:** the sherd's picture is cut out of its pot
  texture (the pixels darker than the pot around them) and drawn in the dye's
  color, named with the sherd's anvil name. Costs 8, 16 or 32 pixels of ink.
  Counted with pins against `maxPinsPerMap`. A ninth rack slot; the desk is 16
  pixels wider.
- **Pins show their banner:** the front of the banner's flag, with its
  patterns, made from the game's own textures (so resource packs apply).
- **Sizes:** scroll over a mark with its tool to size it. Growing a label or
  icon costs the difference, in its own ink; shrinking is free.
- **Players with the mod draw all marks themselves;** the server no longer sends
  them pins as vanilla markers. Players without the mod see every mark as a
  named banner marker.
- **Paper is a blank canvas,** like an empty map.

### Built: the planning desk

What was built, and where it differs from the plan above:

- **A loom next to the table unlocks marking.** Labels (sign), pins (banner)
  and borders (iron chain) need a loom in one of the eight blocks around the
  table, on the same level, like bookshelves around an enchanting table
  (`markingNeedsLoom`). Without one, those tools are faded on the rack.
- **The rack** is eight tools: feather, brush, string, item frame, bucket, iron
  chain, sign, banner. Clicking one picks it up from the inventory (or the off
  hand); clicking the one in hand puts it back. The brush-size buttons are gone:
  the feather draws 1 pixel, the brush 3.
- **The bucket fills up to ink.** On bare map it floods everything enclosed by
  the ink lines around it, whatever the terrain; on ink it floods that ink.
  Gaps of up to two pixels in a line hold the fill, so a dashed border drawn
  with the chain can be filled. The desk gets the map's ink from the server
  while the bucket is in hand (`InkLayerPayload`).
- **Borders** are dashed ink lines (3 on, 2 off), drawn like the string's line.
- **Labels:** click the map with a sign and type; scroll to size (small,
  medium, large), Enter to place, Esc to cancel. Letters cost 2, 4 or 8 pixels
  of ink each, in the second slot's dye. Click a label with a sign to rewrite
  it (paying only for what's added in the same ink), drag it to move it, drag
  it off the map to remove it. Up to 32 characters, 32 labels per map. Glowing
  labels come later.
- **Pins** are free and capped at 32 per map; a pin is the banner's own marker,
  named with the banner's anvil name. The banner isn't used up. Moved and
  removed like labels, with a banner on the cursor.
- **Players without the mod** see pins as banner markers and labels as banner
  markers named with their text, in the label's dye.
- **Washing** in a cauldron removes ink and labels; pins stay.
- **Seals are owned:** each map's maker is recorded (when an empty map is used,
  or a blank canvas made). On a map whose maker is known, the sealer is
  recorded too, and with `sealsAreOwned` only the sealer, the maker or an
  operator gets a result from an axe. Maps made before MapPlus have no maker,
  so their seals have no owner and anyone can scrape them, as before; that way
  a copy of an older map can't be used to lock its maker out. A sealed map
  keeps its seal's owner.
- **Labels stay on the map:** they can't be typed or sized wider than it, and
  when drawn they're shrunk and moved in to fit, whatever was saved.
- **Safety:** clients send plain text, cleaned of formatting codes, control and
  invisible characters; strokes and mark edits are limited to about four a tick
  per player (with room for bursts after lag), and placing, moving or removing
  marks to about two a second; each label and pin placed or removed is logged
  with who did it (`logMarks`).

## Data layout

- `world/data/mapplus/maps/<id>.dat`: ink pixels, labels, the seal and the map's maker, vector shapes, zones, pins for map `<id>`.
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

## Built: nations, caves, the echo, the spyglass and Foresight

Where the build differs from the plans below:

- **Nations come from banners alone.** Only a *named* banner put up in the world
  is a border post, so decorative banners don't claim land. A nation is a banner
  design (base color and patterns); its name is the name on its most recent
  post. A post holds land out to `postReach` (48) blocks. On each map, land
  floods out from the posts over the map's own pixels: costs grow with distance
  (diagonals cost more), so neighbors meet halfway, and water pixels and
  unexplored ones stop the flood. Every map shows it, not a separate political
  map: borders in the nation's dark shade, the land inside as the faintest tint
  (blended for players with the mod, a sparse pattern for the rest). Maps are
  sent again whole when their territory changes. Posts whose banner is gone are
  taken down when their chunk is loaded. Claimed (drawn) borders, contested
  hatching and the political map are left for later.
- **"Entering …"** is worked out from the nearest post in reach, straight-line,
  every half second; "Leaving …" when there's none.
- **Cave maps** are made by using an empty map with at least 8 blocks of rock or
  soil overhead (leaves don't count); the map is named "Cave Map (Y …)". It shows
  a floor plan: the block at that level in its darkest shade if solid, else the
  floor below in a shade by how far it drops. It fills within `caveMapReach`
  blocks while its holder is within 12 blocks of its level. Locking and zooming
  keep the level. The Nether maps well this way.
- **The echo** is the planned amethyst resonance: an amethyst shard (not used
  up, with a cooldown) rings and fills the map in a circle at once, through
  rock; an echo shard reaches twice as far.
- **Spyglass:** zoom is scrolling while scoping (¼ to 2½ times vanilla's view);
  the map in the other hand shows in the top right corner while scoping. Far
  view and the spyglass enchantments are left for later.
- **Foresight** is a data-driven enchantment (helmets, one level, treasure,
  tradeable, on random loot), so vanilla players can join and carry it. The
  projection is client-side only, for players with the mod: ink within 40
  blocks as see-through squares on the ground (fainter for watered ink), labels
  written flat on the ground, pins and icons as pillars of light. Players with
  the mod are now sent a map's whole ink and territory for it. The vanilla
  display-entity fallback and the uninstall datapack are left for later. Cave
  maps project onto the surface for now.

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
- Watered-down ink is blended over the map by players with the mod, and shown
  as a fixed pattern of ink and map to players without it: everyone sees a tint,
  and nobody sees a hue that isn't there.
- A water cauldron waters a map down a step per dip; four dips wash full ink off.
- Framed-map painting is on by default and open to players without the mod.
- Paper is a blank canvas at the table, as an empty map is.
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
- Should seals be owned, so only the sealer, the map's maker or an operator can
  scrape them off? Planned: yes (`sealsAreOwned`), unlike vanilla wax, because a
  copy of a map reaches every other copy and a sign never does.
- Sketch cap (16 pages per book). Size: decided, the page's own writing area,
  as a vanilla book lays it out (see Sketches).

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
