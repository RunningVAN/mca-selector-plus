package net.querz.mcaselector.nbtstudio;

import net.querz.mcaselector.io.WorldDirectories;
import net.querz.mcaselector.util.progress.Progress;
import net.querz.nbt.NamedTag;
import net.querz.nbt.Tag;
import net.querz.nbt.io.NBTReader;
import net.querz.nbt.io.NBTWriter;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * NBT data files of a world: {@code level.dat}, {@code data/}, {@code playerdata/} and the same of
 * every dimension.
 *
 * <p>A chunk file ({@code .mca}) holds hundreds of compressed chunks behind a sector table, a data
 * file holds exactly one named tag tree. That is the only difference: the search and the replacement
 * run on the same {@link NbtSearcher} and {@link NbtReplacer} that the chunk based NBT Studio mode
 * uses, only the tree that is handed to them comes from a whole file instead of a chunk.</p>
 *
 * <p>Files are written back in the compression they were read with (gzip for everything the game
 * writes, zlib and uncompressed for deliberately hand edited files), and they are written to a
 * temporary file in the same directory first and moved over the original afterwards, so an aborted
 * write never leaves a truncated data file behind.</p>
 */
public final class NbtDataFiles {

	/** how many individual changes a preview collects before it stops growing */
	public static final int MAX_CHANGES = 500;

	/** data files are small, anything larger is either not a data file or not worth rewriting */
	private static final long MAX_FILE_SIZE = 128L * 1024 * 1024;

	private NbtDataFiles() {
	}

	/**
	 * @param file  the file on disk
	 * @param label the path relative to the world directory, e.g. "playerdata/0000.dat"
	 */
	public record FileRef(File file, String label) {
	}

	/**
	 * A single tag found in a data file.
	 */
	public record Match(FileRef file, String path, Tag.Type type, String value) {
	}

	/**
	 * A single name or value change in a data file.
	 */
	public record Change(FileRef file, String path, String oldValue, String newValue) {
	}

	public record SearchOutcome(List<Match> matches, int filesScanned, int filesWithMatches, int total,
			boolean truncated, List<String> errors) {
	}

	public record ReplaceOutcome(int filesScanned, int filesUpdated, int replaced, int skipped,
			List<Change> changes, boolean truncated, List<String> errors) {
	}

	/**
	 * Collects every NBT data file of the open world: {@code level.dat}, the {@code data} and
	 * {@code playerdata} directories and every structure file, for the overworld and for every
	 * dimension.
	 *
	 * <p>Structure files ({@code structures/**}, {@code generated/minecraft/structures/**} and
	 * anything a data pack ships under {@code data/<namespace>/structures/**}) are full NBT trees
	 * like the data files, so they are handled by exactly the same code.</p>
	 */
	public static List<FileRef> collect(WorldDirectories directories) {
		if (directories == null || directories.getRegion() == null) {
			return List.of();
		}
		File world = directories.getRegion().getParentFile();
		if (world == null || !world.isDirectory()) {
			return List.of();
		}
		Map<String, FileRef> files = new LinkedHashMap<>();
		// level.dat only exists once, for the world itself
		addFile(files, world, new File(world, "level.dat"));
		for (File root : dimensionRoots(world)) {
			collectLevel(files, world, root);
		}
		return new ArrayList<>(files.values());
	}

