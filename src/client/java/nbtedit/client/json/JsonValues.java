package nbtedit.client.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

public final class JsonValues {
	private static final int COLOR_NUMBER = 0xFFFFD966;
	private static final int COLOR_STRING = 0xFFA6E22E;
	private static final int COLOR_OBJECT = 0xFFFFFFFF;
	private static final int COLOR_ARRAY = 0xFF9AA0FF;
	private static final int COLOR_LITERAL = 0xFFFF9E64;

	private JsonValues() {
	}

	public enum Kind {
		OBJECT,
		ARRAY,
		STRING,
		NUMBER,
		BOOLEAN,
		NULL
	}

	public static Kind kindOf(JsonElement element) {
		if (element.isJsonObject()) {
			return Kind.OBJECT;
		}

		if (element.isJsonArray()) {
			return Kind.ARRAY;
		}

		if (element.isJsonNull()) {
			return Kind.NULL;
		}

		JsonPrimitive primitive = element.getAsJsonPrimitive();
		if (primitive.isString()) {
			return Kind.STRING;
		}

		return primitive.isBoolean() ? Kind.BOOLEAN : Kind.NUMBER;
	}

	public static String typeName(Kind kind) {
		return switch (kind) {
			case OBJECT -> "Object";
			case ARRAY -> "Array";
			case STRING -> "String";
			case NUMBER -> "Number";
			case BOOLEAN -> "Boolean";
			case NULL -> "Null";
		};
	}

	public static String badge(Kind kind) {
		return switch (kind) {
			case OBJECT -> "{}";
			case ARRAY -> "[]";
			case STRING -> "\"\"";
			case NUMBER -> "#";
			case BOOLEAN -> "b";
			case NULL -> "~";
		};
	}

	public static int color(Kind kind) {
		return switch (kind) {
			case OBJECT -> COLOR_OBJECT;
			case ARRAY -> COLOR_ARRAY;
			case STRING -> COLOR_STRING;
			case NUMBER -> COLOR_NUMBER;
			case BOOLEAN, NULL -> COLOR_LITERAL;
		};
	}

	public static boolean isContainer(JsonElement element) {
		return element.isJsonObject() || element.isJsonArray();
	}

	public static boolean hasEditableText(JsonElement element) {
		return element.isJsonPrimitive();
	}

	public static String summary(JsonElement element) {
		if (element instanceof JsonObject object) {
			return object.size() + " entries";
		}

		if (element instanceof JsonArray array) {
			return array.size() + " entries";
		}

		return text(element);
	}

	public static String text(JsonElement element) {
		if (element.isJsonNull()) {
			return "null";
		}

		if (element.isJsonPrimitive()) {
			return element.getAsJsonPrimitive().getAsString();
		}

		return element.toString();
	}

	public static @Nullable JsonElement parse(Kind kind, String input) {
		String trimmed = input.trim();

		return switch (kind) {
			case OBJECT -> new JsonObject();
			case ARRAY -> new JsonArray();
			case STRING -> new JsonPrimitive(input);
			case NUMBER -> parseNumber(trimmed);
			case BOOLEAN -> parseBoolean(trimmed);
			case NULL -> JsonNull.INSTANCE;
		};
	}

	private static @Nullable JsonElement parseNumber(String input) {
		try {
			return new JsonPrimitive(Long.parseLong(input));
		} catch (NumberFormatException ignored) {
		}

		try {
			return new JsonPrimitive(Double.parseDouble(input));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static @Nullable JsonElement parseBoolean(String input) {
		String value = input.toLowerCase(Locale.ROOT);
		if (value.equals("true")) {
			return new JsonPrimitive(Boolean.TRUE);
		}

		return value.equals("false") ? new JsonPrimitive(Boolean.FALSE) : null;
	}
}
