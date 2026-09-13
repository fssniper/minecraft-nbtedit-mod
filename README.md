# NBT Edit

A client-side Fabric mod that turns the singleplayer world list into an NBT and JSON editor. Everything
happens inside the game window: no external tool like NBTExplorer, no separate window, no alt-tab.

Minecraft 26.2, Fabric Loader 0.19.5+, Java 25. The full Fabric API is not required: the mod only needs
`fabric-resource-loader-v0`, which exposes its language files to the game, `fabric-command-api-v2` for
the `/nbtedit` command and `fabric-key-mapping-api-v1` for its keybind. All three are bundled inside the
jar together with what they depend on.

## What the mod adds

- An azure `{}` button next to the search field of the singleplayer world list, enabled once a world is
  selected.
- A read only view of the world you are playing, opened with `/nbtedit` from inside the game. The same
  browser, the same trees, the same surface map, with every write path off: no save, no add, no delete,
  no rename, no backups. What it shows is the world as it is **on disk**, which is the last save, not
  the block you just placed: the game keeps the world in memory and writes it out roughly every five
  minutes, when a chunk unloads, or when you leave to the menu. There is a keybind for the same thing
  under **Controls**, **Miscellaneous**, bound to nothing until you choose a key. Both work only in a
  singleplayer world.
- A file and its backups are tied together in the browser by colour: whenever a folder holds more than
  one file with the same name before the backup suffix, the whole group is painted in one colour picked
  from the name, and the backups are written in italics. The game's own `.dat_old` counts as a backup of
  the `.dat` next to it. Files without a backup keep the ordinary colours that say what kind they are.
- A file browser over the selected world folder: directories expand in place, so `level.dat`,
  `playerdata/`, `data/`, `stats/`, `advancements/` and `datapacks/` are all reachable from one tree.
- An NBT editor for `.dat`, `.dat_old`, `.nbt`, `.schematic` and `.mcstructure` files, with every tag
  type shown by a coloured badge next to its name and value.
- A surface map for `region/*.mca` and the other `.mca` folders: opening a region file draws the whole
  dimension the way an in-game map does, one pixel per block, in the colours the game itself paints
  blocks with, shaded by slope and by water depth. Drag to pan, scroll to zoom, hover for the block
  under the cursor, the chunk it belongs to and the file it lives in, click and press **Open** to read its NBT. A field above the
  map jumps to block coordinates straight from F3, the layer switch moves between `region`, `entities`
  and `poi`, and the colour switch replaces the terrain with how much data a chunk holds or when it was
  last saved. The map opens at one pixel per block, with chunk and region borders drawn over it; the
  terrain is drawn region by region in the background, so the map is usable while it fills in. Zoomed
  out it switches to a smaller copy of the same terrain, four pixels per chunk, which keeps the shape
  of a river or a forest readable instead of averaging a whole chunk into one colour.
- A chunk list behind the **List** button, for the same region file row by row when the keyboard is
  faster than the map.
- Chunk editing: a chunk opens in the same tree as `level.dat` and saves back into its region file. The
  region is rebuilt whole, the untouched chunks keep the bytes they were stored with, the new file is
  read back before it replaces the old one, and a copy of the region is kept next to it on every save,
  even with backups turned off. A warning names the cost before the first chunk of a session is opened:
  a chunk the game cannot read is thrown away and generated again.
- A JSON editor for `.json` and `.mcmeta` files with the same tree, plus a **Tree** / **Text** switch
  that shows the whole document as plain text and parses it back.
- A text editor for any file that reads like text: `.txt`, `.log`, `.properties`, `.mcfunction` and
  anything else without a known extension, recognised by sniffing the content rather than the name.
- An image viewer for `.png`, `.jpg` and `.jpeg`, scaled to the screen over a checkerboard so that
  transparency is visible; the world `icon.png` is the obvious case.
- File deletion from the browser, with a confirmation and, while backups are on, a copy kept next to
  the file so a wrong click can be undone.
- Editing in place: a double click opens an editor right inside the row, on the value or on the name,
  `Enter` applies it. The **Edit value** button still opens a separate screen, and JSON text mode edits
  the document as a whole.
- Structural editing: rename keys, delete any node except the root, and add entries through a panel
  that opens over the tree: it shows the path it adds into, picks the type from a dropdown list, checks
  the name while it is typed and drops straight into editing the new value.
- Clipboard: `Ctrl+C` puts the selected entry into the system clipboard as SNBT or JSON, `Ctrl+X` does
  the same and removes it, `Ctrl+V` adds the clipboard into the selected container or right after the
  selected list element, so an item stack moves between two player files in two keys, and SNBT from
  `/data get` can be pasted straight in. `Ctrl+D` duplicates the selected entry in place.
- Hovering a row shows its full path, its exact type and the value untruncated; `Ctrl+Shift+C` puts the
  path into the clipboard, ready to paste into a `/data` command or a bug report.
- Undo and redo with `Ctrl+Z` and `Ctrl+Y`, for every change including JSON text mode, up to 100 steps
  per open file. Undoing back to the saved state clears the unsaved mark, and expanded branches stay
  open.
- Search above the tree that keeps only the entries whose name or value matches, together with the path
  leading to them.
- Search across the whole world behind the **Search** button of the file browser or `Ctrl+F`: every tag
  in every `.dat`, `.json` and, unless the **Chunks** switch is off, every chunk of every region, read
  on background threads while the results fill in. A result opens its file or chunk with the matching
  entry expanded and selected. The scan stops at five hundred matches.
