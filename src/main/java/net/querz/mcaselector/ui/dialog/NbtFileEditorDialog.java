package net.querz.mcaselector.ui.dialog;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import net.querz.mcaselector.nbtstudio.NbtDataFiles;
import net.querz.mcaselector.nbtstudio.NbtQuery;
import net.querz.mcaselector.text.Translation;
import net.querz.mcaselector.ui.component.NBTEditorOptionsBar;
import net.querz.mcaselector.ui.component.NBTFindBar;
import net.querz.mcaselector.ui.component.NBTTreeView;
import net.querz.nbt.NamedTag;
import net.querz.nbt.Tag;
import net.querz.nbt.io.NBTWriter;
import net.querz.nbt.CompoundTag;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * Edits one data file of the world ({@code level.dat}, {@code data}, {@code playerdata} or a
 * structure file) as a tree, the same way the chunk editor edits a chunk.
 *
 * <p>Chunks and data files hold the same kind of tree, so the editor uses the same
 * {@link NBTTreeView} and the same {@link NBTFindBar} the chunk editor uses: tags can be renamed,
 * values edited, added and deleted, the find bar walks the matches of the query that came from the
 * NBT Studio dialog and replaces them, and the matches are expanded right away, so the result of
 * the search shows up in the tree instead of in a second window.</p>
 *
 * <p>Saving writes the file back in the compression it was read with.</p>
 */
public class NbtFileEditorDialog extends Dialog<Void> {

	private static final long MAX_EDITABLE_SIZE = 64L * 1024 * 1024;

	private final NbtDataFiles.FileRef file;
	private final Stage primaryStage;
	private final NBTTreeView view = new NBTTreeView();
	private final Label status = new Label();

	private CompoundTag root;
	private String rootName = "";
	private NbtDataFiles.Compression compression = NbtDataFiles.Compression.NONE;
	private byte[] baseline;
	private String problem;

	public NbtFileEditorDialog(NbtDataFiles.FileRef file, NbtQuery query, Stage primaryStage) {
		this.file = file;
		this.primaryStage = primaryStage;
		titleProperty().bind(Translation.DIALOG_NBT_STUDIO_FILE_EDITOR_TITLE.getProperty());
		initStyle(StageStyle.UTILITY);
		setResizable(true);

		getDialogPane().getStyleClass().add("nbt-studio-dialog-pane");
		if (primaryStage != null && primaryStage.getScene() != null) {
			getDialogPane().getScene().getStylesheets().addAll(primaryStage.getScene().getStylesheets());
		}
		var ownStyleSheet = NbtFileEditorDialog.class.getClassLoader()
				.getResource("style/component/nbt-studio-dialog.css");
		if (ownStyleSheet != null) {
			getDialogPane().getScene().getStylesheets().add(ownStyleSheet.toExternalForm());
		}

		ButtonType save = new ButtonType(
				Translation.DIALOG_NBT_STUDIO_FILE_EDITOR_SAVE.toString(), ButtonBar.ButtonData.APPLY);
		ButtonType close = new ButtonType(
				Translation.BUTTON_CANCEL.toString(), ButtonBar.ButtonData.CANCEL_CLOSE);
		getDialogPane().getButtonTypes().addAll(save, close);

		view.setArrayEditor(a -> new EditArrayDialog(a, primaryStage).showAndWait());
		readFile();

		// the root of a file cannot be deleted, so no root handler is handed in: a file always has
		// one root compound and deleting it would only produce a file that cannot be read
		BorderPane treeViewHolder = new BorderPane();
		treeViewHolder.getStyleClass().add("nbt-tree-view-holder");
		treeViewHolder.setCenter(view);
		NBTEditorOptionsBar options = new NBTEditorOptionsBar(view, primaryStage, treeViewHolder, null);

		NBTFindBar findBar = new NBTFindBar(view);
		// the query that brought the user here is applied to this file, so the tags that matched in
		// the result list are expanded and selected here as well
		if (query != null && problem == null) {
			findBar.applyQuery(query);
		}
		if (problem != null) {
			findBar.setDisable(true);
			options.setDisable(true);
			view.setDisable(true);
		}

		Label header = new Label(headerText());
		header.getStyleClass().add("nbt-studio-summary");
		header.setWrapText(true);
		header.setMaxWidth(760);
		header.setMinHeight(Region.USE_PREF_SIZE);

		updateStatus();

		HBox statusBar = new HBox(status);
		statusBar.setPadding(new Insets(2, 6, 0, 6));

		VBox content = new VBox(6, header, findBar, treeViewHolder, options, statusBar);
		VBox.setVgrow(treeViewHolder, Priority.ALWAYS);
		content.setPadding(new Insets(4, 6, 2, 6));
		content.setPrefWidth(800);
		content.setPrefHeight(620);
		getDialogPane().setContent(content);
		getDialogPane().setPrefWidth(820);
		getDialogPane().setPrefHeight(660);

		// saving must not close the editor: a data file is usually edited in several passes
		getDialogPane().lookupButton(save).addEventFilter(ActionEvent.ACTION, e -> {
			e.consume();
			saveFile();
		});
		if (primaryStage != null) {
			initOwner(primaryStage);
		}
		pollForChanges();
	}

