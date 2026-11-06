package net.querz.mcaselector.ui.component;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import net.querz.mcaselector.nbtstudio.NbtMatchMode;
import net.querz.mcaselector.nbtstudio.NbtPattern;
import net.querz.mcaselector.nbtstudio.NbtQuery;
import net.querz.mcaselector.text.Translation;

/**
 * Find and replace bar for the chunk editor ({@link net.querz.mcaselector.ui.dialog.NBTEditorDialog}).
 *
 * <p>The bar is a copy of NBT Studio's find window: one field for the tag name, one for the tag
 * value and a regex switch. Without the switch both fields are matched as a case insensitive
 * substring, with it as a regular expression. An empty field matches everything, and when both
 * fields are filled a tag has to satisfy both. The two arrow buttons build the match list, expand it
 * and walk through it, replace rewrites the current match and replace all rewrites every match. Because the tree holds the very tag instances that are written back to the region file, no
 * extra apply step is needed.</p>
 */
public class NBTFindBar extends VBox {

	// remembered between dialog instances and between the region/poi/entities tabs
	private static String lastName = "";
	private static String lastValue = "";
	private static String lastReplace = "";
	private static boolean lastRegex;

	private final NBTTreeView tree;
	private final TextField nameField = new TextField(lastName);
	private final TextField valueField = new TextField(lastValue);
	private final TextField replaceField = new TextField(lastReplace);
	private final CheckBox regex = new CheckBox(Translation.DIALOG_NBT_STUDIO_MODE_REGEX.toString());
	private final Label status = new Label();

	public NBTFindBar(NBTTreeView tree) {
		super(4);
		this.tree = tree;
		getStyleClass().add("nbt-find-bar");
		setPadding(new Insets(4, 6, 4, 6));

		nameField.setPromptText(Translation.DIALOG_NBT_STUDIO_NAME.toString());
		valueField.setPromptText(Translation.DIALOG_NBT_STUDIO_VALUE.toString());
		replaceField.setPromptText(Translation.DIALOG_NBT_STUDIO_REPLACEMENT.toString());
		HBox.setHgrow(nameField, Priority.ALWAYS);
		HBox.setHgrow(valueField, Priority.ALWAYS);
		HBox.setHgrow(replaceField, Priority.ALWAYS);

		regex.setSelected(lastRegex);

		Button previous = new Button(Translation.DIALOG_NBT_EDITOR_PREVIOUS.toString());
		Button next = new Button(Translation.DIALOG_NBT_EDITOR_NEXT.toString());
		Button replace = new Button(Translation.DIALOG_NBT_STUDIO_REPLACE.toString());
		Button replaceAll = new Button(Translation.DIALOG_NBT_EDITOR_REPLACE_ALL.toString());
		for (Button b : new Button[]{previous, next, replace, replaceAll}) {
			// the find bar is embedded in a dialog with an apply button, so none of these may
			// become the default button and swallow an accidental enter press
			b.setDefaultButton(false);
			b.setFocusTraversable(false);
		}

		HBox nameRow = new HBox(6, label(Translation.DIALOG_NBT_STUDIO_NAME), nameField, regex);
		nameRow.setAlignment(Pos.CENTER_LEFT);
		HBox valueRow = new HBox(6, label(Translation.DIALOG_NBT_STUDIO_VALUE), valueField);
		valueRow.setAlignment(Pos.CENTER_LEFT);

		status.getStyleClass().add("nbt-find-bar-status");
		status.setWrapText(false);
		status.setMinWidth(0);
		status.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(status, Priority.ALWAYS);
		HBox navigationRow = new HBox(6, previous, next, status);
		navigationRow.setAlignment(Pos.CENTER_LEFT);

		HBox replaceRow = new HBox(6, label(Translation.DIALOG_NBT_STUDIO_REPLACEMENT), replaceField, replace, replaceAll);
		replaceRow.setAlignment(Pos.CENTER_LEFT);

		getChildren().addAll(nameRow, valueRow, navigationRow, replaceRow);

		next.setOnAction(e -> onNext());
		previous.setOnAction(e -> onPrevious());
		replace.setOnAction(e -> onReplace());
		replaceAll.setOnAction(e -> onReplaceAll());

		// enter searches for the next hit, shift+enter walks backwards, exactly like NBT Studio.
		// The action event of a TextField does not carry the modifiers, so this is hooked on the
		// key press instead.
		nameField.setOnKeyPressed(e -> onEnter(e));
		valueField.setOnKeyPressed(e -> onEnter(e));
		regex.setOnKeyPressed(e -> onEnter(e));
		replaceField.setOnKeyPressed(e -> {
			if (e.getCode() == KeyCode.ENTER) {
				e.consume();
				if (e.isShiftDown()) {
					onNext();
				} else {
					onReplace();
				}
			}
		});

		// changing the query invalidates the previous match list, the status line is reset so it
		// never claims hits for a query that is no longer in the field
		nameField.textProperty().addListener((obs, o, n) -> clearStatus());
		valueField.textProperty().addListener((obs, o, n) -> clearStatus());
		regex.selectedProperty().addListener((obs, o, n) -> clearStatus());
	}

