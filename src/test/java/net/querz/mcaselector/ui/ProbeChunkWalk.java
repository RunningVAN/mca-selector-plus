package net.querz.mcaselector.ui;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;
import net.querz.mcaselector.config.ConfigProvider;
import net.querz.mcaselector.config.WorldConfig;
import net.querz.mcaselector.filter.filters.NbtFilter;
import net.querz.mcaselector.io.WorldDirectories;
import net.querz.mcaselector.io.mca.Region;
import net.querz.mcaselector.nbtstudio.NbtMatchMode;
import net.querz.mcaselector.nbtstudio.NbtPattern;
import net.querz.mcaselector.nbtstudio.NbtQuery;
import net.querz.mcaselector.selection.ChunkSet;
import net.querz.mcaselector.selection.Selection;
import net.querz.mcaselector.text.Translation;
import net.querz.mcaselector.ui.dialog.NBTEditorDialog;
import net.querz.mcaselector.util.point.Point2i;

import javax.imageio.ImageIO;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Opens the chunk editor the way a find does, with every chunk that matched, then presses the
 * "next chunk" arrow once and prints what the header says before and after. Screenshots both.
 *
 * args: shotBefore shotAfter [term] [regionX regionZ] [maxChunks]
 */
public class ProbeChunkWalk {

	private static List<Node> allNodes(Node root) {
		List<Node> out = new ArrayList<>();
		out.add(root);
		if (root instanceof Parent parent) {
			for (Node child : parent.getChildrenUnmodifiable()) {
				out.addAll(allNodes(child));
			}
		}
		return out;
	}

	private static String header(Node root) {
		for (Node n : allNodes(root)) {
			if (n instanceof Label l && l.getStyleClass().contains("nbt-editor-chunk-position")) {
				return l.getText();
			}
		}
		return "<no header>";
	}

	private static Button arrow(Node root, String text) {
		for (Node n : allNodes(root)) {
			if (n instanceof Button b && text.equals(b.getText())) {
				return b;
			}
		}
		return null;
	}

	private static String selectedTab(Node root) {
		for (Node n : allNodes(root)) {
			if (n instanceof TabPane tp && tp.getSelectionModel().getSelectedItem() != null) {
				return tp.getSelectionModel().getSelectedItem().getText();
			}
		}
		return "<no tabs>";
	}

	/** First few rows of the tree that is on screen, to see whether another chunk was really loaded. */
	private static String treeSummary(Node root) {
		for (Node n : allNodes(root)) {
			if (n instanceof javafx.scene.control.TreeView<?> tv && tv.getRoot() != null) {
				StringBuilder sb = new StringBuilder();
				sb.append(((javafx.scene.control.TreeItem<?>) tv.getRoot()).getValue());
				int count = 0;
				for (javafx.scene.control.TreeItem<?> child : tv.getRoot().getChildren()) {
					if (count++ >= 3) {
						break;
					}
					sb.append(" | ").append(child.getValue());
				}
				return sb.toString();
			}
		}
		return "<no tree>";
	}

	private static String navInfo(Node root) {
		for (Node n : allNodes(root)) {
			if (n.getStyleClass().contains("nbt-editor-chunk-nav")) {
				javafx.geometry.Bounds b = n.getBoundsInParent();
				javafx.scene.control.ComboBox<?> cb = pickerOf(n);
				return "visible=" + n.isVisible() + " managed=" + n.isManaged()
						+ " size=" + (int) b.getWidth() + "x" + (int) b.getHeight()
						+ " y=" + (int) b.getMinY()
						+ " pickerWidth=" + (cb == null ? -1 : (int) cb.getWidth());
			}
		}
		return "<no nav>";
	}

	private static javafx.scene.control.ComboBox<?> pickerOf(Node root) {
		for (Node n : allNodes(root)) {
			if (n instanceof javafx.scene.control.ComboBox<?> cb) {
				return cb;
			}
		}
		return null;
	}

