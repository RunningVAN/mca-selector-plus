package net.querz.mcaselector.ui;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Task;
import net.querz.mcaselector.util.progress.Progress;
import net.querz.mcaselector.text.Translation;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class ProgressTask extends Task<Void> implements Progress {

	private int max;
	private final AtomicInteger current = new AtomicInteger(0);
	private final StringProperty infoProperty = new SimpleStringProperty(Translation.DIALOG_PROGRESS_RUNNING.toString());
	private Runnable onFinish;
	private boolean locked = false;
	private boolean cancelled = false;

	public ProgressTask() {}

	public ProgressTask(int max) {
		this.max = max;
	}

	@Override
	public void cancelTask() {
		cancelled = true;
		done("");
	}

	@Override
	public boolean taskCancelled() {
		return cancelled;
	}

	@Override
	public void setMax(int max) {
		this.max = max;
	}

	/**
	 * Returns how many units the work has reported so far.
	 *
	 * <p>{@link #getProgress()} is the JavaFX accessor and throws
	 * "Task must only be used from the FX Application Thread" when it is called from the worker
	 * thread that runs the task, so code that watches the run from inside the task body has to read
	 * this counter instead. It is updated by every {@code incrementProgress} call, i.e. by the
	 * region jobs themselves, and is safe to read from any thread.</p>
	 */
	public int reportedUnits() {
		return current.get();
	}

	@Override
	public void incrementProgress(String info) {
		updateProgress(info, current.incrementAndGet(), max);
	}

	@Override
	public void incrementProgress(String info, int count) {
		updateProgress(info, current.addAndGet(count), max);
	}

	public void setLocked(boolean locked) {
		this.locked = locked;
	}

	@Override
	public void done(String info) {
		updateProgress(info, 1, 1);
	}

	/**
	 * Reports the task as finished no matter how many units it had.
	 *
	 * <p>{@link #done(String)} reaches the two argument {@code updateProgress}, which compares
	 * against the task maximum, so a run that set a maximum of n and finished early (or finished a
	 * blocking pass without reporting any unit) never reached it and the dialog stayed open. This
	 * reports a maximum of one that is already reached.</p>
	 */
	public void finish(String info) {
		updateProgress(info, 1.0, 1);
	}

	@Override
	public void setMessage(String msg) {
		Platform.runLater(() -> infoProperty.setValue(msg));
	}

	public void setIndeterminate(String info) {
		Platform.runLater(() -> infoProperty.setValue(info));
		updateProgress(-1, 0);
	}

	@Override
	public void updateProgress(String info, int progress) {
		current.set(progress);
		updateProgress(info, progress, max);
	}

	public void updateProgress(String info, double progress, int max) {
		if (!locked) {
			Platform.runLater(() -> infoProperty.setValue(info));
			updateProgress(progress, max);
			if (progress == max && onFinish != null) {
				Platform.runLater(onFinish);
			}
		}
	}

	public StringProperty infoProperty() {
		return infoProperty;
	}

	public void setOnFinish(Runnable r) {
		onFinish = r;
	}
}
