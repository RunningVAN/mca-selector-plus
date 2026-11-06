package net.querz.mcaselector.ui;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import net.querz.mcaselector.FxTestSupport;
import net.querz.mcaselector.config.ConfigProvider;
import net.querz.mcaselector.config.GlobalConfig;
import net.querz.mcaselector.config.OverlayConfig;
import net.querz.mcaselector.config.WorldConfig;
import net.querz.mcaselector.io.WorldDirectories;
import net.querz.mcaselector.nbtstudio.NbtMatchMode;
import net.querz.mcaselector.nbtstudio.NbtPattern;
import net.querz.mcaselector.nbtstudio.NbtQuery;
import net.querz.mcaselector.text.Translation;
import net.querz.mcaselector.tile.TileMap;
import net.querz.mcaselector.util.point.Point2i;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs "find" end to end the way the dialog runs it: real map, real progress dialog, real region
 * jobs, and checks that the chunk half still comes back with hits.
 *
 * <p>The chunk pass is the half that runs inside the progress task, on its worker thread. Watching
 * that run from in there has to stay clear of the JavaFX accessors that refuse to run outside the
 * FX thread: reading {@code Task#getProgress()} threw, the exception aborted the whole chunk pass,
 * the dialog closed on the spot and "find" returned nothing but data files.</p>
 */
public class NbtStudioFindChunksEndToEndTest {

	private static final File WORLD = new File(System.getProperty("world",
			System.getProperty("user.home") + "/Documents/Minecraft/科塔丘实验室4demo（联壁汉化1.2）"));

	private static boolean toolkit = true;

	@BeforeAll
	static void initToolkit() {
		FxTestSupport.start();
		toolkit = FxTestSupport.available();
		assumeTrue(toolkit, "no JavaFX toolkit available");
	}

	@Test
	public void findReturnsTheChunksItSelects() throws Exception {
		assumeTrue(new File(WORLD, "region").isDirectory(), "world not present");

		Translation.load(Locale.SIMPLIFIED_CHINESE);
		ConfigProvider.GLOBAL = new GlobalConfig();
		ConfigProvider.OVERLAY = new OverlayConfig();
		ConfigProvider.WORLD = new WorldConfig();
		ConfigProvider.WORLD.setWorldDirs(new WorldDirectories(new File(WORLD, "region"),
				new File(WORLD, "poi"), new File(WORLD, "entities")));

		NbtQuery query = NbtQuery.create(null,
				NbtPattern.of("command", NbtMatchMode.SMART, true),
				NbtPattern.of("\"text\":", NbtMatchMode.SMART, true),
				NbtQuery.ROOT_ALL, null, NbtQuery.DEFAULT_LIMIT, true, true);

		AtomicReference<String> failure = new AtomicReference<>();
		AtomicReference<List<Point2i>> hits = new AtomicReference<>(List.of());
		AtomicReference<Integer> selected = new AtomicReference<>(0);
		CountDownLatch done = new CountDownLatch(1);
		Platform.runLater(() -> {
			try {
				Stage stage = new Stage();
				stage.setScene(new Scene(new StackPane(), 400, 300));
				TileMap map = new TileMap(null, 800, 600);
				hits.set(NbtStudioFinder.findChunks(map, query, stage));
				selected.set(map.getSelectedChunks());
			} catch (Throwable t) {
				StringWriter sw = new StringWriter();
				t.printStackTrace(new PrintWriter(sw));
				failure.set(sw.toString());
			} finally {
				done.countDown();
			}
		});
		assertTrue(done.await(180, TimeUnit.SECONDS), "find never returned, so its dialog never closed");
		assertTrue(failure.get() == null, "find failed: " + failure.get());
		assertTrue(!hits.get().isEmpty(), "find has to return the chunks it matched, not data files only");
		assertTrue(selected.get() == hits.get().size(),
				"the returned chunks and the map selection have to agree, map has " + selected.get());
	}
}
