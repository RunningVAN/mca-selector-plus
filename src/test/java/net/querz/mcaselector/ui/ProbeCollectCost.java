package net.querz.mcaselector.ui;

import net.querz.mcaselector.filter.Operator;
import net.querz.mcaselector.filter.filters.GroupFilter;
import net.querz.mcaselector.filter.filters.NbtFilter;
import net.querz.mcaselector.io.RegionDirectories;
import net.querz.mcaselector.io.WorldDirectories;
import net.querz.mcaselector.io.mca.Region;
import net.querz.mcaselector.nbtstudio.NbtMatchMode;
import net.querz.mcaselector.nbtstudio.NbtPattern;
import net.querz.mcaselector.nbtstudio.NbtQuery;
import net.querz.mcaselector.selection.ChunkSet;
import net.querz.mcaselector.selection.Selection;
import net.querz.mcaselector.tile.Tile;
import net.querz.mcaselector.util.point.Point2i;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Measures what the "find" callback costs on the job thread, so we can tell whether the hit list
 * accumulation added in this version is what made the progress bar crawl, or whether the cost sits
 * in the per-region selection merge that the UI thread does.
 */
public class ProbeCollectCost {

	public static void main(String[] args) throws Exception {
		String world = System.getProperty("world",
				System.getProperty("user.home") + "/Documents/Minecraft/派对b");
		NbtQuery query = NbtQuery.create(null,
				NbtPattern.of("command", NbtMatchMode.SMART, true),
				NbtPattern.of("\"text\":", NbtMatchMode.SMART, true),
				NbtQuery.ROOT_ALL, null, NbtQuery.DEFAULT_LIMIT, true, true);

		NbtFilter nbtFilter = new NbtFilter();
		nbtFilter.setFilterValue(query.canonical());
		GroupFilter filter = new GroupFilter(Operator.AND);
		filter.addFilter(nbtFilter);

		WorldDirectories wd = new WorldDirectories(
				new File(world, "region"), new File(world, "poi"), new File(world, "entities"));
		RegionDirectories[] rds = wd.listRegions(null);
		System.out.println("world = " + world);
		System.out.println("regions to scan = " + (rds == null ? 0 : rds.length));

		List<Point2i> found = Collections.synchronizedList(new ArrayList<>());
		Selection accumulated = new Selection();
		long load = 0, select = 0, collect = 0, mergeCount = 0;
		int hitRegions = 0, hitChunks = 0;

		long start = System.nanoTime();
		for (RegionDirectories rd : rds) {
			long t0 = System.nanoTime();
			Region region = Region.loadRegion(rd);
			long t1 = System.nanoTime();
			ChunkSet chunks = region.getFilteredChunks(filter, null);
			long t2 = System.nanoTime();
			load += t1 - t0;
			select += t2 - t1;
			if (chunks == null || chunks.isEmpty()) {
				continue;
			}
			hitRegions++;
			hitChunks += chunks.size();
			if (chunks.size() == Tile.CHUNKS) {
				chunks = null;
			}
			Selection s = new Selection();
			s.addAll(rd.getLocation(), chunks);

			long t3 = System.nanoTime();
			DialogHelper.collectChunks(s, found);
			long t4 = System.nanoTime();
			collect += t4 - t3;

			// what TileMap.addSelection does per callback: merge into the running selection and
			// recount all of it from scratch
			accumulated.merge(s);
			int count = accumulated.count();
			mergeCount += System.nanoTime() - t4;
			if (count < 0) {
				throw new IllegalStateException();
			}
		}
		long total = System.nanoTime() - start;

		System.out.printf("total            %8.1f ms%n", total / 1e6);
		System.out.printf("  region load    %8.1f ms%n", load / 1e6);
		System.out.printf("  filter select  %8.1f ms%n", select / 1e6);
		System.out.printf("  collectChunks  %8.1f ms (%.1f%%)%n", collect / 1e6, 100.0 * collect / total);
		System.out.printf("  merge+count    %8.1f ms (%.1f%%)%n", mergeCount / 1e6, 100.0 * mergeCount / total);
		System.out.println("hit regions = " + hitRegions + ", hit chunks = " + hitChunks
				+ ", collected points = " + found.size());

		long t5 = System.nanoTime();
		List<Point2i> sorted = DialogHelper.sortChunks(found);
		System.out.printf("sortChunks     %8.1f ms (%d points)%n", (System.nanoTime() - t5) / 1e6, sorted.size());
	}
}
