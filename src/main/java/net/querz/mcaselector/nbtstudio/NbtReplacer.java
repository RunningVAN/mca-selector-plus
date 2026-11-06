package net.querz.mcaselector.nbtstudio;

import net.querz.nbt.CompoundTag;
import net.querz.nbt.ListTag;
import net.querz.nbt.Tag;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Find &amp; replace for NBT names and values, mirroring the functionality of NBT Studio.
 *
 * <p>Replacement supports regular expressions with capture groups ($1, $2, ...) when the query
 * mode is {@link NbtMatchMode#REGEX}. Values are converted back to their original NBT type;
 * matches whose replacement cannot be parsed into that type are skipped and reported.</p>
 *
 * <p>Like NBT Studio, renaming never destroys data: a key is only renamed when the target compound
 * does not already contain the new key. Colliding renames are skipped and counted.</p>
 *
 * <p>Names and values are collected in a <b>single traversal</b> and applied afterwards, values
 * first, names second. This is essential: renaming a key invalidates the key that a pending value
 * change refers to, and a second traversal would run on already renamed keys and therefore match
 * different tags than the preview did.</p>
 */
public final class NbtReplacer {

	public enum Target {
		NAME, VALUE, BOTH
	}

	private NbtReplacer() {
	}

	public static NbtReplaceResult replace(Tag root, NbtQuery query, Target target, String replacement) {
		if (target == null) {
			return new NbtReplaceResult();
		}
		return replace(root, query, target != Target.VALUE, target != Target.NAME, replacement);
	}

	/**
	 * Replaces names and/or values in a single pass over the tree.
	 *
	 * @param names  whether keys of compounds may be renamed
	 * @param values whether scalar values may be rewritten
	 */
	public static NbtReplaceResult replace(Tag root, NbtQuery query, boolean names, boolean values, String replacement) {
		NbtReplaceResult result = new NbtReplaceResult();
		if (root == null || query == null || !query.isValid() || replacement == null || (!names && !values)) {
			return result;
		}

		List<NameChange> nameChanges = new ArrayList<>();
		List<ValueChange> valueChanges = new ArrayList<>();
		// a path containing '**' can reach the same tag through several different routes, so the
		// same node would be rewritten (and counted) more than once - every tag is handled once
		Set<Tag> visited = Collections.newSetFromMap(new IdentityHashMap<>());

		NbtSearcher.traverse(root, query, (tag, parent, key, index, path) -> {
			if (!visited.add(tag)) {
				return;
			}
			if (values) {
				collectValueChange(query, replacement, tag, parent, key, index, path, valueChanges, result);
			}
			if (names) {
				collectNameChange(query, replacement, parent, key, path, nameChanges, result);
			}
		});

		// values first: renaming a key afterwards keeps the rewritten value with its key
		applyValueChanges(valueChanges, result);
		applyNameChanges(nameChanges, result);
		return result;
	}

	private static void collectNameChange(NbtQuery query, String replacement, Object parent, String key, String path,
			List<NameChange> changes, NbtReplaceResult result) {
		if (!(parent instanceof CompoundTag compound) || key == null) {
			// list entries and the root don't have a name that could be changed - like NBT Studio
			// these matches are not counted at all instead of being reported as skipped
			return;
		}
		result.examine();
		NbtPattern pattern = query.getNamePattern();
		String newKey;
		if (pattern == null || !pattern.matches(key)) {
			// the match came from the value pattern, so the whole name is replaced
			newKey = replacement;
		} else {
			newKey = pattern.replaceAll(key, replacement);
		}
		if (newKey == null || newKey.isEmpty()) {
			result.skip("replacement \"" + replacement + "\" would produce an empty name for \"" + key + "\"");
			return;
		}
		if (newKey.equals(key)) {
			result.unchanged();
			return;
		}
		changes.add(new NameChange(compound, key, newKey, path));
	}

	private static void collectValueChange(NbtQuery query, String replacement, Tag tag, Object parent, String key,
			int index, String path, List<ValueChange> changes, NbtReplaceResult result) {
		if (!NbtValues.isScalar(tag.getType())) {
			// compounds, lists and arrays have no scalar value that could be replaced
			result.examine();
			// NBT Studio only edits primitive values, arrays and containers are not replaceable
			result.skip(NbtValues.typeName(tag.getType()) + " values cannot be replaced");
			return;
		}
		CompoundTag compound = parent instanceof CompoundTag c ? c : null;
		ListTag list = parent instanceof ListTag l ? l : null;
		if ((compound == null || key == null) && (list == null || index < 0)) {
			// the root tag has no parent to write back to
			return;
		}
		result.examine();
		Tag replacementTag = rewriteValue(query.getValuePattern(), tag, replacement);
		if (replacementTag == null) {
			result.skip("replacement \"" + replacement + "\" does not fit type " + NbtValues.typeName(tag.getType()));
			return;
		}
		String oldValue = NbtValues.display(tag);
		String newValue = NbtValues.display(replacementTag);
		if (oldValue.equals(newValue)) {
			result.unchanged();
			return;
		}
		changes.add(new ValueChange(compound, list, key, index, replacementTag, path, oldValue));
	}

	private static void applyNameChanges(List<NameChange> changes, NbtReplaceResult result) {
		if (changes.isEmpty()) {
			return;
		}
		// group by compound so every compound is rewritten exactly once
		Map<CompoundTag, List<NameChange>> byCompound = new LinkedHashMap<>();
		for (NameChange change : changes) {
			byCompound.computeIfAbsent(change.compound(), k -> new ArrayList<>()).add(change);
		}
		for (var entry : byCompound.entrySet()) {
			CompoundTag compound = entry.getKey();
			Map<String, NameChange> renames = new LinkedHashMap<>();
			for (NameChange change : entry.getValue()) {
				// a key can only be renamed once, the first match wins
				renames.putIfAbsent(change.oldKey(), change);
			}

			// rebuild the compound in its original order and rename entry by entry. NBT Studio
			// applies the renames one after another and refuses a rename if the target key is
			// taken at that moment, so a chain like a->b, b->c is evaluated against the live state
			Map<String, Tag> current = new LinkedHashMap<>();
			for (var child : compound) {
				current.put(child.getKey(), child.getValue());
			}
			List<NameChange> applied = new ArrayList<>();
			List<NameChange> skipped = new ArrayList<>();
			for (NameChange change : renames.values()) {
				if (!current.containsKey(change.oldKey())) {
					continue;
				}
				if (current.containsKey(change.newKey())) {
					// renaming would overwrite an existing entry, keep the original key
					skipped.add(change);
					continue;
				}
				Map<String, Tag> next = new LinkedHashMap<>(current.size());
				for (var child : current.entrySet()) {
					if (child.getKey().equals(change.oldKey())) {
						next.put(change.newKey(), child.getValue());
					} else {
						next.put(child.getKey(), child.getValue());
					}
				}
				current = next;
				applied.add(change);
			}

			compound.clear();
			current.forEach(compound::put);
			for (NameChange change : applied) {
				result.replace(change.path() + ": name \"" + change.oldKey() + "\" -> \"" + change.newKey() + "\"");
				result.change(change.path(), change.oldKey(), change.newKey());
			}
			for (NameChange change : skipped) {
				result.skip("key \"" + change.newKey() + "\" already exists in "
						+ (change.path().isEmpty() ? "root" : change.path()));
			}
		}
	}

	private static void applyValueChanges(List<ValueChange> changes, NbtReplaceResult result) {
		for (ValueChange change : changes) {
			if (change.compound() != null) {
				if (change.key() == null) {
					continue;
				}
				change.compound().put(change.key(), change.tag());
			} else if (change.list() != null && change.index() >= 0 && change.index() < change.list().size()) {
				change.list().set(change.index(), change.tag());
			} else {
				continue;
			}
			result.replace(change.path() + " = " + NbtValues.display(change.tag()));
			result.change(change.path(), change.oldValue(), NbtValues.display(change.tag()));
		}
	}

	private static Tag rewriteValue(NbtPattern pattern, Tag tag, String replacement) {
		List<String> candidates = NbtValues.candidates(tag);
		if (candidates.isEmpty()) {
			return null;
		}
		if (pattern == null) {
			// the match came from the name pattern, so the whole value is replaced
			return NbtValues.parse(tag.getType(), replacement);
		}
		Set<String> rewritten = new LinkedHashSet<>(candidates.size());
		for (String candidate : candidates) {
			if (pattern.matches(candidate)) {
				String value = pattern.replaceAll(candidate, replacement);
				if (value != null) {
					rewritten.add(value);
				}
			}
		}
		if (rewritten.isEmpty()) {
			return null;
		}
		// like NBT Studio, the replacement has to stay a valid value of the same type
		for (String value : rewritten) {
			Tag parsed = NbtValues.parse(tag.getType(), value);
			if (parsed != null) {
				return parsed;
			}
		}
		return null;
	}

	private record NameChange(CompoundTag compound, String oldKey, String newKey, String path) {
	}

	private record ValueChange(CompoundTag compound, ListTag list, String key, int index, Tag tag, String path,
			String oldValue) {
	}
}
