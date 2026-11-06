package net.querz.mcaselector.nbtstudio;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.util.Duration;
import net.querz.mcaselector.config.ConfigProvider;
import net.querz.mcaselector.config.WorldConfig;
import net.querz.mcaselector.io.FileHelper;
import net.querz.mcaselector.io.WorldDirectories;
import net.querz.mcaselector.io.mca.RegionMCAFile;
import net.querz.mcaselector.ui.component.NBTEditorOptionsBar;
import net.querz.mcaselector.ui.dialog.NBTEditorDialog;
import net.querz.mcaselector.util.point.Point2i;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Opens the chunk editor on a real chunk and checks that every tab still carries the row of add
 * and delete buttons, i.e. that the row moved into NBTEditorOptionsBar without losing anything.
 */
public class NbtEditorOptionsBarShot {

	private static <T extends Node> List<T> findAll(Node node, Class<T> type, List<T> out) {
		if (type.isInstance(node)) {
			out.add(type.cast(node));
		}
		if (node instanceof Parent parent) {
			for (Node child : parent.getChildrenUnmodifiable()) {
				findAll(child, type, out);
			}
		}
		return out;
	}

	public static void main(String[] args) throws Exception {
		net.querz.mcaselector.text.Translation.load(java.util.Locale.SIMPLIFIED_CHINESE);
		String out = args[0];
		File world = new File(args.length > 1 ? args[1] : "/Users/goog/Documents/Minecraft/falling_sky-1.1.2");

		WorldDirectories dirs = new WorldDirectories(new File(world, "region"),
				new File(world, "poi"), new File(world, "entities"));
		ConfigProvider.WORLD = WorldConfig.load(dirs, new ArrayList<>());

		File regionFile = new File(new File(world, "region"), "r.-2.0.mca");
		RegionMCAFile mca = new RegionMCAFile(regionFile);
		mca.load(false);
		Point2i region = FileHelper.parseMCAFileName(regionFile.getName());
		Point2i chunk = null;
		for (int i = 0; i < 1024; i++) {
			if (mca.getChunk(i) != null) {
				chunk = region.regionToChunk().add(new Point2i(i));
				break;
			}
		}
		System.out.println("CHUNK " + chunk);
		Point2i selectedChunk = chunk;

		Platform.startup(() -> {
			Stage owner = new Stage();
			URL base = NbtEditorOptionsBarShot.class.getClassLoader().getResource("style/base.css");
			Scene ownerScene = new Scene(new javafx.scene.layout.StackPane(), 1000, 800);
			if (base != null) {
				ownerScene.getStylesheets().add(base.toExternalForm());
			}
			owner.setScene(ownerScene);
			owner.show();

			NBTEditorDialog dialog = new NBTEditorDialog(null, owner, null, selectedChunk);
			dialog.show();

			PauseTransition p = new PauseTransition(Duration.millis(1500));
			p.setOnFinished(e -> {
				try {
					List<NBTEditorOptionsBar> bars = findAll(dialog.getDialogPane().getScene().getRoot(),
							NBTEditorOptionsBar.class, new ArrayList<>());
					List<TabPane> tabPanes = findAll(dialog.getDialogPane().getScene().getRoot(),
							TabPane.class, new ArrayList<>());
					System.out.println("BARS " + bars.size() + " TABS "
							+ (tabPanes.isEmpty() ? 0 : tabPanes.get(0).getTabs().size()));
					for (NBTEditorOptionsBar bar : bars) {
						long enabled = bar.getChildren().stream()
								.filter(n -> n instanceof Label l && !l.isDisabled()).count();
						Label delete = (Label) bar.getChildren().get(0);
						System.out.println("BAR buttons=" + bar.getChildren().size()
								+ " enabled=" + enabled + " deleteDisabled=" + delete.isDisabled());
					}
					try (FileOutputStream fos = new FileOutputStream(out)) {
						WritableImage img = dialog.getDialogPane().getScene().getRoot().snapshot(null, null);
						PixelReader pr = img.getPixelReader();
						int w = (int) img.getWidth();
						int h = (int) img.getHeight();
						fos.write(("P6\n" + w + " " + h + "\n255\n").getBytes("ASCII"));
						for (int y = 0; y < h; y++) {
							for (int x = 0; x < w; x++) {
								int argb = pr.getArgb(x, y);
								fos.write((argb >> 16) & 0xff);
								fos.write((argb >> 8) & 0xff);
								fos.write(argb & 0xff);
							}
						}
						System.out.println("WROTE " + out + " " + w + "x" + h);
					}
				} catch (Exception ex) {
					ex.printStackTrace();
				}
				System.exit(0);
			});
			p.play();
		});
	}
}
