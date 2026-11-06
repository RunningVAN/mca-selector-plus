package net.querz.mcaselector.nbtstudio;

import net.querz.mcaselector.filter.filters.NbtFilter;
import net.querz.mcaselector.io.RegionDirectories;
import net.querz.mcaselector.io.WorldDirectories;
import net.querz.mcaselector.io.mca.Region;
import net.querz.mcaselector.selection.ChunkSet;
import net.querz.mcaselector.util.point.Point2i;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the same filter the map search runs (NbtFilter fed with query.canonical()) over a whole
 * region file, so we can see whether the map really selects only the chunks that contain the data.
 */
public class ProbeMapFilter {

	public static void main(String[] args) throws Exception {
		String world = System.getProperty("world",
				System.getProperty("user.home") + "/Documents/Minecraft/科塔丘实验室4demo（联壁汉化1.2）");
		NbtQuery dialog = NbtQuery.create(null,
				NbtPattern.of("command", NbtMatchMode.SMART, true),
				NbtPattern.of("\"text\":", NbtMatchMode.SMART, true),
				NbtQuery.ROOT_ALL, null, NbtQuery.DEFAULT_LIMIT, true, true);

		String canonical = dialog.canonical();
		System.out.println("canonical=" + canonical);
		NbtQuery reparsed = NbtQuery.parse(canonical);
		System.out.println("reparsed valid=" + reparsed.isValid()
				+ " any=" + reparsed.isMatchAny()
				+ " chunkWide=" + reparsed.isChunkWide()
				+ " name=" + (reparsed.getNamePattern() == null ? "null" : reparsed.getNamePattern().getSource())
				+ " value=" + (reparsed.getValuePattern() == null ? "null" : reparsed.getValuePattern().getSource())
				+ " mode=" + (reparsed.getValuePattern() == null ? "-" : reparsed.getValuePattern().getMode()));

		NbtFilter filter = new NbtFilter();
		filter.setFilterValue(canonical);
		System.out.println("filter valid=" + filter.isValid() + " value=" + filter.getFilterValue());

		File dir = new File(world, "region");
		File[] files = dir.listFiles((d, n) -> n.endsWith(".mca"));
		if (files == null) {
			System.out.println("no region files in " + dir);
			return;
		}
		List<String> lines = new ArrayList<>();
		int total = 0;
		for (File f : files) {
			String[] parts = f.getName().split("\\.");
			Point2i region = new Point2i(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
			RegionDirectories rd = new WorldDirectories(new File(world, "region"), new File(world, "poi"),
					new File(world, "entities")).makeRegionDirectories(region);
			ChunkSet hits;
			try {
				Region r = Region.loadRegion(rd);
				hits = r.getFilteredChunks(filter, null);
			} catch (Exception ex) {
				lines.add(f.getName() + " -> load error: " + ex.getClass().getSimpleName());
				continue;
			}
			int size = hits == null ? 0 : hits.size();
			total += size;
			lines.add(f.getName() + " -> " + size + " chunks");
			if (size > 0 && size <= 40) {
				List<String> coords = new ArrayList<>();
				for (int p : hits) {
					coords.add(new Point2i(p).add(region.regionToChunk()).toString());
				}
				System.out.println(f.getName() + " hits: " + String.join(" ", coords));
			}
		}
		System.out.println(String.join("\n", lines));
		System.out.println("total matched chunks = " + total);
	}
}
