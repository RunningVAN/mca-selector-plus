package net.querz.mcaselector.ui.dialog;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import net.querz.mcaselector.nbtstudio.NbtDataFiles;
import net.querz.mcaselector.nbtstudio.NbtFileListing;
import net.querz.mcaselector.text.Translation;
import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Lists every editable data file of the world ({@code level.dat}, {@code data}, {@code playerdata},
 * the same directories of every dimension and the structure files) and opens any of them in the
 * {@link NbtFileEditorDialog} without searching first.
 *
 * <p>This is the entry the find results can not replace: a search only ever shows the files that
 * matched, so a file nobody thought of can not be reached that way. Here the whole world is browsable
 * and the filter above the tree only narrows the list down - it never decides what the editor can
 * reach.</p>
 *
 * <p>The dialog stays open while files are edited, so several files can be opened one after another
 * and the file list reflects the sizes the saves left behind.</p>
 */
public class NbtFileBrowserDialog extends Dialog<Void> {

	private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
			.withZone(ZoneId.systemDefault());

	private final Supplier<List<NbtDataFiles.FileRef>> source;
	private final Stage primaryStage;

	private final TreeTableView<Object> tree = new TreeTableView<>();
	private final TextField filter = new TextField();
	private final Label summary = new Label();
	private final Label placeholder = new Label();
	private final Button open = new Button(Translation.DIALOG_NBT_STUDIO_FILE_BROWSER_OPEN.toString());
	private final Button refresh = new Button(Translation.DIALOG_NBT_STUDIO_FILE_BROWSER_REFRESH.toString());

	private List<NbtDataFiles.FileRef> files = List.of();

	private HBox toolbar;

	public NbtFileBrowserDialog(Supplier<List<NbtDataFiles.FileRef>> source, Stage primaryStage) {
		this.source = source;
		this.primaryStage = primaryStage;
		titleProperty().bind(Translation.DIALOG_NBT_STUDIO_FILE_BROWSER_TITLE.getProperty());
		initStyle(StageStyle.UTILITY);
		setResizable(true);

		getDialogPane().getStyleClass().addAll("nbt-studio-dialog-pane", "nbt-file-browser-dialog-pane");
		if (primaryStage != null && primaryStage.getScene() != null) {
			getDialogPane().getScene().getStylesheets().addAll(primaryStage.getScene().getStylesheets());
		}
		var ownStyleSheet = NbtFileBrowserDialog.class.getClassLoader()
				.getResource("style/component/nbt-studio-dialog.css");
		if (ownStyleSheet != null) {
			getDialogPane().getScene().getStylesheets().add(ownStyleSheet.toExternalForm());
		}

		ButtonType close = new ButtonType(Translation.BUTTON_CANCEL.toString(), ButtonBar.ButtonData.CANCEL_CLOSE);
		getDialogPane().getButtonTypes().add(close);

		buildTree();
		buildToolbar();
		reload();

		VBox content = new VBox(6, toolbar, tree);
		VBox.setVgrow(tree, Priority.ALWAYS);
		content.setPadding(new Insets(6, 6, 2, 6));
		content.setPrefWidth(820);
		content.setPrefHeight(560);
		getDialogPane().setContent(content);
		getDialogPane().setPrefWidth(840);
		getDialogPane().setPrefHeight(600);
	}

	private void buildToolbar() {
		Label filterLabel = new Label(Translation.DIALOG_NBT_STUDIO_FILE_BROWSER_FILTER.toString());
		filter.setPromptText(Translation.DIALOG_NBT_STUDIO_FILE_BROWSER_FILTER_PROMPT.toString());
		filter.setPrefWidth(240);
		HBox.setHgrow(filter, Priority.SOMETIMES);
		filter.textProperty().addListener((o, old, value) -> rebuild());

		open.setDefaultButton(true);
		open.setDisable(true);
		open.setOnAction(e -> openSelected());
		refresh.setOnAction(e -> reload());

		summary.getStyleClass().add("nbt-studio-summary");
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		toolbar = new HBox(6, filterLabel, filter, refresh, open, spacer, summary);
		toolbar.setAlignment(Pos.CENTER_LEFT);
		toolbar.setPadding(new Insets(0, 0, 2, 0));
	}

