package net.querz.mcaselector.ui.component;

import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import net.querz.mcaselector.io.FileHelper;
import net.querz.mcaselector.text.Translation;
import net.querz.mcaselector.ui.dialog.RequestNumberDialog;
import net.querz.nbt.ByteArrayTag;
import net.querz.nbt.ByteTag;
import net.querz.nbt.CompoundTag;
import net.querz.nbt.DoubleTag;
import net.querz.nbt.FloatTag;
import net.querz.nbt.IntArrayTag;
import net.querz.nbt.IntTag;
import net.querz.nbt.ListTag;
import net.querz.nbt.LongArrayTag;
import net.querz.nbt.LongTag;
import net.querz.nbt.ShortTag;
import net.querz.nbt.StringTag;
import net.querz.nbt.Tag;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import static net.querz.nbt.Tag.Type.*;

/**
 * The row of buttons under an NBT tree that deletes the selected tag and adds a new tag of any
 * supported type.
 *
 * <p>The chunk editor and the data file editor both edit a plain {@link CompoundTag} tree, so the
 * row lives here instead of in the chunk editor: a data file gets the same ability to add tags of
 * every NBT type (numbers, strings, lists, compounds and the three array types) and to delete tags
 * from the tree, which is exactly what the chunk editor could already do.</p>
 *
 * <p>Adding a tag needs a selected item to hang it on, so the row follows the selection: the
 * buttons for the types that are legal at the selection are enabled, everything else is disabled.
 * {@code rootHandler} is called when the root tag is deleted, because the owner has to react to
 * a tree without a root; passing {@code null} makes the root undeletable, which is what a data
 * file needs, since a file always has a root.</p>
 */
public class NBTEditorOptionsBar extends HBox {

	private final NBTTreeView nbtTreeView;
	private final BorderPane treeViewHolder;
	private final Consumer<CompoundTag> rootHandler;

	private final Label delete = new Label();
	private final ImageView deleteIcon = new ImageView(FileHelper.getIconFromResources("img/delete"));
	private final Map<Tag.Type, Label> addTagLabels = new LinkedHashMap<>();

	/**
	 * @param nbtTreeView    the tree the row edits
	 * @param primaryStage   owner for the dialogs that ask for the length of a new array
	 * @param treeViewHolder holder the tree is put into when a tag is added before the tree is
	 *                       shown, or {@code null} if the tree is always shown
	 * @param rootHandler    called with {@code null} when the root tag was deleted, or {@code null}
	 *                       to make the root undeletable
	 */
	public NBTEditorOptionsBar(NBTTreeView nbtTreeView, Stage primaryStage, BorderPane treeViewHolder, Consumer<CompoundTag> rootHandler) {
		this.nbtTreeView = nbtTreeView;
		this.treeViewHolder = treeViewHolder;
		this.rootHandler = rootHandler;

		getStyleClass().add("nbt-editor-options");
		initDelete(primaryStage);
		initAddTagLabels(primaryStage);
		getChildren().add(delete);
		getChildren().addAll(addTagLabels.values());

		nbtTreeView.setOnSelectionChanged(b -> {
			updateDeleteState();
			enableAddTagLabels(nbtTreeView.getPossibleChildTagTypesFromSelected());
		});
		updateDeleteState();
		enableAddTagLabels(nbtTreeView.getPossibleChildTagTypesFromSelected());
	}

	/**
	 * Enables the add buttons for the given types and disables all others, so a caller that has
	 * just loaded a tree without any selection can still offer the types that make sense there.
	 */
	public void enableAddTagLabels(Tag.Type... types) {
		for (Label label : addTagLabels.values()) {
			label.setDisable(true);
		}
		if (types != null) {
			for (Tag.Type type : types) {
				Label label = addTagLabels.get(type);
				if (label != null) {
					label.setDisable(false);
				}
			}
		}
	}

	private void initDelete(Stage primaryStage) {
		delete.setGraphic(deleteIcon);
		delete.getStyleClass().add("nbt-editor-delete-tag-label");
		delete.setDisable(true);
		deleteIcon.setPreserveRatio(true);
		deleteIcon.setFitWidth(22);
		delete.setOnMouseEntered(e -> {
			if (!delete.isDisabled()) {
				deleteIcon.setFitWidth(24);
			}
		});
		delete.setOnMouseExited(e -> {
			if (!delete.isDisabled()) {
				deleteIcon.setFitWidth(22);
			}
		});
		delete.disableProperty().addListener((i, o, n) -> {
			if (o.booleanValue() != n.booleanValue()) {
				if (n) {
					delete.getStyleClass().remove("nbt-editor-delete-tag-label-enabled");
				} else {
					delete.getStyleClass().add("nbt-editor-delete-tag-label-enabled");
				}
			}
		});
		delete.setOnMouseClicked(e -> {
			NBTTreeView.NBTTreeItem selected = (NBTTreeView.NBTTreeItem) nbtTreeView.getSelectionModel().getSelectedItem();
			if (selected == null) {
				return;
			}
			if (selected.getParent() == null) {
				// without a root handler the root is the file itself, which cannot be deleted
				if (rootHandler == null) {
					return;
				}
				rootHandler.accept(null);
			}
			nbtTreeView.deleteSelectedItem();
			updateDeleteState();
			enableAddTagLabels(nbtTreeView.getPossibleChildTagTypesFromSelected());
		});
	}