	/**
	 * Every directory that holds world data: the world itself, the legacy {@code DIM*} folders and
	 * the {@code dimensions/<namespace>/<name>} folders of custom dimensions.
	 */
	private static List<File> dimensionRoots(File world) {
		List<File> roots = new ArrayList<>();
		roots.add(world);
		File[] entries = world.listFiles(File::isDirectory);
		if (entries == null) {
			return roots;
		}
		List<File> sorted = new ArrayList<>(List.of(entries));
		sorted.sort(Comparator.comparing(File::getName));
		for (File entry : sorted) {
			if (entry.getName().startsWith("DIM")) {
				roots.add(entry);
			}
		}
		File dimensions = new File(world, "dimensions");
		File[] namespaces = dimensions.listFiles(File::isDirectory);
		if (namespaces != null) {
			List<File> sortedNamespaces = new ArrayList<>(List.of(namespaces));
			sortedNamespaces.sort(Comparator.comparing(File::getName));
			for (File namespace : sortedNamespaces) {
				File[] dimensionDirs = namespace.listFiles(File::isDirectory);
				if (dimensionDirs == null) {
					continue;
				}
				List<File> sortedDimensions = new ArrayList<>(List.of(dimensionDirs));
				sortedDimensions.sort(Comparator.comparing(File::getName));
				roots.addAll(sortedDimensions);
			}
		}
		return roots;
	}

	private static void collectLevel(Map<String, FileRef> files, File world, File root) {
		collectDirectory(files, world, new File(root, "data"));
		collectDirectory(files, world, new File(root, "playerdata"));
		// structure files, wherever the game or a data pack put them
		collectStructures(files, world, new File(root, "structures"));
		collectStructures(files, world, new File(new File(new File(root, "generated"), "minecraft"), "structures"));
		File datapacks = new File(root, "datapacks");
		File[] packs = datapacks.listFiles(File::isDirectory);
		if (packs == null) {
			return;
		}
		List<File> sortedPacks = new ArrayList<>(List.of(packs));
		sortedPacks.sort(Comparator.comparing(File::getName));
		for (File pack : sortedPacks) {
			File data = new File(pack, "data");
			File[] namespaces = data.listFiles(File::isDirectory);
			if (namespaces != null) {
				List<File> sortedNamespaces = new ArrayList<>(List.of(namespaces));
				sortedNamespaces.sort(Comparator.comparing(File::getName));
				for (File namespace : sortedNamespaces) {
					collectStructures(files, world, new File(namespace, "structures"));
				}
			}
			// some packs keep the folder right below the pack itself
			collectStructures(files, world, new File(pack, "structures"));
		}
	}

	/**
	 * Structure directories nest as deeply as the author liked, so every level is walked.
	 */
	private static void collectStructures(Map<String, FileRef> files, File world, File directory) {
		collectStructures(files, world, directory, 0);
	}

	private static void collectStructures(Map<String, FileRef> files, File world, File directory, int depth) {
		if (directory == null || !directory.isDirectory() || depth > 8) {
			return;
		}
		File[] entries = directory.listFiles();
		if (entries == null) {
			return;
		}
		List<File> sorted = new ArrayList<>(List.of(entries));
		sorted.sort(Comparator.comparing(File::getName));
		for (File entry : sorted) {
			if (entry.isDirectory()) {
				collectStructures(files, world, entry, depth + 1);
			} else {
				addFile(files, world, entry);
			}
		}
	}

	private static void collectDirectory(Map<String, FileRef> files, File world, File directory) {
		if (directory == null || !directory.isDirectory()) {
			return;
		}
		File[] entries = directory.listFiles();
		if (entries == null) {
			return;
		}
		List<File> sorted = new ArrayList<>(List.of(entries));
		sorted.sort(Comparator.comparing(File::getName));
		for (File entry : sorted) {
			if (entry.isDirectory()) {
				collectDirectory(files, world, entry);
			} else {
				addFile(files, world, entry);
			}
		}
	}

	private static void addFile(Map<String, FileRef> files, File world, File file) {
		if (file == null || !file.isFile()) {
			return;
		}
		String name = file.getName().toLowerCase(Locale.ROOT);
		// level.dat_old and session.lock are not nbt at all, .nbt is what datapacks and editors use
		if (!name.endsWith(".dat") && !name.endsWith(".nbt")) {
			return;
		}
		long length = file.length();
		if (length <= 0 || length > MAX_FILE_SIZE) {
			return;
		}
		String label = label(world, file);
		// a data pack can be linked or the same folder can be reached twice
		files.putIfAbsent(label, new FileRef(file, label));
	}

