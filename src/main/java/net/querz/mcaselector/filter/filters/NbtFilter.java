package net.querz.mcaselector.filter.filters;

import net.querz.mcaselector.filter.Comparator;
import net.querz.mcaselector.filter.FilterType;
import net.querz.mcaselector.filter.Operator;
import net.querz.mcaselector.filter.TextFilter;
import net.querz.mcaselector.io.mca.ChunkData;
import net.querz.mcaselector.nbtstudio.NbtQuery;
import net.querz.mcaselector.nbtstudio.NbtSearchResult;
import net.querz.mcaselector.nbtstudio.NbtSearcher;
import net.querz.nbt.Tag;

/**
 * NBT Studio style filter. The filter value is a {@link NbtQuery} string, e.g.
 *
 * <pre>
 *   name=Items value=diamond mode=glob
 *   path=Sections[*].biomes.palette value=minecraft:plains
 *   value=^minecraft:stone$ mode=regex case=insensitive
 *   diamonds
 * </pre>
 *
 * A chunk matches when at least one node inside its region, poi or entities data matches the query.
 */
public class NbtFilter extends TextFilter<String> {

	private static final Comparator[] comparators = {
			Comparator.CONTAINS,
			Comparator.CONTAINS_NOT
	};

	private transient volatile NbtQuery cached;
	private transient volatile String cachedRaw;

	public NbtFilter() {
		this(Operator.AND, Comparator.CONTAINS, null);
	}

	private NbtFilter(Operator operator, Comparator comparator, String value) {
		super(FilterType.NBT, operator, comparator, value);
		if (value != null) {
			setRawValue(value);
			cachedRaw = value;
			cached = NbtQuery.parse(value);
		}
	}

	@Override
	public Comparator[] getComparators() {
		return comparators;
	}

	@Override
	public boolean matches(ChunkData data) {
		return switch (getComparator()) {
			case CONTAINS -> contains(value, data);
			case CONTAINS_NOT -> containsNot(value, data);
			default -> false;
		};
	}

	@Override
	public boolean contains(String value, ChunkData data) {
		NbtQuery query = query(value);
		if (query == null || !query.isValid() || data == null) {
			return false;
		}
		Tag region = data.region() == null ? null : data.region().getData();
		Tag poi = data.poi() == null ? null : data.poi().getData();
		Tag entities = data.entities() == null ? null : data.entities().getData();
		NbtSearchResult result = NbtSearcher.search(region, poi, entities, query);
		if (result.getTotal() > 0) {
			return true;
		}
		return false;
	}

	@Override
	public boolean containsNot(String value, ChunkData data) {
		NbtQuery query = query(value);
		if (query == null || !query.isValid() || data == null) {
			// an invalid query must not match chunks just because it doesn't contain anything
			return false;
		}
		return !contains(value, data);
	}

	@Override
	public boolean intersects(String value, ChunkData data) {
		return contains(value, data);
	}

	@Override
	public boolean intersectsNot(String value, ChunkData data) {
		return containsNot(value, data);
	}

	private NbtQuery query(String raw) {
		if (raw == null) {
			return null;
		}
		NbtQuery local = cached;
		if (local != null && raw.equals(cachedRaw)) {
			return local;
		}
		local = NbtQuery.parse(raw);
		cachedRaw = raw;
		cached = local;
		return local;
	}

	@Override
	public void setFilterValue(String raw) {
		NbtQuery query = NbtQuery.parse(raw);
		if (query.isValid()) {
			setValid(true);
			setValue(raw);
			setRawValue(raw);
			cached = query;
			cachedRaw = raw;
		} else {
			setValid(false);
			setValue(null);
			cached = null;
			cachedRaw = raw;
		}
	}

	@Override
	public String getFormatText() {
		return "name=<n> value=<v> path=<p> mode=<smart|contains|exact|glob|regex> [case=insensitive] [root=<region,poi,entities>] [type=<string,int,...>]";
	}

	@Override
	public String toString() {
		return "NBT " + getComparator().getQueryString() + " \"" + getRawValue() + "\"";
	}

	@Override
	public NbtFilter clone() {
		return new NbtFilter(getOperator(), getComparator(), value);
	}
}
