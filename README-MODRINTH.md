<div align="center" style="text-align: center;">

<img src="https://raw.githubusercontent.com/fssniper/minecraft-nbtedit-mod/main/docs-assets/editor-leveldat.png" alt="The NBT Edit tree open on level.dat, with a coloured type badge on every tag and a row of icon buttons below" width="720">

# NBT Edit

**Open `level.dat` from the world list. Edit it. Save it. Without ever leaving the game window.**

<img src="https://img.shields.io/modrinth/dt/VoUDsLo2?style=for-the-badge&logo=modrinth&color=00AF5C&label=downloads" alt="Modrinth download count">
<img src="https://img.shields.io/badge/loader-Fabric-DBD0B4?style=for-the-badge" alt="Fabric loader">
<img src="https://img.shields.io/badge/side-client%20only-5865F2?style=for-the-badge" alt="Client-side only">
<img src="https://img.shields.io/badge/Fabric%20API-not%20required-8A8A8A?style=for-the-badge" alt="Fabric API is not required">
<a href="https://github.com/fssniper/minecraft-nbtedit-mod"><img src="https://img.shields.io/badge/source-GitHub-181717?style=for-the-badge&logo=github" alt="Source code on GitHub"></a>

</div>

---

Changing one value in `level.dat` normally means closing Minecraft, opening a file manager, and working
out *which* `saves` folder this world actually lives in. With several installations and a third-party
launcher that is rarely `.minecraft` — it is some instance directory buried three levels deep, and the
world you want looks exactly like the four next to it. Then NBTExplorer for the `.dat` files, a text
editor for the `.json` ones, a map viewer to find the right chunk, and back to the launcher.

**NBT Edit collapses all of that into one button.** Select the world you were already looking at, press
`{}`, and its folder opens as an editable tree inside the game. The mod knows where the save is, because
you just clicked on it.

<div align="center" style="text-align: center;">

| Select the world, press `{}` | The whole world folder, one screen |
| :-: | :-: |
| <img src="https://raw.githubusercontent.com/fssniper/minecraft-nbtedit-mod/main/docs-assets/worldlist-button.png" alt="The singleplayer world list with the azure curly brace button next to the search field" width="400"> | <img src="https://raw.githubusercontent.com/fssniper/minecraft-nbtedit-mod/main/docs-assets/file-browser.png" alt="The world folder open as a tree in the in-game file browser, level.dat and its backup painted in one colour" width="400"> |

<img src="https://raw.githubusercontent.com/fssniper/minecraft-nbtedit-mod/main/docs-assets/region-map.png" alt="A region folder drawn as a surface map, with a tooltip naming the chunk, block, size, save time and file under the cursor" width="720">

*Open a region file and the whole dimension is drawn as a map. Hover a chunk, press Open, edit it.*

</div>

## What you get

- **A `{}` button** in azure next to the search field of the singleplayer world list, live as soon as a
  world is selected.
- **The whole world folder as a tree.** Directories expand in place, so `level.dat`, `playerdata/`,
  `data/`, `stats/`, `advancements/` and `datapacks/` are all one click away. A file and its backups are
  painted in one colour, the backups in italics, so you always see which copy is which.
- **An NBT editor** for `.dat`, `.dat_old`, `.nbt`, `.schematic` and `.mcstructure`, every tag type
  marked by a coloured badge next to its name and value.
- **A map of the world.** Opening a region file draws the whole dimension the way an in-game map does:
  one pixel per block, in the game's own block colours, shaded by slope and water depth. Drag to pan,
  scroll to zoom, hover for the block, its chunk and its file, type coordinates straight from F3 to jump
  there. Switch between the `region`, `entities` and `poi` layers, or colour the chunks by how much data
  they hold or by when they were last saved. The **List** button shows the same chunks row by row.
- **Chunk editing.** Pick a chunk on the map, press **Open**, and it opens in the same tree as
  `level.dat`. Saving writes it back into its region file.
- **Search across the whole world.** <kbd>Ctrl</kbd> + <kbd>F</kbd> in the browser looks for a tag by
  name or value in every `.dat`, every `.json` and every chunk of every region, and a result opens its
  file or chunk with the match already selected.