	private void buildTree() {
		tree.setShowRoot(false);
		tree.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
		tree.setPlaceholder(placeholder);
		placeholder.setWrapText(true);

		TreeTableColumn<Object, String> name = new TreeTableColumn<>(
				Translation.DIALOG_NBT_STUDIO_FILE_BROWSER_COLUMN_FILE.toString());
		name.setCellValueFactory(c -> new SimpleStringProperty(nameOf(c.getValue().getValue())));
		name.setPrefWidth(200);

		TreeTableColumn<Object, String> path = new TreeTableColumn<>(
				Translation.DIALOG_NBT_STUDIO_FILE_BROWSER_COLUMN_PATH.toString());
		path.setCellValueFactory(c -> new SimpleStringProperty(pathOf(c.getValue().getValue())));
		path.setPrefWidth(280);

		TreeTableColumn<Object, String> size = new TreeTableColumn<>(
				Translation.DIALOG_NBT_STUDIO_FILE_BROWSER_COLUMN_SIZE.toString());
		size.setCellValueFactory(c -> new SimpleStringProperty(sizeOf(c.getValue().getValue())));
		size.setPrefWidth(90);

		TreeTableColumn<Object, String> modified = new TreeTableColumn<>(
				Translation.DIALOG_NBT_STUDIO_FILE_BROWSER_COLUMN_MODIFIED.toString());
		modified.setCellValueFactory(c -> new SimpleStringProperty(modifiedOf(c.getValue().getValue())));
		modified.setPrefWidth(130);

		tree.getColumns().addAll(List.of(name, path, size, modified));

		tree.setRowFactory(tv -> {
			TreeTableRow<Object> row = new TreeTableRow<>();
			row.setOnMouseClicked(e -> {
				if (e.getButton() != MouseButton.PRIMARY || e.getClickCount() != 2 || row.isEmpty()) {
					return;
				}
				if (row.getItem() instanceof NbtDataFiles.FileRef file) {
					openEditor(file);
				}
			});
			return row;
		});

		tree.getSelectionModel().selectedItemProperty().addListener((o, old, item) -> {
			boolean fileSelected = item != null && item.getValue() instanceof NbtDataFiles.FileRef;
			open.setDisable(!fileSelected);
			if (fileSelected) {
				refresh.setDefaultButton(false);
				open.setDefaultButton(true);
			}
		});

		tree.setOnKeyPressed(e -> {
			if (e.getCode() == KeyCode.ENTER) {
				openSelected();
			}
		});
	}

	/** rereads the files of the world, e.g. after a save changed a file size. */
	private void reload() {
		files = List.copyOf(source.get());
		rebuild();
	}

	private void rebuild() {
		List<NbtFileListing.Group> groups = NbtFileListing.group(files, filter.getText());
		TreeItem<Object> root = new TreeItem<>();

		for (NbtFileListing.Group group : groups) {
			TreeItem<Object> directory = new TreeItem<>(group);
			for (NbtDataFiles.FileRef file : group.files()) {
				directory.getChildren().add(new TreeItem<>(file));
			}
			directory.setExpanded(true);
			root.getChildren().add(directory);
		}

		tree.setRoot(root);
		updateSummary(groups);
	}

	private void updateSummary(List<NbtFileListing.Group> groups) {
		int shown = NbtFileListing.count(groups);
		String text;
		if (filter.getText() != null && !filter.getText().isBlank()) {
			text = String.format(Translation.DIALOG_NBT_STUDIO_FILE_BROWSER_COUNT_FILTERED.toString(),
					shown, files.size());
		} else {
			text = String.format(Translation.DIALOG_NBT_STUDIO_FILE_BROWSER_COUNT.toString(), shown);
		}
		summary.setText(text);

		if (files.isEmpty()) {
			placeholder.setText(Translation.DIALOG_NBT_STUDIO_FILE_BROWSER_EMPTY.toString());
		} else if (shown == 0) {
			placeholder.setText(Translation.DIALOG_NBT_STUDIO_FILE_BROWSER_NO_MATCH.toString());
		}
		open.setDisable(true);
	}

	private void openSelected() {
		TreeItem<Object> item = tree.getSelectionModel().getSelectedItem();
		if (item == null || !(item.getValue() instanceof NbtDataFiles.FileRef file)) {
			return;
		}
		openEditor(file);
	}

	private void openEditor(NbtDataFiles.FileRef file) {
		// the editor is modal for this browser, but the browser survives it: editing data files
		// usually means going through several of them
		new NbtFileEditorDialog(file, null, primaryStage).showAndWait();
		reload();
	}

	private String nameOf(Object item) {
		if (item instanceof NbtDataFiles.FileRef file) {
			File f = file.file();
			return f == null ? file.label() : f.getName();
		}
		if (item instanceof NbtFileListing.Group group) {
			return group.isRoot() ? Translation.DIALOG_NBT_STUDIO_FILE_BROWSER_ROOT.toString() : group.name();
		}
		return "";
	}

	private String pathOf(Object item) {
		if (item instanceof NbtDataFiles.FileRef file) {
			return file.label();
		}
		if (item instanceof NbtFileListing.Group group) {
			return group.isRoot() ? "" : group.directory() + "/";
		}
		return "";
	}

	private String sizeOf(Object item) {
		if (!(item instanceof NbtDataFiles.FileRef file) || file.file() == null) {
			return "";
		}
		long length = file.file().length();
		return file.file().exists() ? bytes(length) : "";
	}

	private String modifiedOf(Object item) {
		if (!(item instanceof NbtDataFiles.FileRef file) || file.file() == null) {
			return "";
		}
		long lastModified = file.file().lastModified();
		return lastModified <= 0 ? "" : TIMESTAMP.format(Instant.ofEpochMilli(lastModified));
	}

	private static String bytes(long length) {
		if (length < 1024) {
			return length + " B";
		}
		double value = length;
		String[] units = {"KB", "MB", "GB"};
		int unit = -1;
		while (value >= 1024 && unit < units.length - 1) {
			value /= 1024;
			unit++;
		}
		return String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
	}
}
