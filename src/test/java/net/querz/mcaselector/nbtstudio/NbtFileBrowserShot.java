package net.querz.mcaselector.nbtstudio;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.util.Duration;
import net.querz.mcaselector.io.WorldDirectories;
import net.querz.mcaselector.ui.dialog.NbtFileBrowserDialog;
import net.querz.nbt.CompoundTag;
import net.querz.nbt.IntTag;
import net.querz.nbt.NamedTag;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;

/**
 * Renders the file browser on a real world folder: everything is listed without searching, and the
 * filter narrows the same list down. Writes a PPM of both states.
 */
public class NbtFileBrowserShot {

	private static void write(File file, String name) throws Exception {
		file.getParentFile().mkdirs();
		CompoundTag root = new CompoundTag();
		CompoundTag data = new CompoundTag();
		data.putString("LevelName", name);
		data.putInt("DataVersion", 3953);
		root.put("Data", data);
		NbtDataFiles.write(file, new NbtDataFiles.LoadedFile(new NamedTag("", root), NbtDataFiles.Compression.GZIP));
	}

	private static void dumpRows(TreeTableView<Object> tree, String prefix) {
		TreeItem<Object> root = tree.getRoot();
		if (root == null) {
			System.out.println(prefix + "NO ROOT");
			return;
		}
		for (TreeItem<Object> group : root.getChildren()) {
			System.out.println(prefix + "GROUP " + group.getValue());
			for (TreeItem<Object> file : group.getChildren()) {
				System.out.println(prefix + "  FILE  " + file.getValue());
			}
		}
	}

	private static TreeTableView<Object> treeOf(NbtFileBrowserDialog dialog) {
		javafx.scene.Node node = dialog.getDialogPane().getScene().getRoot().lookup(".tree-table-view");
		return (TreeTableView<Object>) node;
	}

	private static void snapshot(javafx.scene.Node root, String out) throws Exception {
		WritableImage img = root.snapshot(null, null);
		PixelReader pr = img.getPixelReader();
		int w = (int) img.getWidth();
		int h = (int) img.getHeight();
		try (FileOutputStream fos = new FileOutputStream(out)) {
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
		}
		System.out.println("WROTE " + out + " " + w + "x" + h);
	}

	public static void main(String[] args) throws Exception {
		String out = args[0];
		net.querz.mcaselector.text.Translation.load(java.util.Locale.SIMPLIFIED_CHINESE);

		File world;
		if (args.length > 1) {
			// render a real world folder instead of the synthetic one
			world = new File(args[1]);
			if (!world.isDirectory()) {
				throw new IllegalArgumentException("not a world folder: " + world.getAbsolutePath());
			}
		} else {
			world = Files.createTempDirectory("nbt-browser").toFile();
			write(new File(world, "level.dat"), "Browser Test World");
			write(new File(world, "data/raids.dat"), "raids");
			write(new File(world, "data/idcounts.dat"), "idcounts");
			write(new File(world, "data/map_0.dat"), "map");
			write(new File(world, "playerdata/069a79f4-44e9-4726-a5be-fca90e38aaf5.dat"), "player");
			write(new File(world, "DIM-1/data/raids.dat"), "nether raids");
			write(new File(world, "structures/plains_small_house_1.nbt"), "house");
		}
		System.out.println("WORLD " + world.getAbsolutePath());
		WorldDirectories dirs = new WorldDirectories(new File(world, "region"),
				new File(world, "poi"), new File(world, "entities"));
		List<NbtDataFiles.FileRef> files = NbtDataFiles.collect(dirs);
		System.out.println("COLLECTED " + files.size() + " " + files.stream().map(NbtDataFiles.FileRef::label).toList());

		Platform.startup(() -> {
			Stage owner = new Stage();
			URL base = NbtFileBrowserShot.class.getClassLoader().getResource("style/base.css");
			Scene ownerScene = new Scene(new javafx.scene.layout.StackPane(), 980, 760);
			if (base != null) {
				ownerScene.getStylesheets().add(base.toExternalForm());
			}
			owner.setScene(ownerScene);
			owner.show();

			NbtFileBrowserDialog dialog = new NbtFileBrowserDialog(
					() -> NbtDataFiles.collect(dirs), owner);
			dialog.show();

			PauseTransition first = new PauseTransition(Duration.millis(800));
			first.setOnFinished(e -> {
				try {
					TreeTableView<Object> tree = treeOf(dialog);
					System.out.println("== ALL FILES");
					dumpRows(tree, "");
					System.out.println("SUMMARY " + dialog.getDialogPane().lookup(".nbt-studio-summary"));
					System.out.println("GEOM dialog=" + dialog.getWidth() + "x" + dialog.getHeight()
							+ " pane=" + dialog.getDialogPane().getWidth() + "x" + dialog.getDialogPane().getHeight()
							+ " scene=" + dialog.getDialogPane().getScene().getWidth() + "x" + dialog.getDialogPane().getScene().getHeight()
							+ " tree=" + tree.getWidth() + "x" + tree.getHeight()
							+ " panePref=" + dialog.getDialogPane().prefWidth(-1) + "x" + dialog.getDialogPane().prefHeight(-1));
					snapshot(dialog.getDialogPane().getScene().getRoot(), out);

					javafx.scene.control.TextField filter =
							(javafx.scene.control.TextField) dialog.getDialogPane().getScene().getRoot().lookup(".text-field");
					filter.setText("raids");
					PauseTransition second = new PauseTransition(Duration.millis(500));
					second.setOnFinished(e2 -> {
						try {
							System.out.println("== FILTERED BY raids");
							dumpRows(treeOf(dialog), "");
							snapshot(dialog.getDialogPane().getScene().getRoot(),
									out.replace(".ppm", "-filtered.ppm"));
							filter.setText("no-such-file");
							System.out.println("== FILTERED BY no-such-file");
							dumpRows(treeOf(dialog), "");
							System.out.println("PLACEHOLDER " + treeOf(dialog).getPlaceholder());
						} catch (Exception ex) {
							ex.printStackTrace();
						}
						System.exit(0);
					});
					second.play();
				} catch (Exception ex) {
					ex.printStackTrace();
					System.exit(1);
				}
			});
			first.play();
		});
	}
}