- **A JSON editor** for `.json` and `.mcmeta` with the same tree, plus a **Tree** / **Text** switch that
  shows the document as plain text and parses it back.
- **Text files and images** too: `.txt`, `.log`, `.properties`, `.mcfunction` open in a text editor,
  and `.png` / `.jpg` in a viewer over a checkerboard, so transparency shows.
- **The world you are playing, read only.** Type `/nbtedit` in a singleplayer world, or bind a key to it,
  and the same browser, trees and map open with every write turned off.
- **A real editor's shortcuts:** edit in place with a double click, copy, cut and paste entries as SNBT
  or JSON (output of `/data get` pastes straight in), duplicate with <kbd>Ctrl</kbd> + <kbd>D</kbd>,
  undo and redo up to 100 steps, and copy the path of any entry, like `Data.Player.Inventory[3].id`.
- **Structural editing:** rename keys, delete any node except the root, and add entries through a panel
  with a type picker that checks the name while you type it.
- **Search inside a file** that keeps only the entries whose name or value matches, together with the
  path to them.
- **Compact icon buttons.** Every action is a small icon with its name in a tooltip, so each screen has a
  single row of buttons and the tree keeps the height.
- **Full keyboard control.** Arrows walk and expand the tree, <kbd>Enter</kbd> edits, <kbd>F2</kbd>,
  <kbd>Insert</kbd> and <kbd>Delete</kbd> rename, add and delete. The mouse is optional.
- **English and Russian**, out of the box.

## Safety nets

The mod writes straight into your world files, and a wrong value can make the game reset or throw away
that data when the world loads. These are the parts that stand between a typo and a lost world:

| | |
| - | - |
| 🗂️ **Backups on demand** | Off by default. The **Backups** control in the corner of the browser keeps 1, 3, 5 or 10 timestamped `.bak` copies next to every file you save or delete, and removes the older ones |
| ⚛️ **Atomic write** | New data goes to a temp file and is moved into place, so an interrupted write can never leave half a world file |
| 🧱 **Verified regions** | A region file is rebuilt with the untouched chunks byte for byte and read back before it replaces the old one |
| 🗜️ **Compression preserved** | Gzipped files stay gzipped, uncompressed files stay uncompressed |
| 🔒 **Session lock** | Taken the same way vanilla's **Edit** screen takes it, so a running world can't be edited underneath itself |
| ↩️ **Undo** | Up to 100 steps per open file, and undoing back to the saved state clears the unsaved mark |
| ⚠️ **Confirm and warn** | Leaving with unsaved changes asks first, deleting a file asks first, and the first chunk of a session opens behind a warning |

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) **0.19.5+** and **Java 25**.
2. Drop the JAR into your `mods` folder.
3. Open the singleplayer world list. The `{}` button is already there.

No Fabric API: the three small Fabric modules the mod uses are bundled inside the JAR.

## Using it

1. Select a world and press `{}`.
2. Walk the folder and open a file. Region files are listed in green and images in blue, anything the
   mod cannot read is greyed out.
3. Double click a value to change it, or use the buttons at the bottom.
4. Press **Save** or <kbd>Ctrl</kbd> + <kbd>S</kbd>.

To look inside the world you are playing, type `/nbtedit` in the chat. The keybind for the same thing is
under **Controls** → **Miscellaneous** and starts unbound. Keep in mind it shows the world as it is
**on disk**: the game saves roughly every five minutes, when a chunk unloads, or when you leave to the
menu, so the block you placed a second ago may not be there yet.

<div align="center" style="text-align: center;">

| `/nbtedit` in a singleplayer world | opens it read only |
| :-: | :-: |
| <img src="https://raw.githubusercontent.com/fssniper/minecraft-nbtedit-mod/main/docs-assets/ingame-command.png" alt="The chat with /nbtedit typed and the command suggested above it" width="400"> | <img src="https://raw.githubusercontent.com/fssniper/minecraft-nbtedit-mod/main/docs-assets/ingame-readonly-leveldat.png" alt="level.dat of the running world open over the game, titled read only" width="400"> |

