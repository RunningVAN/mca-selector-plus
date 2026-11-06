package net.querz.mcaselector.nbtstudio;

import net.querz.nbt.Tag;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A parsed NBT Studio style query.
 *
 * <p>Query syntax (all keys are case insensitive, all keys are optional):</p>
 * <pre>
 *   path=Entries[].id;name=id;value=minecraft:*;mode=glob;case=insensitive;root=region,entities;type=string;limit=1000
 * </pre>
 *
 * <ul>
 *     <li>{@code path}  - NBT path, see {@link NbtPath}</li>
 *     <li>{@code name}  - pattern applied to compound keys</li>
 *     <li>{@code value} - pattern applied to tag values</li>
 *     <li>{@code mode}  - smart (default), contains, exact, glob, regex</li>
 *     <li>{@code case}  - insensitive (default, like NBT Studio) or sensitive</li>
 *     <li>{@code root}  - region, poi, entities (default: all)</li>
 *     <li>{@code type}  - NBT types to include, e.g. string,int,compound (default: all)</li>
 *     <li>{@code limit} - maximum number of reported hits (default: 5000)</li>
 *     <li>{@code chunk} - 1 = name and value only have to occur in the same chunk, not on the same tag</li>
 * </ul>
 *
 * <p>If the query does not contain any known key, the whole query is used as a value pattern,
 * which makes a plain {@code minecraft:zombie} work out of the box.</p>
 */
public final class NbtQuery {

	public static final int ROOT_REGION = 1;
	public static final int ROOT_POI = 2;
	public static final int ROOT_ENTITIES = 4;
	public static final int ROOT_ALL = ROOT_REGION | ROOT_POI | ROOT_ENTITIES;
	/** Maximum number of reported hits when the query does not ask for a different limit. */
	public static final int DEFAULT_LIMIT = 5000;

	private static final Set<String> KNOWN_KEYS = Set.of(
			"path", "p", "name", "n", "value", "v", "mode", "m", "case", "c", "root", "r", "type", "t", "limit", "l",
			"any", "chunk");

	private final String raw;
	private final NbtPath path;
	private final NbtPattern name;
	private final NbtPattern value;
	private final int rootMask;
	private final Set<Tag.Type> types;
	private final int limit;
	private final boolean matchAny;
	// name and value only have to occur somewhere in the same chunk instead of on the same tag
	private final boolean chunkWide;
	private final String error;

	private NbtQuery(String raw, NbtPath path, NbtPattern name, NbtPattern value, int rootMask,
			Set<Tag.Type> types, int limit, String error) {
		this(raw, path, name, value, rootMask, types, limit, false, false, error);
	}

	private NbtQuery(String raw, NbtPath path, NbtPattern name, NbtPattern value, int rootMask,
			Set<Tag.Type> types, int limit, boolean matchAny, String error) {
		this(raw, path, name, value, rootMask, types, limit, matchAny, false, error);
	}

	private NbtQuery(String raw, NbtPath path, NbtPattern name, NbtPattern value, int rootMask,
			Set<Tag.Type> types, int limit, boolean matchAny, boolean chunkWide, String error) {
		this.raw = raw;
		this.path = path;
		this.name = name;
		this.value = value;
		this.rootMask = rootMask;
		this.types = types;
		this.limit = limit;
		this.matchAny = matchAny;
		this.chunkWide = chunkWide;
		this.error = error;
	}

	/**
	 * Builds a query programmatically, e.g. from the NBT Studio dialog. The canonical
	 * string representation is generated from the given parts.
	 *
	 * <p>Name and value are combined with AND, i.e. a tag has to match both to be reported.</p>
	 */
	public static NbtQuery create(NbtPath path, NbtPattern name, NbtPattern value, int rootMask,
			Set<Tag.Type> types, int limit) {
		return create(path, name, value, rootMask, types, limit, false);
	}

	/**
	 * @param matchAny whether a tag is already reported when only its name or only its value
	 *                 matches. The chunk editor find bar uses this, because there a single text
	 *                 is searched in names and values at once, exactly like a text editor does.
	 */
	public static NbtQuery create(NbtPath path, NbtPattern name, NbtPattern value, int rootMask,
			Set<Tag.Type> types, int limit, boolean matchAny) {
		return create(path, name, value, rootMask, types, limit, matchAny, false);
	}

