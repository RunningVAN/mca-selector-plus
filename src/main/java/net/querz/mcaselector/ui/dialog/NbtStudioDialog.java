package net.querz.mcaselector.ui.dialog;

import javafx.event.ActionEvent;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import net.querz.mcaselector.config.ConfigProvider;
import net.querz.mcaselector.nbtstudio.NbtDataFiles;
import net.querz.mcaselector.nbtstudio.NbtMatchMode;
import net.querz.mcaselector.nbtstudio.NbtPattern;
import net.querz.mcaselector.nbtstudio.NbtQuery;
import net.querz.mcaselector.text.Translation;
import net.querz.mcaselector.tile.TileMap;
import net.querz.mcaselector.util.point.Point2i;
import net.querz.mcaselector.ui.NbtStudioFinder;
import net.querz.mcaselector.ui.UIFactory;
import java.util.List;

/**
 * NBT Studio style find and replace dialog.
 *
 * <p>The dialog copies NBT Studio's find window as closely as the chunk map allows: one field for the
 * tag name and one for the tag value, both matched as a case insensitive substring unless the regex
 * switch is on, and only when both are filled does a tag have to match both. There is no path, no
 * match mode, no tag type filter and no limit, because NBT Studio has none of these either: it walks
 * the tags of the open file and reports every tag whose name or value fits.</p>
 *
 * <p>What NBT Studio does inside one file, this dialog does across the map: the matches are the
 * chunks that contain at least one fitting tag. The replace side works like NBT Studio's bulk edit:
 * the replacement text replaces the matched part of the name and of the value, an empty replacement
 * is refused because deleting the whole name or value is never what the button says.</p>
 */
public class NbtStudioDialog extends Dialog<NbtStudioDialog.Result> {

	public enum Action {
		/** select every matching chunk of the map and list the matching data files */
		FIND,
		/** show what a replacement would change without writing it */
		PREVIEW,
		/** replace every match in the map and in the world data files */
		REPLACE
	}

	public enum Target {
		AUTO(Translation.DIALOG_NBT_STUDIO_TARGET_AUTO, ""),
		NAME(Translation.DIALOG_NBT_STUDIO_TARGET_NAME, "@name "),
		VALUE(Translation.DIALOG_NBT_STUDIO_TARGET_VALUE, "@value "),
		BOTH(Translation.DIALOG_NBT_STUDIO_TARGET_BOTH, "@both ");

		private final Translation translation;
		private final String prefix;

		Target(Translation translation, String prefix) {
			this.translation = translation;
			this.prefix = prefix;
		}

		String prefix() {
			return prefix;
		}

		@Override
		public String toString() {
			return translation.toString();
		}
	}

	public record Result(String query, String replacement, Target target, boolean includeDataFiles, Action action,
			boolean openInEditor, NbtQuery parsed) {

		public String toFieldSpec() {
			return target.prefix() + query + " => " + replacement;
		}
	}

	private static final ButtonType FIND_BUTTON = new ButtonType(
			Translation.DIALOG_NBT_STUDIO_FIND_BUTTON.toString(), ButtonBar.ButtonData.LEFT);
	private static final ButtonType PREVIEW_BUTTON = new ButtonType(
			Translation.DIALOG_NBT_STUDIO_PREVIEW.toString(), ButtonBar.ButtonData.LEFT);
	private static final ButtonType REPLACE_BUTTON = new ButtonType(
			Translation.DIALOG_NBT_STUDIO_REPLACE.toString(), ButtonBar.ButtonData.OK_DONE);

	private static final double LABEL_WIDTH = 68;
	private static final double CONTENT_WIDTH = 620;
	private static final double FIELD_SPACING = 8;
	// fixed height for the single status line, so the dialog never jumps while typing
	private static final double STATUS_HEIGHT = 24;

	// remember the last input so repeated runs don't have to be typed again
	private static String lastName = "";
	private static String lastValue = "";
	private static String lastReplacement = "";
	private static boolean lastRegex;
	private static boolean lastIncludeDataFiles = true;

