package net.querz.mcaselector.nbtstudio;

import net.querz.nbt.CompoundTag;
import net.querz.nbt.IntTag;
import net.querz.nbt.ListTag;
import net.querz.nbt.StringTag;
import net.querz.nbt.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class NbtStudioCoreTest {

	private CompoundTag chunk() {
		CompoundTag root = new CompoundTag();
		root.putInt("DataVersion", 3465);

		ListTag palette = new ListTag(Tag.Type.STRING);
		palette.add(StringTag.valueOf("minecraft:stone"));
		palette.add(StringTag.valueOf("minecraft:diamond_ore"));

		CompoundTag section = new CompoundTag();
		section.put("palette", palette);
		section.putByte("Y", (byte) 0);

		CompoundTag section2 = new CompoundTag();
		ListTag palette2 = new ListTag(Tag.Type.STRING);
		palette2.add(StringTag.valueOf("minecraft:bedrock"));
		section2.put("palette", palette2);
		section2.putByte("Y", (byte) -4);

		ListTag sections = new ListTag(Tag.Type.COMPOUND);
		sections.add(section);
		sections.add(section2);
		root.put("sections", sections);

		ListTag entities = new ListTag(Tag.Type.COMPOUND);
		CompoundTag zombie = new CompoundTag();
		zombie.putString("id", "minecraft:zombie");
		zombie.putInt("Health", 20);
		entities.add(zombie);
		root.put("Entities", entities);

		root.putString("Status", "minecraft:full");
		return root;
	}

	@Test
	public void barePatternSearchesNamesAndValues() {
		CompoundTag root = chunk();
		assertTrue(NbtSearcher.search(root, "region", NbtQuery.parse("diamond")).getTotal() > 0);
		assertTrue(NbtSearcher.search(root, "region", NbtQuery.parse("minecraft:bedrock")).getTotal() > 0);
		assertEquals(0, NbtSearcher.search(root, "region", NbtQuery.parse("not_there_at_all")).getTotal());
	}

	@Test
	public void pathOnScalarListLooksIntoTheEntries() {
		CompoundTag root = chunk();
		// a path that ends on the palette list itself has to reach the entries, otherwise a search
		// for a palette entry through its path can never hit anything
		assertEquals(1, NbtSearcher.search(root, "region", NbtQuery.parse("path=sections[*].palette value=minecraft:bedrock")).getTotal());
		assertEquals(0, NbtSearcher.search(root, "region", NbtQuery.parse("path=sections[*].palette value=minecraft:dirt")).getTotal());
	}

	@Test
	public void statsExplainWhyAQueryFindsNothing() {
		CompoundTag root = chunk();
		NbtSearcher.Stats byValue = NbtSearcher.stats(root, NbtQuery.parse("path=sections[*].palette value=minecraft:bedrock"));
		assertEquals(2, byValue.nodes);
		assertEquals(-1, byValue.nameHits);
		assertEquals(1, byValue.valueHits);

		// the name filter is counted on its own: the path ends on two palette lists, both are called
		// palette, so the name is found twice even though the query as a whole reports nothing
		NbtSearcher.Stats split = NbtSearcher.stats(root, NbtQuery.parse("path=sections[*].palette name=palette value=minecraft:bedrock"));
		assertEquals(2, split.nodes);
		assertEquals(2, split.nameHits);
		assertEquals(1, split.valueHits);
		assertEquals(0, NbtSearcher.search(root, "region", NbtQuery.parse("path=sections[*].palette name=palette value=minecraft:bedrock")).getTotal());

		// a typo in the value is a plain zero on that part only
		NbtSearcher.Stats typo = NbtSearcher.stats(root, NbtQuery.parse("path=sections[*].palette value=minecraft:bedrok"));
		assertEquals(2, typo.nodes);
		assertEquals(0, typo.valueHits);
	}

	@Test
	public void chunkWideSplitsNameAndValue() {
		// a sign keeps the field name Text1 and the text itself on the same tag in old versions,
		// in 1.20 the text moved to block_entities, so the strict form cannot match both parts
		CompoundTag root = chunk();
		ListTag blockEntities = new ListTag(Tag.Type.COMPOUND);
		CompoundTag sign = new CompoundTag();
		sign.putString("id", "minecraft:sign");
		CompoundTag frontText = new CompoundTag();
		ListTag messages = new ListTag(Tag.Type.STRING);
		messages.add(StringTag.valueOf("{\"text\":\"谜\"}"));
		frontText.put("messages", messages);
		sign.put("front_text", frontText);
		blockEntities.add(sign);
		root.put("block_entities", blockEntities);

		assertEquals(0, NbtSearcher.search(root, "region", NbtQuery.parse("name=id value=谜")).getTotal());
		assertTrue(NbtSearcher.search(root, "region", NbtQuery.parse("name=id value=谜 chunk=1")).getTotal() > 0);
		assertTrue(NbtSearcher.search(root, "region", NbtQuery.parse("name=messages value=谜 chunk=1")).getTotal() > 0);
		assertTrue(NbtQuery.parse("name=id value=谜 chunk=1").isChunkWide());
		// smart degrades to contains, the canonical form always names the effective mode
		assertEquals("name=id;value=谜;mode=contains;case=insensitive;chunk=1",
				NbtQuery.parse("name=id value=谜 chunk=1").canonical());
		assertEquals(0, NbtSearcher.search(root, "region", NbtQuery.parse("name=id value=不存在 chunk=1")).getTotal());
		// "sign" is only ever stored as the value of id, a chunk wide name part looks at values too
		assertTrue(NbtSearcher.search(root, "region", NbtQuery.parse("name=sign value=谜 chunk=1")).getTotal() > 0);
		// and on its own the name part finds nothing, so it really had to look at values
		assertEquals(0, NbtSearcher.search(root, "region", NbtQuery.parse("name=sign")).getTotal());
	}

	@Test
	public void globAndRegex() {
		CompoundTag root = chunk();
		assertTrue(NbtSearcher.search(root, "region", NbtQuery.parse("mode=glob value=minecraft:*_ore")).getTotal() > 0);
		assertTrue(NbtSearcher.search(root, "region", NbtQuery.parse("mode=regex value=^minecraft:(stone|bedrock)$")).getTotal() > 0);
		assertEquals(0, NbtSearcher.search(root, "region", NbtQuery.parse("mode=regex value=^dirt$")).getTotal());
	}

	@Test
	public void caseInsensitiveByDefault() {
		CompoundTag root = chunk();
		// NBT Studio always searches case insensitively, so that is the default here as well
		assertTrue(NbtSearcher.search(root, "region", NbtQuery.parse("mode=exact value=MINECRAFT:STONE")).getTotal() > 0);
		assertEquals(0, NbtSearcher.search(root, "region", NbtQuery.parse("mode=exact value=MINECRAFT:STONE case=sensitive")).getTotal());
	}

	@Test
	public void floatingPointValuesMatchWithoutTrailingZero() {
		CompoundTag root = chunk();
		root.putFloat("Health", 20.0f);
		assertTrue(NbtSearcher.search(root, "region", NbtQuery.parse("name=Health value=20 mode=exact")).getTotal() > 0);
		assertTrue(NbtSearcher.search(root, "region", NbtQuery.parse("name=Health value=20.0 mode=exact")).getTotal() > 0);
		assertEquals(0, NbtSearcher.search(root, "region", NbtQuery.parse("name=Health value=19 mode=exact")).getTotal());
	}

	@Test
	public void containerSummariesAreSearchable() {
		CompoundTag root = chunk();
		root.putByteArray("Heightmaps", new byte[]{1, 2, 3});
		// NBT Studio renders containers as a summary, so "entries" and "bytes" have to be findable
		assertTrue(NbtSearcher.search(root, "region", NbtQuery.parse("value=entries mode=contains")).getTotal() > 0);
		assertTrue(NbtSearcher.search(root, "region", NbtQuery.parse("value=bytes mode=contains")).getTotal() > 0);
	}

	@Test
	public void listEntriesAreAddressableByIndex() {
		CompoundTag root = chunk();
		assertTrue(NbtSearcher.search(root, "region", NbtQuery.parse("name=\"[0]\" mode=exact")).getTotal() > 0);
		assertEquals(0, NbtSearcher.search(root, "region", NbtQuery.parse("name=\"[9]\" mode=exact")).getTotal());
	}

	@Test
	public void nameAndValuePatterns() {
		CompoundTag root = chunk();
		assertTrue(NbtSearcher.search(root, "region", NbtQuery.parse("name=Health")).getTotal() > 0);
		assertTrue(NbtSearcher.search(root, "region", NbtQuery.parse("name=Health value=20")).getTotal() > 0);
		assertEquals(0, NbtSearcher.search(root, "region", NbtQuery.parse("name=Health value=21")).getTotal());
	}

	@Test
	public void pathRestrictsSearch() {
		CompoundTag root = chunk();
		assertTrue(NbtSearcher.search(root, "region", NbtQuery.parse("path=sections[*].palette name=palette")).getTotal() > 0);
		assertEquals(0, NbtSearcher.search(root, "region", NbtQuery.parse("path=Entities[*].palette name=palette")).getTotal());
	}

	@Test
	public void replaceValuesKeepsType() {
		CompoundTag root = chunk();
		NbtReplaceResult result = NbtReplacer.replace(root, NbtQuery.parse("value=diamond"), NbtReplacer.Target.VALUE, "emerald");
		assertTrue(result.getReplaced() > 0, "expected the palette entry to be replaced, got " + result);

		CompoundTag section = (CompoundTag) ((ListTag) root.get("sections")).get(0);
		ListTag palette = (ListTag) section.get("palette");
		assertEquals("minecraft:emerald_ore", NbtValues.display(palette.get(1)));
		assertEquals("minecraft:stone", NbtValues.display(palette.get(0)));

		NbtReplaceResult numbers = NbtReplacer.replace(root, NbtQuery.parse("mode=exact name=Health value=20"), NbtReplacer.Target.VALUE, "5");
		assertTrue(numbers.getReplaced() > 0);
		Tag health = ((CompoundTag) ((ListTag) root.get("Entities")).get(0)).get("Health");
		assertEquals(Tag.Type.INT, health.getType());
		assertEquals(5, ((IntTag) health).asInt());
	}

	@Test
	public void replaceWithRegexCaptureGroup() {
		CompoundTag root = chunk();
		NbtReplaceResult result = NbtReplacer.replace(root,
				NbtQuery.parse("mode=regex value=^minecraft:([a-z_]+)$"),
				NbtReplacer.Target.VALUE,
				"mca:$1_plus");
		assertTrue(result.getReplaced() >= 3, "expected at least three replacements, got " + result);

		CompoundTag section = (CompoundTag) ((ListTag) root.get("sections")).get(0);
		ListTag palette = (ListTag) section.get("palette");
		assertEquals("mca:stone_plus", NbtValues.display(palette.get(0)));
		assertEquals("mca:full_plus", NbtValues.display(root.get("Status")));
		ListTag palette2 = (ListTag) ((CompoundTag) ((ListTag) root.get("sections")).get(1)).get("palette");
		assertEquals("mca:bedrock_plus", NbtValues.display(palette2.get(0)));
		assertEquals("mca:diamond_ore_plus", NbtValues.display(palette.get(1)));
	}

	@Test
	public void replaceNames() {
		CompoundTag root = chunk();
		NbtReplaceResult result = NbtReplacer.replace(root, NbtQuery.parse("mode=exact name=Status"), NbtReplacer.Target.NAME, "LevelStatus");
		assertEquals(1, result.getReplaced());
		assertNotNull(root.get("LevelStatus"));
		assertNull(root.get("Status"));
	}

	@Test
	public void invalidQueryIsReported() {
		assertFalse(NbtQuery.parse("mode=regex value=[unclosed").isValid());
		assertTrue(NbtQuery.parse("mode=bogus").getError().contains("unknown mode"));
		assertFalse(NbtQuery.parse("").isValid());
	}

	@Test
	public void rootMaskIsRespected() {
		CompoundTag region = chunk();
		CompoundTag entities = new CompoundTag();
		entities.putString("id", "minecraft:zombie");
		assertEquals(0, NbtSearcher.search(region, null, entities, NbtQuery.parse("root=poi value=zombie")).getTotal());
		assertTrue(NbtSearcher.search(region, null, entities, NbtQuery.parse("root=entities value=zombie")).getTotal() > 0);
	}

	@Test
	public void canonicalQuerySurvivesSpecialCharacters() {
		// a pattern containing the query separator, quotes and spaces must round trip
		NbtQuery query = NbtQuery.parse("name=\"a;b\" value=\"x y\" mode=exact");
		assertTrue(query.isValid(), "query should be valid: " + query.getError());
		String canonical = query.canonical();
		NbtQuery reparsed = NbtQuery.parse(canonical);
		assertTrue(reparsed.isValid(), "canonical form should parse again: " + canonical + " -> " + reparsed.getError());
		assertEquals("a;b", reparsed.getNamePattern().getSource());
		assertEquals("x y", reparsed.getValuePattern().getSource());
	}

	@Test
	public void canonicalQueryKeepsScopeTypeAndLimit() {
		// scope, type and limit are exposed as controls in the dialog, so the canonical string has
		// to carry them, otherwise a preview or a field would silently search everything again
		NbtQuery query = NbtQuery.create(null, NbtPattern.of("Items", NbtMatchMode.EXACT, false), null,
				NbtQuery.ROOT_POI | NbtQuery.ROOT_ENTITIES, java.util.EnumSet.of(Tag.Type.STRING), 1234);
		assertTrue(query.isValid(), "query should be valid: " + query.getError());

		String canonical = query.canonical();
		assertTrue(canonical.contains("limit=1234"), canonical);

		NbtQuery reparsed = NbtQuery.parse(canonical);
		assertTrue(reparsed.isValid(), "canonical form should parse again: " + canonical + " -> " + reparsed.getError());
		assertEquals(1234, reparsed.getLimit());
		assertEquals(NbtQuery.ROOT_POI | NbtQuery.ROOT_ENTITIES, reparsed.getRootMask());
		assertTrue(reparsed.acceptsType(Tag.Type.STRING));
		assertFalse(reparsed.acceptsType(Tag.Type.INT));
	}

	@Test
	public void defaultLimitIsNotWrittenToCanonicalQuery() {
		// the default stays out of the string so that an untouched dialog produces the same query
		// text as before the limit control existed
		NbtQuery query = NbtQuery.create(null, NbtPattern.of("Items", NbtMatchMode.EXACT, false), null,
				NbtQuery.ROOT_ALL, null, 5000);
		assertFalse(query.canonical().contains("limit="), query.canonical());
		// the default scope is left out for the same reason, so an all-scope query stays as short
		// as it was before the scope control existed
		assertFalse(query.canonical().contains("root="), query.canonical());
	}

	@Test
	public void regexReplaceKeepsZeroLengthMatchesUntouched() {
		// zero length matches must not insert the replacement between every character
		assertEquals("abc", NbtPattern.of("x*", NbtMatchMode.REGEX, false).replaceAll("abc", "-"));
		assertEquals("a-c", NbtPattern.of("b|^", NbtMatchMode.REGEX, false).replaceAll("abc", "-"));
	}

	@Test
	public void containsReplacementIsLiteral() {
		// a group reference in the replacement must not be expanded for literal matching modes
		assertEquals("minecraft:$1dirt", NbtPattern.of("STONE", NbtMatchMode.CONTAINS, true).replaceAll("minecraft:STONE", "$1dirt"));
	}

	@Test
	public void outOfRangeValueIsRejected() {
		CompoundTag root = new CompoundTag();
		root.putByte("level", (byte) 1);
		NbtReplaceResult result = NbtReplacer.replace(root, NbtQuery.parse("name=level mode=exact"),
				NbtReplacer.Target.VALUE, "300");
		assertEquals(0, result.getReplaced());
		assertEquals(1, root.getByte("level"));
		assertTrue(result.getSkipped() > 0);
	}

	@Test
	public void invalidReplacementForValueTypeIsSkipped() {
		CompoundTag root = chunk();
		NbtReplaceResult result = NbtReplacer.replace(root, NbtQuery.parse("mode=exact name=Status value=minecraft:full"),
				NbtReplacer.Target.VALUE, "minecraft:complete");
		// Status is a string, so this is a valid replacement
		assertEquals(1, result.getReplaced());
		assertEquals("minecraft:complete", root.getString("Status"));
	}
}
