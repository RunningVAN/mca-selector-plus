package net.querz.mcaselector;

import javafx.application.Platform;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Starts the JavaFX toolkit once for the whole test run and keeps it alive.
 *
 * <p>Without this, the toolkit is started on demand by whichever test needs it first and then
 * shuts down as soon as that test closes its last window, because JavaFX exits implicitly when the
 * last window goes away. Every later test then queues its work with {@code Platform.runLater} onto
 * a platform that is gone: the task never runs, its result never arrives and the test hangs until
 * its timeout. Turning implicit exit off once per JVM keeps the platform up for the whole run.</p>
 */
public final class FxTestSupport {

	private static final AtomicBoolean STARTED = new AtomicBoolean();
	private static volatile boolean available = true;

	private FxTestSupport() {}

	public static synchronized void start() {
		if (!STARTED.compareAndSet(false, true)) {
			return;
		}
		CountDownLatch up = new CountDownLatch(1);
		try {
			Platform.startup(up::countDown);
		} catch (IllegalStateException alreadyRunning) {
			up.countDown();
		} catch (UnsupportedOperationException | ExceptionInInitializerError | NoClassDefFoundError noDisplay) {
			available = false;
			up.countDown();
		}
		try {
			up.await(30, TimeUnit.SECONDS);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
		if (!available) {
			return;
		}
		CountDownLatch set = new CountDownLatch(1);
		Platform.runLater(() -> {
			try {
				Platform.setImplicitExit(false);
			} finally {
				set.countDown();
			}
		});
		try {
			set.await(30, TimeUnit.SECONDS);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	public static boolean available() {
		return available;
	}
}