	private final TextField nameField = new TextField(lastName);
	private final TextField valueField = new TextField(lastValue);
	private final TextField replacementField = new TextField(lastReplacement);
	private final CheckBox regex = new CheckBox(Translation.DIALOG_NBT_STUDIO_MODE_REGEX.toString());
	private final CheckBox dataFiles = new CheckBox(Translation.DIALOG_NBT_STUDIO_DATA_FILES.toString());
	private final Label statusLabel = new Label();
	// the search results live in this dialog: a search is not a reason to close the window the
	// user just typed into and open another one on top of it
	private final NbtStudioResultPane resultPane;
	private final TileMap tileMap;
	private final Stage primaryStage;
	private String summaryText;
	private String errorText;
	// the buttons do not exist until the dialog is shown, so the state is remembered and replayed
	private boolean findAllowed;
	private boolean previewAllowed;
	private boolean replaceAllowed;
	private boolean findHooked;

	public NbtStudioDialog(Stage primaryStage, TileMap tileMap) {
		this.primaryStage = primaryStage;
		this.tileMap = tileMap;
		this.resultPane = new NbtStudioResultPane(tileMap, primaryStage);
		titleProperty().bind(Translation.DIALOG_NBT_STUDIO_TITLE.getProperty());
		initStyle(StageStyle.UTILITY);
		setResizable(false);

		getDialogPane().getStyleClass().add("nbt-studio-dialog-pane");
		if (primaryStage != null && primaryStage.getScene() != null) {
			// keep the global theme so colors and fonts match the rest of the application
			getDialogPane().getScene().getStylesheets().addAll(primaryStage.getScene().getStylesheets());
		}
		var ownStyleSheet = NbtStudioDialog.class.getClassLoader().getResource("style/component/nbt-studio-dialog.css");
		if (ownStyleSheet != null) {
			getDialogPane().getScene().getStylesheets().add(ownStyleSheet.toExternalForm());
		}
		// there is one find and one replace button: both already work on everything the query can
		// reach, so an "all" variant would run the exact same operation with a longer label
		getDialogPane().getButtonTypes().addAll(FIND_BUTTON, PREVIEW_BUTTON, REPLACE_BUTTON, ButtonType.CANCEL);

		nameField.setPromptText(Translation.DIALOG_NBT_STUDIO_NAME.toString());
		valueField.setPromptText(Translation.DIALOG_NBT_STUDIO_VALUE.toString());
		replacementField.setPromptText(Translation.DIALOG_NBT_STUDIO_REPLACEMENT.toString());
		for (TextField field : new TextField[]{nameField, valueField, replacementField}) {
			field.setMaxWidth(Double.MAX_VALUE);
			HBox.setHgrow(field, Priority.ALWAYS);
		}

		regex.setSelected(lastRegex);
		regex.setTooltip(new Tooltip(Translation.DIALOG_NBT_STUDIO_MODE_REGEX.toString()));

		dataFiles.setSelected(lastIncludeDataFiles);
		dataFiles.setTooltip(new Tooltip(Translation.DIALOG_NBT_STUDIO_DATA_FILES_TOOLTIP.toString()));

		statusLabel.getStyleClass().add("nbt-studio-summary");
		// the status line is strictly single line: wrapping would make the dialog grow and shrink
		// while typing, so long text is ellipsized and available in full as a tooltip
		statusLabel.setWrapText(false);
		statusLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
		statusLabel.setEllipsisString("...");
		statusLabel.setMaxWidth(Double.MAX_VALUE);
		statusLabel.setMinWidth(0);
		statusLabel.setMinHeight(Region.USE_PREF_SIZE);

		VBox status = new VBox(statusLabel);
		status.setMinHeight(STATUS_HEIGHT);
		status.setPrefHeight(STATUS_HEIGHT);
		status.setMaxHeight(STATUS_HEIGHT);
		status.setAlignment(Pos.CENTER_LEFT);
		status.setFillWidth(true);

		GridPane form = grid();
		form.addRow(0, label(Translation.DIALOG_NBT_STUDIO_NAME), nameField);
		form.addRow(1, label(Translation.DIALOG_NBT_STUDIO_VALUE), valueField);

		HBox findOptions = new HBox(FIELD_SPACING * 2, regex, dataFiles);
		findOptions.setAlignment(Pos.CENTER_LEFT);
		GridPane.setMargin(findOptions, new Insets(2, 0, 0, 1));
		form.add(findOptions, 1, 2);

		Separator sections = new Separator();
		// spanning separators have to be resized to the full width, otherwise only their thin
		// preferred width is used and they show up as a short stub
		sections.setMaxWidth(Double.MAX_VALUE);
		GridPane.setFillWidth(sections, true);
		GridPane.setMargin(sections, new Insets(8, 0, 8, 0));
		form.add(sections, 0, 3, 2, 1);

		form.addRow(4, label(Translation.DIALOG_NBT_STUDIO_REPLACEMENT), replacementField);

		Label hint = new Label(Translation.DIALOG_NBT_STUDIO_HINT.toString());
		hint.setStyle("-fx-opacity: 0.7;");
		hint.setWrapText(true);
		hint.setMaxWidth(CONTENT_WIDTH);
		hint.setMinHeight(Region.USE_PREF_SIZE);
		VBox.setMargin(hint, new Insets(6, 0, 0, 0));

		VBox content = new VBox(8, form, status, hint, resultPane);
		content.setAlignment(Pos.TOP_LEFT);
		// bottom padding keeps the hint clear of the button bar, which otherwise visually covers
		// its lower half
		content.setPadding(new Insets(4, 6, 12, 6));
		content.setFillWidth(true);
		content.setPrefWidth(CONTENT_WIDTH);
		content.setMinWidth(Region.USE_PREF_SIZE);
		VBox.setVgrow(form, Priority.NEVER);
		VBox.setVgrow(resultPane, Priority.ALWAYS);

		getDialogPane().setContent(content);
		getDialogPane().setPrefWidth(CONTENT_WIDTH + 40);
		getDialogPane().setMinWidth(Region.USE_PREF_SIZE);

		// pressing enter in a text field must not run a destructive replace by accident:
		// the action buttons are never default buttons, cancel is a safe default instead
		for (ButtonType buttonType : new ButtonType[]{FIND_BUTTON, PREVIEW_BUTTON, REPLACE_BUTTON}) {
			Node button = getDialogPane().lookupButton(buttonType);
			if (button instanceof Button b) {
				b.setDefaultButton(false);
			}
		}
		Node cancelButton = getDialogPane().lookupButton(ButtonType.CANCEL);
		if (cancelButton instanceof Button b) {
			b.setDefaultButton(true);
		}

		nameField.textProperty().addListener((obs, o, n) -> validate());
		valueField.textProperty().addListener((obs, o, n) -> validate());
		replacementField.textProperty().addListener((obs, o, n) -> validate());
		regex.selectedProperty().addListener((obs, o, n) -> validate());
		dataFiles.selectedProperty().addListener((obs, o, n) -> validate());
		validate();

		setResultConverter(button -> {
			if (button == null || button == ButtonType.CANCEL) {
				return null;
			}
			NbtQuery query = buildQuery();
			if (query == null || !query.isValid()) {
				return null;
			}
			// the button types are compared by identity: find and preview share the LEFT button data
			Action action;
			if (button == FIND_BUTTON) {
				action = Action.FIND;
			} else if (button == PREVIEW_BUTTON) {
				action = Action.PREVIEW;
			} else {
				action = Action.REPLACE;
			}
			String replacement = text(replacementField);
			// only the find and preview actions work without a replacement, everything else
			// rewrites tags
			if (action != Action.FIND && replacement.isEmpty()) {
				return null;
			}

			rememberInput();

			// the replace target is always derived from the query: a name pattern renames, a value
			// pattern rewrites values and both patterns do both, like NBT Studio's bulk edit and
			// bulk rename windows do for the tags the user picked
			return new Result(query.canonical(), replacement, Target.AUTO, dataFiles.isSelected(), action, true, query);
		});

		// buttons are created with the button bar, which happens on the first layout: replay the
		// validation state then so the dialog never opens with enabled but unusable buttons
		setOnShown(e -> {
			validate();
			applyButtonStates();
			Node findButton = getDialogPane().lookupButton(FIND_BUTTON);
			if (findButton != null && !findHooked) {
				// find does not end the dialog: the results are filled into this window, so the
				// default behaviour that closes it on any button press has to be swallowed
				findButton.addEventFilter(ActionEvent.ACTION, event -> {
					event.consume();
					runFind();
				});
				findHooked = true;
			}
		});

		if (primaryStage != null) {
			initOwner(primaryStage);
		}
		nameField.requestFocus();
	}

