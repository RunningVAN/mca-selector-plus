package net.querz.mcaselector.nbtstudio;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.util.Duration;
import net.querz.mcaselector.nbtstudio.NbtMatchMode;
import net.querz.mcaselector.nbtstudio.NbtPattern;
import net.querz.mcaselector.nbtstudio.NbtQuery;
import net.querz.mcaselector.ui.dialog.NbtStudioDialog;
import net.querz.nbt.Tag;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.util.List;

/**
 * Renders the unified NBT Studio window - fields, buttons and results in one dialog - to a PPM file,
 * so the merged layout can be inspected without running the whole application.
 */
public class NbtStudioFindResultShot {

	private static void dump(javafx.scene.Node node, int depth) {
		String text = "";
		if (node instanceof javafx.scene.control.Labeled l) {
			text = l.getText();
		}
		javafx.geometry.Bounds inScene = node.localToScene(node.getLayoutBounds());
		System.out.printf("%s%s y=%.1f h=%.1f w=%.1f visible=%s%s%n",
				"  ".repeat(depth), node.getClass().getSimpleName(),
				inScene.getMinY(), inScene.getHeight(), inScene.getWidth(), node.isVisible(),
				text == null || text.isEmpty() ? "" : " text=[" + text.substring(0, Math.min(44, text.length())) + "]");
		if (node instanceof javafx.scene.Parent parent) {
			for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
				dump(child, depth + 1);
			}
		}
	}

	public static void main(String[] args) throws Exception {
		String out = args[0];
		boolean filesTab = args.length > 1 && args[1].equals("files");
		net.querz.mcaselector.text.Translation.load(java.util.Locale.SIMPLIFIED_CHINESE);

		Platform.startup(() -> {
			Stage owner = new Stage();
			URL base = NbtStudioFindResultShot.class.getClassLoader().getResource("style/base.css");
			Scene ownerScene = new Scene(new javafx.scene.layout.StackPane(), 1000, 760);
			if (base != null) {
				ownerScene.getStylesheets().add(base.toExternalForm());
			}
			owner.setScene(ownerScene);
			owner.show();

			NbtQuery query = NbtQuery.create(null,
					NbtPattern.of("id", NbtMatchMode.CONTAINS, true),
					NbtPattern.of("chest", NbtMatchMode.CONTAINS, true),
					NbtQuery.ROOT_ALL, null, NbtQuery.DEFAULT_LIMIT, false, true);

			List<net.querz.mcaselector.util.point.Point2i> chunks = List.of(
					new net.querz.mcaselector.util.point.Point2i(0, 0),
					new net.querz.mcaselector.util.point.Point2i(0, 1),
					new net.querz.mcaselector.util.point.Point2i(-3, 12),
					new net.querz.mcaselector.util.point.Point2i(7, 7));

			NbtDataFiles.FileRef level = new NbtDataFiles.FileRef(new File("/w/level.dat"), "level.dat");
			NbtDataFiles.FileRef players = new NbtDataFiles.FileRef(new File("/w/playerdata"), "playerdata/");
			NbtDataFiles.FileRef village = new NbtDataFiles.FileRef(
					new File("/w/data/village_poi.dat"), "data/village_poi.dat");
			NbtDataFiles.FileRef structure = new NbtDataFiles.FileRef(
					new File("/w/generated/minecraft/structures/well.nbt"), "structures/well.nbt");
			List<NbtDataFiles.Match> matches = List.of(
					new NbtDataFiles.Match(level, "Data.Player.id", Tag.Type.STRING, "minecraft:chest"),
					new NbtDataFiles.Match(players, "id", Tag.Type.STRING, "minecraft:chest"),
					new NbtDataFiles.Match(village, "Sections[0].palette[2].Name", Tag.Type.STRING, "minecraft:chest"),
					new NbtDataFiles.Match(structure, "palette[1].Name", Tag.Type.STRING, "minecraft:chest"));
			NbtDataFiles.SearchOutcome files = new NbtDataFiles.SearchOutcome(
					matches, 42, 4, matches.size(), false, List.of());

			NbtStudioDialog dialog = new NbtStudioDialog(owner, null);
			dialog.show();
			dialog.showResults(chunks, files, query);

			if (filesTab) {
				for (javafx.scene.Node n : dialog.getDialogPane().getScene().getRoot().lookupAll(".tab-pane")) {
					if (n instanceof TabPane tp) {
						tp.getSelectionModel().select(1);
					}
				}
			}
			for (javafx.scene.Node n : dialog.getDialogPane().getScene().getRoot().lookupAll(".tab-label")) {
				if (n instanceof Label l) {
					System.out.println("TAB [" + l.getText() + "]");
				}
			}
			PauseTransition p = new PauseTransition(Duration.millis(filesTab ? 1200 : 600));
			p.setOnFinished(e -> {
				dump(dialog.getDialogPane().getScene().getRoot(), 0);
				for (javafx.scene.control.ButtonType type : dialog.getDialogPane().getButtonTypes()) {
					if (dialog.getDialogPane().lookupButton(type) instanceof javafx.scene.control.Button b) {
						System.out.println("BUTTON [" + b.getText() + "] default=" + b.isDefaultButton());
					}
				}
				System.out.println("RESIZABLE " + dialog.isResizable());
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