	private static void shot(NBTEditorDialog dialog, String path) {
		try {
			WritableImage img = dialog.getDialogPane().getScene().snapshot(null);
			ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", new File(path));
			System.out.println("SHOT " + (int) img.getWidth() + "x" + (int) img.getHeight() + " -> " + path);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public static void main(String[] args) throws Exception {
		String shotBefore = args[0];
		String shotAfter = args[1];
		String term = args.length > 2 ? args[2] : "minecraft";
		int regionX = args.length > 4 ? Integer.parseInt(args[3]) : 0;
		int regionZ = args.length > 4 ? Integer.parseInt(args[4]) : -2;
		int maxChunks = args.length > 5 ? Integer.parseInt(args[5]) : 4;

		File world = new File(System.getProperty("world",
				System.getProperty("user.home") + "/Documents/Minecraft/科塔丘实验室4demo（联壁汉化1.2）"));

		Translation.load(java.util.Locale.SIMPLIFIED_CHINESE);
		WorldConfig cfg = new WorldConfig();
		ConfigProvider.WORLD = cfg;
		cfg.setWorldDirs(new WorldDirectories(new File(world, "region"), new File(world, "poi"),
				new File(world, "entities")));

		NbtQuery query = NbtQuery.create(null, null,
				NbtPattern.of(term, NbtMatchMode.CONTAINS, true),
				NbtQuery.ROOT_ALL, null, NbtQuery.DEFAULT_LIMIT, false, false);

		NbtFilter filter = new NbtFilter();
		filter.setFilterValue(query.canonical());

		// the real search, so the hit list is exactly what "find" would have found
		Region region = Region.loadRegion(new WorldDirectories(new File(world, "region"),
				new File(world, "poi"), new File(world, "entities")).makeRegionDirectories(new Point2i(regionX, regionZ)));
		ChunkSet hits = region.getFilteredChunks(filter, null);
		Selection selection = new Selection();
		selection.addAll(new Point2i(regionX, regionZ), hits);

		List<Point2i> allHits = new ArrayList<>();
		DialogHelper.collectChunks(selection, allHits);
		List<Point2i> hitChunks = DialogHelper.sortChunks(allHits);
		System.out.println("hits in region (" + regionX + "," + regionZ + ") = " + hitChunks.size());
		if (hitChunks.isEmpty()) {
			System.out.println("RESULT nothing matched, cannot probe the walk");
			System.exit(1);
		}
		if (hitChunks.size() > maxChunks) {
			hitChunks = new ArrayList<>(hitChunks.subList(0, maxChunks));
		}
		System.out.println("walking " + hitChunks);

		List<Point2i> finalHits = hitChunks;
		CountDownLatch latch = new CountDownLatch(1);
		Platform.startup(() -> {
			Stage stage = new Stage();
			stage.setScene(new Scene(new StackPane(), 200, 120));
			stage.show();

			NBTEditorDialog dialog = new NBTEditorDialog(null, stage, query, finalHits.get(0), finalHits);
			dialog.show();

			PauseTransition first = new PauseTransition(Duration.seconds(3));
			first.setOnFinished(e -> {
				System.out.println("RESULT header before = [" + header(dialog.getDialogPane()) + "]");
				System.out.println("RESULT tab before = " + selectedTab(dialog.getDialogPane()) + " tree = " + treeSummary(dialog.getDialogPane()));
				shot(dialog, shotBefore);

				Button next = arrow(dialog.getDialogPane(), "\u25B6");
				if (next == null) {
					System.out.println("RESULT no next arrow found");
					dialog.close();
					stage.close();
					Platform.exit();
					latch.countDown();
					return;
				}
				System.out.println("RESULT next arrow visible = " + next.isVisible());
				next.fire();

				PauseTransition second = new PauseTransition(Duration.seconds(3));
				second.setOnFinished(e2 -> {
					System.out.println("RESULT header after next = [" + header(dialog.getDialogPane()) + "] nav " + navInfo(dialog.getDialogPane()));
					System.out.println("RESULT tab after next = " + selectedTab(dialog.getDialogPane()) + " tree = " + treeSummary(dialog.getDialogPane()));
					shot(dialog, shotAfter);

					Button previous = arrow(dialog.getDialogPane(), "\u25C0");
					if (previous == null) {
						System.out.println("RESULT no previous arrow found");
						dialog.close();
						stage.close();
						Platform.exit();
						latch.countDown();
						return;
					}
					previous.fire();
					PauseTransition third = new PauseTransition(Duration.seconds(2));
					third.setOnFinished(e3 -> {
						System.out.println("RESULT header after back = [" + header(dialog.getDialogPane()) + "]");
						previous.fire();
						PauseTransition fourth = new PauseTransition(Duration.seconds(2));
						fourth.setOnFinished(e4 -> {
							System.out.println("RESULT header after wrap = [" + header(dialog.getDialogPane()) + "]");
							javafx.scene.control.ComboBox<?> picker = pickerOf(dialog.getDialogPane());
							if (picker == null) {
								System.out.println("RESULT no chunk picker found");
								dialog.close();
								stage.close();
								Platform.exit();
								latch.countDown();
								return;
							}
							System.out.println("RESULT picker items = " + picker.getItems().size()
									+ " current = " + picker.getValue());
							picker.getSelectionModel().select(2);
							PauseTransition fifth = new PauseTransition(Duration.seconds(2));
							fifth.setOnFinished(e5 -> {
								System.out.println("RESULT header after pick = [" + header(dialog.getDialogPane()) + "]");
								System.out.println("RESULT tab after pick = " + selectedTab(dialog.getDialogPane()) + " tree = " + treeSummary(dialog.getDialogPane()));
								shot(dialog, shotAfter.replace("walk-after", "walk-picked"));
								dialog.close();
								stage.close();
								Platform.exit();
								latch.countDown();
							});
							fifth.play();
						});
						fourth.play();
					});
					third.play();
				});
				second.play();
			});
			first.play();
		});
		latch.await();
		System.exit(0);
	}
}