	private static GridPane grid() {
		GridPane grid = new GridPane();
		grid.setHgap(FIELD_SPACING);
		grid.setVgap(6);
		// computed size instead of a fixed width: translations such as "Replacement" must not be clipped
		ColumnConstraints labels = new ColumnConstraints();
		labels.setMinWidth(LABEL_WIDTH);
		// left aligned labels all share the same left edge and every control starts at the same x,
		// which keeps the form tidy even when a translation is longer than LABEL_WIDTH
		labels.setPrefWidth(LABEL_WIDTH);
		labels.setHalignment(HPos.LEFT);
		ColumnConstraints fields = new ColumnConstraints();
		fields.setHgrow(Priority.ALWAYS);
		fields.setFillWidth(true);
		grid.getColumnConstraints().addAll(labels, fields);
		return grid;
	}

	private static Label label(Translation translation) {
		Label l = UIFactory.label(translation);
		l.setMinWidth(Region.USE_PREF_SIZE);
		l.setWrapText(false);
		return l;
	}

	private static String text(TextField field) {
		String t = field.getText();
		return t == null ? "" : t.trim();
	}

	/**
	 * @return the query described by the current input, never null
	 */
	private NbtQuery buildQuery() {
		// NBT Studio matches a case insensitive substring, the regex switch replaces that with a
		// regular expression; a field that is left empty matches everything, exactly like there
		NbtMatchMode mode = regex.isSelected() ? NbtMatchMode.REGEX : NbtMatchMode.CONTAINS;
		NbtPattern name = text(nameField).isEmpty() ? null : NbtPattern.of(text(nameField), mode, true);
		NbtPattern value = text(valueField).isEmpty() ? null : NbtPattern.of(text(valueField), mode, true);
		// name and value are matched inside the same chunk instead of on the same tag: the tags of
		// a chunk are spread over several files (a sign keeps its id in the block entity and its
		// text in front_text/messages), a same tag requirement would find nothing for a text that
		// is visibly there. The chunk is the unit this dialog works on, so that is also the unit
		// the two patterns have to meet in
		return NbtQuery.create(null, name, value, NbtQuery.ROOT_ALL, null, NbtQuery.DEFAULT_LIMIT, false, true);
	}

