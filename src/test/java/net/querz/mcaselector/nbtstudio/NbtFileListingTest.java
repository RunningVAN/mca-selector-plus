package net.querz.mcaselector.nbtstudio;

import org.junit.jupiter.api.Test;
import java.io.File;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The file browser lists the whole world, and the filter only narrows that list down. */
public class NbtFileListingTest {

	private static NbtDataFiles.FileRef ref(String label) {
		return new NbtDataFiles.FileRef(new File("/world/" + label), label);
	}

	private static List<NbtDataFiles.FileRef> world() {
		return List.of(
				ref("level.dat"),
				ref("data/idcounts.dat"),
				ref("data/raids.dat"),
				ref("playerdata/00000000-0000-0000-0000-000000000000.dat"),
				ref("DIM-1/data/raids.dat"),
				ref("structures/village/plains/houses/plains_small_house_1.nbt"));
	}

	@Test
	public void everyFileIsListedWithoutFilter() {
		List<NbtFileListing.Group> groups = NbtFileListing.group(world(), null);
		assertEquals(5, groups.size());
		assertEquals(6, NbtFileListing.count(groups));
	}

	@Test
	public void worldDirectoryComesFirst() {
		List<NbtFileListing.Group> groups = NbtFileListing.group(world(), null);
		NbtFileListing.Group first = groups.get(0);
		assertTrue(first.isRoot());
		assertEquals(List.of("level.dat"), first.files().stream().map(NbtDataFiles.FileRef::label).toList());
	}

	@Test
	public void filesAreGroupedByTheirDirectory() {
		List<NbtFileListing.Group> groups = NbtFileListing.group(world(), null);
		NbtFileListing.Group data = groups.stream()
				.filter(g -> "data".equals(g.directory())).findFirst().orElseThrow();
		assertEquals(List.of("data/idcounts.dat", "data/raids.dat"),
				data.files().stream().map(NbtDataFiles.FileRef::label).toList());
		assertEquals("data", data.name());

		NbtFileListing.Group structure = groups.stream()
				.filter(g -> g.directory().startsWith("structures/")).findFirst().orElseThrow();
		assertEquals("plains_small_house_1.nbt", structure.files().get(0).file().getName());
	}

	@Test
	public void filterKeepsOnlyMatchingFilesButNotTheirDirectories() {
		List<NbtFileListing.Group> groups = NbtFileListing.group(world(), "raids");
		assertEquals(2, groups.size());
		assertEquals(2, NbtFileListing.count(groups));
		assertTrue(groups.stream().noneMatch(NbtFileListing.Group::isRoot));
	}

	@Test
	public void filterIgnoresCase() {
		List<NbtFileListing.Group> groups = NbtFileListing.group(world(), "LEVEL");
		assertEquals(1, groups.size());
		assertEquals(1, NbtFileListing.count(groups));
		assertTrue(groups.get(0).isRoot());
	}

	@Test
	public void filterMatchesDirectoriesToo() {
		List<NbtFileListing.Group> groups = NbtFileListing.group(world(), "playerdata/");
		assertEquals(1, groups.size());
		assertEquals("playerdata", groups.get(0).directory());
	}

	@Test
	public void blankFilterKeepsEverything() {
		assertEquals(6, NbtFileListing.count(NbtFileListing.group(world(), "   ")));
	}

	@Test
	public void unmatchedFilterLeavesAnEmptyList() {
		assertTrue(NbtFileListing.group(world(), "does-not-exist").isEmpty());
		assertEquals(0, NbtFileListing.count(NbtFileListing.group(world(), "does-not-exist")));
	}

	@Test
	public void missingInputIsHandled() {
		assertTrue(NbtFileListing.group(null, null).isEmpty());
		assertTrue(NbtFileListing.group(List.of(), "").isEmpty());
	}

	@Test
	public void directoryOfRootFileIsEmpty() {
		assertEquals("", NbtFileListing.directoryOf(ref("level.dat")));
		assertEquals("a/b", NbtFileListing.directoryOf(ref("a/b/c.dat")));
	}
}
