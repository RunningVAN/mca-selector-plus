package net.querz.mcaselector.changer.fields;

import net.querz.mcaselector.changer.Field;
import net.querz.mcaselector.changer.FieldType;
import net.querz.mcaselector.io.mca.Chunk;
import net.querz.mcaselector.io.mca.ChunkData;
import net.querz.mcaselector.nbtstudio.NbtQuery;
import net.querz.mcaselector.nbtstudio.NbtReplaceResult;
import net.querz.mcaselector.nbtstudio.NbtReplacer;
import net.querz.nbt.Tag;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * NBT Studio style find and replace, applied to every chunk this field is executed on.
 *
 * <p>Syntax: {@code <query> => <replacement>}, optionally prefixed with a target selector:</p>
 *
 * <pre>
 *   @name  name=Old => New                        rename keys
 *   @value name=Items value=Diamond => Emerald    replace values
 *   @both  name=Old => New                        replace names and values
 *   value=minecraft:stone => minecraft:dirt       target is derived from the query
 * </pre>
 *
 * <p>When the query only defines a {@code name} pattern, keys are replaced. When it only defines a
 * {@code value} pattern (or is a bare pattern), values are replaced. When both are defined, names and
 * values are replaced. An explicit {@code @name}, {@code @value} or {@code @both} prefix always wins
 * over this derivation. Regular expressions with capture groups ({@code $1}) are supported in
 * {@code regex} mode.</p>
 *
 * <p>The {@code root=} part of the query is respected, so a replacement can be limited to
 * {@code region}, {@code poi} or {@code entities} data.</p>
 */
public class NbtReplaceField extends Field<String> {

	private static final Logger LOGGER = LogManager.getLogger(NbtReplaceField.class);

	private static final String SEPARATOR = "=>";

	private static final AtomicLong TOTAL_REPLACED = new AtomicLong();
	private static final AtomicLong TOTAL_SKIPPED = new AtomicLong();

	private NbtQuery query;
	private String replacement;
	private boolean doNames;
	private boolean doValues;

	public NbtReplaceField() {
		super(FieldType.NBT_REPLACE);
	}

	@Override
	public String getOldValue(ChunkData data) {
		return null;
	}

	@Override
	public boolean parseNewValue(String s) {
		if (s == null) {
			setNewValue(null);
			return false;
		}
		String raw = s.trim();
		int index = indexOfSeparator(raw);
		if (index < 0) {
			// without a replacement this field can never do anything, so report it as invalid
			setNewValue(null);
			return false;
		}

		String queryPart = raw.substring(0, index).trim();
		String replacementPart = raw.substring(index + SEPARATOR.length()).trim();
		String lower = queryPart.toLowerCase(Locale.ROOT);

		NbtReplacer.Target explicit = null;
		int prefixLength = 0;
		if (lower.startsWith("@both")) {
			explicit = NbtReplacer.Target.BOTH;
			prefixLength = 5;
		} else if (lower.startsWith("@name")) {
			explicit = NbtReplacer.Target.NAME;
			prefixLength = 5;
		} else if (lower.startsWith("@value")) {
			explicit = NbtReplacer.Target.VALUE;
			prefixLength = 6;
		}
		if (prefixLength > 0) {
			queryPart = queryPart.substring(prefixLength).trim();
		}

		NbtQuery parsed = NbtQuery.parse(queryPart);
		if (!parsed.isValid()) {
			setNewValue(null);
			return false;
		}

		boolean hasName = parsed.getNamePattern() != null;
		boolean hasValue = parsed.getValuePattern() != null;

		if (explicit == null) {
			// like NBT Studio the target is derived from what the query constrains; a query that
			// only constrains the path would overwrite every value below that path, so it has to
			// be made explicit with @name or @value
			if (!hasName && !hasValue) {
				setNewValue(null);
				return false;
			}
			explicit = hasName && hasValue ? NbtReplacer.Target.BOTH
					: hasName ? NbtReplacer.Target.NAME : NbtReplacer.Target.VALUE;
		}

		query = parsed;
		replacement = replacementPart;
		doNames = explicit != NbtReplacer.Target.VALUE;
		doValues = explicit != NbtReplacer.Target.NAME;

		setNewValue(raw);
		return true;
	}

	/**
	 * Finds the separator that splits query and replacement, ignoring separators inside quoted
	 * patterns (e.g. {@code name="a=>b" => c}).
	 */
	static int indexOfSeparator(String raw) {
		boolean quoted = false;
		char quote = 0;
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			if (quoted) {
				if (c == '\\' && i + 1 < raw.length()) {
					i++;
				} else if (c == quote) {
					quoted = false;
				}
				continue;
			}
			if (c == '"' || c == '\'') {
				quoted = true;
				quote = c;
				continue;
			}
			if (c == '=' && i + 1 < raw.length() && raw.charAt(i + 1) == '>') {
				return i;
			}
		}
		return -1;
	}

	@Override
	public void change(ChunkData data) {
		force(data);
	}

	@Override
	public void force(ChunkData data) {
		if (query == null || replacement == null || data == null || (!doNames && !doValues)) {
			return;
		}
		try {
			NbtReplaceResult result = new NbtReplaceResult();
			result.merge(replaceIn(data.region(), NbtQuery.ROOT_REGION));
			result.merge(replaceIn(data.poi(), NbtQuery.ROOT_POI));
			result.merge(replaceIn(data.entities(), NbtQuery.ROOT_ENTITIES));
			if (result.getReplaced() > 0) {
				TOTAL_REPLACED.addAndGet(result.getReplaced());
			}
			if (result.getSkipped() > 0) {
				TOTAL_SKIPPED.addAndGet(result.getSkipped());
				LOGGER.debug("nbt replace \"{}\" skipped {} matches: {}", getNewValue(), result.getSkipped(),
						result.getSkipSamples());
			}
			if (result.getReplaced() > 0) {
				LOGGER.debug("nbt replace \"{}\": {}", getNewValue(), result);
			}
		} catch (Exception ex) {
			LOGGER.warn("failed to apply nbt replacement \"{}\"", getNewValue(), ex);
		}
	}

	private NbtReplaceResult replaceIn(Chunk chunk, int rootMask) {
		NbtReplaceResult result = new NbtReplaceResult();
		Tag tag = chunk == null ? null : chunk.getData();
		if (tag == null || !query.matchesRoot(rootMask)) {
			return result;
		}
		// one pass for both targets, so names and values are rewritten on the same snapshot
		result.merge(NbtReplacer.replace(tag, query, doNames, doValues, replacement));
		return result;
	}

	/**
	 * @return how many names and values have been replaced since the JVM started, useful for logging
	 */
	public static long getTotalReplaced() {
		return TOTAL_REPLACED.get();
	}

	/**
	 * @return how many matching names and values could not be replaced since the JVM started
	 */
	public static long getTotalSkipped() {
		return TOTAL_SKIPPED.get();
	}

	@Override
	public String valueToString() {
		return getNewValue() == null ? "" : getNewValue();
	}
}