	/**
	 * @param chunkWide whether name and value only have to occur in the same chunk instead of on
	 *                  the same tag. A sign stores its name and its text in different tags, so the
	 *                  strict form can never find {@code name=Text value=hello} on one sign.
	 */
	public static NbtQuery create(NbtPath path, NbtPattern name, NbtPattern value, int rootMask,
			Set<Tag.Type> types, int limit, boolean matchAny, boolean chunkWide) {
		if (name == null && value == null && path == null) {
			return new NbtQuery("", null, null, null, ROOT_ALL, null, DEFAULT_LIMIT,
					"at least one of name, value or path is required");
		}
		if (name != null && !name.isValid()) {
			return new NbtQuery("", null, null, null, ROOT_ALL, null, DEFAULT_LIMIT, "invalid name pattern: " + name.getError());
		}
		if (value != null && !value.isValid()) {
			return new NbtQuery("", null, null, null, ROOT_ALL, null, DEFAULT_LIMIT, "invalid value pattern: " + value.getError());
		}
		int mask = rootMask == 0 ? ROOT_ALL : rootMask;
		return new NbtQuery(null, path, name, value, mask, types, limit <= 0 ? DEFAULT_LIMIT : limit, matchAny, chunkWide, null);
	}

	public static NbtQuery parse(String raw) {
		String src = raw == null ? "" : raw.trim();
		if (src.isEmpty()) {
			return new NbtQuery(src, null, null, null, ROOT_ALL, null, DEFAULT_LIMIT, "query is empty");
		}

		List<String> tokens = splitSegments(src);
		boolean keyed = false;
		for (String token : tokens) {
			int eq = token.indexOf('=');
			if (eq > 0 && KNOWN_KEYS.contains(token.substring(0, eq).trim().toLowerCase(Locale.ROOT))) {
				keyed = true;
				break;
			}
		}

		if (!keyed) {
			// a bare pattern is searched case insensitively, like the keyed form and like NBT Studio
			NbtPattern value = NbtPattern.of(src, NbtMatchMode.SMART, true);
			if (value == null || !value.isValid()) {
				return new NbtQuery(src, null, null, null, ROOT_ALL, null, DEFAULT_LIMIT,
						"invalid pattern: " + (value == null ? src : value.getError()));
			}
			return new NbtQuery(src, null, null, value, ROOT_ALL, null, DEFAULT_LIMIT, null);
		}

		Map<String, String> map = new LinkedHashMap<>();
		for (String token : tokens) {
			if (token.isBlank()) {
				continue;
			}
			int eq = token.indexOf('=');
			if (eq <= 0) {
				return invalid(src, "missing '=' in segment \"" + token.trim() + "\"");
			}
			String key = token.substring(0, eq).trim().toLowerCase(Locale.ROOT);
			if (!KNOWN_KEYS.contains(key)) {
				return invalid(src, "unknown query key \"" + key + "\", allowed keys are: path, name, value, mode, case, root, type, limit");
			}
			map.put(key, unquote(token.substring(eq + 1).trim()));
		}

		// normalize aliases
		String modeRaw = firstNonNull(map, "mode", "m");
		String caseRaw = firstNonNull(map, "case", "c");
		String pathRaw = firstNonNull(map, "path", "p");
		String nameRaw = firstNonNull(map, "name", "n");
		String valueRaw = firstNonNull(map, "value", "v");
		String rootRaw = firstNonNull(map, "root", "r");
		String typeRaw = firstNonNull(map, "type", "t");
		String limitRaw = firstNonNull(map, "limit", "l");
		String anyRaw = map.get("any");
		String chunkRaw = map.get("chunk");

		boolean chunkWide = false;
		if (chunkRaw != null) {
			String cw = chunkRaw.trim().toLowerCase(Locale.ROOT);
			chunkWide = cw.isEmpty() || cw.equals("1") || cw.equals("true") || cw.equals("yes");
		}

		// "any" switches name and value from AND to OR, the chunk editor find bar uses it to
		// search one text in names and values at the same time
		boolean matchAny = false;
		if (anyRaw != null) {
			String a = anyRaw.trim().toLowerCase(Locale.ROOT);
			matchAny = a.isEmpty() || a.equals("1") || a.equals("true") || a.equals("yes") || a.equals("or");
		}

		NbtMatchMode mode = NbtMatchMode.SMART;
		if (modeRaw != null) {
			mode = NbtMatchMode.byName(modeRaw);
			if (mode == null) {
				return invalid(src, "unknown mode \"" + modeRaw + "\", allowed: smart, contains, exact, glob, regex");
			}
		}

		// NBT Studio always searches case insensitively, so that is the default here too
		boolean ignoreCase = true;
		if (caseRaw != null) {
			String c = caseRaw.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
			ignoreCase = c.startsWith("insens") || c.equals("i") || c.equals("false") || c.equals("ignore");
		}

		NbtPattern name = null;
		if (nameRaw != null) {
			name = NbtPattern.of(nameRaw, mode, ignoreCase);
			if (!name.isValid()) {
				return invalid(src, "invalid name pattern: " + name.getError());
			}
		}

		NbtPattern value = null;
		if (valueRaw != null) {
			value = NbtPattern.of(valueRaw, mode, ignoreCase);
			if (!value.isValid()) {
				return invalid(src, "invalid value pattern: " + value.getError());
			}
		}

		int rootMask = ROOT_ALL;
		if (rootRaw != null) {
			rootMask = 0;
			for (String r : rootRaw.split("[,+]")) {
				switch (r.trim().toLowerCase(Locale.ROOT)) {
					case "", "all" -> rootMask |= ROOT_ALL;
					case "region", "chunk", "terrain" -> rootMask |= ROOT_REGION;
					case "poi" -> rootMask |= ROOT_POI;
					case "entities", "entity" -> rootMask |= ROOT_ENTITIES;
					default -> {
						return invalid(src, "unknown root \"" + r.trim() + "\", allowed: region, poi, entities");
					}
				}
			}
			if (rootMask == 0) {
				rootMask = ROOT_ALL;
			}
		}

		Set<Tag.Type> types = null;
		if (typeRaw != null) {
			types = EnumSet.noneOf(Tag.Type.class);
			for (String t : typeRaw.split("[,+]")) {
				String tt = t.trim().toUpperCase(Locale.ROOT);
				if (tt.isEmpty()) {
					continue;
				}
				switch (tt) {
					case "STRING" -> types.add(Tag.Type.STRING);
					case "BYTE", "BOOLEAN", "BOOL" -> types.add(Tag.Type.BYTE);
					case "SHORT" -> types.add(Tag.Type.SHORT);
					case "INT", "INTEGER" -> types.add(Tag.Type.INT);
					case "LONG" -> types.add(Tag.Type.LONG);
					case "FLOAT" -> types.add(Tag.Type.FLOAT);
					case "DOUBLE" -> types.add(Tag.Type.DOUBLE);
					case "BYTE_ARRAY", "BYTEARRAY" -> types.add(Tag.Type.BYTE_ARRAY);
					case "INT_ARRAY", "INTARRAY" -> types.add(Tag.Type.INT_ARRAY);
					case "LONG_ARRAY", "LONGARRAY" -> types.add(Tag.Type.LONG_ARRAY);
					case "LIST" -> types.add(Tag.Type.LIST);
					case "COMPOUND" -> types.add(Tag.Type.COMPOUND);
					case "NUMBER", "NUMERIC" -> types.addAll(EnumSet.of(Tag.Type.BYTE, Tag.Type.SHORT, Tag.Type.INT,
							Tag.Type.LONG, Tag.Type.FLOAT, Tag.Type.DOUBLE));
					case "ARRAY" -> types.addAll(EnumSet.of(Tag.Type.BYTE_ARRAY, Tag.Type.INT_ARRAY, Tag.Type.LONG_ARRAY));
					default -> {
						return invalid(src, "unknown nbt type \"" + t.trim() + "\"");
					}
				}
			}
			if (types.isEmpty()) {
				types = null;
			}
		}

		int limit = DEFAULT_LIMIT;
		if (limitRaw != null) {
			try {
				limit = Integer.parseInt(limitRaw.trim());
			} catch (NumberFormatException ex) {
				return invalid(src, "invalid limit \"" + limitRaw + "\"");
			}
			if (limit <= 0) {
				limit = DEFAULT_LIMIT;
			}
		}

		NbtPath path;
		try {
			path = NbtPath.parse(pathRaw);
		} catch (Exception ex) {
			return invalid(src, "invalid path: " + ex.getMessage());
		}

		if (name == null && value == null && path == null) {
			return invalid(src, "at least one of name, value or path is required");
		}

		return new NbtQuery(src, path, name, value, rootMask, types, limit, matchAny, chunkWide, null);
	}

