package net.querz.mcaselector.nbtstudio;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of a find &amp; replace operation on a chunk.
 */
public final class NbtReplaceResult {

	/** upper bound for the collected change list so previews stay small */
	public static final int MAX_CHANGES = 500;

	private int replaced;
	private int unchanged;
	private int skipped;
	private int examined;
	private final List<String> samples = new ArrayList<>();
	private final List<String> skipSamples = new ArrayList<>();
	private final List<Change> changes = new ArrayList<>();

	/**
	 * A single name or value change, used to preview what a replacement is going to do.
	 */
	public record Change(String path, String oldValue, String newValue) {
	}

	void examine() {
		examined++;
	}

	void replace(String sample) {
		replaced++;
		if (samples.size() < 20) {
			samples.add(sample);
		}
	}

	void change(String path, String oldValue, String newValue) {
		if (changes.size() < MAX_CHANGES) {
			changes.add(new Change(path, oldValue, newValue));
		}
	}

	void change(Change change) {
		if (changes.size() < MAX_CHANGES) {
			changes.add(change);
		}
	}

	/**
	 * A match whose replacement would not change anything. Like NBT Studio these are neither
	 * counted as changes nor reported as problems.
	 */
	void unchanged() {
		unchanged++;
	}

	void skip() {
		skipped++;
	}

	void skip(String reason) {
		skipped++;
		if (reason != null && skipSamples.size() < 20 && !skipSamples.contains(reason)) {
			skipSamples.add(reason);
		}
	}

	public void merge(NbtReplaceResult other) {
		if (other == null) {
			return;
		}
		this.replaced += other.replaced;
		this.unchanged += other.unchanged;
		this.skipped += other.skipped;
		this.examined += other.examined;
		for (String sample : other.samples) {
			if (this.samples.size() >= 20) {
				break;
			}
			this.samples.add(sample);
		}
		for (String sample : other.skipSamples) {
			if (this.skipSamples.size() >= 20) {
				break;
			}
			if (!this.skipSamples.contains(sample)) {
				this.skipSamples.add(sample);
			}
		}
	}

	/**
	 * @return the number of successfully replaced names/values
	 */
	public int getReplaced() {
		return replaced;
	}

	/**
	 * @return the number of matches that already had the requested name or value
	 */
	public int getUnchanged() {
		return unchanged;
	}

	/**
	 * @return the number of matches that could not be replaced, e.g. because the new value does not fit the nbt type
	 */
	public int getSkipped() {
		return skipped;
	}

	public int getExamined() {
		return examined;
	}

	/**
	 * @return up to {@link #MAX_CHANGES} changes that this replacement would apply
	 */
	public List<Change> getChanges() {
		return changes;
	}

	public List<String> getSamples() {
		return samples;
	}

	/**
	 * @return up to 20 distinct reasons why matches could not be replaced
	 */
	public List<String> getSkipSamples() {
		return skipSamples;
	}

	public boolean isEmpty() {
		return replaced == 0;
	}

	@Override
	public String toString() {
		String s = replaced + " replaced";
		if (skipped > 0) {
			s += ", " + skipped + " skipped";
		}
		return s;
	}
}
