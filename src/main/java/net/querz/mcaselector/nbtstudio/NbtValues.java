package net.querz.mcaselector.nbtstudio;

import net.querz.nbt.ByteArrayTag;
import net.querz.nbt.ByteTag;
import net.querz.nbt.CompoundTag;
import net.querz.nbt.DoubleTag;
import net.querz.nbt.FloatTag;
import net.querz.nbt.IntArrayTag;
import net.querz.nbt.IntTag;
import net.querz.nbt.ListTag;
import net.querz.nbt.LongArrayTag;
import net.querz.nbt.LongTag;
import net.querz.nbt.NumberTag;
import net.querz.nbt.ShortTag;
import net.querz.nbt.StringTag;
import net.querz.nbt.Tag;
import java.util.ArrayList;
import java.util.List;

/**
 * Helpers to convert NBT tags to searchable strings and back.
 */
public final class NbtValues {

	private NbtValues() {
	}

	/**
	 * @return all strings that represent the given tag value. Arrays are expanded into their elements,
	 *         containers and arrays additionally report the summary NBT Studio shows in its tree.
	 */
	public static List<String> candidates(Tag tag) {
		if (tag == null) {
			return List.of();
		}
		switch (tag.getType()) {
			case STRING:
				return List.of(((StringTag) tag).getValue());
			case BYTE, SHORT, INT, LONG, FLOAT, DOUBLE:
				List<String> result = new ArrayList<>(3);
				String number = numberString(tag);
				result.add(number);
				if (tag.getType() == Tag.Type.FLOAT || tag.getType() == Tag.Type.DOUBLE) {
					// 1.0 is also shown as 1 in NBT Studio, so both spellings have to match
					String trimmed = trimTrailingZero(number);
					if (!trimmed.equals(number)) {
						result.add(trimmed);
					}
				}
				if (tag.getType() == Tag.Type.BYTE) {
					byte b = ((NumberTag) tag).asNumber().byteValue();
					if (b == 0 || b == 1) {
						result.add(b == 1 ? "true" : "false");
					}
				}
				return result;
			case BYTE_ARRAY: {
				byte[] values = ((ByteArrayTag) tag).getValue();
				List<String> strings = new ArrayList<>(values.length + 1);
				for (byte value : values) {
					strings.add(Byte.toString(value));
				}
				// the summary NBT Studio shows in its tree is searchable as well
				strings.add("[" + values.length + " bytes]");
				return strings;
			}
			case INT_ARRAY: {
				int[] values = ((IntArrayTag) tag).getValue();
				List<String> strings = new ArrayList<>(values.length + 1);
				for (int value : values) {
					strings.add(Integer.toString(value));
				}
				strings.add("[" + values.length + " ints]");
				return strings;
			}
			case LONG_ARRAY: {
				long[] values = ((LongArrayTag) tag).getValue();
				List<String> strings = new ArrayList<>(values.length + 1);
				for (long value : values) {
					strings.add(Long.toString(value));
				}
				strings.add("[" + values.length + " longs]");
				return strings;
			}
			default:
				// NBT Studio shows containers as a summary in its tree, so searching for
				// "entries" or "ints" has to be able to find them
				String preview = preview(tag);
				return preview == null ? List.of() : List.of(preview);
		}
	}

	/**
	 * @return how NBT Studio renders a tag in its tree: a summary for containers and arrays,
	 *         the plain value for everything else
	 */
	public static String preview(Tag tag) {
		if (tag == null) {
			return null;
		}
		return switch (tag.getType()) {
			case COMPOUND -> "[" + ((CompoundTag) tag).size() + " entries]";
			case LIST -> {
				ListTag list = (ListTag) tag;
				if (list.size() == 0) {
					yield "[0 entries]";
				}
				Tag.Type elementType = list.getElementType();
				yield "[" + list.size() + " "
						+ (elementType == null ? "entries" : typeName(elementType) + "s") + "]";
			}
			case BYTE_ARRAY -> "[" + ((ByteArrayTag) tag).getValue().length + " bytes]";
			case INT_ARRAY -> "[" + ((IntArrayTag) tag).getValue().length + " ints]";
			case LONG_ARRAY -> "[" + ((LongArrayTag) tag).getValue().length + " longs]";
			default -> display(tag);
		};
	}

	/**
	 * @return a single string representing the given tag, compounds and lists are rendered as SNBT
	 */
	public static String display(Tag tag) {
		if (tag == null) {
			return "null";
		}
		return switch (tag.getType()) {
			case STRING -> ((StringTag) tag).getValue();
			case BYTE, SHORT, INT, LONG, FLOAT, DOUBLE -> numberString(tag);
			default -> tag.toString();
		};
	}

