package net.querz.mcaselector.nbtstudio;

import net.querz.nbt.CompoundTag;
import net.querz.nbt.ListTag;
import net.querz.nbt.StringTag;
import net.querz.nbt.Tag;
import java.util.ArrayList;
import java.util.List;

/** temporary probe: how does a value pattern match against a plain string tag */
public class TmpProbe {
	public static void main(String[] args) {
		for (String raw : new String[]{"value=minecraft:stone", "value=stone", "value=minecraft:", "value=minecraft:stone;mode=contains"}) {
			NbtQuery q = NbtQuery.parse(raw);
			System.out.println("RAW " + raw + " valid=" + q.isValid() + " err=" + q.getError()
					+ " name=" + q.getNamePattern() + " value=" + q.getValuePattern()
					+ " mode=" + (q.getValuePattern() == null ? "-" : q.getValuePattern().getMode()));
			CompoundTag root = new CompoundTag();
			ListTag palette = new ListTag(Tag.Type.STRING);
			palette.add(StringTag.valueOf("minecraft:stone"));
			palette.add(StringTag.valueOf("minecraft:dirt"));
			CompoundTag section = new CompoundTag();
			section.put("palette", palette);
			ListTag sections = new ListTag(Tag.Type.COMPOUND);
			sections.add(section);
			root.put("sections", sections);
			NbtSearchResult r = NbtSearcher.search(root, "region", q);
			List<String> hits = new ArrayList<>();
			for (NbtSearchHit h : r.getHits()) {
				hits.add(h.path() + "=" + h.value());
			}
			System.out.println("  hits=" + r.getTotal() + " " + hits);
			List<String> flat = new ArrayList<>();
			CompoundTag flatRoot = new CompoundTag();
			flatRoot.put("a", StringTag.valueOf("minecraft:stone"));
			NbtSearcher.traverse(flatRoot, NbtQuery.parse("value=minecraft:stone"), (tag, parent, key, index, path) -> flat.add(path + "#" + System.identityHashCode(tag)));
			System.out.println("  flat=" + flat);
			NbtSearcher.traverse(root, q, (tag, parent, key, index, path) -> {
				System.out.println("  VISIT " + path + " tag#" + System.identityHashCode(tag)
						+ " parent=" + (parent == null ? "null" : parent.getClass().getSimpleName() + "#" + System.identityHashCode(parent))
						+ " key=" + key + " index=" + index);
				StackTraceElement[] st = new Exception().getStackTrace();
				for (int i = 1; i < Math.min(4, st.length); i++) {
					System.out.println("      at " + st[i]);
				}
			});
		}
	}
}
