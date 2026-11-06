package net.querz.mcaselector.nbtstudio;

import net.querz.nbt.CompoundTag;
import net.querz.nbt.ListTag;
import net.querz.nbt.Tag;
import java.util.List;

/**
 * Traverses NBT trees and reports all nodes matching an {@link NbtQuery}.
 *
 * <p>The search is driven by the path of the query: only nodes reachable through the path are
 * evaluated, which keeps deep scans of chunk data cheap. If no path is given, every node in the
 * tree is evaluated.</p>
 */
public final class NbtSearcher {

	private NbtSearcher() {
	}

	/**
	 * Receives every node of an NBT tree that matches a query.
	 */
	public interface NbtVisitor {

		/**
		 * @param tag    the matching tag
		 * @param parent the compound tag or list tag containing the matching tag, null for the root
		 * @param key    the key of the tag inside its parent compound, null for list entries and the root
		 * @param index  the index inside the parent list, -1 for compound entries and the root
		 * @param path   the full path of the tag, e.g. "Sections[3].biomes.palette[0]"
		 */
		void visit(Tag tag, Object parent, String key, int index, String path);
	}

	public static NbtSearchResult search(Tag root, String rootLabel, NbtQuery query, int limit) {
		int max = limit > 0 ? limit : query.getLimit();
		NbtSearchResult result = new NbtSearchResult(max);
		if (root == null || query == null || !query.isValid()) {
			return result;
		}
		String label = rootLabel == null ? "root" : rootLabel;
		if (query.isChunkWide() && !query.isMatchAny()
				&& query.getNamePattern() != null && query.getValuePattern() != null) {
			// name and value only have to occur in the same chunk, so both halves are searched
			// separately and the value hits are reported when both were found
			NbtSearchResult byName = search(root, label, splitPart(query, query.getNamePattern(), null), max);
			if (byName.getTotal() == 0) {
				return result;
			}
			return search(root, label, splitPart(query, null, query.getValuePattern()), max);
		}
		traverse(root, query, (tag, parent, key, index, path) ->
				result.add(new NbtSearchHit(label, path, key, tag.getType(), NbtValues.display(tag))));
		return result;
	}

	public static NbtSearchResult search(Tag root, String rootLabel, NbtQuery query) {
		return search(root, rootLabel, query, query.getLimit());
	}

	/**
	 * Collects all hits of all given roots, e.g. region, poi and entities of the same chunk.
	 */
	public static NbtSearchResult search(Tag region, Tag poi, Tag entities, NbtQuery query) {
		NbtSearchResult result = new NbtSearchResult(query.getLimit());
		if (query == null || !query.isValid()) {
			return result;
		}
		if (region != null && query.matchesRoot(NbtQuery.ROOT_REGION)) {
			result.merge(search(region, "region", query));
		}
		if (poi != null && query.matchesRoot(NbtQuery.ROOT_POI)) {
			result.merge(search(poi, "poi", query));
		}
		if (entities != null && query.matchesRoot(NbtQuery.ROOT_ENTITIES)) {
			result.merge(search(entities, "entities", query));
		}
		return result;
	}

	/**
	 * Applies the query to the given root and calls the visitor for every match.
	 */
	public static void traverse(Tag root, NbtQuery query, NbtVisitor visitor) {
		if (root == null || query == null || !query.isValid() || visitor == null) {
			return;
		}
		NbtPath path = query.getPath();
		List<NbtPath.Segment> segments = path == null ? List.of(NbtPath.Segment.deep()) : path.effectiveSegments();
		descend(root, null, null, -1, "", segments, 0, query, visitor);
	}

	/**
	 * Counts how far the parts of a query get on their own. This answers the question behind an
	 * empty result: did the path not exist at all, did the name never match, or did the value never
	 * match, so names and values can never be found in the same tag.
	 */
	public static final class Stats {

		/** nodes that the path reaches, regardless of name and value */
		public int nodes;
		/** nodes whose key matches the name pattern of the query, -1 when the query has no name */
		public int nameHits = -1;
		/** nodes whose value matches the value pattern, -1 when the query has no value */
		public int valueHits = -1;

		public void add(Stats other) {
			nodes += other.nodes;
			nameHits += other.nameHits;
			valueHits += other.valueHits;
		}
	}

	public static Stats stats(Tag root, NbtQuery query) {
		Stats stats = new Stats();
		if (root == null || query == null || !query.isValid()) {
			return stats;
		}
		traverse(root, onlyPart(query, null, null), (tag, parent, key, index, path) -> stats.nodes++);
		if (query.getNamePattern() != null) {
			stats.nameHits = 0;
			traverse(root, onlyPart(query, query.getNamePattern(), null), (tag, parent, key, index, path) -> stats.nameHits++);
		}
		if (query.getValuePattern() != null) {
			stats.valueHits = 0;
			traverse(root, onlyPart(query, null, query.getValuePattern()), (tag, parent, key, index, path) -> stats.valueHits++);
		}
		return stats;
	}

	/**
	 * One half of a chunk wide query. The name half is searched in keys and in string values, so a
	 * query like {@code name=sign value=hello} finds a chunk whose sign really has the id
	 * {@code minecraft:sign} and the text {@code hello}: "sign" only ever appears as a value.
	 */
	private static NbtQuery splitPart(NbtQuery query, NbtPattern name, NbtPattern value) {
		// the path is dropped on purpose, chunk wide means anywhere in the chunk
		boolean nameAnywhere = name != null && value == null;
		return NbtQuery.create(null, name, nameAnywhere ? name : value, query.getRootMask(), query.getTypes(),
				Integer.MAX_VALUE, nameAnywhere, false);
	}

