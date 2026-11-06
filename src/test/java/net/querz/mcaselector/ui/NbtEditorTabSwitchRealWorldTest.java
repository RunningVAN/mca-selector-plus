package net.querz.mcaselector.ui;

import net.querz.mcaselector.io.FileHelper;
import net.querz.mcaselector.io.mca.EntitiesMCAFile;
import net.querz.mcaselector.io.mca.PoiMCAFile;
import net.querz.mcaselector.io.mca.RegionMCAFile;
import net.querz.mcaselector.nbtstudio.NbtMatchMode;
import net.querz.mcaselector.nbtstudio.NbtPattern;
import net.querz.mcaselector.nbtstudio.NbtQuery;
import net.querz.mcaselector.ui.dialog.NBTEditorDialog;
import net.querz.mcaselector.util.point.Point2i;
import net.querz.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Replays the reported case on a real world: a search that only hits entities has to make the
 * editor open the entities tab, not stay on region with "0 matches".
 *
 * <p>The world is only present on a machine that has it, so the test is skipped elsewhere.</p>
 */
public class NbtEditorTabSwitchRealWorldTest {

	private static final File WORLD = new File(System.getProperty("world",
			System.getProperty("user.home") + "/Documents/Minecraft/科塔丘实验室4demo（联壁汉化1.2）"));

	private CompoundTag load(File file, Point2i chunk) {
		if (!file.exists()) {
			return null;
		}
		try {
			if (file.getParentFile().getName().equals("entities")) {
				return (CompoundTag) new EntitiesMCAFile(file).loadSingleChunk(chunk).getData();
			}
			if (file.getParentFile().getName().equals("poi")) {
				return (CompoundTag) new PoiMCAFile(file).loadSingleChunk(chunk).getData();
			}
			return (CompoundTag) new RegionMCAFile(file).loadSingleChunk(chunk).getData();
		} catch (Exception ex) {
			return null;
		}
	}

	private CompoundTag at(File dir, Point2i chunk) {
		Point2i region = chunk.chunkToRegion();
		return load(new File(dir, "r." + region.getX() + "." + region.getZ() + ".mca"), chunk);
	}

	private NbtQuery itemNameQuery() {
		// a value-only search for the item name seen in the report
		return NbtQuery.create(null, null,
				NbtPattern.of("扳手", NbtMatchMode.CONTAINS, true),
				NbtQuery.ROOT_ALL, null, NbtQuery.DEFAULT_LIMIT, false, false);
	}

	@Test
	public void realWorldEntitiesHitOpensEntitiesTab() {
		String[] regions = {"r.0.-2.mca", "r.-1.-2.mca", "r.0.-1.mca", "r.-1.-1.mca", "r.0.0.mca", "r.-1.0.mca"};
		assumeTrue(new File(WORLD, "entities").isDirectory(), "world not present");
		NbtQuery query = itemNameQuery();

		for (String regionName : regions) {
			File entitiesFile = new File(new File(WORLD, "entities"), regionName);
			if (!entitiesFile.exists()) {
				continue;
			}
			EntitiesMCAFile entities = new EntitiesMCAFile(entitiesFile);
			try {
				entities.load(false);
			} catch (Exception ex) {
				continue;
			}
			Point2i region = FileHelper.parseMCAFileName(regionName);
			for (int i = 0; i < 1024; i++) {
				if (entities.getChunk(i) == null) {
					continue;
				}
				Point2i chunk = region.regionToChunk().add(new Point2i(i));
				CompoundTag regionData = at(new File(WORLD, "region"), chunk);
				CompoundTag poiData = at(new File(WORLD, "poi"), chunk);
				CompoundTag entitiesData = at(new File(WORLD, "entities"), chunk);
				if (entitiesData == null) {
					continue;
				}
				int regionHits = NBTEditorDialog.countHits(NbtQuery.ROOT_REGION, regionData, query);
				int poiHits = NBTEditorDialog.countHits(NbtQuery.ROOT_POI, poiData, query);
				int entitiesHits = NBTEditorDialog.countHits(NbtQuery.ROOT_ENTITIES, entitiesData, query);
				if (entitiesHits == 0 || regionHits > 0) {
					continue;
				}
				Map<Integer, Integer> hits = new LinkedHashMap<>();
				hits.put(NbtQuery.ROOT_REGION, regionHits);
				hits.put(NbtQuery.ROOT_POI, poiHits);
				hits.put(NbtQuery.ROOT_ENTITIES, entitiesHits);
				System.out.println("chunk " + chunk + " hits " + hits);
				assertEquals(NbtQuery.ROOT_ENTITIES, NBTEditorDialog.pickTabWithHits(hits),
						"editor has to open the entities tab for chunk " + chunk);
				return;
			}
		}
		fail("no chunk in " + WORLD + " (" + Arrays.toString(regions) + ") has an entities-only hit");
	}
}
