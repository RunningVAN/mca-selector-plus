package net.querz.mcaselector.ui;

import javafx.application.Platform;
import javafx.stage.Stage;
import net.querz.mcaselector.filter.Operator;
import net.querz.mcaselector.filter.filters.GroupFilter;
import net.querz.mcaselector.filter.filters.NbtFilter;
import net.querz.mcaselector.io.JobHandler;
import net.querz.mcaselector.io.job.SelectFilterJob;
import net.querz.mcaselector.nbtstudio.NbtDataFiles;
import net.querz.mcaselector.nbtstudio.NbtQuery;
import net.querz.mcaselector.selection.Selection;
import net.querz.mcaselector.text.Translation;
import net.querz.mcaselector.tile.TileMap;
import net.querz.mcaselector.ui.dialog.CancellableProgressDialog;
import net.querz.mcaselector.ui.dialog.ErrorDialog;
import net.querz.mcaselector.util.point.Point2i;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs one NBT Studio search over both halves of a world: the chunks on the map and the data files
 * that have no coordinates.
 *
 * <p>It lives outside the dialog so the dialog can run a search without closing itself: the results
 * are filled into the same window that holds the fields, instead of being handed to a second window
 * that the user has to open to see them.</p>
 */
public final class NbtStudioFinder {

	private static final Logger LOGGER = LogManager.getLogger(NbtStudioFinder.class);

	private NbtStudioFinder() {}

	/**
	 * Selects every matching chunk on the map and returns their coordinates, sorted the way the map
	 * reads, so the editor can walk them left to right and top to bottom.
	 */
	public static List<Point2i> findChunks(TileMap tileMap, NbtQuery query, Stage primaryStage) {
		NbtFilter nbtFilter = new NbtFilter();
		nbtFilter.setFilterValue(query.canonical());
		if (!nbtFilter.isValid()) {
			new ErrorDialog(primaryStage, Translation.DIALOG_NBT_STUDIO_INVALID_QUERY + ": " + query.canonical());
			return List.of();
		}
		GroupFilter filter = new GroupFilter(Operator.AND);
		filter.addFilter(nbtFilter);

		// "find" is navigation, not accumulation: the previous selection is dropped
		Selection selection = null;
		List<Point2i> foundChunks = Collections.synchronizedList(new ArrayList<>());
		tileMap.clearSelection();
		CancellableProgressDialog c = new CancellableProgressDialog(
				Translation.DIALOG_PROGRESS_TITLE_SELECTING_FILTERED_CHUNKS, primaryStage);
		// repainting the map once per finished region floods the FX queue on a big world and makes
		// the dialog look frozen, so only every few regions repaint and the map is drawn once more
		// when the run is over
		AtomicInteger regions = new AtomicInteger();
		c.showProgressBarUntilDone(t -> {
			// a failure in here must not take the chunk half down with it: everything found up to
			// that point is still returned instead of the search silently dropping to data files only
			try {
				SelectFilterJob.selectFilter(
						filter,
						selection,
						0,
						s -> {
							DialogHelper.collectChunks(s, foundChunks);
							boolean repaint = regions.incrementAndGet() % 16 == 1;
							Platform.runLater(() -> {
								tileMap.addSelection(s);
								if (repaint) {
									tileMap.draw();
								}
							});
						},
						t,
						false);
				// the dialog otherwise only closes once the run reports its last region; a job that
				// is cancelled or dies before reporting leaves it open until the user presses cancel
				drainJobs(t);
			} catch (RuntimeException ex) {
				LOGGER.error("chunk find failed, returning the {} chunks found so far",
						foundChunks.size(), ex);
			}
		});
		tileMap.draw();

		if (c.cancelled()) {
			return List.of();
		}
		List<Point2i> hitChunks = DialogHelper.sortChunks(foundChunks);
		if (!hitChunks.isEmpty()) {
			Point2i firstHit = hitChunks.get(0);
			tileMap.goTo(firstHit.getX(), firstHit.getZ());
		}
		return hitChunks;
	}

	/**
	 * Waits until the queued region jobs are done so the dialog can close by itself.
	 *
	 * <p>The progress dialog closes when the task reports its last unit of progress, which assumes
	 * every queued region job reports exactly one. A job that is cancelled or fails before it
	 * reports never delivers that last unit and the dialog stayed open until the user cancelled.
	 * Waiting for the job queue to empty closes it either way; a run that stops reporting for a
	 * minute is treated as stuck and closed too, so the found chunks stay reachable.</p>
	 */
	static void drainJobs(ProgressTask t) {
		drainJobs(t, 60_000);
	}

	static void drainJobs(ProgressTask t, long stalledMillis) {
		// the reported unit count, not Task#getProgress(): this runs on the worker thread that runs
		// the task, and that accessor throws "Task must only be used from the FX Application
		// Thread". Thrown from here it aborted the whole chunk pass, the dialog closed on the spot
		// and "find" came back with no chunk at all, only the data files
		int last = Integer.MIN_VALUE;
		long lastChange = System.currentTimeMillis();
		while (!t.taskCancelled() && JobHandler.getActiveJobs() > 0) {
			int reported = t.reportedUnits();
			if (reported != last) {
				last = reported;
				lastChange = System.currentTimeMillis();
			} else if (System.currentTimeMillis() - lastChange > stalledMillis) {
				LOGGER.warn("chunk find stopped reporting, closing its dialog with {} jobs left",
						JobHandler.getActiveJobs());
				return;
			}
			try {
				Thread.sleep(100);
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	/** Runs the query over the world data files and returns the matches. */
	public static NbtDataFiles.SearchOutcome findInDataFiles(List<NbtDataFiles.FileRef> dataFiles,
			NbtQuery query, Stage primaryStage) {
		if (dataFiles == null || dataFiles.isEmpty()) {
			return null;
		}
		// the data files are not split into jobs, so this run is one blocking pass and has to close
		// its own dialog: the plain showProgressBar only closes when the task reports the last unit
		// of progress, which never happened here and left the dialog open until the user cancelled
		CancellableProgressDialog c = new CancellableProgressDialog(
				Translation.DIALOG_PROGRESS_TITLE_SEARCHING_DATA_FILES, primaryStage);
		NbtDataFiles.SearchOutcome[] holder = new NbtDataFiles.SearchOutcome[1];
		c.showProgressBarUntilDone(t -> holder[0] = NbtDataFiles.search(dataFiles, query, t));
		return holder[0];
	}
}
