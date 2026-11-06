package net.querz.mcaselector.nbtstudio;

import net.querz.mcaselector.io.mca.RegionChunk;
import net.querz.mcaselector.io.mca.RegionMCAFile;
import net.querz.mcaselector.util.point.Point2i;
import net.querz.nbt.Tag;
import java.io.File;

public class ProbeRepro {

	private static final String WORLD = System.getProperty("world",
			System.getProperty("user.home") + "/Documents/Minecraft/科塔丘实验室4demo（联壁汉化1.2）");

	public static void main(String[] args) throws Exception {
		NbtPattern name = NbtPattern.of("command", NbtMatchMode.SMART, true);
		NbtPattern value = NbtPattern.of("\"text\":", NbtMatchMode.SMART, true);
		System.out.println("name.pattern built=" + (name != null) + " value.pattern built=" + (value != null));

		NbtQuery dialog = NbtQuery.create(null, name, value, NbtQuery.ROOT_ALL, null, NbtQuery.DEFAULT_LIMIT, true, true);
		System.out.println("[dialog]      " + dialog.canonical());

		NbtQuery reparsed = NbtQuery.parse(dialog.canonical());
		System.out.println("[reparsed]    valid=" + reparsed.isValid() + " err=" + reparsed.getError()
				+ " name=" + (reparsed.getNamePattern() == null ? "-" : "[" + reparsed.getNamePattern().getSource() + "]")
				+ " value=" + (reparsed.getValuePattern() == null ? "-" : "[" + reparsed.getValuePattern().getSource() + "]")
				+ " any=" + reparsed.isMatchAny() + " chunk=" + reparsed.isChunkWide()
				+ " modeName=" + (reparsed.getNamePattern() == null ? "-" : reparsed.getNamePattern().getMode()));

		NbtQuery mapScan = NbtQuery.parse("name=command;value=\"text\":");  // strict, what a hand written filter would do
		System.out.println("[strict]      " + mapScan.canonical());

		Point2i[] chunks = {new Point2i(0, -2), new Point2i(-2, -29), new Point2i(17, -22)};
		for (Point2i chunk : chunks) {
			Tag region = loadRegion(chunk);
			System.out.println("chunk " + chunk + " loaded=" + (region != null));
			if (region == null) {
				continue;
			}
			for (NbtQuery q : new NbtQuery[]{dialog, reparsed, mapScan}) {
				NbtSearchResult r = NbtSearcher.search(region, null, null, q);
				StringBuilder types = new StringBuilder();
				for (NbtSearchHit h : r.getHits()) {
					types.append(h.root()).append(':').append(h.path()).append("  ");
				}
				System.out.println("   " + q.canonical() + "\n      -> total=" + r.getTotal() + " " + types);
			}
			String raw = region.toString();
			System.out.println("   rawContainsQuotedText=" + raw.contains("\"text\":"));
		}
	}

	private static Tag loadRegion(Point2i chunk) throws Exception {
		Point2i region = chunk.chunkToRegion();
		File file = new File(WORLD, "region/r." + region.getX() + "." + region.getZ() + ".mca");
		if (!file.exists()) {
			return null;
		}
		RegionChunk data = new RegionMCAFile(file).loadSingleChunk(chunk);
		return data == null ? null : data.getData();
	}
}
