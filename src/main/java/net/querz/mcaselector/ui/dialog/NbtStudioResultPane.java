package net.querz.mcaselector.ui.dialog;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import net.querz.mcaselector.nbtstudio.NbtDataFiles;
import net.querz.mcaselector.nbtstudio.NbtQuery;
import net.querz.mcaselector.text.Translation;
import net.querz.mcaselector.tile.TileMap;
import net.querz.mcaselector.util.point.Point2i;
import java.util.List;

/**
 * The result half of the NBT Studio window: the chunks that matched and the data files that
 * matched, shown inside the dialog that ran the search instead of in a second window.
 *
 * <p>Chunks live on the map and data files do not, so the two halves stay apart as two tabs, but
 * they belong to the same search: a row opens the editor of what it points at. A chunk row opens
 * the chunk editor with the query applied and every hit expanded, a data file row opens the tree
 * editor of that file with the same query applied. Both can be opened again and again without
 * leaving the window.</p>
 */
public class NbtStudioResultPane extends VBox {

	private final TableView<Point2i> chunkTable = new TableView<>();
	private final TableView<NbtDataFiles.Match> fileTable = new TableView<>();
	private final TabPane tabs = new TabPane();
	private final Label header = new Label();
	private final Button openButton = new Button();
	private final Label errors = new Label();
	private final Tab chunkTab = new Tab();
	private final Tab fileTab = new Tab();
	private final TileMap tileMap;
	private final Stage primaryStage;
	private List<Point2i> hitChunks = List.of();
	private NbtQuery query;

	public NbtStudioResultPane(TileMap tileMap, Stage primaryStage) {
		super(6);
		this.tileMap = tileMap;
		this.primaryStage = primaryStage;

		header.getStyleClass().add("nbt-studio-summary");
		header.setWrapText(true);
		header.setMaxWidth(Double.MAX_VALUE);
		header.setMinHeight(Region.USE_PREF_SIZE);

		errors.getStyleClass().add("nbt-studio-errors");
		errors.setWrapText(true);
		errors.setMaxWidth(Double.MAX_VALUE);
		errors.setMinHeight(Region.USE_PREF_SIZE);
		errors.setVisible(false);
		errors.setManaged(false);

		buildChunkTable();
		buildFileTable();

		chunkTab.setClosable(false);
		chunkTab.setContent(chunkTable);
		fileTab.setClosable(false);
		fileTab.setContent(fileTable);
		tabs.getTabs().addAll(chunkTab, fileTab);
		tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
		VBox.setVgrow(tabs, Priority.ALWAYS);
		tabs.setMinHeight(180);
		tabs.setPrefHeight(260);

		Button open = openButton;
		open.setText(Translation.DIALOG_NBT_STUDIO_FIND_RESULT_OPEN.toString());
		open.setDisable(true);
		open.setOnAction(e -> openSelection());
		open.setMinWidth(Region.USE_PREF_SIZE);
		javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		HBox toolbar = new HBox(8, header, spacer, open);
		toolbar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
		HBox.setHgrow(header, Priority.ALWAYS);
		header.setMaxWidth(Double.MAX_VALUE);
		// the summary is allowed to shrink and wrap, the button next to it is not: without this the
		// label claims its full untruncated width and pushes the button out of the window
		header.setMinWidth(0);

		chunkTable.getSelectionModel().selectedItemProperty()
				.addListener((o, a, b) -> updateOpenButton());
		fileTable.getSelectionModel().selectedItemProperty()
				.addListener((o, a, b) -> updateOpenButton());
		tabs.getSelectionModel().selectedItemProperty()
				.addListener((o, a, b) -> updateOpenButton());

		getChildren().addAll(toolbar, errors, tabs);
		setFillWidth(true);
		setPadding(new Insets(6, 0, 0, 0));
		setVisible(false);
		setManaged(false);
	}

