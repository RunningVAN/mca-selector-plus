package net.querz.mcaselector.ui;

import net.querz.mcaselector.io.JobHandler;
import net.querz.mcaselector.io.RegionDirectories;
import net.querz.mcaselector.io.job.ProcessDataJob;
import net.querz.mcaselector.util.point.Point2i;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the wait that keeps the chunk half of "find" from leaving its dialog open.
 *
 * <p>The progress dialog closes when the task reports its last unit of progress. A queued region
 * job that never reports leaves it open until the user cancels, which is exactly what was reported.
 * The wait closes the dialog once the queue is empty, and gives up on a queue that stopped
 * reporting instead of holding the window forever.</p>
 */
public class NbtStudioDrainJobsTest {

	@BeforeAll
	static void initToolkit() throws InterruptedException {
		// the progress task reports through Platform.runLater
		CountDownLatch latch = new CountDownLatch(1);
		try {
			Platform.startup(latch::countDown);
		} catch (IllegalStateException alreadyStarted) {
			latch.countDown();
		}
		latch.await();
	}

	@BeforeEach
	void clearQueue() {
		// one test leaves a slow job queued, which the next one would otherwise wait for
		JobHandler.cancelAllJobsAndFlush();
	}

	private static ProgressTask task() {
		return new ProgressTask() {
			@Override
			protected Void call() {
				return null;
			}
		};
	}

	private static RegionDirectories dirs() {
		return new RegionDirectories(new Point2i(0, 0), new File("region/r.0.0.mca"),
				new File("poi/r.0.0.mca"), new File("entities/r.0.0.mca"));
	}

	private static ProcessDataJob job(long millis) {
		return new ProcessDataJob(dirs(), 0) {
			@Override
			public boolean execute() {
				try {
					Thread.sleep(millis);
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
				return true;
			}
		};
	}

	@Test
	public void emptyQueueReturnsAtOnce() {
		long start = System.currentTimeMillis();
		NbtStudioFinder.drainJobs(task());
		assertTrue(System.currentTimeMillis() - start < 1_000, "an empty queue must not be waited on");
	}

	@Test
	public void waitsForQueuedJobsToFinish() {
		JobHandler.addJob(job(400));
		long start = System.currentTimeMillis();
		NbtStudioFinder.drainJobs(task());
		long spent = System.currentTimeMillis() - start;
		assertTrue(spent >= 300, "it has to wait for the queue, waited only " + spent + "ms");
		assertTrue(JobHandler.getActiveJobs() == 0, "it only returns once the queue is empty");
	}

	/**
	 * The wait runs on the worker thread that runs the progress task, so it must not touch the
	 * JavaFX accessors on that task: reading {@code getProgress()} from there throws
	 * "Task must only be used from the FX Application Thread", which aborted the whole chunk pass
	 * and made "find" return data files only.
	 */
	@Test
	public void runsOffTheFxThreadWhileJobsReport() throws Exception {
		ProgressTask reporting = new ProgressTask() {
			@Override
			protected Void call() {
				return null;
			}
		};
		reporting.setMax(2);
		JobHandler.addJob(job(400));

		AtomicReference<Throwable> thrown = new AtomicReference<>();
		Thread worker = new Thread(() -> {
			try {
				NbtStudioFinder.drainJobs(reporting);
			} catch (Throwable t) {
				thrown.set(t);
			}
		}, "progress-worker");
		worker.start();
		worker.join(10_000);

		assertFalse(worker.isAlive(), "the wait has to end once the queue drains");
		assertTrue(thrown.get() == null, "the wait must not use FX-thread-only Task accessors: " + thrown.get());
	}

	@Test
	public void stopsWaitingWhenTheQueueStopsReporting() {
		// a job that runs far longer than the stall limit stands in for a queue that hung
		JobHandler.addJob(job(3_000));
		long start = System.currentTimeMillis();
		NbtStudioFinder.drainJobs(task(), 300);
		long spent = System.currentTimeMillis() - start;
		assertTrue(spent < 2_500, "it has to give up on a stuck queue, waited " + spent + "ms");
	}

	@Test
	public void cancelledTaskStopsWaiting() {
		ProgressTask t = task();
		AtomicBoolean returned = new AtomicBoolean(false);
		Thread runner = new Thread(() -> {
			t.cancelTask();
			NbtStudioFinder.drainJobs(t);
			returned.set(true);
		});
		JobHandler.addJob(job(3_000));
		runner.start();
		try {
			runner.join(1_500);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
		assertFalse(runner.isAlive(), "a cancelled run has to stop waiting");
		assertTrue(returned.get());
	}
}
