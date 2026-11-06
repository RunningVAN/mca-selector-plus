package net.querz.mcaselector.nbtstudio;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns the flat list of data files of a world into the directories they live in, optionally
 * reduced to the files that match a filter.
 *
 * <p>Search results only ever contain files that matched a query, so they can not be used to pick a
 * file nobody searched for. The browser entry builds its tree from this listing instead: every
 * editable file of the world shows up, and the filter narrows the same tree down without hiding the
 * rest of the world.</p>
 */
public final class NbtFileListing {

	/**
	 * One directory of the world with the data files it holds. An empty directory is the world
	 * directory itself, where {@code level.dat} lives.
	 */
	public record Group(String directory, List<NbtDataFiles.FileRef> files) {

		public boolean isRoot() {
			return directory.isEmpty();
		}

		/** last segment of the directory, used as the label of the tree group. */
		public String name() {
			int slash = directory.lastIndexOf('/');
			return slash < 0 ? directory : directory.substring(slash + 1);
		}
	}

	private NbtFileListing() {}

	/** directory part of the relative path of a file, {@code ""} for files next to the world folder. */
	public static String directoryOf(NbtDataFiles.FileRef file) {
		String label = file.label() == null ? "" : file.label().replace('\\', '/');
		int slash = label.lastIndexOf('/');
		return slash < 0 ? "" : label.substring(0, slash);
	}

	/**
	 * Groups files by directory and drops the ones that do not match the filter. The filter matches
	 * anywhere in the relative path, ignoring case, so typing "playerdata" keeps that whole directory
	 * and typing "level" keeps only {@code level.dat}.
	 *
	 * @param files  files to list, may be {@code null}
	 * @param filter filter text, {@code null} or blank keeps everything
	 * @return groups sorted by directory, files sorted by path, the world directory first
	 */
	public static List<Group> group(List<NbtDataFiles.FileRef> files, String filter) {
		String needle = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
		Map<String, List<NbtDataFiles.FileRef>> byDirectory = new LinkedHashMap<>();
		if (files != null) {
			for (NbtDataFiles.FileRef file : files) {
				if (file == null) {
					continue;
				}
				String label = file.label() == null ? "" : file.label();
				if (!needle.isEmpty() && !label.toLowerCase(Locale.ROOT).contains(needle)) {
					continue;
				}
				byDirectory.computeIfAbsent(directoryOf(file), d -> new ArrayList<>()).add(file);
			}
		}

		List<Group> groups = new ArrayList<>(byDirectory.size());
		byDirectory.forEach((directory, list) -> {
			list.sort(Comparator.comparing(f -> f.label() == null ? "" : f.label(), String.CASE_INSENSITIVE_ORDER));
			groups.add(new Group(directory, List.copyOf(list)));
		});
		groups.sort(Comparator.comparing(Group::directory, String.CASE_INSENSITIVE_ORDER));
		return groups;
	}

	/** number of files in the groups, i.e. the size of the tree below the directories. */
	public static int count(List<Group> groups) {
		int count = 0;
		for (Group group : groups) {
			count += group.files().size();
		}
		return count;
	}
}
