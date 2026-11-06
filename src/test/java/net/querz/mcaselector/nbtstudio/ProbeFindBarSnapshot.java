package net.querz.mcaselector.nbtstudio;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.VBox;
import net.querz.mcaselector.io.mca.RegionChunk;
import net.querz.mcaselector.io.mca.RegionMCAFile;
import net.querz.mcaselector.ui.component.NBTFindBar;
import net.querz.mcaselector.ui.component.NBTTreeView;
import net.querz.mcaselector.util.point.Point2i;
import net.querz.nbt.CompoundTag;
import javax.imageio.ImageIO;
import java.io.File;
import java.util.concurrent.CountDownLatch;

/** Renders the chunk editor tree after the NBT Studio query was applied, as a visual record. */
public class ProbeFindBarSnapshot {

	private static final String WORLD = System.getProperty("world",
			System.getProperty("user.home") + "/Documents/Minecraft/科塔丘实验室4demo（联壁汉化1.2）");
	private static final File OUT = new File(System.getProperty("out", "out.png"));

	public static void main(String[] args) throws Exception {
		NbtQuery query = NbtQuery.create(null,
				NbtPattern.of("command", NbtMatchMode.SMART, true),
				NbtPattern.of("\"text\":", NbtMatchMode.SMART, true),
				NbtQuery.ROOT_ALL, null, NbtQuery.DEFAULT_LIMIT, true, true);
		CountDownLatch done = new CountDownLatch(1);
		Platform.startup(() -> {
			try {
				Point2i chunk = new Point2i(Integer.getInteger("chunkX", 17), Integer.getInteger("chunkZ", -22));
				Point2i region = chunk.chunkToRegion();
				File file = new File(WORLD, "region/r." + region.getX() + "." + region.getZ() + ".mca");
				RegionChunk data = new RegionMCAFile(file).loadSingleChunk(chunk);
				NBTTreeView tree = new NBTTreeView();
				NBTFindBar bar = new NBTFindBar(tree);
				VBox box = new VBox(bar, tree);
				box.getStyleClass().add("nbt-editor-dialog-pane");
				Scene scene = new Scene(box, 1000, 680);
				for (String css : new String[]{"style/base.css", "style/component/nbt-editor-dialog.css",
						"style/component/nbt-tree-view.css", "style/component/nbt-find-bar.css"}) {
					java.net.URL url = ProbeFindBarSnapshot.class.getClassLoader().getResource(css);
					if (url != null) {
						scene.getStylesheets().add(url.toExternalForm());
					}
				}
				if (data.getData() instanceof CompoundTag root) {
					tree.load(root);
				}
				int count = bar.applyQuery(query);
				System.out.println("snapshot matches=" + count);
				box.applyCss();
				box.layout();
				WritableImage image = scene.snapshot(null);
				ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", OUT);
				System.out.println("written " + OUT.getAbsolutePath());
			} catch (Exception ex) {
				ex.printStackTrace();
			} finally {
				done.countDown();
			}
		});
		done.await();
		Platform.exit();
	}
}