	private void validate() {
		// both fields empty would match every tag of every chunk, NBT Studio treats an empty box as
		// "anything", but there it only walks one file, so this case is refused instead
		if (text(nameField).isEmpty() && text(valueField).isEmpty()) {
			setSummary(null);
			showError(Translation.DIALOG_NBT_STUDIO_TARGET_REQUIRED.toString(), false, false, false);
			return;
		}

		NbtQuery query = buildQuery();
		if (invalidPattern(text(nameField)) || invalidPattern(text(valueField))) {
			setSummary(null);
			showError(Translation.DIALOG_NBT_STUDIO_INVALID_QUERY + ": " + query.getError(), false, false, false);
			return;
		}

		// an optional summary makes it obvious what is going to be searched for
		setSummary(Translation.DIALOG_NBT_STUDIO_QUERY + ": " + query.canonical());

		if (text(replacementField).isEmpty()) {
			// selecting matching chunks does not need a replacement, replacing them does
			showError(Translation.DIALOG_NBT_STUDIO_INVALID_REPLACEMENT.toString(), true, true, false);
			return;
		}
		showError(null, true, true, true);
	}

	/**
	 * @return whether the field contains a pattern that cannot be compiled
	 */
	private boolean invalidPattern(String text) {
		if (text.isEmpty()) {
			return false;
		}
		NbtMatchMode mode = regex.isSelected() ? NbtMatchMode.REGEX : NbtMatchMode.CONTAINS;
		NbtPattern pattern = NbtPattern.of(text, mode, true);
		return pattern == null || !pattern.isValid();
	}

	/** Remembers the query summary; the error message, if any, takes precedence over it. */
	private void setSummary(String message) {
		summaryText = message;
		updateStatus();
	}