	/** Fills the pane with one search result and makes it visible. */
	public void fill(List<Point2i> chunks, NbtDataFiles.SearchOutcome files, NbtQuery query) {
		this.query = query;
		this.hitChunks = chunks == null ? List.of() : chunks;
		List<NbtDataFiles.Match> matches = files == null ? List.of() : files.matches();

		chunkTable.setItems(FXCollections.observableArrayList(this.hitChunks));
		fileTable.setItems(FXCollections.observableArrayList(matches));

		chunkTab.setText(String.format(
				Translation.DIALOG_NBT_STUDIO_FIND_RESULT_CHUNKS_TAB.toString(), this.hitChunks.size()));
		fileTab.setText(String.format(
				Translation.DIALOG_NBT_STUDIO_FIND_RESULT_FILES_TAB.toString(), matches.size()));
		tabs.getSelectionModel().select(this.hitChunks.isEmpty() && !matches.isEmpty() ? fileTab : chunkTab);

		header.setText(headerText(this.hitChunks, files));
		String errorText = errorText(files);
		errors.setText(errorText);
		errors.setVisible(!errorText.isEmpty());
		errors.setManaged(!errorText.isEmpty());

		setVisible(true);
		setManaged(true);
		updateOpenButton();
	}

	/** @return how many chunk rows the pane holds, for tests and for the "open" button state */
	public int chunkCount() {
		return chunkTable.getItems().size();
	}

	/** @return how many data file rows the pane holds */
	public int fileCount() {
		return fileTable.getItems().size();
	}

	/** @return the tab pane that holds the two result halves */
	public TabPane lookupTabPane() {
		return tabs;
	}

	/** @return the two tabs, chunks first, files second */
	public List<Tab> tabsList() {
		return List.copyOf(tabs.getTabs());
	}

	/** @return the tab that is shown right now */
	public Tab selectedTab() {
		return tabs.getSelectionModel().getSelectedItem();
	}

	/** Switches to the chunk half, used when a search is repeated from the same window. */
	public void selectChunkTab() {
		tabs.getSelectionModel().select(chunkTab);
	}

	/** @return the one line summary above the tabs */
	public String headerTextValue() {
		return header.getText();
	}

	/** @return the data file row at {@code index} */
	public NbtDataFiles.Match fileRow(int index) {
		return fileTable.getItems().get(index);
	}

	/** A row has to be picked before "open" means anything, and the tab decides which row counts. */
	private void updateOpenButton() {
		if (openButton == null) {
			return;
		}
		boolean hasRow = tabs.getSelectionModel().getSelectedItem() == fileTab
				? fileTable.getSelectionModel().getSelectedItem() != null
				: chunkTable.getSelectionModel().getSelectedItem() != null;
		openButton.setDisable(!hasRow);
	}

	public boolean isEmptyResult() {
		return hitChunks.isEmpty() && fileTable.getItems().isEmpty();
	}

	private String headerText(List<Point2i> chunks, NbtDataFiles.SearchOutcome files) {
		if (chunks.isEmpty() && (files == null || files.total() == 0)) {
			return Translation.DIALOG_NBT_STUDIO_RESULT_NONE.toString();
		}
		StringBuilder text = new StringBuilder(String.format(
				Translation.DIALOG_NBT_STUDIO_FIND_RESULT_HEADER.toString(), chunks.size(),
				files == null ? 0 : files.total(), files == null ? 0 : files.filesWithMatches()));
		if (files != null && files.truncated()) {
			text.append(' ').append(Translation.DIALOG_NBT_STUDIO_FILE_RESULT_TRUNCATED.toString());
		}
		return text.toString();
	}

	private String errorText(NbtDataFiles.SearchOutcome files) {
		if (files == null || files.errors().isEmpty()) {
			return "";
		}
		StringBuilder text = new StringBuilder(String.format(
				Translation.DIALOG_NBT_STUDIO_FILE_RESULT_ERRORS.toString(), files.errors().size()));
		int shown = 0;
		for (String error : files.errors()) {
			if (shown++ >= 5) {
				break;
			}
			text.append('\n').append(error);
		}
		return text.toString();
	}

