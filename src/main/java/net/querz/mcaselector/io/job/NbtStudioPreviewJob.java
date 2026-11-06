package net.querz.mcaselector.io.job;

import net.querz.mcaselector.config.ConfigProvider;
import net.querz.mcaselector.io.JobHandler;
import net.querz.mcaselector.io.RegionDirectories;
import net.querz.mcaselector.io.WorldDirectories;
import net.querz.mcaselector.io.mca.ChunkData;
import net.querz.mcaselector.io.mca.EntitiesChunk;
import net.querz.mcaselector.io.mca.PoiChunk;
import net.querz.mcaselector.io.mca.Region;
import net.querz.mcaselector.io.mca.RegionChunk;
import net.querz.mcaselector.nbtstudio.NbtQuery;
import net.querz.mcaselector.nbtstudio.NbtReplaceResult;
import net.querz.mcaselector.nbtstudio.NbtReplacer;
import net.querz.mcaselector.selection.Selection;
import net.querz.mcaselector.text.Translation;
import net.querz.mcaselector.util.point.Point2i;
import net.querz.mcaselector.util.progress.Progress;
import net.querz.nbt.Tag;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Dry run for an NBT Studio replacement.
 *
 * <p>Reads chunks exactly like {@link SelectFilterJob} does and applies the replacement to the
 * loaded (in memory only) tags, so the resulting changes can be shown to the user before anything
 * is written to disk. The scan stops as soon as enough samples were collected or the configured
 * chunk budget is exhausted, which keeps the preview fast even on huge worlds.</p>
 */
public final class NbtStudioPreviewJob extends ProcessDataJob {

	private static final Logger LOGGER = LogManager.getLogger(NbtStudioPreviewJob.class);

	/**
	 * Collects the preview while the jobs run. All methods are synchronized, the caller reads the
	 * final state once the progress dialog is closed, which is guaranteed to happen after the last
	 * job reported its progress.
	 */
	public static final class PreviewAccumulator {

		/**
		 * A sampled change together with the chunk it was found in, so the result table can jump
		 * back to the affected chunk like NBT Studio's search and bulk edit results do.
		 */
		public record Row(Point2i chunk, NbtReplaceResult.Change change) {
		}

		private final int maxChunks;
		private final int maxChanges;
		private final List<Row> rows = new ArrayList<>();
		private int scanned;
		private int matched;
		private int total;
		private boolean full;

		public PreviewAccumulator(int maxChunks, int maxChanges) {
			this.maxChunks = maxChunks;
			this.maxChanges = maxChanges;
		}

		private synchronized void add(Point2i chunk, NbtReplaceResult result) {
			scanned++;
			if (result.getReplaced() > 0) {
				matched++;
				total += result.getReplaced();
			}
			for (NbtReplaceResult.Change change : result.getChanges()) {
				if (rows.size() >= maxChanges) {
					full = true;
					break;
				}
				rows.add(new Row(chunk, change));
			}
			if (scanned >= maxChunks) {
				full = true;
			}
		}

		private synchronized boolean isFull() {
			return full;
		}

		public synchronized int getScannedChunks() {
			return scanned;
		}

		public synchronized int getMatchedChunks() {
			return matched;
		}

		public synchronized int getTotalChanges() {
			return total;
		}

		public synchronized List<Row> getRows() {
			return List.copyOf(rows);
		}

		public synchronized List<NbtReplaceResult.Change> getChanges() {
			List<NbtReplaceResult.Change> changes = new ArrayList<>(rows.size());
			for (Row row : rows) {
				changes.add(row.change());
			}
			return changes;
		}

		public synchronized boolean isTruncated() {
			return full;
		}
	}

