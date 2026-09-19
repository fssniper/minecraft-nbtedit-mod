package nbtedit.nbt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import nbtedit.client.nbt.NbtValues;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

class NbtValuesTest {
	@Test
	void numbersAreParsedByType() {
		assertEquals("7", String.valueOf(NbtValues.parse(Tag.TAG_BYTE, "7").asNumber().orElseThrow()));
		assertEquals("7", String.valueOf(NbtValues.parse(Tag.TAG_SHORT, "7").asNumber().orElseThrow()));
		assertEquals("7", String.valueOf(NbtValues.parse(Tag.TAG_INT, "7").asNumber().orElseThrow()));
		assertEquals("7", String.valueOf(NbtValues.parse(Tag.TAG_LONG, "7").asNumber().orElseThrow()));
	}

	@Test
	void surroundingSpaceIsIgnoredForNumbers() {
		assertEquals(IntTag.valueOf(42), NbtValues.parse(Tag.TAG_INT, "  42  "));
	}

	@Test
	void stringsKeepTheirSpace() {
		assertEquals(StringTag.valueOf("  a b  "), NbtValues.parse(Tag.TAG_STRING, "  a b  "));
	}

	@Test
	void valuesOutOfRangeAreRejected() {
		assertNull(NbtValues.parse(Tag.TAG_BYTE, "200"));
		assertNull(NbtValues.parse(Tag.TAG_INT, "5000000000"));
		assertNull(NbtValues.parse(Tag.TAG_INT, "1.5"));
		assertNull(NbtValues.parse(Tag.TAG_LONG, "ten"));
	}

	@Test
	void emptyInputIsRejectedForNumbers() {
		assertNull(NbtValues.parse(Tag.TAG_INT, ""));
		assertNull(NbtValues.parse(Tag.TAG_DOUBLE, "   "));
	}

	@Test
	void containersAreCreatedEmpty() {
		assertEquals(0, assertInstanceOf(ListTag.class, NbtValues.parse(Tag.TAG_LIST, "anything")).size());
		assertTrue(assertInstanceOf(CompoundTag.class, NbtValues.parse(Tag.TAG_COMPOUND, "anything")).isEmpty());
	}

	@Test
	void arraysAcceptSeparatedNumbers() {
		assertArrayEquals(new byte[]{1, 2, 3}, assertInstanceOf(ByteArrayTag.class, NbtValues.parse(Tag.TAG_BYTE_ARRAY, "1, 2, 3")).getAsByteArray());
		assertEquals(3, assertInstanceOf(IntArrayTag.class, NbtValues.parse(Tag.TAG_INT_ARRAY, "1 2 3")).size());
		assertEquals(2, assertInstanceOf(LongArrayTag.class, NbtValues.parse(Tag.TAG_LONG_ARRAY, "1, 2")).size());
	}

	@Test
	void anEmptyArrayIsValid() {
		assertEquals(0, assertInstanceOf(IntArrayTag.class, NbtValues.parse(Tag.TAG_INT_ARRAY, "")).size());
	}

	@Test
	void oneBadElementRejectsTheWholeArray() {
		assertNull(NbtValues.parse(Tag.TAG_INT_ARRAY, "1, two, 3"));
	}

	@Test
	void anUnknownTypeIsRejected() {
		assertNull(NbtValues.parse(Tag.TAG_END, "0"));
	}

	@Test
	void everyCreatableTypeHasADefaultThatParsesBack() {
		for (byte id : NbtValues.CREATABLE_TYPES) {
			Tag tag = NbtValues.defaultTag(id);
			assertEquals(id, tag.getId(), NbtValues.typeName(id));
			if (NbtValues.hasEditableText(tag)) {
				assertEquals(tag, NbtValues.parse(id, NbtValues.text(tag)), NbtValues.typeName(id));
			}
		}
	}

	@Test
	void containersHaveNoEditableText() {
		assertFalse(NbtValues.hasEditableText(new CompoundTag()));
		assertFalse(NbtValues.hasEditableText(new ListTag()));
		assertTrue(NbtValues.hasEditableText(StringTag.valueOf("text")));
		assertTrue(NbtValues.hasEditableText(IntTag.valueOf(1)));
	}

	@Test
	void arraysRoundTripThroughTheirText() {
		Tag tag = NbtValues.parse(Tag.TAG_INT_ARRAY, "-1, 0, 1");
		assertEquals(tag, NbtValues.parse(Tag.TAG_INT_ARRAY, NbtValues.text(tag)));
	}

	private static void assertArrayEquals(byte[] expected, byte[] actual) {
		org.junit.jupiter.api.Assertions.assertArrayEquals(expected, actual);
	}
}