	private void buildChunkTable() {
		chunkTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
		chunkTable.setPlaceholder(new Label(Translation.DIALOG_NBT_STUDIO_FIND_RESULT_CHUNKS_EMPTY.toString()));

		TableColumn<Point2i, String> chunk = new TableColumn<>(
				Translation.DIALOG_NBT_STUDIO_COLUMN_CHUNK.toString());
		chunk.setCellValueFactory(c -> new SimpleStringProperty(
				c.getValue().getX() + " / " + c.getValue().getZ()));
		chunkTable.getColumns().add(chunk);

		chunkTable.setRowFactory(tv -> {
			TableRow<Point2i> row = new TableRow<>();
			row.setOnMouseClicked(e -> {
				if (e.getButton() != MouseButton.PRIMARY || e.getClickCount() != 2 || row.isEmpty()) {
					return;
				}
				openChunkEditor(row.getItem());
			});
			return row;
		});
	}

	private void buildFileTable() {
		fileTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
		fileTable.setPlaceholder(new Label(Translation.DIALOG_NBT_STUDIO_FILE_RESULT_EMPTY.toString()));

		TableColumn<NbtDataFiles.Match, String> file = new TableColumn<>(
				Translation.DIALOG_NBT_STUDIO_COLUMN_FILE.toString());
		file.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().file().label()));
		file.setPrefWidth(230);

		TableColumn<NbtDataFiles.Match, String> path = new TableColumn<>(
				Translation.DIALOG_NBT_STUDIO_COLUMN_PATH.toString());
		path.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().path()));
		path.setPrefWidth(300);

		TableColumn<NbtDataFiles.Match, String> value = new TableColumn<>(
				Translation.DIALOG_NBT_STUDIO_VALUE.toString());
		value.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().value()));
		value.setPrefWidth(200);

		fileTable.getColumns().addAll(List.of(file, path, value));

		fileTable.setRowFactory(tv -> {
			TableRow<NbtDataFiles.Match> row = new TableRow<>();
			row.setOnMouseClicked(e -> {
				if (e.getButton() != MouseButton.PRIMARY || e.getClickCount() != 2 || row.isEmpty()) {
					return;
				}
				openFileEditor(row.getItem());
			});
			MenuItem copyFile = new MenuItem(Translation.DIALOG_NBT_STUDIO_COPY_FILE.toString());
			copyFile.setOnAction(e -> {
				if (row.isEmpty()) {
					return;
				}
				ClipboardContent content = new ClipboardContent();
				content.putString(row.getItem().file().file().getAbsolutePath());
				Clipboard.getSystemClipboard().setContent(content);
			});
			MenuItem copyPath = new MenuItem(Translation.DIALOG_NBT_STUDIO_COPY_PATH.toString());
			copyPath.setOnAction(e -> {
				if (row.isEmpty()) {
					return;
				}
				ClipboardContent content = new ClipboardContent();
				content.putString(row.getItem().path());
				Clipboard.getSystemClipboard().setContent(content);
			});
			row.setContextMenu(new ContextMenu(copyFile, copyPath));
			return row;
		});
	}

	/** Opens the editor of the selected row, if there is one. */
	public void openSelection() {
		Tab selected = tabs.getSelectionModel().getSelectedItem();
		if (selected == null) {
			return;
		}
		if (selected == chunkTab) {
			Point2i chunk = chunkTable.getSelectionModel().getSelectedItem();
			if (chunk != null) {
				openChunkEditor(chunk);
			}
		} else {
			NbtDataFiles.Match match = fileTable.getSelectionModel().getSelectedItem();
			if (match != null) {
				openFileEditor(match);
			}
		}
	}

	/**
	 * Opens the chunk editor on one hit, with the query applied and every hit of the search
	 * available, so the arrows in the editor walk the whole result list.
	 */
	public void openChunkEditor(Point2i chunk) {
		if (tileMap == null || chunk == null) {
			return;
		}
		new NBTEditorDialog(tileMap, primaryStage, query, chunk, hitChunks).showAndWait();
	}

	/** Opens the tree editor of a data file, with the query applied to its tags. */
	public void openFileEditor(NbtDataFiles.Match match) {
		if (match == null) {
			return;
		}
		new NbtFileEditorDialog(match.file(), query, primaryStage).showAndWait();
	}
}
