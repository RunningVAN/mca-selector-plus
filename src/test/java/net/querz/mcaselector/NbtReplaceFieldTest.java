package net.querz.mcaselector;

import net.querz.mcaselector.changer.fields.NbtReplaceField;
import net.querz.mcaselector.io.mca.ChunkData;
import net.querz.mcaselector.io.mca.RegionChunk;
import net.querz.mcaselector.util.point.Point2i;
import net.querz.nbt.CompoundTag;
import net.querz.nbt.ListTag;
import net.querz.nbt.StringTag;
import net.querz.nbt.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End to end checks for the NBT Studio replacement pipeline: the field spec is parsed exactly like
 * the CLI / GUI does and then applied to a real chunk tree.
 */
public class NbtReplaceFieldTest {

	private CompoundTag chunkTree() {
		CompoundTag root = new CompoundTag();
		root.putInt("DataVersion", 3953);

		ListTag items = new ListTag(Tag.Type.COMPOUND);
		CompoundTag item = new CompoundTag();
		item.putString("id", "minecraft:stone");
		item.putInt("Count", 3);
		CompoundTag display = new CompoundTag();
		display.putString("name", "minecraft:diamond_block");
		item.put("display", display);
		items.add(item);

		CompoundTag blockEntity = new CompoundTag();
		blockEntity.putString("id", "minecraft:chest");
		blockEntity.put("Items", items);
		root.put("block_entities", blockEntity);

		CompoundTag structure = new CompoundTag();
		structure.putString("id", "minecraft:village");
		structure.putString("name", "plains");
		root.put("structures", structure);
		return root;
	}

	private ChunkData chunkData(CompoundTag tag) {
		RegionChunk region = new RegionChunk(new Point2i(0, 0));
		region.setData(tag);
		return new ChunkData(region, null, null, false);
	}

	private CompoundTag item(CompoundTag tag) {
		return (CompoundTag) tag.getCompoundTag("block_entities").getListTag("Items").get(0);
	}

	@Test
	public void replacesValuesOnly() {
		CompoundTag tag = chunkTree();
		NbtReplaceField field = new NbtReplaceField();
		assertTrue(field.parseNewValue("value=minecraft:stone => minecraft:dirt"), "spec should parse");

		field.force(chunkData(tag));

		assertEquals("minecraft:dirt", item(tag).getString("id"), "value replaced");
		assertEquals("minecraft:diamond_block", item(tag).getCompoundTag("display").getString("name"),
				"unrelated value untouched");
		assertEquals("minecraft:chest", tag.getCompoundTag("block_entities").getString("id"),
				"a value query must not rename keys");
	}

	@Test
	public void replacesNamesOnly() {
		CompoundTag tag = chunkTree();
		NbtReplaceField field = new NbtReplaceField();
		assertTrue(field.parseNewValue("@name value=plains => savanna"), "spec should parse");

		field.force(chunkData(tag));

		assertEquals("plains", tag.getCompoundTag("structures").getString("savanna"),
				"the key whose value is plains has been renamed to savanna");
		assertFalse(tag.getCompoundTag("structures").containsKey("name"), "old key is gone");
		assertEquals("minecraft:chest", tag.getCompoundTag("block_entities").getString("id"),
				"unrelated keys untouched");
		assertEquals("minecraft:diamond_block", item(tag).getCompoundTag("display").getString("name"),
				"keys that do not match the query are untouched");
	}

	@Test
	public void replacesValuesSelectedByANamePattern() {
		CompoundTag tag = chunkTree();
		NbtReplaceField field = new NbtReplaceField();
		assertTrue(field.parseNewValue("@value name=id => minecraft:replaced"), "spec should parse");

		field.force(chunkData(tag));

		assertEquals("minecraft:replaced", item(tag).getString("id"), "value of a matching key replaced");
		assertEquals("minecraft:replaced", tag.getCompoundTag("block_entities").getString("id"),
				"value of a matching key replaced");
		assertEquals("minecraft:replaced", tag.getCompoundTag("structures").getString("id"),
				"every value behind a key called id is replaced");
	}

	@Test
	public void skipsRenameWhenTheTargetKeyExists() {
		CompoundTag tag = chunkTree();
		NbtReplaceField field = new NbtReplaceField();
		// "structures" contains both "id" and "name", renaming the latter would destroy the former
		assertTrue(field.parseNewValue("@name name=name => id"), "spec should parse");

		field.force(chunkData(tag));

		CompoundTag structures = tag.getCompoundTag("structures");
		assertEquals("minecraft:village", structures.getString("id"), "existing key is never overwritten");
		assertEquals("plains", structures.getString("name"), "colliding rename is skipped");
	}
	@Test
	public void replacesValuesWithRegexCaptureGroups() {
		CompoundTag tag = chunkTree();
		NbtReplaceField field = new NbtReplaceField();
		assertTrue(field.parseNewValue("value=minecraft:(.+) mode=regex => mca:$1"), "spec should parse");

		field.force(chunkData(tag));

		assertEquals("mca:diamond_block", item(tag).getCompoundTag("display").getString("name"),
				"regex group expanded");
		assertEquals("mca:stone", item(tag).getString("id"), "regex group expanded");
	}

	@Test
	public void rejectsSpecWithoutReplacement() {
		NbtReplaceField field = new NbtReplaceField();
		assertFalse(field.parseNewValue("value=minecraft:stone"), "spec without replacement must fail");
	}
}