- Branch controls: a click expands one level, `Shift` + click expands or collapses the whole branch,
  the wheel scrolls three rows per notch.
- Full keyboard control: arrows walk and expand the tree, `Enter` edits in place, `F2`, `Insert` and
  `Delete` map to rename, add and delete, `Ctrl+S` saves.
- A session lock on the edited world, taken the same way the vanilla **Edit** screen takes it, so a
  world cannot be edited while it is running.
- Optional backups with rotation, off by default: every save copies the original next to itself and
  keeps only the newest copies; the count is set in the corner of the file browser.
- A red warning banner at the top of the file browser, with a scrolling note on what a broken edit can
  do to a world. It stays until it is closed with the cross on its right.
- English and Russian translations.

## Usage

1. Select a world in the singleplayer list and press the `{}` button.
2. Walk the world folder and open a file. Region files are listed in green and images in blue;
   binary files that the mod cannot read stay greyed out.
3. Edit the tree: double click a value to change it, or use the buttons at the bottom.
4. Press **Save** or `Ctrl+S`. Leaving with unsaved changes asks first.

Keys and clicks:

| Action | Result |
| --- | --- |
| click on a container row | expands or collapses one level |
| `Shift` + click | expands or collapses the whole branch |
| double click on a value | opens the value editor inside the row |
| double click on a name | opens the rename editor inside the row |
| `/nbtedit` in the chat | opens the world you are playing, read only |
| the **Open the world read only** keybind | the same, once you bind a key to it |
| `Ctrl+F` in the browser | searches the whole world |
| `Ctrl+S` | saves the file |
| `Delete` in the browser | deletes the selected file after a confirmation |

The interface is fully usable without a mouse. The tree takes focus when a screen opens, `Tab` cycles
through the search field and the buttons, and `Esc` closes the screen.

| Key | Result |
| --- | --- |
| `Up` / `Down` | moves through the rows, scrolling the list along |
| `Right` | expands a collapsed row, or steps into its first child |
| `Left` | collapses an expanded row, or steps out to its parent |
| `Shift` + `Left` / `Right` | collapses or expands the whole branch |
| `Enter` or `Space` | toggles a container, opens a file in the browser, starts editing a value in place |
| `Enter` in an editor | applies the value, keeps it open with red text if it does not parse |
| `Esc` in an editor | drops the edit and returns focus to the tree |
| `F2` | renames the selected entry, also in the row |
| `Insert` | opens the add panel for the selected container |
| `Delete` | deletes the selected entry |
| `Ctrl+C` | copies the selected entry into the clipboard as SNBT or JSON |
| `Ctrl+Shift+C` | copies the path of the selected entry, such as `Data.Player.Inventory[3].id` |
| `Ctrl+Z` | undoes the last change |
| `Ctrl+Y` or `Ctrl+Shift+Z` | redoes the undone change |
| `Ctrl+X` | copies the selected entry and deletes it |
| `Ctrl+V` | pastes the clipboard into the selected container, or right after the selected element |
| `Ctrl+D` | duplicates the selected entry right after itself, opening the rename editor in a compound |
| `Ctrl+S` | saves the file |

## Saving

Every save writes the new data to a temporary file and moves it into place, so an interrupted write
cannot leave a half written world file.

Backups are off by default. The **Backups** control in the top right corner of the file browser turns
them on and sets how many copies of one file are kept: 1, 3, 5, 10 or off. While they are on, every save
first copies the original to `<name>.<timestamp>.bak` in the same directory, and older copies of the
same file are deleted, so the folder does not fill up. Deletion is covered the same way: a deleted file
is copied to a backup first. Turning backups off stops new copies from being made and leaves the
existing ones alone. The setting is stored in `config/nbtedit.json`.

### Restoring from a backup

1. Leave the world and close the editor.
2. Open the `.bak` file in the browser and check that it holds what you expect: backups open like any
   other file.
3. In the world folder, delete or rename the broken file, then rename the backup back to the original
   name by removing the `.<timestamp>.bak` part, for example `level.dat.20260911-181500.bak` becomes
   `level.dat`.

Independently of the mod, the game keeps its own previous copy of `level.dat` as `level.dat_old` and of
every player file as `playerdata/<uuid>.dat_old`, written each time the game saves. They restore the
same way, and when a player file cannot be read at all, the game falls back to its `.dat_old` on its
own.
NBT compression is preserved: gzipped files stay gzipped, uncompressed files stay uncompressed. JSON is
written back pretty printed, so the original formatting and any comments are lost.

## Supported types

NBT: byte, short, int, long, float, double, string, list, compound, byte array, int array, long array.
Arrays are edited either as a comma separated list of numbers or element by element in the tree.

JSON: object, array, string, number, boolean, null. Member order is kept, except that a renamed member
moves to the end of its object.

Text files are read and written as UTF-8, and files above 2 MB are not opened in the editor. A file in
another encoding will lose the characters that UTF-8 cannot represent, so treat the backup as the
original in that case.

## Not covered

- Live editing of a loaded world: entities, block entities, inventories in game.
- Adding a chunk to a region or removing one: only chunks that are already stored can be edited.

## Build

```
./gradlew build
```

The jar is written to `build/libs`.

```
./gradlew test
```

Runs the tests over the parts where a mistake is silent: the region reader, the chunk surface
sampler, backup rotation, value parsing and the tree filter.

## License

MIT, see [LICENSE](LICENSE).
