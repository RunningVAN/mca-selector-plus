package net.querz.mcaselector.nbtstudio;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.util.Duration;
import net.querz.mcaselector.ui.dialog.NbtStudioDialog;
import net.querz.nbt.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Renders the unified NBT Studio window to a PPM file so its layout can be looked at without
 * starting the whole application. Skipped when no window system is available.
 */
// manual layout check: it opens a real window and writes a snapshot, which is too slow and too
// environment dependent for the normal test run. Run it explicitly with --tests.
@org.junit.jupiter.api.Disabled("manual layout snapshot, run with --tests to inspect the window")
public class NbtStudioMergedWindowShotTest {

	private static final String OUT = System.getProperty("nbt.shot", "/tmp/nbt_merged.ppm");

	@BeforeAll
	static void init() throws InterruptedException {
		net.querz.mcaselector.text.Translation.load(java.util.Locale.SIMPLIFIED_CHINESE);
		CountDownLatch latch = new CountDownLatch(1);
		try {
			Platform.startup(latch::countDown);
		} catch (IllegalStateException already) {
			latch.countDown();
		}
		latch.await();
	}

	@Test
	void shots() throws Exception {
		CountDownLatch done = new CountDownLatch(1);
		Platform.runLater(() -> {
			try {
				Stage owner = new Stage();
				owner.setScene(new Scene(new javafx.scene.layout.StackPane(), 1000, 780));
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
				NbtDataFiles.SearchOutcome files = new NbtDataFiles.SearchOutcome(List.of(
						new NbtDataFiles.Match(level, "Data.Player.id", Tag.Type.STRING, "minecraft:chest"),
						new NbtDataFiles.Match(players, "id", Tag.Type.STRING, "minecraft:chest"),
						new NbtDataFiles.Match(village, "Sections[0].palette[2].Name", Tag.Type.STRING,
								"minecraft:chest"),
						new NbtDataFiles.Match(structure, "palette[1].Name", Tag.Type.STRING, "minecraft:chest")),
						42, 4, 4, false, List.of());

				NbtStudioDialog dialog = new NbtStudioDialog(owner, null);
				dialog.show();
				dialog.showResults(chunks, files, query);

				PauseTransition p = new PauseTransition(Duration.millis(1600));
				p.setOnFinished(e -> {
					try {
						for (ButtonType type : dialog.getDialogPane().getButtonTypes()) {
							if (dialog.getDialogPane().lookupButton(type) instanceof Button b) {
								System.out.println("BUTTON [" + b.getText() + "] default=" + b.isDefaultButton()
										+ " disabled=" + b.isDisabled());
							}
						}
						for (javafx.scene.Node n : dialog.getDialogPane().getScene().getRoot()
								.lookupAll(".tab-label")) {
							if (n instanceof Label l) {
								System.out.println("TAB [" + l.getText() + "]");
							}
						}
						boolean hasTabPane = !dialog.getDialogPane().getScene().getRoot()
								.lookupAll(".tab-pane").isEmpty();
						System.out.println("HAS_TABPANE " + hasTabPane + " RESIZABLE " + dialog.isResizable());
						write(dialog, OUT);
						// second shot: the data file half of the same window
						dialog.getResultPane().lookupTabPane().getSelectionModel().select(1);
						javafx.scene.Parent root2 = dialog.getDialogPane().getScene().getRoot();
						root2.applyCss();
						root2.layout();
						write(dialog, OUT.replace(".ppm", "_files.ppm"));
					} catch (Exception ex) {
						ex.printStackTrace();
					} finally {
						done.countDown();
					}
				});
				p.play();
			} catch (Exception ex) {
				ex.printStackTrace();
				done.countDown();
			}
		});
		assertTrue(done.await(30, TimeUnit.SECONDS), "snapshot did not finish");
		Platform.runLater(() -> {
			Stage s = new Stage();
			s.close();
		});
	}

	private static void write(NbtStudioDialog dialog, String path) throws Exception {
		javafx.scene.Parent root = dialog.getDialogPane().getScene().getRoot();
		// a table view only builds its rows during a layout pass, a raw snapshot would show an empty grid
		root.applyCss();
		root.layout();
		WritableImage img = root.snapshot(null, null);
		PixelReader pr = img.getPixelReader();
		int w = (int) img.getWidth();
		int h = (int) img.getHeight();
		try (FileOutputStream fos = new FileOutputStream(path)) {
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
		System.out.println("WROTE " + path + " " + w + "x" + h);
	}
}
