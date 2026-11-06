package net.querz.mcaselector.nbtstudio;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import net.querz.mcaselector.ui.dialog.NbtStudioResultPane;
import net.querz.nbt.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks the merged window: one search fills the result pane inside the dialog that holds the
 * fields, with a chunk tab and a data file tab, and without opening a second window.
 */
public class NbtStudioResultPaneTest {

	private static boolean toolkit = true;

	@BeforeAll
	static void initToolkit() throws InterruptedException {
		// the tab labels and the summary line are translations, so they have to be loaded before
		// any of them is built, otherwise the assertions would compare against the raw keys
		net.querz.mcaselector.text.Translation.load(java.util.Locale.SIMPLIFIED_CHINESE);
		CountDownLatch latch = new CountDownLatch(1);
		try {
			Platform.startup(latch::countDown);
		} catch (IllegalStateException alreadyStarted) {
			latch.countDown();
		} catch (UnsupportedOperationException | ExceptionInInitializerError noDisplay) {
			// a machine without a window system cannot build JavaFX controls at all, so these checks
			// are skipped there instead of failing
			toolkit = false;
			latch.countDown();
		}
		latch.await();
		org.junit.jupiter.api.Assumptions.assumeTrue(toolkit, "no JavaFX toolkit available");
	}

	private static NbtQuery query() {
		return NbtQuery.create(null,
				NbtPattern.of("id", NbtMatchMode.CONTAINS, true),
				NbtPattern.of("chest", NbtMatchMode.CONTAINS, true),
				NbtQuery.ROOT_ALL, null, NbtQuery.DEFAULT_LIMIT, false, true);
	}

	private static NbtDataFiles.SearchOutcome files() {
		NbtDataFiles.FileRef level = new NbtDataFiles.FileRef(new File("/w/level.dat"), "level.dat");
		NbtDataFiles.FileRef structure = new NbtDataFiles.FileRef(
				new File("/w/generated/minecraft/structures/well.nbt"), "structures/well.nbt");
		return new NbtDataFiles.SearchOutcome(List.of(
				new NbtDataFiles.Match(level, "Data.Player.id", Tag.Type.STRING, "minecraft:chest"),
				new NbtDataFiles.Match(structure, "palette[1].Name", Tag.Type.STRING, "minecraft:chest")),
				4, 2, 4, false, List.of());
	}

	@Test
	void hiddenUntilFilled() {
		NbtStudioResultPane pane = new NbtStudioResultPane(null, null);
		assertFalse(pane.isVisible());
		assertFalse(pane.isManaged());
	}

	@Test
	void fillShowsChunksAndFilesInOnePane() {
		NbtStudioResultPane pane = new NbtStudioResultPane(null, null);
		pane.fill(List.of(new net.querz.mcaselector.util.point.Point2i(0, 0),
				new net.querz.mcaselector.util.point.Point2i(1, 2)), files(), query());

		assertTrue(pane.isVisible());
		assertTrue(pane.isManaged());
		assertEquals(2, pane.chunkCount());
		assertEquals(2, pane.fileCount());
		assertFalse(pane.isEmptyResult());

		TabPane tabs = (TabPane) pane.lookupTabPane();
		assertEquals(2, tabs.getTabs().size());
		// the chunk tab is first and is the one that is shown when chunks matched
		assertSame(tabs.getTabs().get(0), tabs.getSelectionModel().getSelectedItem());
		assertTrue(tabs.getTabs().get(0).getText().contains("2"));
		assertTrue(tabs.getTabs().get(1).getText().contains("2"));
	}

	@Test
	void filesTabIsChosenWhenOnlyFilesMatched() {
		NbtStudioResultPane pane = new NbtStudioResultPane(null, null);
		pane.fill(List.of(), files(), query());
		pane.selectChunkTab();
		pane.fill(List.of(), files(), query());

		Tab files = pane.tabsList().get(1);
		assertSame(files, pane.selectedTab());
	}

	@Test
	void emptyResultStillFillsAndSaysSo() {
		NbtStudioResultPane pane = new NbtStudioResultPane(null, null);
		pane.fill(List.of(), null, query());
		assertTrue(pane.isEmptyResult());
		assertTrue(pane.isVisible());
		assertEquals(0, pane.chunkCount());
	}

	@Test
	void headerCountsChunksAndFiles() {
		NbtStudioResultPane pane = new NbtStudioResultPane(null, null);
		pane.fill(List.of(new net.querz.mcaselector.util.point.Point2i(0, 0)), files(), query());
		String header = pane.headerTextValue();
		assertTrue(header.contains("1"), header);
		assertTrue(header.contains("4"), header);
	}

	@Test
	void rowsCarryThePathThatIsCopiedAndOpened() {
		NbtStudioResultPane pane = new NbtStudioResultPane(null, null);
		pane.fill(List.of(), files(), query());
		assertEquals("level.dat", pane.fileRow(0).file().label());
		assertEquals("Data.Player.id", pane.fileRow(0).path());
		assertTrue(pane.tabsList().get(1).getContent() instanceof TableView);
		assertTrue(pane.tabsList().get(0).getContent() instanceof TableView);
	}
}