	private void onEnter(javafx.scene.input.KeyEvent e) {
		if (e.getCode() == KeyCode.ENTER) {
			e.consume();
			if (e.isShiftDown()) {
				onPrevious();
			} else {
				onNext();
			}
		}
	}

	private static Label label(Translation translation) {
		Label l = new Label(translation.toString());
		l.setMinWidth(Region.USE_PREF_SIZE);
		return l;
	}

	private String nameText() {
		String text = nameField.getText();
		return text == null ? "" : text.trim();
	}

	private String valueText() {
		String text = valueField.getText();
		return text == null ? "" : text.trim();
	}

	private String replaceText() {
		String text = replaceField.getText();
		return text == null ? "" : text;
	}

	private void remember() {
		lastName = nameText();
		lastValue = valueText();
		lastReplace = replaceText();
		lastRegex = regex.isSelected();
	}

	/** Builds or rebuilds the match list for the current query. */
	private int ensureMatches(NbtQuery query) {
		if (query == null) {
			return 0;
		}
		int count = tree.collectMatches(query);
		if (count == 0) {
			setStatus(Translation.DIALOG_NBT_EDITOR_NO_MATCH, true);
		}
		return count;
	}

	/**
	 * Builds the query for the current input, or null if the input cannot be searched. Both fields
	 * are independent patterns that have to match the same tag; an empty field is not a pattern at
	 * all, so only the filled fields restrict the search.
	 */
	private NbtQuery query() {
		String name = nameText();
		String value = valueText();
		if (name.isEmpty() && value.isEmpty()) {
			setStatus(Translation.DIALOG_NBT_EDITOR_QUERY_REQUIRED, true);
			return null;
		}

		NbtMatchMode mode = regex.isSelected() ? NbtMatchMode.REGEX : NbtMatchMode.CONTAINS;
		NbtPattern namePattern = name.isEmpty() ? null : NbtPattern.of(name, mode, true);
		NbtPattern valuePattern = value.isEmpty() ? null : NbtPattern.of(value, mode, true);
		for (NbtPattern pattern : new NbtPattern[]{namePattern, valuePattern}) {
			if (pattern != null && !pattern.isValid()) {
				setStatus(String.format(Translation.DIALOG_NBT_EDITOR_INVALID.toString(), pattern.getError()), true);
				return null;
			}
		}

		// inside one chunk "name and value" means "a tag matching either of them", because the two
		// parts are very often stored on different tags, so both fields filled widens the match
		boolean any = namePattern != null && valuePattern != null;
		NbtQuery query = NbtQuery.create(null, namePattern, valuePattern, NbtQuery.ROOT_ALL, null,
				NbtQuery.DEFAULT_LIMIT, any, false);
		if (!query.isValid()) {
			setStatus(String.format(Translation.DIALOG_NBT_EDITOR_INVALID.toString(),
					String.valueOf(query.getError())), true);
			return null;
		}
		remember();
		return query;
	}

	/**
	 * Translates a query into the one the tree is actually searched with.
	 *
	 * <p>A query that came from the chunk dialog asks for "name and value on the same tag", which is
	 * what the bar does, but inside a single chunk the two parts are very often stored on different
	 * tags (a sign keeps its id and its text apart), so the same tag requirement is dropped here and
	 * every tag that fits either part is highlighted instead.</p>
	 *
	 * <p>Exposed as a static method because the editor needs the very same query to count the hits
	 * of a tab before deciding which tab to show.</p>
	 */
	public static NbtQuery toTreeQuery(NbtQuery query) {
		if (query == null) {
			return null;
		}
		NbtPattern name = query.getNamePattern();
		NbtPattern value = query.getValuePattern();
		if (!query.isMatchAny() && name != null && value != null) {
			NbtQuery any = NbtQuery.create(null, name, value, NbtQuery.ROOT_ALL, null,
					NbtQuery.DEFAULT_LIMIT, true, false);
			if (any.isValid()) {
				return any;
			}
		}
		return query;
	}

