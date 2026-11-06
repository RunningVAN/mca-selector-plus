package net.querz.mcaselector.nbtstudio;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.util.Duration;
import net.querz.mcaselector.ui.dialog.NbtFileEditorDialog;
import net.querz.nbt.CompoundTag;
import net.querz.nbt.NamedTag;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.nio.file.Files;

/**
 * Renders the data file tree editor on a real gzip compressed level.dat and checks that saving
 * writes the file back with the compression it was read with.
 */
public class NbtFileEditorShot {

	private static void dump(javafx.scene.Node node, int depth) {
		String text = "";
		if (node instanceof javafx.scene.control.Labeled l && l.getText() != null) {
			text = l.getText();
		}
		System.out.printf("%s%s%s%n", "  ".repeat(depth), node.getClass().getSimpleName(),
				text.isEmpty() ? "" : " text=[" + text.substring(0, Math.min(60, text.length())) + "]");
		if (node instanceof javafx.scene.Parent parent) {
			for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
				dump(child, depth + 1);
			}
		}
	}

	private static byte[] head(File file) throws Exception {
		byte[] all = Files.readAllBytes(file.toPath());
		return java.util.Arrays.copyOf(all, Math.min(2, all.length));
	}

	public static void main(String[] args) throws Exception {
		String out = args[0];
		net.querz.mcaselector.text.Translation.load(java.util.Locale.SIMPLIFIED_CHINESE);

		File world = Files.createTempDirectory("nbt-file-editor").toFile();
		File level = new File(world, "level.dat");
		CompoundTag root = new CompoundTag();
		CompoundTag data = new CompoundTag();
		data.putString("LevelName", "Marvis Test World");
		data.putInt("GameType", 1);
		data.putString("id", "minecraft:chest");
		root.put("Data", data);
		NbtDataFiles.write(level, new NbtDataFiles.LoadedFile(new NamedTag("", root),
				NbtDataFiles.Compression.GZIP));
		System.out.println("SAMPLE size=" + level.length() + " head=" + java.util.Arrays.toString(head(level)));

		NbtDataFiles.FileRef ref = new NbtDataFiles.FileRef(level, "level.dat");
		Platform.startup(() -> {
			Stage owner = new Stage();
			URL base = NbtFileEditorShot.class.getClassLoader().getResource("style/base.css");
			Scene ownerScene = new Scene(new javafx.scene.layout.StackPane(), 900, 700);
			if (base != null) {
				ownerScene.getStylesheets().add(base.toExternalForm());
			}
			owner.setScene(ownerScene);
			owner.show();

			NbtFileEditorDialog dialog = new NbtFileEditorDialog(ref, null, owner);
			dialog.show();

			PauseTransition p = new PauseTransition(Duration.millis(700));
			p.setOnFinished(e -> {
				dump(dialog.getDialogPane().getScene().getRoot(), 0);
				try {
					java.lang.reflect.Method save = NbtFileEditorDialog.class.getDeclaredMethod("saveFile");
					save.setAccessible(true);
					save.invoke(dialog);
					System.out.println("AFTER size=" + level.length() + " head="
							+ java.util.Arrays.toString(head(level)));
					CompoundTag reread = (CompoundTag) NbtDataFiles.read(level).tag().tag();
					CompoundTag rereadData = (CompoundTag) reread.get("Data");
					System.out.println("REREAD name=" + rereadData.get("LevelName")
							+ " gameType=" + rereadData.get("GameType"));
				} catch (Exception ex) {
					ex.printStackTrace();
				}
				try (FileOutputStream fos = new FileOutputStream(out)) {
					WritableImage img = dialog.getDialogPane().getScene().getRoot().snapshot(null, null);
					PixelReader pr = img.getPixelReader();
					int w = (int) img.getWidth();
					int h = (int) img.getHeight();
					ByteArrayOutputStream header = new ByteArrayOutputStream();
					header.write(("P6\n" + w + " " + h + "\n255\n").getBytes("ASCII"));
					fos.write(header.toByteArray());
					for (int y = 0; y < h; y++) {
						for (int x = 0; x < w; x++) {
							int argb = pr.getArgb(x, y);
							fos.write((argb >> 16) & 0xff);
							fos.write((argb >> 8) & 0xff);
							fos.write(argb & 0xff);
						}
					}
					System.out.println("WROTE " + out + " " + w + "x" + h);
				} catch (Exception ex) {
					ex.printStackTrace();
				}
				System.exit(0);
			});
			p.play();
		});
	}
}