	private static String label(File world, File file) {
		try {
			return world.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/');
		} catch (RuntimeException ex) {
			return file.getName();
		}
	}

	/**
	 * Finds every tag in the given files that matches the query.
	 */
	public static SearchOutcome search(List<FileRef> files, NbtQuery query) {
		return search(files, query, null);
	}

	/**
	 * Same search, reporting one unit of progress per file so a progress dialog can show where the
	 * run is instead of sitting at "running" until it ends.
	 *
	 * @param progress may be null
	 */
	public static SearchOutcome search(List<FileRef> files, NbtQuery query, Progress progress) {
		if (progress != null && files != null) {
			progress.setMax(Math.max(1, files.size()));
		}
		List<Match> matches = new ArrayList<>();
		List<String> errors = new ArrayList<>();
		if (files == null || files.isEmpty() || query == null || !query.isValid()) {
			return new SearchOutcome(matches, 0, 0, 0, false, errors);
		}
		int scanned = 0;
		int filesWithMatches = 0;
		int total = 0;
		int limit = query.getLimit();
		for (FileRef ref : files) {
			if (progress != null && progress.taskCancelled()) {
				break;
			}
			NamedTag namedTag;
			try {
				namedTag = read(ref.file()).tag();
			} catch (IOException | RuntimeException ex) {
				errors.add(ref.label() + ": " + ex.getMessage());
				if (progress != null) {
					progress.incrementProgress(ref.label());
				}
				continue;
			}
			scanned++;
			NbtSearchResult result = NbtSearcher.search(namedTag.tag(), ref.label(), query, limit);
			if (result.isEmpty()) {
				if (progress != null) {
					progress.incrementProgress(ref.label());
				}
				continue;
			}
			filesWithMatches++;
			total += result.getTotal();
			for (NbtSearchHit hit : result.getHits()) {
				if (matches.size() >= limit) {
					break;
				}
				matches.add(new Match(ref, hit.path(), hit.type(), hit.value()));
			}
			if (progress != null) {
				progress.incrementProgress(ref.label());
			}
		}
		boolean truncated = total > matches.size();
		return new SearchOutcome(matches, scanned, filesWithMatches, total, truncated, errors);
	}

	/**
	 * Applies a replacement to every matching tag of the given files.
	 *
	 * @param dryRun when true the files are read and rewritten in memory only, nothing is written
	 */
	public static ReplaceOutcome replace(List<FileRef> files, NbtQuery query, boolean names, boolean values,
			String replacement, boolean dryRun) {
		return replace(files, query, names, values, replacement, dryRun, null);
	}

	/**
	 * Same replacement, reporting one unit of progress per file.
	 *
	 * @param progress may be null, and must be when the caller already drives another progress
	 *                 counter (the chunk pass sets its own maximum)
	 */
	public static ReplaceOutcome replace(List<FileRef> files, NbtQuery query, boolean names, boolean values,
			String replacement, boolean dryRun, Progress progress) {
		if (progress != null && files != null) {
			progress.setMax(Math.max(1, files.size()));
		}
		List<Change> changes = new ArrayList<>();
		List<String> errors = new ArrayList<>();
		if (files == null || files.isEmpty() || query == null || !query.isValid() || replacement == null
				|| replacement.isEmpty() || (!names && !values)) {
			return new ReplaceOutcome(0, 0, 0, 0, changes, false, errors);
		}
		int scanned = 0;
		int updated = 0;
		int replaced = 0;
		int skipped = 0;
		boolean truncated = false;
		for (FileRef ref : files) {
			if (progress != null && progress.taskCancelled()) {
				break;
			}
			LoadedFile parsed;
			try {
				parsed = read(ref.file());
			} catch (IOException | RuntimeException ex) {
				errors.add(ref.label() + ": " + ex.getMessage());
				if (progress != null) {
					progress.incrementProgress(ref.label());
				}
				continue;
			}
			scanned++;
			NbtReplaceResult result = NbtReplacer.replace(parsed.tag().tag(), query, names, values, replacement);
			skipped += result.getSkipped();
			if (result.getReplaced() == 0) {
				if (progress != null) {
					progress.incrementProgress(ref.label());
				}
				continue;
			}
			replaced += result.getReplaced();
			for (NbtReplaceResult.Change change : result.getChanges()) {
				if (changes.size() >= MAX_CHANGES) {
					truncated = true;
					break;
				}
				changes.add(new Change(ref, change.path(), change.oldValue(), change.newValue()));
			}
			if (dryRun) {
				updated++;
				if (progress != null) {
					progress.incrementProgress(ref.label());
				}
				continue;
			}
			try {
				write(ref.file(), parsed);
				updated++;
			} catch (IOException | RuntimeException ex) {
				errors.add(ref.label() + ": " + ex.getMessage());
			}
			if (progress != null) {
				progress.incrementProgress(ref.label());
			}
		}
		return new ReplaceOutcome(scanned, updated, replaced, skipped, changes, truncated, errors);
	}