	/**
	 * Fills the bar with a query that came from somewhere else, e.g. from the NBT Studio dialog,
	 * and runs "find all" so the hits are expanded right away.
	 */
	public int applyQuery(NbtQuery query) {
		if (query == null) {
			return 0;
		}
		NbtPattern name = query.getNamePattern();
		NbtPattern value = query.getValuePattern();
		NbtPattern any = name != null ? name : value;
		nameField.setText(name == null ? "" : name.getSource());
		valueField.setText(value == null ? "" : value.getSource());
		regex.setSelected(any != null && any.getMode() == NbtMatchMode.REGEX);

		int count = ensureMatches(toTreeQuery(query));
		if (count == 0) {
			showPosition(0);
			return 0;
		}
		tree.expandMatches();
		tree.selectFirstMatch();
		showPosition(count);
		return count;
	}

	private void onNext() {
		int count = tree.getMatchCount();
		if (count == 0) {
			NbtQuery query = query();
			if (query == null) {
				return;
			}
			count = ensureMatches(query);
			// there is no "find all" button anymore: the first navigation builds the match list and
			// expands it, so walking through the hits never happens on a still collapsed tree
			if (count > 0) {
				tree.expandMatches();
			}
		}
		if (count == 0) {
			return;
		}
		tree.selectNextMatch();
		showPosition(tree.getMatchCount());
	}

	private void onPrevious() {
		int count = tree.getMatchCount();
		if (count == 0) {
			NbtQuery query = query();
			if (query == null) {
				return;
			}
			count = ensureMatches(query);
			if (count > 0) {
				tree.expandMatches();
			}
		}
		if (count == 0) {
			return;
		}
		tree.selectPreviousMatch();
		showPosition(tree.getMatchCount());
	}

	private void onReplace() {
		NbtQuery query = query();
		if (query == null) {
			return;
		}
		if (tree.getMatchCount() == 0) {
			ensureMatches(query);
		}
		boolean names = query.getNamePattern() != null;
		boolean values = query.getValuePattern() != null;
		boolean changed = tree.replaceCurrentMatch(query, replaceText(), names, values);
		if (!changed) {
			setStatus(Translation.DIALOG_NBT_EDITOR_NO_CHANGE, true);
			return;
		}
		// the replaced tag no longer matches, so the match list has to be rebuilt
		ensureMatches(query());
		setStatus(String.format(Translation.DIALOG_NBT_EDITOR_REPLACED_CURRENT.toString(), tree.getMatchCount()), false);
	}

	private void onReplaceAll() {
		NbtQuery query = query();
		if (query == null) {
			return;
		}
		boolean names = query.getNamePattern() != null;
		boolean values = query.getValuePattern() != null;
		int changed = tree.replaceMatches(query, replaceText(), names, values);
		if (changed == 0) {
			setStatus(Translation.DIALOG_NBT_EDITOR_NO_CHANGE, true);
			return;
		}
		ensureMatches(query());
		setStatus(String.format(Translation.DIALOG_NBT_EDITOR_REPLACED.toString(), changed), false);
	}

	private void showPosition(int count) {
		int index = tree.getMatchIndex();
		String path = tree.getMatchPath(index);
		String message = String.format(Translation.DIALOG_NBT_EDITOR_MATCHES.toString(), count);
		if (index >= 0) {
			message += "  " + String.format(Translation.DIALOG_NBT_EDITOR_POSITION.toString(), index + 1, count);
			if (!path.isEmpty()) {
				message += "  " + path;
			}
		}
		setStatus(message, false);
	}

	private void clearStatus() {
		status.setText("");
		status.getStyleClass().remove("nbt-find-bar-error");
	}

	private void setStatus(Translation translation, boolean error) {
		setStatus(translation.toString(), error);
	}

	private void setStatus(String message, boolean error) {
		status.setText(message);
		status.getStyleClass().remove("nbt-find-bar-error");
		if (error) {
			status.getStyleClass().add("nbt-find-bar-error");
		}
	}
}
