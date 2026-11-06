package net.querz.mcaselector.nbtstudio;

import net.querz.mcaselector.io.WorldDirectories;
import net.querz.nbt.CompoundTag;
import net.querz.nbt.IntTag;
import net.querz.nbt.ListTag;
import net.querz.nbt.NamedTag;
import net.querz.nbt.Tag;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** World data files must cover structure files too, and keep their compression when written back. */
public class NbtDataFilesStructureTest {

	private static File world() throws IOException {
		File root = Files.createTempDirectory("nbt-data-structure").toFile();
		return root;
	}

	private static void write(File file, CompoundTag root, NbtDataFiles.Compression compression) throws IOException {
		file.getParentFile().mkdirs();
		NbtDataFiles.write(file, new NbtDataFiles.LoadedFile(new NamedTag("", root), compression));
	}

	private static CompoundTag structure(String paletteName) {
		CompoundTag root = new CompoundTag();
		CompoundTag entry = new CompoundTag();
		entry.putString("Name", paletteName);
		ListTag palette = new ListTag(Tag.Type.COMPOUND);
		palette.add(entry);
		root.put("palette", palette);
		root.put("DataVersion", IntTag.valueOf(3953));
		return root;
	}

	@Test
	public void collectsStructureFilesNextToDatFiles() throws IOException {
		File root = world();
		write(new File(root, "level.dat"), structure("minecraft:stone"), NbtDataFiles.Compression.GZIP);
		write(new File(root, "data/map_0.dat"), structure("minecraft:stone"), NbtDataFiles.Compression.ZLIB);
		write(new File(root, "DIM-1/data/raids.dat"), structure("minecraft:stone"), NbtDataFiles.Compression.NONE);
		write(new File(root, "generated/minecraft/structures/well.nbt"),
				structure("minecraft:stone"), NbtDataFiles.Compression.GZIP);
		write(new File(root, "datapacks/pack/data/demo/structures/hut.nbt"),
				structure("minecraft:stone"), NbtDataFiles.Compression.NONE);

		List<NbtDataFiles.FileRef> refs = NbtDataFiles.collect(new WorldDirectories(new File(root, "region"), new File(root, "poi"), new File(root, "entities")));
		List<String> labels = refs.stream().map(NbtDataFiles.FileRef::label).toList();
		assertTrue(labels.contains("level.dat"), labels.toString());
		assertTrue(labels.stream().anyMatch(l -> l.equals("data/map_0.dat")), labels.toString());
		assertTrue(labels.stream().anyMatch(l -> l.endsWith("well.nbt")), labels.toString());
		assertTrue(labels.stream().anyMatch(l -> l.endsWith("hut.nbt")), labels.toString());
	}

	@Test
	public void replaceLandsInStructureFilesAndKeepsCompression() throws IOException {
		File root = world();
		File well = new File(root, "generated/minecraft/structures/well.nbt");
		write(well, structure("minecraft:stone"), NbtDataFiles.Compression.GZIP);
		byte[] before = Files.readAllBytes(well.toPath());

		NbtQuery query = NbtQuery.parse("value=minecraft:stone");
		List<NbtDataFiles.FileRef> refs = NbtDataFiles.collect(new WorldDirectories(new File(root, "region"), new File(root, "poi"), new File(root, "entities")));
		assertEquals(1, refs.size(), refs.toString());

		NbtDataFiles.SearchOutcome found = NbtDataFiles.search(refs, query);
		assertEquals(1, found.matches().size(), found.toString());
		assertEquals("palette[0].Name", found.matches().get(0).path());

		NbtDataFiles.ReplaceOutcome outcome = NbtDataFiles.replace(refs, query, false, true, "minecraft:dirt", false);
		assertEquals(1, outcome.replaced(), outcome.toString());

		byte[] after = Files.readAllBytes(well.toPath());
		assertEquals(before[0], after[0]);
		assertEquals(before[1], after[1]);
		assertEquals((byte) 0x1f, after[0]);
		assertEquals((byte) 0x8b, after[1]);
		CompoundTag reread = (CompoundTag) NbtDataFiles.read(well).tag().tag();
		assertEquals("minecraft:dirt",
				((net.querz.nbt.StringTag) ((CompoundTag) ((ListTag) reread.get("palette")).get(0))
						.get("Name")).getValue());
	}
}
