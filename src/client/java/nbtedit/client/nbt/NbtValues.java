package nbtedit.client.nbt;

import java.util.Locale;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.jspecify.annotations.Nullable;

public final class NbtValues {
	public static final byte[] CREATABLE_TYPES = {
		Tag.TAG_BYTE,
		Tag.TAG_SHORT,
		Tag.TAG_INT,
		Tag.TAG_LONG,
		Tag.TAG_FLOAT,
		Tag.TAG_DOUBLE,
		Tag.TAG_STRING,
		Tag.TAG_LIST,
		Tag.TAG_COMPOUND,
		Tag.TAG_BYTE_ARRAY,
		Tag.TAG_INT_ARRAY,
		Tag.TAG_LONG_ARRAY
	};

	private static final int MAX_INLINE_ARRAY = 4096;
	private static final int COLOR_NUMBER = 0xFFFFD966;
	private static final int COLOR_STRING = 0xFFA6E22E;
	private static final int COLOR_COMPOUND = 0xFFFFFFFF;
	private static final int COLOR_COLLECTION = 0xFF9AA0FF;

	private NbtValues() {
	}

	public static String typeName(byte id) {
		return switch (id) {
			case Tag.TAG_BYTE -> "Byte";
			case Tag.TAG_SHORT -> "Short";
			case Tag.TAG_INT -> "Int";
			case Tag.TAG_LONG -> "Long";
			case Tag.TAG_FLOAT -> "Float";
			case Tag.TAG_DOUBLE -> "Double";
			case Tag.TAG_STRING -> "String";
			case Tag.TAG_LIST -> "List";
			case Tag.TAG_COMPOUND -> "Compound";
			case Tag.TAG_BYTE_ARRAY -> "Byte[]";
			case Tag.TAG_INT_ARRAY -> "Int[]";
			case Tag.TAG_LONG_ARRAY -> "Long[]";
			default -> "?";
		};
	}

	public static String badge(byte id) {
		return switch (id) {
			case Tag.TAG_BYTE -> "b";
			case Tag.TAG_SHORT -> "s";
			case Tag.TAG_INT -> "i";
			case Tag.TAG_LONG -> "l";
			case Tag.TAG_FLOAT -> "f";
			case Tag.TAG_DOUBLE -> "d";
			case Tag.TAG_STRING -> "\"\"";
			case Tag.TAG_LIST -> "[]";
			case Tag.TAG_COMPOUND -> "{}";
			case Tag.TAG_BYTE_ARRAY -> "[b]";
			case Tag.TAG_INT_ARRAY -> "[i]";
			case Tag.TAG_LONG_ARRAY -> "[l]";
			default -> "?";
		};
	}

	public static int color(byte id) {
		return switch (id) {
			case Tag.TAG_STRING -> COLOR_STRING;
			case Tag.TAG_COMPOUND -> COLOR_COMPOUND;
			case Tag.TAG_LIST, Tag.TAG_BYTE_ARRAY, Tag.TAG_INT_ARRAY, Tag.TAG_LONG_ARRAY -> COLOR_COLLECTION;
			default -> COLOR_NUMBER;
		};
	}

	public static boolean isContainer(Tag tag) {
		return tag instanceof CompoundTag || tag instanceof CollectionTag;
	}

	public static boolean hasEditableText(Tag tag) {
		if (tag instanceof CompoundTag || tag instanceof ListTag) {
			return false;
		}

		return !(tag instanceof CollectionTag collection) || collection.size() <= MAX_INLINE_ARRAY;
	}

	public static String summary(Tag tag) {
		if (tag instanceof CompoundTag compound) {
			return compound.size() + " entries";
		}

		if (tag instanceof CollectionTag collection) {
			return collection.size() + " entries";
		}

		return text(tag);
	}

	public static String text(Tag tag) {
		if (tag instanceof StringTag string) {
			return string.value();
		}

		if (tag instanceof ByteArrayTag array) {
			StringBuilder builder = new StringBuilder();
			for (byte value : array.getAsByteArray()) {
				appendSeparated(builder, Byte.toString(value));
			}

			return builder.toString();
		}

		if (tag instanceof IntArrayTag array) {
			StringBuilder builder = new StringBuilder();
			for (int value : array.getAsIntArray()) {
				appendSeparated(builder, Integer.toString(value));
			}

			return builder.toString();
		}

		if (tag instanceof LongArrayTag array) {
			StringBuilder builder = new StringBuilder();
			for (long value : array.getAsLongArray()) {
				appendSeparated(builder, Long.toString(value));
			}

			return builder.toString();
		}

		return tag.asNumber().map(Object::toString).orElseGet(tag::toString);
	}

	public static @Nullable Tag parse(byte id, String input) {
		String trimmed = input.trim();

		try {
			return switch (id) {
				case Tag.TAG_BYTE -> ByteTag.valueOf(Byte.parseByte(trimmed));
				case Tag.TAG_SHORT -> ShortTag.valueOf(Short.parseShort(trimmed));
				case Tag.TAG_INT -> IntTag.valueOf(Integer.parseInt(trimmed));
				case Tag.TAG_LONG -> LongTag.valueOf(Long.parseLong(trimmed));
				case Tag.TAG_FLOAT -> FloatTag.valueOf(Float.parseFloat(trimmed));
				case Tag.TAG_DOUBLE -> DoubleTag.valueOf(Double.parseDouble(trimmed));
				case Tag.TAG_STRING -> StringTag.valueOf(input);
				case Tag.TAG_LIST -> new ListTag();
				case Tag.TAG_COMPOUND -> new CompoundTag();
				case Tag.TAG_BYTE_ARRAY -> parseByteArray(trimmed);
				case Tag.TAG_INT_ARRAY -> parseIntArray(trimmed);
				case Tag.TAG_LONG_ARRAY -> parseLongArray(trimmed);
				default -> null;
			};
		} catch (NumberFormatException e) {
			return null;
		}
	}

	public static Tag defaultTag(byte id) {
		Tag tag = parse(id, "0");
		return tag == null ? StringTag.valueOf("") : tag;
	}

	private static ByteArrayTag parseByteArray(String input) {
		String[] parts = split(input);
		byte[] values = new byte[parts.length];
		for (int i = 0; i < parts.length; i++) {
			values[i] = Byte.parseByte(parts[i]);
		}

		return new ByteArrayTag(values);
	}

	private static IntArrayTag parseIntArray(String input) {
		String[] parts = split(input);
		int[] values = new int[parts.length];
		for (int i = 0; i < parts.length; i++) {
			values[i] = Integer.parseInt(parts[i]);
		}

		return new IntArrayTag(values);
	}

	private static LongArrayTag parseLongArray(String input) {
		String[] parts = split(input);
		long[] values = new long[parts.length];
		for (int i = 0; i < parts.length; i++) {
			values[i] = Long.parseLong(parts[i]);
		}

		return new LongArrayTag(values);
	}

	private static String[] split(String input) {
		if (input.isEmpty()) {
			return new String[0];
		}

		return input.toLowerCase(Locale.ROOT).split("[,;\s]+");
	}

	private static void appendSeparated(StringBuilder builder, String value) {
		if (!builder.isEmpty()) {
			builder.append(", ");
		}

		builder.append(value);
	}
}