	/**
	 * @param tag         the root tag of the file
	 * @param compression how the file was stored, which decides how it is written back
	 */
	public record LoadedFile(NamedTag tag, Compression compression) {
	}

	public enum Compression {
		GZIP, ZLIB, NONE
	}

	/**
	 * Reads a data file, keeping the root name and the compression around so it can be written back
	 * unchanged by the editor.
	 */
	public static LoadedFile read(File file) throws IOException {
		Compression compression = detectCompression(file);
		// the compression is detected here and not by the reader: the reader only knows gzip and
		// would parse a zlib or uncompressed file as if it were raw nbt
		try (InputStream raw = switch (compression) {
			case GZIP -> new GZIPInputStream(new BufferedInputStream(new FileInputStream(file), 8192), 8192);
			case ZLIB -> new InflaterInputStream(new BufferedInputStream(new FileInputStream(file), 8192));
			case NONE -> new BufferedInputStream(new FileInputStream(file), 8192);
		}) {
			// same reader settings as the chunk loader: no raw arrays, the tags are edited in place
			NamedTag namedTag = new NBTReader().compressed(false).readNamed(raw);
			return new LoadedFile(namedTag, compression);
		}
	}

	private static Compression detectCompression(File file) throws IOException {
		try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
			int b0 = in.read();
			int b1 = in.read();
			if (b0 < 0 || b1 < 0) {
				return Compression.NONE;
			}
			if (b0 == 0x1F && b1 == 0x8B) {
				return Compression.GZIP;
			}
			// zlib: the first byte is CMF (compression method 8, deflate), the first two bytes as a
			// big endian integer are a multiple of 31. an uncompressed nbt file starts with a tag
			// type byte, which never satisfies both
			if ((b0 & 0x0F) == 8 && ((b0 << 8) | b1) % 31 == 0) {
				return Compression.ZLIB;
			}
			return Compression.NONE;
		}
	}

	/**
	 * Writes a data file back in its original compression, atomically.
	 */
	public static void write(File file, LoadedFile loaded) throws IOException {
		File temporary = new File(file.getParentFile(), file.getName() + ".nbtstudio-tmp");
		try {
			try (FileOutputStream fos = new FileOutputStream(temporary);
					BufferedOutputStream bos = new BufferedOutputStream(fos, 8192)) {
				switch (loaded.compression()) {
					case GZIP -> {
						try (OutputStream out = new GZIPOutputStream(bos, 8192)) {
							new NBTWriter().compressed(false).writeNamed(out, loaded.tag());
						}
					}
					case ZLIB -> {
						try (OutputStream out = new DeflaterOutputStream(bos, new Deflater(Deflater.DEFAULT_COMPRESSION), 8192)) {
							new NBTWriter().compressed(false).writeNamed(out, loaded.tag());
						}
					}
					case NONE -> new NBTWriter().compressed(false).writeNamed(bos, loaded.tag());
				}
			}
			Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} finally {
			if (temporary.exists() && !temporary.delete()) {
				temporary.deleteOnExit();
			}
		}
	}
}
