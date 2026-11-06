package net.querz.mcaselector.nbtstudio;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A compiled name/value pattern used by {@link NbtQuery}.
 *
 * <p>In contrast to the matcher used for chunk coords or biomes, this supports
 * glob wildcards ('*' and '?'), regular expressions and simple contains/exact matches.</p>
 */
public final class NbtPattern {

	private final String source;
	private final NbtMatchMode mode;
	private final boolean ignoreCase;
	private final Pattern pattern;
	private final boolean fullMatch;
	private final String error;

	private NbtPattern(String source, NbtMatchMode mode, boolean ignoreCase, Pattern pattern, boolean fullMatch, String error) {
		this.source = source;
		this.mode = mode;
		this.ignoreCase = ignoreCase;
		this.pattern = pattern;
		this.fullMatch = fullMatch;
		this.error = error;
	}

	public static NbtPattern of(String source, NbtMatchMode mode, boolean ignoreCase) {
		if (source == null) {
			return null;
		}
		NbtMatchMode effectiveMode = mode == null ? NbtMatchMode.SMART : mode;
		if (effectiveMode == NbtMatchMode.SMART) {
			effectiveMode = source.indexOf('*') >= 0 || source.indexOf('?') >= 0 ? NbtMatchMode.GLOB : NbtMatchMode.CONTAINS;
		}

		int flags = ignoreCase ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0;
		try {
			return switch (effectiveMode) {
				case CONTAINS -> new NbtPattern(source, effectiveMode, ignoreCase, Pattern.compile(Pattern.quote(source), flags), false, null);
				case EXACT -> new NbtPattern(source, effectiveMode, ignoreCase, Pattern.compile(Pattern.quote(source), flags), true, null);
				case GLOB -> new NbtPattern(source, effectiveMode, ignoreCase, Pattern.compile(globToRegex(source), flags), true, null);
				case REGEX -> new NbtPattern(source, effectiveMode, ignoreCase, Pattern.compile(source, flags), false, null);
				default -> throw new IllegalStateException("unexpected mode " + effectiveMode);
			};
		} catch (PatternSyntaxException ex) {
			return new NbtPattern(source, effectiveMode, ignoreCase, null, false, ex.getDescription() + " near index " + ex.getIndex());
		}
	}

	private static String globToRegex(String glob) {
		StringBuilder sb = new StringBuilder();
		for (char c : glob.toCharArray()) {
			switch (c) {
				case '*' -> sb.append(".*");
				case '?' -> sb.append('.');
				case '.', '(', ')', '+', '|', '^', '$', '@', '%', '{', '}', '[', ']', '\\' -> sb.append('\\').append(c);
				default -> sb.append(c);
			}
		}
		return sb.toString();
	}

	public boolean isValid() {
		return error == null;
	}

	public String getError() {
		return error;
	}

	public String getSource() {
		return source;
	}

	public NbtMatchMode getMode() {
		return mode;
	}

	public boolean isIgnoreCase() {
		return ignoreCase;
	}

	public boolean matches(String value) {
		if (pattern == null || value == null) {
			return false;
		}
		Matcher m = pattern.matcher(value);
		return fullMatch ? m.matches() : m.find();
	}

	/**
	 * @return a new string where every match of this pattern is replaced by the given replacement.
	 *         Regular expressions may reference capture groups as $1, $2, ...
	 */
	public String replaceAll(String value, String replacement) {
		if (pattern == null || value == null) {
			return value;
		}
		if (fullMatch) {
			// exact and glob patterns describe the whole value, so the entire value is replaced
			return matches(value) ? replacement : value;
		}
		if (mode == NbtMatchMode.CONTAINS) {
			// never interpret the replacement as a group reference for literal modes
			return pattern.matcher(value).replaceAll(Matcher.quoteReplacement(replacement));
		}
		return replaceRegex(value, replacement);
	}

	/**
	 * Same as {@link Matcher#replaceAll(String)}, but zero length matches are left untouched.
	 * NBT Studio applies the same rule, otherwise patterns like {@code ^} or {@code \d*}
	 * would insert the replacement between every single character.
	 */
	private String replaceRegex(String value, String replacement) {
		Matcher m = pattern.matcher(value);
		StringBuilder sb = new StringBuilder(value.length() + 16);
		while (m.find()) {
			if (m.start() == m.end()) {
				continue;
			}
			m.appendReplacement(sb, replacement);
		}
		m.appendTail(sb);
		return sb.toString();
	}

	/**
	 * @return whether this pattern can be used to rewrite values
	 */
	public boolean isRewriteable() {
		return pattern != null;
	}

	@Override
	public String toString() {
		return source;
	}
}