	/**
	 * Keeps the status line honest while the user edits: the tree is edited in place, so nothing
	 * tells the dialog that something changed. The serialized tree is compared against the state
	 * the file was read in, but only for files small enough that doing so is cheap.
	 */
	private void pollForChanges() {
		if (root == null || baseline == null || baseline.length > 8 * 1024 * 1024) {
			return;
		}
		Timeline poll = new Timeline(new KeyFrame(Duration.millis(700), e -> updateStatus()));
		poll.setCycleCount(Timeline.INDEFINITE);
		poll.play();
		setOnHidden(e -> poll.stop());
	}

	/**
	 * Reads the file and builds the tree around it. A file that cannot be read, or whose root is not
	 * a compound, is shown as a disabled tree with the reason in the header instead of opening a
	 * dialog that looks editable but is not.
	 */
	private void readFile() {
		if (file == null || !file.file().isFile()) {
			problem = Translation.DIALOG_NBT_STUDIO_FILE_EDITOR_MISSING.toString();
			return;
		}
		if (file.file().length() > MAX_EDITABLE_SIZE) {
			problem = Translation.DIALOG_NBT_STUDIO_FILE_EDITOR_TOO_LARGE.toString();
			return;
		}
		try {
			NbtDataFiles.LoadedFile loaded = NbtDataFiles.read(file.file());
			compression = loaded.compression();
			rootName = loaded.tag().name() == null ? "" : loaded.tag().name();
			if (!(loaded.tag().tag() instanceof CompoundTag compound)) {
				problem = Translation.DIALOG_NBT_STUDIO_FILE_EDITOR_NOT_COMPOUND.toString();
				return;
			}
			root = compound;
			view.load(compound);
			// one more level than the root, so a level.dat opens on its Data tag and a structure
			// file on its palette instead of on a single collapsed line
			for (var child : view.getRoot().getChildren()) {
				child.setExpanded(true);
			}
			// selecting the root makes the add buttons usable before anything else is clicked, and
			// the root itself stays undeletable
			view.getSelectionModel().select(0);
			baseline = bytesOf(compound);
		} catch (IOException | RuntimeException ex) {
			problem = String.format(
					Translation.DIALOG_NBT_STUDIO_FILE_EDITOR_READ_FAILED.toString(), ex.getMessage());
		}
	}

	private String headerText() {
		StringBuilder text = new StringBuilder(file == null ? "" : file.label());
		if (problem == null) {
			text.append("  ·  ").append(compression);
		} else {
			text.append("  ·  ").append(problem);
		}
		return text.toString();
	}

	private void saveFile() {
		if (root == null) {
			return;
		}
		CompoundTag compound = currentRoot();
		try {
			NbtDataFiles.write(file.file(), new NbtDataFiles.LoadedFile(
					new NamedTag(rootName, compound), compression));
			baseline = bytesOf(compound);
			status.setText(Translation.DIALOG_NBT_STUDIO_FILE_EDITOR_SAVED.toString());
		} catch (IOException | RuntimeException ex) {
			String message = String.format(
					Translation.DIALOG_NBT_STUDIO_FILE_EDITOR_SAVE_FAILED.toString(), ex.getMessage());
			status.setText(message);
			new ErrorDialog(primaryStage, message);
		}
	}

	private CompoundTag currentRoot() {
		if (view.getRoot() == null) {
			return root;
		}
		Tag tag = view.getRoot().getValue().getRef();
		return tag instanceof CompoundTag compound ? compound : root;
	}

	private boolean isDirty() {
		if (root == null || baseline == null) {
			return false;
		}
		try {
			return !Arrays.equals(baseline, bytesOf(currentRoot()));
		} catch (IOException | RuntimeException ex) {
			return true;
		}
	}

	private void updateStatus() {
		if (problem != null) {
			status.setText(problem);
			return;
		}
		status.setText(isDirty()
				? Translation.DIALOG_NBT_STUDIO_FILE_EDITOR_DIRTY.toString()
				: Translation.DIALOG_NBT_STUDIO_FILE_EDITOR_UNCHANGED.toString());
	}

	private static byte[] bytesOf(Tag tag) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		new NBTWriter().compressed(false).writeNamed(bytes, new NamedTag("", tag));
		return bytes.toByteArray();
	}
}
