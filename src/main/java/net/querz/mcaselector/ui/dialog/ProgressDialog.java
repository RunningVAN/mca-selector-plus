package net.querz.mcaselector.ui.dialog;

import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import net.querz.mcaselector.io.JobHandler;
import net.querz.mcaselector.text.Translation;
import net.querz.mcaselector.ui.ProgressTask;
import net.querz.mcaselector.ui.UIFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;
import java.util.function.Consumer;

public class ProgressDialog extends Stage {

	private static final Logger LOGGER = LogManager.getLogger(ProgressDialog.class);

	private final Label title = new Label();
	private final ProgressBar progressBar = new ProgressBar(-1);
	private final Label label = UIFactory.label(Translation.DIALOG_PROGRESS_RUNNING);

	private ProgressTask currentTask;

	private final VBox box = new VBox();

	public ProgressDialog(Translation title, Window primaryStage) {
		initStyle(StageStyle.TRANSPARENT);
		setResizable(false);
		initModality(Modality.APPLICATION_MODAL);
		initOwner(primaryStage);

		this.title.textProperty().bind(title.getProperty());
		this.title.getStyleClass().add("progress-title");

		label.getStyleClass().add("progress-info");

		box.getStyleClass().add("progress-dialog");
		box.getChildren().addAll(this.title, progressBar, label);

		progressBar.prefWidthProperty().bind(box.widthProperty());
		this.title.prefWidthProperty().bind(box.widthProperty());
		label.prefWidthProperty().bind(box.widthProperty());

		Scene scene = new Scene(box);
		scene.setFill(Color.TRANSPARENT);

		scene.getStylesheets().addAll(primaryStage.getScene().getStylesheets());
		scene.getStylesheets().add(Objects.requireNonNull(ProgressDialog.class.getClassLoader().getResource("style/component/progress-dialog.css")).toExternalForm());

		setScene(scene);
	}

	public void showProgressBar(Consumer<ProgressTask> r) {
		JobHandler.setTrimSaveData(false);
		currentTask = new ProgressTask() {
			@Override
			protected Void call() {
				r.accept(this);
				return null;
			}
		};
		progressBar.progressProperty().bind(currentTask.progressProperty());
		label.textProperty().bind(currentTask.infoProperty());
		currentTask.setOnFinish(this::close);
		Thread thread = new Thread(currentTask);
		thread.start();
		showAndWait();
		JobHandler.setTrimSaveData(true);
	}

	/**
	 * Runs a synchronous body on the progress task and closes the dialog when it returns.
	 *
	 * <p>{@link #showProgressBar(Consumer)} only closes itself when the task reports the last unit
	 * of progress, which is right for work that is split into jobs and reports each finished unit,
	 * but wrong for a body that does one blocking pass over a list: that pass never reports a unit,
	 * so the dialog stayed open until the user pressed cancel. This variant closes unconditionally
	 * when the body returns, including when it fails.</p>
	 */
	public void showProgressBarUntilDone(Consumer<ProgressTask> r) {
		showProgressBar(t -> {
			try {
				r.accept(t);
			} catch (RuntimeException ex) {
				LOGGER.error("error while running progress task", ex);
			} finally {
				// the cancel button already locked the task and closed the dialog itself, so the
				// lock is lifted again to let the finish report through
				t.setLocked(false);
				t.finish(Translation.DIALOG_PROGRESS_DONE.toString());
			}
		});
	}

	public void updateProgress(String status, double progress) {
		progressBar.setProgress(progress);
		label.setText(status);
	}

	protected VBox getBox() {
		return box;
	}

	protected ProgressTask getCurrentTask() {
		return currentTask;
	}
}