	/**
	 * Starts the preview jobs. The returned accumulator fills up while the jobs run.
	 *
	 * @param maxChunks  how many chunks are inspected at most
	 * @param maxChanges how many sample changes are collected at most
	 */
	public static PreviewAccumulator preview(NbtQuery query, boolean names, boolean values, String replacement,
			Selection selection, int maxChunks, int maxChanges, Progress progressChannel, boolean cli) {
		PreviewAccumulator accumulator = new PreviewAccumulator(maxChunks, maxChanges);

		WorldDirectories wd = ConfigProvider.WORLD.getWorldDirs();
		RegionDirectories[] rd = wd.listRegions(selection);
		if (rd == null || rd.length == 0) {
			if (cli) {
				progressChannel.done("no files");
			} else {
				progressChannel.done(Translation.DIALOG_PROGRESS_NO_FILES.toString());
			}
			return accumulator;
		}

		JobHandler.clearQueues();

		progressChannel.setMax(rd.length);
		progressChannel.updateProgress(rd[0].getLocationAsFileName(), 0);

		Consumer<Throwable> errorHandler = t -> progressChannel.incrementProgress("error");

		for (RegionDirectories r : rd) {
			if (accumulator.isFull()) {
				progressChannel.incrementProgress(r.getLocationAsFileName());
				continue;
			}
			NbtStudioPreviewJob job = new NbtStudioPreviewJob(r, query, names, values, replacement, selection,
					progressChannel, accumulator);
			job.errorHandler = errorHandler;
			JobHandler.addJob(job);
		}
		return accumulator;
	}

	private final Progress progressChannel;
	private final NbtQuery query;
	private final boolean names;
	private final boolean values;
	private final String replacement;
	private final Selection selection;
	private final PreviewAccumulator accumulator;

	private NbtStudioPreviewJob(RegionDirectories dirs, NbtQuery query, boolean names, boolean values,
			String replacement, Selection selection, Progress progressChannel, PreviewAccumulator accumulator) {
		super(dirs, PRIORITY_LOW);
		this.query = query;
		this.names = names;
		this.values = values;
		this.replacement = replacement;
		this.selection = selection;
		this.progressChannel = progressChannel;
		this.accumulator = accumulator;
	}

	@Override
	public boolean execute() {
		Point2i location = getRegionDirectories().getLocation();
		try {
			Region region = Region.loadRegion(getRegionDirectories());
			for (int i = 0; i < 1024; i++) {
				Point2i chunkLocation = location.regionToChunk().add(new Point2i(i));
				if (selection != null && !selection.isChunkSelected(chunkLocation)) {
					continue;
				}
				RegionChunk regionChunk = region.getRegion() == null ? null : region.getRegion().getChunk(i);
				PoiChunk poiChunk = region.getPoi() == null ? null : region.getPoi().getChunk(i);
				EntitiesChunk entitiesChunk = region.getEntities() == null ? null : region.getEntities().getChunk(i);
				if (regionChunk == null && poiChunk == null && entitiesChunk == null) {
					continue;
				}

				ChunkData data = new ChunkData(chunkLocation, regionChunk, poiChunk, entitiesChunk, true);
				NbtReplaceResult result = new NbtReplaceResult();
				// the scope of the query has to be respected here too, otherwise a preview would
				// list changes in roots that the later replace (NbtReplaceField) never touches
				Tag[] roots = {tagOf(data.region()), tagOf(data.poi()), tagOf(data.entities())};
				int[] masks = {NbtQuery.ROOT_REGION, NbtQuery.ROOT_POI, NbtQuery.ROOT_ENTITIES};
				for (int rootIndex = 0; rootIndex < roots.length; rootIndex++) {
					Tag tag = roots[rootIndex];
					if (tag == null || !query.matchesRoot(masks[rootIndex])) {
						continue;
					}
					try {
						// work on a deep copy so that a preview can never modify the loaded world data
						result.merge(NbtReplacer.replace(tag.copy(), query, names, values, replacement));
					} catch (Exception ex) {
						LOGGER.debug("failed to preview chunk {}: {}", chunkLocation, ex.getMessage());
					}
				}
				if (result.getExamined() == 0) {
					// nothing in this chunk could match, so it does not count as inspected work
					continue;
				}
				accumulator.add(chunkLocation, result);
				if (accumulator.isFull()) {
					break;
				}
			}
		} catch (Exception ex) {
			LOGGER.warn("error previewing NBT Studio replacement in {}",
					getRegionDirectories().getLocationAsFileName(), ex);
		}
		progressChannel.incrementProgress(getRegionDirectories().getLocationAsFileName());
		return true;
	}

	private static Tag tagOf(Object chunk) {
		if (chunk == null) {
			return null;
		}
		if (chunk instanceof RegionChunk regionChunk) {
			return regionChunk.getData();
		}
		if (chunk instanceof PoiChunk poiChunk) {
			return poiChunk.getData();
		}
		if (chunk instanceof EntitiesChunk entitiesChunk) {
			return entitiesChunk.getData();
		}
		return null;
	}
}
