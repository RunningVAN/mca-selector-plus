package net.querz.mcaselector.nbtstudio;

import java.util.ArrayList;
import java.util.List;

/**
 * A path through an NBT tree, similar to the path syntax used by NBT Studio.
 *
 * <pre>
 *   Entries[].id            every "id" tag inside the "Entries" list
 *   Sections[*].biomes       same as above, '*' and '[]' are equivalent
 *   Sections[0].palette      only the first element of the list
 *   *.biomes.palette         "biomes.palette" below any tag
 *   **.id                    every "id" tag at any depth
 *   ^Data.Sections[].biomes  anchored at the root (no implicit any-depth prefix)
 * </pre>
 *
 * Separators are '.' and '/'. A leading '^' anchors the path at the root of the
 * scanned tag, otherwise the path may start at any depth.
 */
public final class NbtPath {

	public enum Kind {
		KEY, ANY_KEY, LIST_ANY, LIST_INDEX, DEEP
	}

	public record Segment(Kind kind, String key, int index) {

		public static Segment key(String key) {
			return new Segment(Kind.KEY, key, -1);
		}

		public static Segment anyKey() {
			return new Segment(Kind.ANY_KEY, null, -1);
		}

		public static Segment listAny() {
			return new Segment(Kind.LIST_ANY, null, -1);
		}

		public static Segment listIndex(int index) {
			return new Segment(Kind.LIST_INDEX, null, index);
		}

		public static Segment deep() {
			return new Segment(Kind.DEEP, null, -1);
		}

		@Override
		public String toString() {
			return switch (kind) {
				case KEY -> key;
				case ANY_KEY -> "*";
				case LIST_ANY -> "[]";
				case LIST_INDEX -> "[" + index + "]";
				case DEEP -> "**";
			};
		}
	}

	private final List<Segment> segments;
	private final boolean anchored;
	private final String source;

	private NbtPath(String source, List<Segment> segments, boolean anchored) {
		this.source = source;
		this.segments = segments;
		this.anchored = anchored;
	}

	public static NbtPath parse(String path) {
		if (path == null || path.isBlank()) {
			return null;
		}
		String s = path.trim();
		boolean anchored = false;
		if (s.startsWith("^")) {
			anchored = true;
			s = s.substring(1);
		}

		List<Segment> segments = new ArrayList<>();
		StringBuilder key = new StringBuilder();
		int i = 0;
		while (i < s.length()) {
			char c = s.charAt(i);
			if (c == '.' || c == '/') {
				flushKey(key, segments);
				i++;
			} else if (c == '[') {
				flushKey(key, segments);
				int end = s.indexOf(']', i);
				if (end < 0) {
					// unterminated index, take the rest as key
					key.append(s.substring(i));
					break;
				}
				String inner = s.substring(i + 1, end).trim();
				if (inner.isEmpty() || inner.equals("*")) {
					segments.add(Segment.listAny());
				} else if (inner.startsWith("\"") && inner.endsWith("\"") && inner.length() >= 2) {
					segments.add(Segment.key(inner.substring(1, inner.length() - 1)));
				} else {
					try {
						segments.add(Segment.listIndex(Integer.parseInt(inner)));
					} catch (NumberFormatException ex) {
						segments.add(Segment.key(inner));
					}
				}
				i = end + 1;
			} else {
				key.append(c);
				i++;
			}
		}
		flushKey(key, segments);

		return new NbtPath(s, segments, anchored);
	}

	private static void flushKey(StringBuilder key, List<Segment> segments) {
		String k = key.toString().trim();
		key.setLength(0);
		if (k.isEmpty()) {
			return;
		}
		switch (k) {
			case "*" -> segments.add(Segment.anyKey());
			case "**" -> segments.add(Segment.deep());
			default -> segments.add(Segment.key(k));
		}
	}

	public List<Segment> getSegments() {
		return segments;
	}

	public boolean isAnchored() {
		return anchored;
	}

	public boolean isEmpty() {
		return segments.isEmpty();
	}

	/**
	 * @return the segments as they have to be evaluated on a tag: unanchored paths get an implicit
	 *         any-depth prefix so that they can match anywhere below the root.
	 */
	public List<Segment> effectiveSegments() {
		if (anchored || segments.isEmpty()) {
			return segments;
		}
		List<Segment> result = new ArrayList<>(segments.size() + 1);
		result.add(Segment.deep());
		result.addAll(segments);
		return result;
	}

	@Override
	public String toString() {
		return (anchored ? "^" : "") + source;
	}
}