	private static String firstNonNull(Map<String, String> map, String... keys) {
		for (String k : keys) {
			if (map.containsKey(k)) {
				return map.get(k);
			}
		}
		return null;
	}

	private static NbtQuery invalid(String raw, String error) {
		return new NbtQuery(raw, null, null, null, ROOT_ALL, null, DEFAULT_LIMIT, error);
	}

	/**
	 * Quotes a pattern so that it survives a round trip through {@link #parse(String)}.
	 *
	 * <p>Patterns that contain a separator, a quote, a backslash, surrounding whitespace or a
	 * {@code key=} pair that would be mistaken for a query key are wrapped in double quotes.</p>
	 */
	private static String quote(String s) {
		if (s == null) {
			return "";
		}
		boolean needsQuotes = s.isEmpty()
				|| s.indexOf(';') >= 0
				|| s.indexOf('"') >= 0
				|| s.indexOf('\n') >= 0
				|| !s.equals(s.trim());
		if (!needsQuotes) {
			for (String key : KNOWN_KEYS) {
				if (s.toLowerCase(Locale.ROOT).contains(" " + key + "=") || s.toLowerCase(Locale.ROOT).startsWith(key + "=")) {
					needsQuotes = true;
					break;
				}
			}
		}
		if (!needsQuotes) {
			return s;
		}
		StringBuilder sb = new StringBuilder(s.length() + 8);
		sb.append('"');
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '"' -> sb.append("\\\"");
				case '\\' -> sb.append("\\\\");
				case '\n' -> sb.append("\\n");
				case '\t' -> sb.append("\\t");
				default -> sb.append(c);
			}
		}
		sb.append('"');
		return sb.toString();
	}

	private static String unquote(String s) {
		if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
			String inner = s.substring(1, s.length() - 1);
			StringBuilder sb = new StringBuilder(inner.length());
			for (int i = 0; i < inner.length(); i++) {
				char c = inner.charAt(i);
				if (c == '\\' && i + 1 < inner.length()) {
					char next = inner.charAt(i + 1);
					switch (next) {
						case 'n' -> sb.append('\n');
						case 't' -> sb.append('\t');
						case '"' -> sb.append('"');
						case '\\' -> sb.append('\\');
						default -> sb.append(next);
					}
					i++;
				} else {
					sb.append(c);
				}
			}
			return sb.toString();
		}
		return s;
	}

	private static List<String> splitSegments(String s) {
		List<String> result = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '\\' && i + 1 < s.length()) {
				current.append(c).append(s.charAt(i + 1));
				i++;
				continue;
			}
			if (c == '"') {
				quoted = !quoted;
				current.append(c);
				continue;
			}
			if (!quoted && (c == ';' || c == '\n')) {
				result.add(current.toString());
				current.setLength(0);
			} else {
				current.append(c);
			}
		}
		result.add(current.toString());

		// a segment can contain several "key=value" pairs separated by whitespace,
		// e.g. "name=Health value=20 mode=regex"
		List<String> refined = new ArrayList<>(result.size());
		for (String segment : result) {
			refined.addAll(splitKeyValuePairs(segment));
		}
		return refined;
	}

	private static List<String> splitKeyValuePairs(String segment) {
		List<String> parts = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		int i = 0;
		int length = segment.length();
		while (i < length) {
			char c = segment.charAt(i);
			if (Character.isWhitespace(c)) {
				int next = i;
				while (next < length && Character.isWhitespace(segment.charAt(next))) {
					next++;
				}
				String rest = segment.substring(next);
				int eq = rest.indexOf('=');
				boolean startsNewPair = false;
				if (eq > 0) {
					String key = rest.substring(0, eq).trim().toLowerCase(Locale.ROOT);
					startsNewPair = KNOWN_KEYS.contains(key);
				}
				if (current.length() > 0 && startsNewPair) {
					parts.add(current.toString());
					current.setLength(0);
					i = next;
					continue;
				}
				if (current.length() > 0) {
					current.append(c);
				}
				i++;
				continue;
			}
			current.append(c);
			i++;
		}
		if (!current.isEmpty()) {
			parts.add(current.toString());
		}
		return parts;
	}

	public boolean isValid() {
		return error == null;
	}

	public String getError() {
		return error;
	}

	public String getRaw() {
		return raw == null ? canonical() : raw;
	}

	public NbtPath getPath() {
		return path;
	}

	public NbtPattern getNamePattern() {
		return name;
	}

	public NbtPattern getValuePattern() {
		return value;
	}

	public int getRootMask() {
		return rootMask;
	}

	public boolean matchesRoot(int mask) {
		if (mask == 0) {
			return true;
		}
		return (rootMask & mask) != 0;
	}

	public Set<Tag.Type> getTypes() {
		return types;
	}

	public boolean acceptsType(Tag.Type type) {
		return types == null || types.contains(type);
	}

	public int getLimit() {
		return limit;
	}

	/**
	 * @return whether a tag is already reported when only its name or only its value matches,
	 *         instead of requiring both to match
	 */
	public boolean isMatchAny() {
		return matchAny;
	}

	/**
	 * @return whether name and value only have to occur somewhere in the same chunk. The strict
	 *         default requires both on the same tag.
	 */
	public boolean isChunkWide() {
		return chunkWide;
	}

	/**
	 * @return a canonical, human readable representation of this query, usable as input for {@link #parse(String)} again
	 */
	public String canonical() {
		if (!isValid()) {
			return raw == null ? "" : raw;
		}
		StringBuilder sb = new StringBuilder();
		if (path != null) {
			sb.append("path=").append(quote(path.toString())).append(';');
		}
		if (name != null) {
			sb.append("name=").append(quote(name.getSource())).append(';');
		}
		if (value != null) {
			sb.append("value=").append(quote(value.getSource())).append(';');
		}
		if (name != null) {
			sb.append("mode=").append(name.getMode().getName()).append(';');
			// the case mode is always written, otherwise a case sensitive query would silently
			// turn case insensitive when it is parsed again (e.g. by the filter or the CLI)
			sb.append("case=").append(name.isIgnoreCase() ? "insensitive" : "sensitive").append(';');
		} else if (value != null) {
			sb.append("mode=").append(value.getMode().getName()).append(';');
			sb.append("case=").append(value.isIgnoreCase() ? "insensitive" : "sensitive").append(';');
		}
		if (rootMask != ROOT_ALL) {
			List<String> roots = new ArrayList<>(3);
			if ((rootMask & ROOT_REGION) != 0) {
				roots.add("region");
			}
			if ((rootMask & ROOT_POI) != 0) {
				roots.add("poi");
			}
			if ((rootMask & ROOT_ENTITIES) != 0) {
				roots.add("entities");
			}
			sb.append("root=").append(String.join(",", roots)).append(';');
		}
		if (types != null) {
			List<String> names = new ArrayList<>(types.size());
			for (Tag.Type type : types) {
				names.add(type.name().toLowerCase(Locale.ROOT));
			}
			sb.append("type=").append(String.join(",", names)).append(';');
		}
		// the limit is only written when it differs from the default, so that a canonical query
		// stays short while a custom limit still survives a round trip through parse()
		if (matchAny) {
			sb.append("any=1;");
		}
		if (chunkWide) {
			sb.append("chunk=1;");
		}
		if (limit != DEFAULT_LIMIT) {
			sb.append("limit=").append(limit).append(';');
		}
		String result = sb.toString();
		if (result.endsWith(";")) {
			result = result.substring(0, result.length() - 1);
		}
		return result;
	}

	@Override
	public String toString() {
		return canonical();
	}
}