	/**
	 * Shows a single line status text: the error message when there is one, otherwise the query
	 * summary. The full text is mirrored into a tooltip because the line is ellipsized.
	 */
	private void updateStatus() {
		boolean error = errorText != null && !errorText.isEmpty();
		String message = error ? errorText : summaryText;
		statusLabel.setText(message == null ? "" : message);
		statusLabel.getStyleClass().removeAll("nbt-studio-error", "nbt-studio-summary");
		statusLabel.getStyleClass().add(error ? "nbt-studio-error" : "nbt-studio-summary");
		if (message == null || message.isEmpty()) {
			statusLabel.setTooltip(null);
		} else if (statusLabel.getTooltip() == null) {
			statusLabel.setTooltip(new Tooltip(message));
		} else {
			statusLabel.getTooltip().setText(message);
		}
	}

	private void showError(String message, boolean findEnabled, boolean previewEnabled, boolean replaceEnabled) {
		// the label is a single line, so the untruncated message is available as a tooltip
		errorText = message;
		updateStatus();
		findAllowed = findEnabled;
		previewAllowed = previewEnabled;
		replaceAllowed = replaceEnabled;
		applyButtonStates();
	}

	/**
	 * Runs the search without closing the dialog: the hits are filled into the result pane below the
	 * fields, so one window holds both the query and what it found. The map selection is updated on
	 * the way, which is what "find" means for the chunk half.
	 */
	private void runFind() {
		NbtQuery query = buildQuery();
		if (query == null || !query.isValid()) {
			showError(Translation.DIALOG_NBT_STUDIO_INVALID_QUERY + ": "
					+ (query == null ? "" : query.getError()), true, false, false);
			return;
		}
		rememberInput();

		List<NbtDataFiles.FileRef> refs = List.of();
		if (dataFiles.isSelected()) {
			try {
				refs = NbtDataFiles.collect(ConfigProvider.WORLD.getWorldDirs());
			} catch (RuntimeException ex) {
				// a world that is not loaded yet has no data files to walk, the chunk half still runs
				refs = List.of();
			}
		}

		List<Point2i> chunks = tileMap == null
				? List.of()
				: NbtStudioFinder.findChunks(tileMap, query, primaryStage);
		NbtDataFiles.SearchOutcome files = NbtStudioFinder.findInDataFiles(refs, query, primaryStage);

		showResults(chunks, files, query);
	}

	/**
	 * Fills the result pane inside this dialog with one search result and makes room for it. Also the
	 * entry point for tests and for a repeated search: the window is never replaced, only refilled.
	 */
	public void showResults(List<Point2i> chunks, NbtDataFiles.SearchOutcome files, NbtQuery query) {
		resultPane.fill(chunks, files, query);
		growForResults();
	}

	/** @return the result pane, so a caller can open the row that was selected */
	public NbtStudioResultPane getResultPane() {
		return resultPane;
	}

	/** Remembers the current input, so a repeated search does not have to be typed again. */
	private void rememberInput() {
		lastName = text(nameField);
		lastValue = text(valueField);
		lastReplacement = text(replacementField);
		lastRegex = regex.isSelected();
		lastIncludeDataFiles = dataFiles.isSelected();
	}

	/** The dialog is small while it only holds fields; the result pane needs the room. */
	private void growForResults() {
		setResizable(true);
		Window window = getDialogPane().getScene() == null ? null : getDialogPane().getScene().getWindow();
		if (window instanceof Stage stage) {
			stage.setWidth(Math.max(stage.getWidth(), 860));
			stage.setHeight(Math.max(stage.getHeight(), 720));
		}
	}

	/**
	 * Applies the remembered button states. The buttons only exist once the dialog has been shown,
	 * so this has to run again in {@code setOnShown}, otherwise a validation result from before the
	 * first layout would have no effect at all.
	 */
	private void applyButtonStates() {
		setDisabled(FIND_BUTTON, !findAllowed);
		setDisabled(PREVIEW_BUTTON, !previewAllowed);
		setDisabled(REPLACE_BUTTON, !replaceAllowed);
	}

	private void setDisabled(ButtonType buttonType, boolean disabled) {
		Node button = getDialogPane().lookupButton(buttonType);
		if (button != null) {
			button.setDisable(disabled);
		}
	}
}
