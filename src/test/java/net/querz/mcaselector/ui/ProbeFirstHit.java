package net.querz.mcaselector.ui;

import net.querz.mcaselector.filter.filters.NbtFilter;
import net.querz.mcaselector.io.WorldDirectories;
import net.querz.mcaselector.io.mca.Chunk;
import net.querz.mcaselector.io.mca.MCAFile;
import net.querz.mcaselector.io.mca.Region;
import net.querz.mcaselector.nbtstudio.NbtMatchMode;
import net.querz.mcaselector.nbtstudio.NbtPattern;
import net.querz.mcaselector.nbtstudio.NbtQuery;
import net.querz.mcaselector.nbtstudio.NbtSearcher;
import net.querz.mcaselector.selection.ChunkSet;
import net.querz.mcaselector.selection.Selection;
import net.querz.mcaselector.util.point.Point2i;
import net.querz.nbt.CompoundTag;
import java.io.File;

/**
 * Shows what "find" would open in the chunk editor: the first hit has to be the chunk that really
 * contains the data. Selection.one() returns the region coordinate, using it as a chunk coordinate
 * opens an unrelated chunk, which is exactly what happened before.
 */
public class ProbeFirstHit {

	public static void main(String[] args) throws Exception {
		String world = System.getProperty("world",
				System.getProperty("user.home") + "/Documents/Minecraft/科塔丘实验室4demo（联壁汉化1.2）");
		NbtQuery query = NbtQuery.create(null,
				NbtPattern.of("command", NbtMatchMode.SMART, true),
				NbtPattern.of("\"text\":", NbtMatchMode.SMART, true),
				NbtQuery.ROOT_ALL, null, NbtQuery.DEFAULT_LIMIT, true, true);

		NbtFilter filter = new NbtFilter();
		filter.setFilterValue(query.canonical());

		File regionDir = new File(world, "region");
		WorldDirectories wd = new WorldDirectories(regionDir, new File(world, "poi"), new File(world, "entities"));
		Region region = Region.loadRegion(wd.makeRegionDirectories(new Point2i(0, -2)));
		ChunkSet hits = region.getFilteredChunks(filter, null);
		Selection selection = new Selection();
		selection.addAll(new Point2i(0, -2), hits);

		Point2i oldBehaviour = selection.one();
		Point2i fixed = DialogHelper.firstChunkOf(selection);
		System.out.println("hits in region (0,-2) = " + (hits == null ? 0 : hits.size()));
		System.out.println("old firstHit = " + oldBehaviour + " (region coordinate, used as a chunk) -> matches "
				+ count(region, oldBehaviour, query));
		System.out.println("new firstHit = " + fixed + " -> matches " + count(region, fixed, query));
	}

	private static int count(Region region, Point2i chunk, NbtQuery query) {
		try {
			Chunk data = region.getRegion().loadSingleChunk(chunk);
			if (data == null || !(data.getData() instanceof CompoundTag compound)) {
				return 0;
			}
			return NbtSearcher.search(compound, null, query, 0).getHits().size();
		} catch (Exception ex) {
			return -1;
		}
	}
}
