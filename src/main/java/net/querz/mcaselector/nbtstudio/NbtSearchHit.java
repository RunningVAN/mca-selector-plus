package net.querz.mcaselector.nbtstudio;

import net.querz.nbt.Tag;

/**
 * A single match of an {@link NbtQuery}.
 *
 * @param root  the root of the scanned tag, e.g. "region", "poi", "entities"
 * @param path  the full path to the matched tag, e.g. "Sections[3].biomes.palette[0]"
 * @param name  the key of the matched tag, or null if it does not have one (list entries, root)
 * @param type  the NBT type of the matched tag
 * @param value the string representation of the matched value
 */
public record NbtSearchHit(String root, String path, String name, Tag.Type type, String value) {

	@Override
	public String toString() {
		return (root == null ? "" : root + ":") + path + " = " + value + " (" + type + ")";
	}
}
