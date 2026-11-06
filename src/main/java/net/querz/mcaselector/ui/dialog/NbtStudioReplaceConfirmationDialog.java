package net.querz.mcaselector.ui.dialog;

import javafx.stage.Stage;
import net.querz.mcaselector.text.Translation;

/**
 * Confirmation before an NBT Studio replacement is written.
 *
 * <p>The generic change field confirmation reuses the header of the "delete chunks" dialog, which
 * asks the user to confirm a deletion of chunks - an NBT Studio replacement never deletes a chunk,
 * it rewrites the tags that matched. This dialog states the actual consequence instead: every
 * matching tag in the chunk files and, if enabled, in the given number of world data files is
 * overwritten.</p>
 */
public class NbtStudioReplaceConfirmationDialog extends ConfirmationDialog {

	public NbtStudioReplaceConfirmationDialog(int dataFileCount, Stage primaryStage) {
		super(
				primaryStage,
				Translation.DIALOG_NBT_STUDIO_REPLACE_CONFIRMATION_TITLE,
				Translation.DIALOG_NBT_STUDIO_REPLACE_CONFIRMATION_HEADER_SHORT,
				"change"
		);

		// the short header is bound to the translation property, swapping in the file count means
		// unbinding it first
		headerTextProperty().unbind();
		if (dataFileCount > 0) {
			setHeaderText(String.format(
					Translation.DIALOG_NBT_STUDIO_REPLACE_CONFIRMATION_HEADER_VERBOSE.toString(), dataFileCount));
		} else {
			setHeaderText(Translation.DIALOG_NBT_STUDIO_REPLACE_CONFIRMATION_HEADER_SHORT.toString());
		}
	}
}
