package net.querz.mcaselector.ui.dialog;

import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import net.querz.mcaselector.text.Translation;

/**
 * Small themed dialog that reports how many chunks were selected or how many names and values were
 * replaced by NBT Studio operations.
 */
public class NbtStudioResultDialog extends Dialog<Void> {

	public NbtStudioResultDialog(String message, Stage primaryStage) {
		titleProperty().bind(Translation.DIALOG_NBT_STUDIO_RESULT_TITLE.getProperty());
		initStyle(StageStyle.UTILITY);
		getDialogPane().getStyleClass().add("nbt-studio-dialog-pane");
		if (primaryStage != null && primaryStage.getScene() != null) {
			getDialogPane().getScene().getStylesheets().addAll(primaryStage.getScene().getStylesheets());
		}
		var ownStyleSheet = NbtStudioResultDialog.class.getClassLoader()
				.getResource("style/component/nbt-studio-dialog.css");
		if (ownStyleSheet != null) {
			getDialogPane().getScene().getStylesheets().add(ownStyleSheet.toExternalForm());
		}

		getDialogPane().getButtonTypes().add(new ButtonType(
				Translation.BUTTON_OK.toString(), ButtonBar.ButtonData.OK_DONE));

		Label label = new Label(message);
		label.getStyleClass().add("nbt-studio-summary");
		label.setWrapText(true);
		label.setMaxWidth(420);

		VBox content = new VBox(label);
		content.setPadding(new Insets(12, 14, 6, 14));
		getDialogPane().setContent(content);
		getDialogPane().setPrefWidth(460);

		if (primaryStage != null) {
			initOwner(primaryStage);
		}
	}
}
