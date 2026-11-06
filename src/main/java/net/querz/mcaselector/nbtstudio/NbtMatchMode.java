package net.querz.mcaselector.nbtstudio;

import java.util.Locale;

/**
 * Matching strategies for NBT Studio style name/value search.
 *
 * <ul>
 *     <li>{@link #SMART} - wildcards if the pattern contains '*' or '?', contains otherwise (default)</li>
 *     <li>{@link #CONTAINS} - substring match</li>
 *     <li>{@link #EXACT} - full string equality</li>
 *     <li>{@link #GLOB} - '*' and '?' wildcards, full match</li>
 *     <li>{@link #REGEX} - java.util.regex, partial match</li>
 * </ul>
 */
public enum NbtMatchMode {

	SMART("smart"),
	CONTAINS("contains"),
	EXACT("exact"),
	GLOB("glob"),
	REGEX("regex");

	private final String name;

	NbtMatchMode(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	@Override
	public String toString() {
		return name;
	}

	public static NbtMatchMode byName(String name) {
		if (name == null) {
			return null;
		}
		String n = name.trim().toLowerCase(Locale.ROOT);
		return switch (n) {
			case "smart", "auto", "wildcard" -> SMART;
			case "contains", "contain", "like" -> CONTAINS;
			case "exact", "equals", "eq" -> EXACT;
			case "glob", "wildcards" -> GLOB;
			case "regex", "regexp", "re" -> REGEX;
			default -> null;
		};
	}
}
