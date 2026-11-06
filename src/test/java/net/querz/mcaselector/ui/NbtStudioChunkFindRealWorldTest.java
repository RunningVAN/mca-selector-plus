package net.querz.mcaselector.ui;

import net.querz.mcaselector.config.ConfigProvider;
import net.querz.mcaselector.config.WorldConfig;
import net.querz.mcaselector.filter.Operator;
import net.querz.mcaselector.filter.filters.GroupFilter;
import net.querz.mcaselector.filter.filters.NbtFilter;
import net.querz.mcaselector.io.JobHandler;
import net.querz.mcaselector.io.job.SelectFilterJob;
import net.querz.mcaselector.io.WorldDirectories;
import net.querz.mcaselector.selection.Selection;
import net.querz.mcaselector.text.Translation;
import net.querz.mcaselector.util.point.Point2i;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Replays the chunk half of "find" on a real world and checks that it converges on its own.
 *
 * <p>The chunk pass is asynchronous: {@link SelectFilterJob} queues one job per region and the
 * progress dialog only closes once the task reports its last unit, so a job that never reports
 * leaves the dialog open until the user cancels. This runs the same call the dialog makes and
 * fails when the run neither finishes nor keeps reporting.</p>
 */
public class NbtStudioChunkFindRealWorldTest {

	private static final File WORLD = new File(System.getProperty("world",
			System.getProperty("user.home") + "/Documents/Minecraft/科塔丘实验室4demo（联壁汉化1.2）"));

	private static boolean toolkit = true;

	@BeforeAll
	static void initToolkit() throws InterruptedException {
		CountDownLatch latch = new CountDownLatch(1);
		try {
			Platform.startup(latch::countDown);
		} catch (IllegalStateException alreadyStarted) {
			latch.countDown();
		} catch (UnsupportedOperationException | ExceptionInInitializerError noDisplay) {
			toolkit = false;
			latch.countDown();
		}
		latch.await();
		assumeTrue(toolkit, "no JavaFX toolkit available");
	}

	@Test
	public void chunkFindReportsEveryRegionAndFinishes() throws Exception {
		assumeTrue(new File(WORLD, "region").isDirectory(), "world not present");

		Translation.load(java.util.Locale.SIMPLIFIED_CHINESE);
		ConfigProvider.WORLD = new WorldConfig();
		ConfigProvider.WORLD.setWorldDirs(new WorldDirectories(new File(WORLD, "region"),
				new File(WORLD, "poi"), new File(WORLD, "entities")));

		NbtFilter nbtFilter = new NbtFilter();
		nbtFilter.setFilterValue("value=minecraft:stone");
		GroupFilter filter = new GroupFilter(Operator.AND);
		filter.addFilter(nbtFilter);

		List<Point2i> found = Collections.synchronizedList(new ArrayList<>());
		CountDownLatch finished = new CountDownLatch(1);
		AtomicInteger reports = new AtomicInteger();
		AtomicInteger max = new AtomicInteger();

		// count the units the run reports, so a stalled queue is visible and not just a timeout
		ProgressTask counting = new ProgressTask() {
			@Override
			protected Void call() {
				return null;
			}

			@Override
			public void setMax(int value) {
				max.set(value);
				super.setMax(value);
			}

			@Override
			public void updateProgress(String info, double progress, int max) {
				reports.incrementAndGet();
				super.updateProgress(info, progress, max);
			}
		};
		counting.setOnFinish(finished::countDown);

		SelectFilterJob.selectFilter(filter, null, 0, s -> DialogHelper.collectChunks(s, found), counting, false);

		// this is the wait the find dialog does before it closes itself; the progress reports alone
		// are not enough, because a region job can drop out without reporting its unit
		NbtStudioFinder.drainJobs(counting, 60_000);
		boolean drained = JobHandler.getActiveJobs() == 0;
		System.out.println("chunk find drained=" + drained
				+ " reports=" + reports.get() + "/" + max.get() + " chunks=" + found.size());
		assertTrue(drained, "the chunk queue never emptied, so the dialog would hang");
		assertTrue(found.size() > 0, "the run has to find the chunks it reports");
	}
}
