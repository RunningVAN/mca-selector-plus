package net.querz.mcaselector.nbtstudio;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.VBox;
import net.querz.mcaselector.io.mca.RegionChunk;
import net.querz.mcaselector.io.mca.RegionMCAFile;
import net.querz.mcaselector.ui.component.NBTFindBar;
import net.querz.mcaselector.ui.component.NBTTreeView;
import net.querz.mcaselector.util.point.Point2i;
import net.querz.nbt.Tag;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Loads a real chunk from the test world, feeds it into the editor tree and runs the query that the
 * NBT Studio dialog builds, so the "find in the map does not expand anything in the editor" report
 * can be checked against real data.
 */
public class ProbeFindBar {

	private static final String WORLD = System.getProperty("world",
			System.getProperty("user.home") + "/Documents/Minecraft/科塔丘实验室4demo（联壁汉化1.2）");

	public static void main(String[] args) throws Exception {
		NbtQuery dialog = NbtQuery.create(null,
				NbtPattern.of("command", NbtMatchMode.SMART, true),
				NbtPattern.of("\"text\":", NbtMatchMode.SMART, true),
				NbtQuery.ROOT_ALL, null, NbtQuery.DEFAULT_LIMIT, true, true);

		Point2i[] chunks = {new Point2i(0, -2), new Point2i(-2, -29), new Point2i(17, -22)};
		CountDownLatch done = new CountDownLatch(1);
		Platform.startup(() -> {
			try {
				for (Point2i chunk : chunks) {
					report(chunk, dialog);
				}
			} catch (Exception ex) {
				ex.printStackTrace();
			} finally {
				done.countDown();
			}
		});
		done.await();
		Platform.exit();
	}

	private static void report(Point2i chunk, NbtQuery query) throws Exception {
		Tag data = loadRegion(chunk);
		NBTTreeView tree = new NBTTreeView();
		NBTFindBar bar = new NBTFindBar(tree);
		VBox box = new VBox(bar, tree);
		new javafx.scene.Scene(box, 900, 600);
		if (data instanceof net.querz.nbt.CompoundTag root) {
			tree.load(root);
		}
		int count = bar.applyQuery(query);
		System.out.println("chunk " + chunk
				+ " applyQuery=" + count
				+ " treeMatchCount=" + tree.getMatchCount()
				+ " status=[" + statusText(box) + "]"
				+ " expanded=" + expandedCount(tree.getRoot())
				+ " selected=" + describe(tree.getSelectionModel() == null ? null : tree.getSelectionModel().getSelectedItem())
				+ " fields=[" + fieldText(bar) + "]");
	}

	private static String fieldText(NBTFindBar bar) {
		List<String> texts = new ArrayList<>();
		for (Node n : allNodes(bar)) {
			if (n instanceof javafx.scene.control.TextField f) {
				texts.add(f.getPromptText() + "=" + f.getText());
			}
		}
		return String.join(", ", texts);
	}

	private static String describe(TreeItem<NBTTreeView.NamedTag> item) {
		if (item == null) {
			return "none";
		}
		return item.getValue() == null ? "none" : item.getValue().toString();
	}

	private static int expandedCount(TreeItem<?> item) {
		if (item == null) {
			return 0;
		}
		int n = item.isExpanded() ? 1 : 0;
		for (TreeItem<?> child : item.getChildren()) {
			n += expandedCount(child);
		}
		return n;
	}

	private static String statusText(Parent root) {
		for (Node n : allNodes(root)) {
			if (n instanceof Label l && n.getStyleClass().contains("nbt-find-bar-status")) {
				return l.getText();
			}
		}
		return "";
	}

	private static List<Node> allNodes(Node root) {
		List<Node> out = new ArrayList<>();
		out.add(root);
		if (root instanceof Parent p) {
			for (Node child : p.getChildrenUnmodifiable()) {
				out.addAll(allNodes(child));
			}
		}
		return out;
	}

	private static Tag loadRegion(Point2i chunk) throws Exception {
		Point2i region = chunk.chunkToRegion();
		File file = new File(WORLD, "region/r." + region.getX() + "." + region.getZ() + ".mca");
		if (!file.exists()) {
			return null;
		}
		RegionChunk data = new RegionMCAFile(file).loadSingleChunk(chunk);
		return data == null ? null : data.getData();
	}
}
