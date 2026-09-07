# NBT Edit

A client-side Fabric mod that turns the singleplayer world list into an NBT and JSON editor. Everything
happens inside the game window: no external tool like NBTExplorer, no separate window, no alt-tab.

Minecraft 26.2, Fabric Loader 0.19.5+, Java 25. Fabric API is not required.

## What the mod adds

- An azure `{}` button next to the search field of the singleplayer world list, enabled once a world is
  selected.
- A file browser over the selected world folder: directories expand in place, so `level.dat`,
  `playerdata/`, `data/`, `stats/`, `advancements/` and `datapacks/` are all reachable from one tree.
- An NBT editor for `.dat`, `.dat_old`, `.nbt`, `.schematic` and `.mcstructure` files, with every tag
  type shown by a coloured badge next to its name and value.
- A JSON editor for `.json` and `.mcmeta` files with the same tree, plus a **Tree** / **Text** switch
  that shows the whole document as plain text and parses it back.
- Value editing in three ways: a double click opens an editor right inside the row, the **Edit value**
  button opens it in a separate screen, and JSON text mode edits the document as a whole.
- Structural editing: rename keys, add entries with a type picker, delete any node except the root.
- Search above the tree that keeps only the entries whose name or value matches, together with the path
  leading to them.
- Branch controls: a click expands one level, `Shift` + click expands or collapses the whole branch,
  the wheel scrolls three rows per notch.
- A session lock on the edited world, taken the same way the vanilla **Edit** screen takes it, so a
  world cannot be edited while it is running.
- Automatic backups: every save copies the original file next to itself before writing.
- English and Russian translations.

## Usage

1. Select a world in the singleplayer list and press the `{}` button.
2. Walk the world folder and open a file. Files that are neither NBT nor JSON, for example
   `region/*.mca`, are greyed out.
3. Edit the tree: double click a value to change it, or use the buttons at the bottom.
4. Press **Save** or `Ctrl+S`. Leaving with unsaved changes asks first.

Keys and clicks:

| Action | Result |
| --- | --- |
| click on a container row | expands or collapses one level |
| `Shift` + click | expands or collapses the whole branch |
| double click on a value | opens the editor inside the row |
| `Enter` | applies the inline edit, keeps it open with red text if the value does not parse |
| `Esc` | drops the inline edit |
| `Ctrl+S` | saves the file |

## Saving

Every save copies the original to `<name>.<timestamp>.bak` in the same directory, writes the new data to
a temporary file and moves it into place, so an interrupted write cannot leave a half written world file.
NBT compression is preserved: gzipped files stay gzipped, uncompressed files stay uncompressed. JSON is
written back pretty printed, so the original formatting and any comments are lost.

## Supported types

NBT: byte, short, int, long, float, double, string, list, compound, byte array, int array, long array.
Arrays are edited either as a comma separated list of numbers or element by element in the tree.

JSON: object, array, string, number, boolean, null. Member order is kept, except that a renamed member
moves to the end of its object.

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
