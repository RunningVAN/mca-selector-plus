package net.querz.mcaselector.ui.component;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import javafx.util.Duration;
import net.querz.mcaselector.nbtstudio.NbtDataFiles;
import net.querz.mcaselector.ui.dialog.NbtFileEditorDialog;
import net.querz.nbt.CompoundTag;
import net.querz.nbt.NamedTag;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Drives the data file editor the way a user would: click the string button, select a tag and
 * delete it with the trash button, then save. The file has to contain the new tag and to have lost
 * the deleted one, otherwise the buttons would only be decoration.
 */
public class NbtFileEditorEditShot {

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

	private static NBTTreeView.NBTTreeItem findItem(javafx.scene.control.TreeItem<NBTTreeView.NamedTag> item, String name) {
		if (name.equals(item.getValue().getName())) {
			return (NBTTreeView.NBTTreeItem) item;
		}
		for (javafx.scene.control.TreeItem<NBTTreeView.NamedTag> child : item.getChildren()) {
			NBTTreeView.NBTTreeItem found = findItem(child, name);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	public static void main(String[] args) throws Exception {
		net.querz.mcaselector.text.Translation.load(java.util.Locale.SIMPLIFIED_CHINESE);

		File world = Files.createTempDirectory("nbt-file-editor-edit").toFile();
		File level = new File(world, "level.dat");
		CompoundTag root = new CompoundTag();
		CompoundTag data = new CompoundTag();
		data.putString("LevelName", "Marvis Test World");
		data.putInt("GameType", 1);
		data.putDouble("RandomSeed", 42.0);
		root.put("Data", data);
		NbtDataFiles.write(level, new NbtDataFiles.LoadedFile(new NamedTag("", root),
				NbtDataFiles.Compression.GZIP));

		NbtDataFiles.FileRef ref = new NbtDataFiles.FileRef(level, "level.dat");
		Platform.startup(() -> {
			Stage owner = new Stage();
			URL base = NbtFileEditorEditShot.class.getClassLoader().getResource("style/base.css");
			Scene ownerScene = new Scene(new javafx.scene.layout.StackPane(), 900, 700);
			if (base != null) {
				ownerScene.getStylesheets().add(base.toExternalForm());
			}
			owner.setScene(ownerScene);
			owner.show();

			NbtFileEditorDialog dialog = new NbtFileEditorDialog(ref, null, owner);
			dialog.show();

			PauseTransition step1 = new PauseTransition(Duration.millis(400));
			step1.setOnFinished(e -> {
				try {
					NBTEditorOptionsBar bar = find(dialog.getDialogPane().getScene().getRoot(), NBTEditorOptionsBar.class);
					List<Label> labels = new ArrayList<>();
					for (Node child : bar.getChildren()) {
						labels.add((Label) child);
					}
					Label delete = labels.get(0);
					List<Label> add = labels.subList(1, labels.size());
					long enabled = add.stream().filter(l -> !l.isDisabled()).count();
					System.out.println("ADD buttons=" + add.size() + " enabled=" + enabled);
					System.out.println("DELETE disabled at root=" + delete.isDisabled());

					// add a string tag on the root
					add.get(6).getOnMouseClicked().handle(null);
					System.out.println("STATUS after add=" + status(dialog));

					// select Data/GameType and delete it
					Field viewField = NbtFileEditorDialog.class.getDeclaredField("view");
					viewField.setAccessible(true);
					NBTTreeView view = (NBTTreeView) viewField.get(dialog);
					NBTTreeView.NBTTreeItem gameType = findItem(view.getRoot(), "GameType");
					System.out.println("GameType item found=" + (gameType != null) + " path="
							+ (gameType == null ? "-" : path(gameType)));
					view.getSelectionModel().select(gameType);
					System.out.println("DELETE disabled on GameType=" + delete.isDisabled());
					delete.getOnMouseClicked().handle(null);
					System.out.println("DELETE disabled back on root=" + delete.isDisabled());

					Method save = NbtFileEditorDialog.class.getDeclaredMethod("saveFile");
					save.setAccessible(true);
					save.invoke(dialog);

					CompoundTag reread = (CompoundTag) NbtDataFiles.read(level).tag().tag();
					CompoundTag rereadData = (CompoundTag) reread.get("Data");
					System.out.println("SAVED root keys=" + reread.keySet());
					System.out.println("SAVED Data keys=" + rereadData.keySet());
					System.out.println("STATUS after save=" + status(dialog));
				} catch (Exception ex) {
					ex.printStackTrace();
				}
				System.exit(0);
			});
			step1.play();
		});
	}

	private static String path(javafx.scene.control.TreeItem<NBTTreeView.NamedTag> item) {
		StringBuilder sb = new StringBuilder(item.getValue().getName());
		javafx.scene.control.TreeItem<NBTTreeView.NamedTag> parent = item.getParent();
		while (parent != null) {
			sb.insert(0, parent.getValue().getName() + "/");
			parent = parent.getParent();
		}
		return sb.toString();
	}

	private static String status(NbtFileEditorDialog dialog) throws Exception {
		Field f = NbtFileEditorDialog.class.getDeclaredField("status");
		f.setAccessible(true);
		return ((Label) f.get(dialog)).getText();
	}
}
