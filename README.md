# NBT Edit

A client-side Fabric mod that turns the singleplayer world list into an NBT and JSON editor. Everything
happens inside the game window: no external tool like NBTExplorer, no separate window, no alt-tab.

Minecraft 26.2, Fabric Loader 0.19.5+, Java 25. The full Fabric API is not required: the mod only needs
`fabric-resource-loader-v0`, which exposes its language files to the game, and that module is bundled
inside the jar.

## What the mod adds

- An azure `{}` button next to the search field of the singleplayer world list, enabled once a world is
  selected.
- A file browser over the selected world folder: directories expand in place, so `level.dat`,
  `playerdata/`, `data/`, `stats/`, `advancements/` and `datapacks/` are all reachable from one tree.
- An NBT editor for `.dat`, `.dat_old`, `.nbt`, `.schematic` and `.mcstructure` files, with every tag
  type shown by a coloured badge next to its name and value.
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
- Undo and redo with `Ctrl+Z` and `Ctrl+Y`, for every change including JSON text mode, up to 100 steps
  per open file. Undoing back to the saved state clears the unsaved mark, and expanded branches stay
  open.
- Search above the tree that keeps only the entries whose name or value matches, together with the path
  leading to them.
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
2. Walk the world folder and open a file. Binary files that the mod cannot read, for example
   `region/*.mca`, stay greyed out; images are listed in blue.
3. Edit the tree: double click a value to change it, or use the buttons at the bottom.
4. Press **Save** or `Ctrl+S`. Leaving with unsaved changes asks first.

Keys and clicks:

| Action | Result |
| --- | --- |
| click on a container row | expands or collapses one level |
| `Shift` + click | expands or collapses the whole branch |
| double click on a value | opens the value editor inside the row |
| double click on a name | opens the rename editor inside the row |
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

- Region files (`.mca`) and chunk data.
- Live editing of a loaded world: entities, block entities, inventories in game.

## Build

```
./gradlew build
```

The jar is written to `build/libs`.

## License

MIT, see [LICENSE](LICENSE).
