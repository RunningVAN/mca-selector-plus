package net.querz.mcaselector.nbtstudio;

import net.querz.mcaselector.io.WorldDirectories;
import net.querz.mcaselector.util.progress.Progress;
import net.querz.nbt.CompoundTag;
import net.querz.nbt.IntTag;
import net.querz.nbt.ListTag;
import net.querz.nbt.NamedTag;
import net.querz.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The data file half of the find dialog used to run as one blocking pass that never reported a
 * unit of progress, which left its dialog open until the user pressed cancel. These tests pin the
 * two things that fix depends on: every file is reported, and a cancelled run stops early.
 */
public class NbtStudioProgressTest {

	/** Records what a dialog would have shown. */
	private static final class RecordingProgress implements Progress {

		private final List<String> reported = new ArrayList<>();
		private int max = -1;
		private int current;
		private boolean cancelled;

		@Override
		public void setMax(int max) {
			this.max = max;
		}

		@Override
		public void updateProgress(String msg, int progress) {
		}

		@Override
		public void done(String msg) {
		}

		@Override
		public boolean taskCancelled() {
			return cancelled;
		}

		@Override
		public void cancelTask() {
			cancelled = true;
		}

		@Override
		public void incrementProgress(String msg) {
			incrementProgress(msg, 1);
		}

		@Override
		public void incrementProgress(String msg, int progress) {
			current += progress;
			reported.add(msg);
		}

		@Override
		public void setMessage(String msg) {
		}
	}

	private static void write(File file, CompoundTag root) throws IOException {
		file.getParentFile().mkdirs();
		NbtDataFiles.write(file, new NbtDataFiles.LoadedFile(new NamedTag("", root),
				NbtDataFiles.Compression.GZIP));
	}

	private static CompoundTag data(String name) {
		CompoundTag root = new CompoundTag();
		CompoundTag entry = new CompoundTag();
		entry.putString("Name", name);
		ListTag palette = new ListTag(Tag.Type.COMPOUND);
		palette.add(entry);
		root.put("palette", palette);
		root.put("DataVersion", IntTag.valueOf(3953));
		return root;
	}

	private static List<NbtDataFiles.FileRef> world(int files) throws IOException {
		File root = Files.createTempDirectory("nbt-progress").toFile();
		write(new File(root, "level.dat"), data("minecraft:stone"));
		for (int i = 0; i < files; i++) {
			write(new File(root, "data/map_" + i + ".dat"), data("minecraft:stone"));
		}
		return NbtDataFiles.collect(new WorldDirectories(new File(root, "region"),
				new File(root, "poi"), new File(root, "entities")));
	}

	@Test
	public void searchReportsEveryFileItScanned() throws IOException {
		List<NbtDataFiles.FileRef> refs = world(2);
		RecordingProgress progress = new RecordingProgress();

		NbtDataFiles.SearchOutcome found = NbtDataFiles.search(refs, NbtQuery.parse("value=minecraft:stone"), progress);

		assertEquals(refs.size(), found.matches().size(), found.toString());
		assertEquals(refs.size(), progress.max, "the dialog has to know how many files there are");
		assertEquals(refs.size(), progress.reported.size(), "a dialog closes on the last reported unit");
		assertTrue(progress.reported.contains("level.dat"), progress.reported.toString());
	}

	@Test
	public void replaceReportsEveryFileItScanned() throws IOException {
		List<NbtDataFiles.FileRef> refs = world(2);
		RecordingProgress progress = new RecordingProgress();

		NbtDataFiles.ReplaceOutcome outcome = NbtDataFiles.replace(
				refs, NbtQuery.parse("value=minecraft:stone"), false, true, "minecraft:dirt", true, progress);

		assertEquals(refs.size(), outcome.replaced(), outcome.toString());
		assertEquals(refs.size(), progress.max);
		assertEquals(refs.size(), progress.reported.size());
	}

	@Test
	public void cancellingStopsTheRun() throws IOException {
		List<NbtDataFiles.FileRef> refs = world(3);
		RecordingProgress progress = new RecordingProgress();
		progress.cancelled = true;

		NbtDataFiles.SearchOutcome found = NbtDataFiles.search(refs, NbtQuery.parse("value=minecraft:stone"), progress);

		assertEquals(0, found.matches().size(), "a cancelled run must not keep scanning");
	}
}
