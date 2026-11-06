package net.querz.mcaselector.nbtstudio;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregated result of an NBT search. Hits are capped by the limit of the {@link NbtQuery},
 * the total amount of matches is tracked separately.
 */
public final class NbtSearchResult {

	private final List<NbtSearchHit> hits = new ArrayList<>();
	private int total;
	private int limit;

	public NbtSearchResult(int limit) {
		this.limit = limit <= 0 ? Integer.MAX_VALUE : limit;
	}

	void add(NbtSearchHit hit) {
		total++;
		if (hits.size() < limit) {
			hits.add(hit);
		}
	}

	public void merge(NbtSearchResult other) {
		if (other == null) {
			return;
		}
		this.total += other.total;
		for (NbtSearchHit hit : other.hits) {
			if (this.hits.size() >= this.limit) {
				break;
			}
			this.hits.add(hit);
		}
	}

	public List<NbtSearchHit> getHits() {
		return hits;
	}

	/**
	 * @return the total amount of matches, which can be larger than {@link #getHits()}.size()
	 */
	public int getTotal() {
		return total;
	}

	public boolean isTruncated() {
		return total > hits.size();
	}

	public boolean isEmpty() {
		return total == 0;
	}

	public String summary() {
		if (total == 0) {
			return "no matches";
		}
		if (isTruncated()) {
			return total + " matches (" + hits.size() + " shown)";
		}
		return total + (total == 1 ? " match" : " matches");
	}
}
