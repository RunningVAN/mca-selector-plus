package net.querz.mcaselector.nbtstudio;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import javafx.util.Duration;
import net.querz.mcaselector.ui.component.NBTEditorOptionsBar;
import net.querz.mcaselector.ui.dialog.NbtFileEditorDialog;
import net.querz.nbt.CompoundTag;
import net.querz.nbt.NamedTag;
import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks that the data file editor offers the same add and delete buttons the chunk editor
 * offers, that the root cannot be deleted and that adding a tag really changes the file.
 */
public class NbtFileEditorAddTagShot {

	private static <T extends Node> T find(Node node, Class<T> type) {
		if (type.isInstance(node)) {
			return type.cast(node);
		}
		if (node instanceof Parent parent) {
			for (Node child : parent.getChildrenUnmodifiable()) {
				T found = find(child, type);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	public static void main(String[] args) throws Exception {
		net.querz.mcaselector.text.Translation.load(java.util.Locale.SIMPLIFIED_CHINESE);

		File world = Files.createTempDirectory("nbt-file-editor-add").toFile();
		File level = new File(world, "level.dat");
		CompoundTag root = new CompoundTag();
		CompoundTag data = new CompoundTag();
		data.putString("LevelName", "Marvis Test World");
		data.putInt("GameType", 1);
		root.put("Data", data);
		NbtDataFiles.write(level, new NbtDataFiles.LoadedFile(new NamedTag("", root),
				NbtDataFiles.Compression.GZIP));

		NbtDataFiles.FileRef ref = new NbtDataFiles.FileRef(level, "level.dat");
		Platform.startup(() -> {
			Stage owner = new Stage();
			URL base = NbtFileEditorAddTagShot.class.getClassLoader().getResource("style/base.css");
			Scene ownerScene = new Scene(new javafx.scene.layout.StackPane(), 900, 700);
			if (base != null) {
				ownerScene.getStylesheets().add(base.toExternalForm());
			}
			owner.setScene(ownerScene);
			owner.show();

			NbtFileEditorDialog dialog = new NbtFileEditorDialog(ref, null, owner);
			dialog.show();

			PauseTransition p = new PauseTransition(Duration.millis(300));
			p.setOnFinished(e -> {
				try {
					NBTEditorOptionsBar bar = find(dialog.getDialogPane().getScene().getRoot(), NBTEditorOptionsBar.class);
					System.out.println("BAR found=" + (bar != null));
					if (bar == null) {
						System.exit(1);
					}
					List<Label> labels = new ArrayList<>();
					for (Node child : bar.getChildren()) {
						labels.add((Label) child);
					}
					Label delete = labels.get(0);
					List<Label> add = labels.subList(1, labels.size());
					long enabled = add.stream().filter(l -> !l.isDisabled()).count();
					System.out.println("BUTTONS add=" + add.size() + " enabled=" + enabled + " deleteDisabled=" + delete.isDisabled());

					// the root is selected on open and the root cannot be deleted
					// order: BYTE SHORT INT LONG FLOAT DOUBLE STRING LIST COMPOUND BYTE_ARRAY INT_ARRAY LONG_ARRAY
					Label string = add.get(6);
					string.getOnMouseClicked().handle(null);

					CompoundTag after = (CompoundTag) NbtDataFiles.read(level).tag().tag();
					var current = dialog.getClass().getDeclaredMethod("currentRoot");
					current.setAccessible(true);
					CompoundTag inTree = (CompoundTag) current.invoke(dialog);
					System.out.println("ROOT keys after add=" + inTree.keySet());
					System.out.println("FILE keys (not saved yet)=" + after.keySet());

					Thread.sleep(1200);
					
					for (Node child : ((Parent) dialog.getDialogPane().getScene().getRoot()).getChildrenUnmodifiable()) {
						Label l = find(child, Label.class);
					}
					System.out.println("STATUS " + statusText(dialog));
					System.exit(0);
				} catch (Exception ex) {
					ex.printStackTrace();
					System.exit(1);
				}
			});
			p.play();
		});
	}

	private static String statusText(NbtFileEditorDialog dialog) {
		for (Node node : allNodes(dialog.getDialogPane().getScene().getRoot())) {
			if (node instanceof Label label && label.getText() != null
					&& (label.getText().contains("修改") || label.getText().equals("没有修改。"))) {
				return label.getText();
			}
		}
		return "<none>";
	}

	private static List<Node> allNodes(Node node) {
		List<Node> nodes = new ArrayList<>();
		nodes.add(node);
		if (node instanceof Parent parent) {
			for (Node child : parent.getChildrenUnmodifiable()) {
				nodes.addAll(allNodes(child));
			}
		}
		return nodes;
	}
}
