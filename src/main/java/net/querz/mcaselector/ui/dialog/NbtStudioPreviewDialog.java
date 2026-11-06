package net.querz.mcaselector.ui.dialog;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import net.querz.mcaselector.io.job.NbtStudioPreviewJob;
import net.querz.mcaselector.nbtstudio.NbtDataFiles;
import net.querz.mcaselector.selection.Selection;
import net.querz.mcaselector.text.Translation;
import net.querz.mcaselector.tile.TileMap;
import net.querz.mcaselector.util.point.Point2i;
import java.util.List;

/**
 * Shows what an NBT Studio replacement is going to change without writing anything to disk.
 *
 * <p>The table lists the chunk, the path, the old name or value and the new name or value for the
 * collected samples, the header summarizes how many chunks were inspected and how many changes were
 * found. Like the result tree in NBT Studio, a row navigates back to the affected chunk: a double
 * click selects it on the map and centers the view on it, the context menu copies the path.</p>
 */
public class NbtStudioPreviewDialog extends Dialog<Void> {

	public NbtStudioPreviewDialog(NbtStudioPreviewJob.PreviewAccumulator preview, Stage primaryStage) {
		this(preview, primaryStage, null);
	}

	public NbtStudioPreviewDialog(NbtStudioPreviewJob.PreviewAccumulator preview, Stage primaryStage, TileMap tileMap) {
		this(preview, primaryStage, tileMap, null);
	}

	/**
	 * @param filePreview what the same replacement would change in the world data files, or null
	 *                    when the run only covers chunk files
	 */
	public NbtStudioPreviewDialog(NbtStudioPreviewJob.PreviewAccumulator preview, Stage primaryStage,
			TileMap tileMap, NbtDataFiles.ReplaceOutcome filePreview) {
		titleProperty().bind(Translation.DIALOG_NBT_STUDIO_PREVIEW_TITLE.getProperty());
		initStyle(StageStyle.UTILITY);
		setResizable(true);

		getDialogPane().getStyleClass().add("nbt-studio-dialog-pane");
		if (primaryStage != null && primaryStage.getScene() != null) {
			getDialogPane().getScene().getStylesheets().addAll(primaryStage.getScene().getStylesheets());
		}
		var ownStyleSheet = NbtStudioPreviewDialog.class.getClassLoader()
				.getResource("style/component/nbt-studio-dialog.css");
		if (ownStyleSheet != null) {
			getDialogPane().getScene().getStylesheets().add(ownStyleSheet.toExternalForm());
		}

		getDialogPane().getButtonTypes().add(new ButtonType(
				Translation.BUTTON_OK.toString(), ButtonBar.ButtonData.CANCEL_CLOSE));

		List<NbtStudioPreviewJob.PreviewAccumulator.Row> rows = preview.getRows();
		int total = preview.getTotalChanges();
		int matched = preview.getMatchedChunks();
		int scanned = preview.getScannedChunks();

		String headerText = total == 0
				? Translation.DIALOG_NBT_STUDIO_PREVIEW_EMPTY.toString()
				: String.format(Translation.DIALOG_NBT_STUDIO_PREVIEW_HEADER.toString(), total, matched, scanned);
		if (total > rows.size()) {
			headerText += " " + String.format(
					Translation.DIALOG_NBT_STUDIO_PREVIEW_TRUNCATED.toString(), rows.size());
		}

		Label header = new Label(headerText);
		header.getStyleClass().add("nbt-studio-summary");
		header.setWrapText(true);
		header.setMaxWidth(620);
		header.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);

		TableView<NbtStudioPreviewJob.PreviewAccumulator.Row> table =
				new TableView<>(FXCollections.observableArrayList(rows));
		table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
		table.setPrefHeight(320);
		table.setPlaceholder(new Label(Translation.DIALOG_NBT_STUDIO_PREVIEW_EMPTY.toString()));

		TableColumn<NbtStudioPreviewJob.PreviewAccumulator.Row, String> chunk = new TableColumn<>(
				Translation.DIALOG_NBT_STUDIO_COLUMN_CHUNK.toString());
		chunk.setCellValueFactory(c -> new SimpleStringProperty(
				c.getValue().chunk().getX() + " / " + c.getValue().chunk().getZ()));
		chunk.setPrefWidth(110);