	/**
	 * The delete button is only usable when something is selected, and the root tag can only be
	 * deleted when the owner knows how to handle a tree without a root.
	 */
	private void updateDeleteState() {
		NBTTreeView.NBTTreeItem selected = (NBTTreeView.NBTTreeItem) nbtTreeView.getSelectionModel().getSelectedItem();
		delete.setDisable(selected == null || selected.getParent() == null && rootHandler == null);
	}

	private void initAddTagLabels(Stage primaryStage) {
		addTagLabels.put(BYTE, iconLabel(NBTTreeView.NBTTreeCell.getIcon(BYTE), () -> ByteTag.valueOf((byte) 0)));
		addTagLabels.put(SHORT, iconLabel(NBTTreeView.NBTTreeCell.getIcon(SHORT), () -> ShortTag.valueOf((short) 0)));
		addTagLabels.put(INT, iconLabel(NBTTreeView.NBTTreeCell.getIcon(INT), () -> IntTag.valueOf(0)));
		addTagLabels.put(LONG, iconLabel(NBTTreeView.NBTTreeCell.getIcon(LONG), () -> LongTag.valueOf(0)));
		addTagLabels.put(FLOAT, iconLabel(NBTTreeView.NBTTreeCell.getIcon(FLOAT), () -> FloatTag.valueOf(0)));
		addTagLabels.put(DOUBLE, iconLabel(NBTTreeView.NBTTreeCell.getIcon(DOUBLE), () -> DoubleTag.valueOf(0)));
		addTagLabels.put(STRING, iconLabel(NBTTreeView.NBTTreeCell.getIcon(STRING), () -> StringTag.valueOf("")));
		addTagLabels.put(LIST, iconLabel(NBTTreeView.NBTTreeCell.getIcon(LIST), ListTag::new));
		addTagLabels.put(COMPOUND, iconLabel(NBTTreeView.NBTTreeCell.getIcon(COMPOUND), CompoundTag::new));
		addTagLabels.put(BYTE_ARRAY, iconLabel(NBTTreeView.NBTTreeCell.getIcon(BYTE_ARRAY), () -> {
			Long l = new RequestNumberDialog(primaryStage, Translation.DIALOG_REQUEST_NUMBER_TITLE_ARRAY_LENGTH, 0, Integer.MAX_VALUE).showAndWait().orElse(null);
			return l == null ? null : new ByteArrayTag(new byte[l.intValue()]);
		}));
		addTagLabels.put(INT_ARRAY, iconLabel(NBTTreeView.NBTTreeCell.getIcon(INT_ARRAY), () -> {
			Long l = new RequestNumberDialog(primaryStage, Translation.DIALOG_REQUEST_NUMBER_TITLE_ARRAY_LENGTH, 0, Integer.MAX_VALUE).showAndWait().orElse(null);
			return l == null ? null : new IntArrayTag(new int[l.intValue()]);
		}));
		addTagLabels.put(LONG_ARRAY, iconLabel(NBTTreeView.NBTTreeCell.getIcon(LONG_ARRAY), () -> {
			Long l = new RequestNumberDialog(primaryStage, Translation.DIALOG_REQUEST_NUMBER_TITLE_ARRAY_LENGTH, 0, Integer.MAX_VALUE).showAndWait().orElse(null);
			return l == null ? null : new LongArrayTag(new long[l.intValue()]);
		}));
	}

	private Label iconLabel(Image img, Supplier<Tag> tagSupplier) {
		ImageView icon = new ImageView(img);
		Label label = new Label("", icon);
		icon.setPreserveRatio(true);
		icon.setFitWidth(16);
		label.setOnMouseEntered(e -> icon.setFitWidth(18));
		label.setOnMouseExited(e -> icon.setFitWidth(16));
		label.getStyleClass().add("nbt-editor-add-tag-label");
		label.setOnMouseClicked(e -> {
			if (treeViewHolder != null) {
				treeViewHolder.setCenter(nbtTreeView);
			}
			Tag newTag = tagSupplier.get();
			if (newTag == null) {
				return;
			}
			if (nbtTreeView.addItemAtSelected("Unknown", newTag, true)) {
				// if we created a root tag, it is always a compound tag
				if (rootHandler != null && nbtTreeView.getRoot() != null && nbtTreeView.getRoot().getValue().getRef() == newTag) {
					rootHandler.accept((CompoundTag) newTag);
				}
				updateDeleteState();
			}
		});
		return label;
	}
}