	public static boolean isScalar(Tag.Type type) {
		return switch (type) {
			case STRING, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE -> true;
			default -> false;
		};
	}

	/**
	 * Removes a useless {@code .0} from a floating point representation, so that a float 1.0 can be
	 * found with the search text {@code 1} exactly like in NBT Studio's preview.
	 */
	static String trimTrailingZero(String number) {
		if (number.indexOf('E') >= 0 || number.indexOf('e') >= 0) {
			return number;
		}
		if (number.endsWith(".0")) {
			return number.substring(0, number.length() - 2);
		}
		return number;
	}

	private static String numberString(Tag tag) {
		Number n = ((NumberTag) tag).asNumber();
		return switch (tag.getType()) {
			case FLOAT -> Float.toString(n.floatValue());
			case DOUBLE -> Double.toString(n.doubleValue());
			case LONG -> Long.toString(n.longValue());
			default -> Long.toString(n.longValue());
		};
	}

	/**
	 * Creates a new tag of the same type from the given string.
	 *
	 * <p>In addition to plain numbers this accepts the suffixes minecraft uses in SNBT
	 * ({@code 1b}, {@code 2s}, {@code 3l}, {@code 1.5f}, {@code 2.5d}), hexadecimal numbers
	 * ({@code 0x1F}) and {@code true}/{@code false} for bytes, so a replacement can be written
	 * the same way it would be written in NBT Studio.</p>
	 *
	 * @return the new tag or null if the string cannot be parsed into the given type
	 */
	public static Tag parse(Tag.Type type, String value) {
		if (type == null || value == null) {
			return null;
		}
		try {
			return switch (type) {
				case STRING -> StringTag.valueOf(value);
				case BYTE -> ByteTag.valueOf(parseByte(value));
				case SHORT -> ShortTag.valueOf(toShort(parseIntegral(value, 's')));
				case INT -> IntTag.valueOf(toInt(parseIntegral(value, 'i')));
				case LONG -> LongTag.valueOf(parseIntegral(value, 'l'));
				case FLOAT -> FloatTag.valueOf(parseFloat(stripSuffix(value.trim(), 'f')));
				case DOUBLE -> DoubleTag.valueOf(parseDouble(stripSuffix(value.trim(), 'd')));
				default -> null;
			};
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	/**
	 * Narrowing conversions use explicit range checks instead of a cast, so that an out of range
	 * replacement is reported as invalid instead of silently wrapping around and corrupting data.
	 */
	private static byte toByte(long value) throws NumberFormatException {
		if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
			throw new NumberFormatException("value out of range for a byte: " + value);
		}
		return (byte) value;
	}

	private static short toShort(long value) throws NumberFormatException {
		if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
			throw new NumberFormatException("value out of range for a short: " + value);
		}
		return (short) value;
	}

	private static int toInt(long value) throws NumberFormatException {
		if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
			throw new NumberFormatException("value out of range for an int: " + value);
		}
		return (int) value;
	}

	private static byte parseByte(String value) {
		String s = stripSuffix(value.trim(), 'b');
		if (s.equalsIgnoreCase("true")) {
			return 1;
		}
		if (s.equalsIgnoreCase("false")) {
			return 0;
		}
		return toByte(parseIntegralRaw(s));
	}

	private static long parseIntegral(String value, char suffix) {
		return parseIntegralRaw(stripSuffix(value.trim(), suffix));
	}

	private static long parseIntegralRaw(String s) {
		if (s.startsWith("0x") || s.startsWith("0X") || s.startsWith("#")) {
			return Long.parseLong(s.startsWith("#") ? s.substring(1) : s.substring(2), 16);
		}
		if (s.endsWith("b") || s.endsWith("B")) {
			return Long.parseLong(s.substring(0, s.length() - 1));
		}
		return Long.parseLong(s);
	}

	private static float parseFloat(String s) {
		return Float.parseFloat(s);
	}

	private static double parseDouble(String s) {
		return Double.parseDouble(s);
	}

	private static String stripSuffix(String s, char suffix) {
		if (!s.isEmpty()) {
			char last = s.charAt(s.length() - 1);
			if (last == suffix || last == Character.toUpperCase(suffix)) {
				return s.substring(0, s.length() - 1);
			}
		}
		return s;
	}

	public static String typeName(Tag.Type type) {
		return type == null ? "?" : type.name().toLowerCase(java.util.Locale.ROOT);
	}
}