	private static NbtQuery onlyPart(NbtQuery query, NbtPattern name, NbtPattern value) {
		// the same path, root scope and type restriction, but at most one of name and value, so
		// every part is counted on its own instead of only counting tags that match all of them
		return NbtQuery.create(query.getPath(), name, value, query.getRootMask(), query.getTypes(),
				Integer.MAX_VALUE, query.isMatchAny());
	}

	public static void traverse(Tag root, NbtQuery query, String rootLabel, NbtVisitor visitor) {
		traverse(root, query, visitor);
	}

	private static void descend(Tag tag, Object parent, String key, int index, String path,
			List<NbtPath.Segment> segments, int segIndex, NbtQuery query, NbtVisitor visitor) {
		if (tag == null || tag.getType() == Tag.Type.END) {
			return;
		}
		if (segIndex >= segments.size()) {
			boolean explicitPath = query.getPath() != null && !query.getPath().isEmpty();
			evaluate(tag, parent, key, index, path, query, visitor);
			// a path can end on a container whose entries are scalars, e.g. Sections[*].biomes.palette
			// or a sign's front_text.messages. Searching a value through such a path means looking
			// into the list, so the scalar entries are evaluated as well. Without a path the
			// recursion already visits every node of the tree, so the entries must not be visited a
			// second time here
			if (explicitPath && tag instanceof ListTag list && query.getValuePattern() != null
					&& list.getElementType() != null && NbtValues.isScalar(list.getElementType())) {
				for (int i = 0; i < list.size(); i++) {
					evaluate(list.get(i), list, null, i, path + "[" + i + "]", query, visitor);
				}
			}
			return;
		}

		NbtPath.Segment segment = segments.get(segIndex);
		switch (segment.kind()) {
			case KEY -> {
				if (tag instanceof CompoundTag compound) {
					Tag child = compound.get(segment.key());
					if (child != null) {
						descend(child, compound, segment.key(), -1, join(path, segment.key()), segments, segIndex + 1, query, visitor);
					}
				}
			}
			case ANY_KEY -> {
				if (tag instanceof CompoundTag compound) {
					for (var entry : compound) {
						descend(entry.getValue(), compound, entry.getKey(), -1, join(path, entry.getKey()), segments, segIndex + 1, query, visitor);
					}
				} else if (tag instanceof ListTag list) {
					for (int i = 0; i < list.size(); i++) {
						descend(list.get(i), list, null, i, path + "[" + i + "]", segments, segIndex + 1, query, visitor);
					}
				}
			}
			case LIST_ANY -> {
				if (tag instanceof ListTag list) {
					for (int i = 0; i < list.size(); i++) {
						descend(list.get(i), list, null, i, path + "[" + i + "]", segments, segIndex + 1, query, visitor);
					}
				} else if (tag instanceof CompoundTag compound) {
					// be forgiving: [] on a compound behaves like *
					for (var entry : compound) {
						descend(entry.getValue(), compound, entry.getKey(), -1, join(path, entry.getKey()), segments, segIndex + 1, query, visitor);
					}
				}
			}
			case LIST_INDEX -> {
				if (tag instanceof ListTag list && segment.index() >= 0 && segment.index() < list.size()) {
					descend(list.get(segment.index()), list, null, segment.index(), path + "[" + segment.index() + "]", segments, segIndex + 1, query, visitor);
				}
			}
			case DEEP -> {
				// '**' matches zero or more levels
				descend(tag, parent, key, index, path, segments, segIndex + 1, query, visitor);
				if (tag instanceof CompoundTag compound) {
					for (var entry : compound) {
						descend(entry.getValue(), compound, entry.getKey(), -1, join(path, entry.getKey()), segments, segIndex, query, visitor);
					}
				} else if (tag instanceof ListTag list) {
					for (int i = 0; i < list.size(); i++) {
						descend(list.get(i), list, null, i, path + "[" + i + "]", segments, segIndex, query, visitor);
					}
				}
			}
		}
	}

	private static void evaluate(Tag tag, Object parent, String key, int index, String path, NbtQuery query, NbtVisitor visitor) {
		if (!query.acceptsType(tag.getType())) {
			return;
		}
		NbtPattern namePattern = query.getNamePattern();
		NbtPattern valuePattern = query.getValuePattern();

		boolean nameMatched = false;
		if (namePattern != null) {
			// NBT Studio shows list entries with their index as name, so name=[2] is a valid search
			String name = key != null ? key : index >= 0 ? "[" + index + "]" : null;
			nameMatched = name != null && namePattern.matches(name);
		}
		boolean valueMatched = false;
		if (valuePattern != null) {
			for (String candidate : NbtValues.candidates(tag)) {
				if (valuePattern.matches(candidate)) {
					valueMatched = true;
					break;
				}
			}
		}

		boolean accepted;
		if (query.isMatchAny() && namePattern != null && valuePattern != null) {
			// one text searched in names and values at the same time, like a text editor does
			accepted = nameMatched || valueMatched;
		} else {
			accepted = (namePattern == null || nameMatched) && (valuePattern == null || valueMatched);
		}
		if (!accepted) {
			return;
		}
		visitor.visit(tag, parent, key, index, path);
	}

	private static String join(String path, String key) {
		return path.isEmpty() ? key : path + "." + key;
	}
}
