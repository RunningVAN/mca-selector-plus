package net.querz.mcaselector.nbtstudio;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import net.querz.mcaselector.io.WorldDirectories;
import net.querz.mcaselector.ui.NbtStudioFinder;
import net.querz.nbt.CompoundTag;
import net.querz.nbt.NamedTag;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The find dialog for data files is modal, so a run that never closes it hangs the whole window:
 * the results only appeared after the user pressed cancel. This test runs the real dialog and
 * fails if it does not return by itself.
 */
public class NbtStudioFindDialogTest {

	private static File world() throws IOException {
		File root = Files.createTempDirectory("nbt-find-dialog").toFile();
		for (int i = 0; i < 3; i++) {
			File file = new File(root, i == 0 ? "level.dat" : "data/map_" + i + ".dat");
			file.getParentFile().mkdirs();
			CompoundTag compound = new CompoundTag();
			compound.putString("Name", "minecraft:stone");
			NbtDataFiles.write(file, new NbtDataFiles.LoadedFile(new NamedTag("", compound),
					NbtDataFiles.Compression.GZIP));
		}
		return root;
	}

	@Test
	public void findClosesItsOwnDialog() throws Exception {
		File root = world();
		List<NbtDataFiles.FileRef> refs = NbtDataFiles.collect(new WorldDirectories(
				new File(root, "region"), new File(root, "poi"), new File(root, "entities")));

		CountDownLatch started = new CountDownLatch(1);
		try {
			Platform.startup(started::countDown);
		} catch (IllegalStateException ex) {
			// the toolkit is already up from another test
			started.countDown();
		} catch (RuntimeException | Error ex) {
			Assumptions.abort("no javaFX toolkit in this environment: " + ex);
		}
		Assumptions.assumeTrue(started.await(30, TimeUnit.SECONDS), "javaFX toolkit did not start");

		CountDownLatch returned = new CountDownLatch(1);
		NbtDataFiles.SearchOutcome[] result = new NbtDataFiles.SearchOutcome[1];
		Platform.runLater(() -> {
			try {
				// the dialog takes its owner window from here, so it cannot be null
				Stage owner = new Stage();
				owner.setScene(new Scene(new StackPane()));
				result[0] = NbtStudioFinder.findInDataFiles(refs, NbtQuery.parse("value=minecraft:stone"), owner);
			} finally {
				returned.countDown();
			}
		});

		assertTrue(returned.await(20, TimeUnit.SECONDS),
				"the progress dialog did not close on its own, the user still has to press cancel");
		assertTrue(result[0] != null && result[0].matches().size() == refs.size(), String.valueOf(result[0]));
	}
}