		TableColumn<NbtStudioPreviewJob.PreviewAccumulator.Row, String> path = new TableColumn<>(
				Translation.DIALOG_NBT_STUDIO_COLUMN_PATH.toString());
		path.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().change().path()));
		path.setPrefWidth(280);

		TableColumn<NbtStudioPreviewJob.PreviewAccumulator.Row, String> oldValue = new TableColumn<>(
				Translation.DIALOG_NBT_STUDIO_COLUMN_OLD.toString());
		oldValue.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().change().oldValue()));
		oldValue.setPrefWidth(140);

		TableColumn<NbtStudioPreviewJob.PreviewAccumulator.Row, String> newValue = new TableColumn<>(
				Translation.DIALOG_NBT_STUDIO_COLUMN_NEW.toString());
		newValue.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().change().newValue()));
		newValue.setPrefWidth(140);

		table.getColumns().addAll(List.of(chunk, path, oldValue, newValue));

		// double click on a row jumps to the chunk that the change belongs to
		table.setRowFactory(tv -> {
			TableRow<NbtStudioPreviewJob.PreviewAccumulator.Row> row = new TableRow<>();
			row.setOnMouseClicked(e -> {
				if (e.getButton() != MouseButton.PRIMARY || e.getClickCount() != 2 || row.isEmpty() || tileMap == null) {
					return;
				}
				Point2i location = row.getItem().chunk();
				tileMap.clearSelection();
				Selection selection = new Selection();
				selection.addChunk(location);
				tileMap.setSelection(selection);
				tileMap.goTo(location.getX(), location.getZ());
				tileMap.draw();
			});
			MenuItem copyPath = new MenuItem(Translation.DIALOG_NBT_STUDIO_COPY_PATH.toString());
			copyPath.setOnAction(e -> {
				if (row.isEmpty()) {
					return;
				}
				ClipboardContent content = new ClipboardContent();
				content.putString(row.getItem().change().path());
				Clipboard.getSystemClipboard().setContent(content);
			});
			row.setContextMenu(new ContextMenu(copyPath));
			return row;
		});

		VBox content;
		if (filePreview == null || filePreview.changes().isEmpty()) {
			content = new VBox(8, header, table);
			VBox.setVgrow(table, Priority.ALWAYS);
		} else {
			// the same replacement touches chunks and data files: both halves are shown side by
			// side instead of in two windows that open one after the other
			Tab chunkTab = new Tab(String.format(
					Translation.DIALOG_NBT_STUDIO_FIND_RESULT_CHUNKS_TAB.toString(), matched));
			chunkTab.setClosable(false);
			chunkTab.setContent(table);

			Tab fileTab = new Tab(String.format(
					Translation.DIALOG_NBT_STUDIO_FIND_RESULT_FILES_TAB.toString(), filePreview.replaced()));
			fileTab.setClosable(false);
			fileTab.setContent(buildFileChangeTable(filePreview, primaryStage));

			TabPane tabs = new TabPane(chunkTab, fileTab);
			tabs.getSelectionModel().select(0);
			content = new VBox(8, header, tabs);
			VBox.setVgrow(tabs, Priority.ALWAYS);
		}
		content.setPadding(new Insets(4, 6, 2, 6));
		content.setPrefWidth(680);

		getDialogPane().setContent(content);
		getDialogPane().setPrefWidth(700);

		if (primaryStage != null) {
			initOwner(primaryStage);
		}
	}

	/**
	 * The data file half of a preview: one row per change, and a double click opens the tree editor
	 * of that file with the query applied, so the file can be edited before it is written.
	 */
	private TableView<NbtDataFiles.Change> buildFileChangeTable(NbtDataFiles.ReplaceOutcome filePreview,
			Stage primaryStage) {
		TableView<NbtDataFiles.Change> table =
				new TableView<>(FXCollections.observableArrayList(filePreview.changes()));
		table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
		table.setPlaceholder(new Label(Translation.DIALOG_NBT_STUDIO_FILE_RESULT_EMPTY.toString()));

		TableColumn<NbtDataFiles.Change, String> file = new TableColumn<>(
				Translation.DIALOG_NBT_STUDIO_COLUMN_FILE.toString());
		file.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().file().label()));
		file.setPrefWidth(210);

		TableColumn<NbtDataFiles.Change, String> path = new TableColumn<>(
				Translation.DIALOG_NBT_STUDIO_COLUMN_PATH.toString());
		path.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().path()));
		path.setPrefWidth(260);

		TableColumn<NbtDataFiles.Change, String> oldValue = new TableColumn<>(
				Translation.DIALOG_NBT_STUDIO_COLUMN_OLD.toString());
		oldValue.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().oldValue()));
		oldValue.setPrefWidth(150);

		TableColumn<NbtDataFiles.Change, String> newValue = new TableColumn<>(
				Translation.DIALOG_NBT_STUDIO_COLUMN_NEW.toString());
		newValue.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().newValue()));
		newValue.setPrefWidth(150);

		table.getColumns().addAll(List.of(file, path, oldValue, newValue));

		table.setRowFactory(tv -> {
			TableRow<NbtDataFiles.Change> row = new TableRow<>();
			row.setOnMouseClicked(e -> {
				if (e.getButton() != MouseButton.PRIMARY || e.getClickCount() != 2 || row.isEmpty()) {
					return;
				}
				new NbtFileEditorDialog(row.getItem().file(), null, primaryStage).showAndWait();
			});
			return row;
		});
		return table;
	}
}
