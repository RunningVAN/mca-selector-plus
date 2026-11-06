package net.querz.mcaselector.nbtstudio;

import net.querz.mcaselector.filter.filters.NbtFilter;
import net.querz.mcaselector.io.WorldDirectories;
import net.querz.mcaselector.io.mca.Chunk;
import net.querz.mcaselector.io.mca.MCAFile;
import net.querz.mcaselector.io.mca.Region;
import net.querz.mcaselector.selection.ChunkSet;
import net.querz.mcaselector.util.point.Point2i;
import net.querz.nbt.CompoundTag;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Compares what the map filter calls a hit with what the chunk editor would actually find in that
 * chunk: a chunk the filter selects but the tree cannot expand is exactly the reported symptom.
 */
public class ProbeMapMismatch {

	public static void main(String[] args) throws Exception {
		String world = System.getProperty("world",
				System.getProperty("user.home") + "/Documents/Minecraft/科塔丘实验室4demo（联壁汉化1.2）");
		NbtQuery dialog = NbtQuery.create(null,
				NbtPattern.of("command", NbtMatchMode.SMART, true),
				NbtPattern.of("\"text\":", NbtMatchMode.SMART, true),
				NbtQuery.ROOT_ALL, null, NbtQuery.DEFAULT_LIMIT, true, true);
		NbtFilter filter = new NbtFilter();
		filter.setFilterValue(dialog.canonical());

		File regionDir = new File(world, "region");
		File[] files = regionDir.listFiles((d, n) -> n.endsWith(".mca"));
		int checked = 0;
		int noTreeMatch = 0;
		List<String> samples = new ArrayList<>();
		for (File f : files) {
			String[] parts = f.getName().split("\\.");
			Point2i region = new Point2i(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
			WorldDirectories wd = new WorldDirectories(regionDir, new File(world, "poi"), new File(world, "entities"));
			Region r;
			try {
				r = Region.loadRegion(wd.makeRegionDirectories(region));
			} catch (Exception ex) {
				continue;
			}
			ChunkSet hits = r.getFilteredChunks(filter, null);
			if (hits == null) {
				continue;
			}
			for (int p : hits) {
				Point2i chunk = new Point2i(p).add(region.regionToChunk());
				int inRegion = count(r.getRegion(), chunk, dialog);
				int inPoi = count(r.getPoi(), chunk, dialog);
				int inEntities = count(r.getEntities(), chunk, dialog);
				checked++;
				int total = inRegion + inPoi + inEntities;
				if (total == 0) {
					noTreeMatch++;
					if (samples.size() < 10) {
						samples.add("filter hit, tree empty: " + chunk);
					}
				} else if (inRegion == 0 && samples.size() < 20) {
					samples.add("hit outside the region tab: " + chunk + " (poi=" + inPoi + " entities=" + inEntities + ")");
				}
			}
		}
		System.out.println("filteredChunks=" + checked + " treeEmpty=" + noTreeMatch);
		for (String s : samples) {
			System.out.println(s);
		}
	}

	private static int count(MCAFile<?> file, Point2i chunk, NbtQuery query) {
		if (file == null) {
			return 0;
		}
		try {
			Chunk data = file.loadSingleChunk(chunk);
			if (data == null || !(data.getData() instanceof CompoundTag compound)) {
				return 0;
			}
			return NbtSearcher.search(compound, null, query, 0).getHits().size();
		} catch (Exception ex) {
			return -1;
		}
	}
}
