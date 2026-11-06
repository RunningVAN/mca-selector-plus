package net.querz.mcaselector.ui;

import net.querz.mcaselector.selection.ChunkSet;
import net.querz.mcaselector.selection.Selection;
import net.querz.mcaselector.ui.dialog.NBTEditorDialog;
import net.querz.mcaselector.util.point.Point2i;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The chunk editor shows one chunk, and the arrows in its header walk the chunks a search matched.
 * These are the pieces of that walk that can be checked without a screen: the index arithmetic,
 * turning a selection into an ordered hit list.
 */
class NbtEditorChunkWalkTest {

	@Test
	void stepForwardsWrapsAround() {
		assertEquals(1, NBTEditorDialog.stepHitIndex(0, 5, 1));
		assertEquals(4, NBTEditorDialog.stepHitIndex(3, 5, 1));
		assertEquals(0, NBTEditorDialog.stepHitIndex(4, 5, 1), "the last hit leads back to the first");
	}

	@Test
	void stepBackwardsWrapsAround() {
		assertEquals(4, NBTEditorDialog.stepHitIndex(0, 5, -1));
		assertEquals(3, NBTEditorDialog.stepHitIndex(4, 5, -1));
	}

	@Test
	void singleOrNoHitStaysPut() {
		assertEquals(0, NBTEditorDialog.stepHitIndex(0, 1, 1), "a lone hit has nowhere to go");
		assertEquals(0, NBTEditorDialog.stepHitIndex(0, 0, 1), "an empty list must not divide by zero");
		assertEquals(0, NBTEditorDialog.stepHitIndex(0, 0, -1));
	}

	@Test
	void selectionBecomesAllHitChunks() {
		// a chunk index is x & 0x1F for the column and index >> 5 for the row inside the region,
		// region (0,0) starts at chunk 0,0
		Selection selection = new Selection();
		ChunkSet chunks = new ChunkSet();
		chunks.set(0);
		chunks.set(33);
		chunks.set(5);
		selection.addAll(new Point2i(0, 0), chunks);

		List<Point2i> collected = new ArrayList<>();
		DialogHelper.collectChunks(selection, collected);

		assertEquals(3, collected.size());
		assertTrue(collected.contains(new Point2i(0, 0)));
		assertTrue(collected.contains(new Point2i(1, 1)), "chunk index 33 is x=1 z=1 inside the region");
		assertTrue(collected.contains(new Point2i(5, 0)));
	}

	@Test
	void wholeRegionHitExpandsToItsChunks() {
		Selection selection = new Selection();
		selection.addAll(new Point2i(0, 0), null);

		List<Point2i> collected = new ArrayList<>();
		DialogHelper.collectChunks(selection, collected);

		assertEquals(32 * 32, collected.size(), "a fully selected region holds 1024 chunks");
		assertTrue(collected.contains(new Point2i(0, 0)));
		assertTrue(collected.contains(new Point2i(31, 31)));
		assertFalse(collected.contains(new Point2i(32, 0)), "the next region must not leak in");
	}

	@Test
	void hitsAreOrderedLikeTheMap() {
		Selection selection = new Selection();
		ChunkSet chunks = new ChunkSet();
		chunks.set(40); // x=8 z=1
		chunks.set(2);  // x=2 z=0
		selection.addAll(new Point2i(0, 0), chunks);

		List<Point2i> collected = new ArrayList<>();
		DialogHelper.collectChunks(selection, collected);
		List<Point2i> sorted = DialogHelper.sortChunks(collected);

		assertEquals(new Point2i(2, 0), sorted.get(0));
		assertEquals(new Point2i(8, 1), sorted.get(1));
	}

	@Test
	void sortingDoesNotTouchTheCallersList() {
		List<Point2i> hits = new ArrayList<>(List.of(new Point2i(9, 9), new Point2i(1, 1)));
		List<Point2i> sorted = DialogHelper.sortChunks(hits);

		assertEquals(new Point2i(1, 1), sorted.get(0));
		assertEquals(new Point2i(9, 9), hits.get(0), "the collected order must survive a sort");
	}
}