</div>

| Action | Result |
| - | - |
| click on a container row | expands or collapses one level |
| <kbd>Shift</kbd> + click | expands or collapses the whole branch |
| double click on a value or a name | opens the editor inside the row |
| <kbd>Enter</kbd> / <kbd>Esc</kbd> | applies the inline edit / drops it |
| <kbd>F2</kbd> / <kbd>Insert</kbd> / <kbd>Delete</kbd> | renames / adds / deletes an entry |
| <kbd>Ctrl</kbd> + <kbd>C</kbd> / <kbd>X</kbd> / <kbd>V</kbd> | copies / cuts / pastes an entry as SNBT or JSON |
| <kbd>Ctrl</kbd> + <kbd>D</kbd> | duplicates the selected entry |
| <kbd>Ctrl</kbd> + <kbd>Shift</kbd> + <kbd>C</kbd> | copies the path of the selected entry |
| <kbd>Ctrl</kbd> + <kbd>Z</kbd> / <kbd>Y</kbd> | undoes / redoes a change |
| <kbd>Ctrl</kbd> + <kbd>F</kbd> in the browser | searches the whole world |
| <kbd>Ctrl</kbd> + <kbd>S</kbd> | saves the file |

## Compatibility

- **Minecraft 26.3**, Fabric Loader 0.19.5+, Java 25.
- **Client-side only.** Nothing to install on a server, and it stays out of the way while you play on one.
- **Fabric API is not required.**

## FAQ

<details>
<summary><strong>Do I still need NBTExplorer?</strong></summary>

Not for world files. NBT, JSON, text, images, region files and single chunks all open directly in the
game. The one thing the mod does not do is add a chunk to a region or remove one.

</details>

<details>
<summary><strong>Which tags can I actually edit?</strong></summary>

All of them: byte, short, int, long, float, double, string, list, compound, byte array, int array and
long array. Arrays can be edited as a comma separated list of numbers or element by element in the tree.

JSON covers objects, arrays, strings, numbers, booleans and null. Member order is kept, except that a
renamed member moves to the end of its object.

</details>

<details>
<summary><strong>Can I edit a world while it is open?</strong></summary>

No, and that is deliberate. The mod takes the same session lock the vanilla **Edit** screen takes, so a
running world can never be modified underneath itself. You can still look at it: `/nbtedit` or the
keybind opens the running world read only.

</details>

<details>
<summary><strong>What happens if I break a file?</strong></summary>

Turn backups on before editing anything valuable: with the **Backups** control set to anything but
**Off**, every save leaves a `<name>.<timestamp>.bak` copy of the original in the same directory. Rename
it back and the file is exactly as it was. Until you save, <kbd>Ctrl</kbd> + <kbd>Z</kbd> undoes the
last 100 changes.

The game also keeps its own previous copy of `level.dat` as `level.dat_old`, and of every player file as
`<uuid>.dat_old`. The browser shows them next to the originals, painted in the same colour.

</details>

<details>
<summary><strong>Is it safe on servers with anti-cheat?</strong></summary>

The mod only reads and writes your own singleplayer saves on your own machine. `/nbtedit` is a client
command that never reaches the server and refuses to work outside singleplayer. Nothing is sent to any
server and there is no gameplay advantage.

</details>

<details>
<summary><strong>Can I edit region files or chunks?</strong></summary>

Yes. Open a region file, pick a chunk on the map or in the list, press **Open**, edit, save. Every save
rewrites the whole region, and a chunk the game cannot read is thrown away and generated again, so turn
backups on first. Chunks can be edited, but not added to a region or removed from it.

</details>

<details>
<summary><strong>Will my JSON formatting survive?</strong></summary>

JSON is written back pretty printed, so the original indentation and any comments are lost. NBT files
keep their exact structure and compression.

</details>

---

<div align="center" style="text-align: center;">

**Found a file that will not open, or a tag that will not save?**
[Open an issue](https://github.com/fssniper/minecraft-nbtedit-mod/issues) · [Source](https://github.com/fssniper/minecraft-nbtedit-mod)

</div>
