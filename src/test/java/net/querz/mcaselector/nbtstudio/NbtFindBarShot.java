package net.querz.mcaselector.nbtstudio;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import net.querz.mcaselector.text.Translation;
import net.querz.mcaselector.ui.component.NBTFindBar;
import net.querz.mcaselector.ui.component.NBTTreeView;
import net.querz.nbt.CompoundTag;
import net.querz.nbt.IntTag;
import net.querz.nbt.ListTag;
import net.querz.nbt.StringTag;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Renders the find bar of the chunk editor together with a populated tree, so the layout and the
 * status line can be inspected without opening a world. It also prints the match list and the
 * effect of "find all" and "replace all", which is the actual contract of the bar.
 */
public class NbtFindBarShot {

	private static CompoundTag chunk() {
		CompoundTag root = new CompoundTag();
		root.put("DataVersion", IntTag.valueOf(3953));
		root.put("Status", StringTag.valueOf("minecraft:full"));
		ListTag sections = new ListTag();
		for (int i = 0; i < 3; i++) {
			CompoundTag section = new CompoundTag();
			section.put("Y", IntTag.valueOf(i));
			ListTag palette = new ListTag();
			for (int j = 0; j < 3; j++) {
				CompoundTag entry = new CompoundTag();
				entry.put("Name", StringTag.valueOf(j == 1 ? "minecraft:stone" : "minecraft:dirt"));
				palette.add(entry);
			}
			section.put("palette", palette);
			sections.add(section);
		}
		root.put("sections", sections);
		return root;
	}

	private static List<javafx.scene.Node> allNodes(javafx.scene.Node root) {
		List<javafx.scene.Node> out = new ArrayList<>();
		out.add(root);
		if (root instanceof javafx.scene.Parent parent) {
			for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
				out.addAll(allNodes(child));
			}
		}
		return out;
	}

	private static Button button(javafx.scene.Node root, String text) {
		for (javafx.scene.Node n : allNodes(root)) {
			if (n instanceof Button b && text.equals(b.getText())) {
				return b;
			}
		}
		return null;
	}

	private static void writePpm(javafx.scene.image.Image img, String out) throws Exception {
		WritableImage image = new WritableImage((int) img.getWidth(), (int) img.getHeight());
		PixelReader pr = img.getPixelReader();
		int w = (int) img.getWidth();
		int h = (int) img.getHeight();
		ByteArrayOutputStream header = new ByteArrayOutputStream();
		header.write(("P6\n" + w + " " + h + "\n255\n").getBytes("ASCII"));
		try (FileOutputStream fos = new FileOutputStream(out)) {
			fos.write(header.toByteArray());
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int argb = pr.getArgb(x, y);
					fos.write((argb >> 16) & 0xff);
					fos.write((argb >> 8) & 0xff);
					fos.write(argb & 0xff);
				}
			}
		}
		System.out.println("SHOT " + w + "x" + h);
	}

	public static void main(String[] args) throws Exception {
		String out = args[0];
		String scenario = args.length > 1 ? args[1] : "findall";
		Translation.load(java.util.Locale.SIMPLIFIED_CHINESE);
		CountDownLatch latch = new CountDownLatch(1);

		Platform.startup(() -> {
			NBTTreeView tree = new NBTTreeView();
			tree.load(chunk());
			NBTFindBar bar = new NBTFindBar(tree);

			VBox root = new VBox(bar, tree);
			VBox.setVgrow(tree, javafx.scene.layout.Priority.ALWAYS);

			Scene scene = new Scene(root, 720, 420);
			URL base = NbtFindBarShot.class.getClassLoader().getResource("style/base.css");
			if (base != null) {
				scene.getStylesheets().add(base.toExternalForm());
			}
			URL css = NbtFindBarShot.class.getClassLoader().getResource("style/component/nbt-editor-dialog.css");
			if (css != null) {
				scene.getStylesheets().add(css.toExternalForm());
			}
			Stage stage = new Stage();
			stage.setScene(scene);
			stage.show();

			PauseTransition fill = new PauseTransition(Duration.millis(400));
			fill.setOnFinished(e -> {
				TextField find = (TextField) allNodes(bar).stream()
						.filter(n -> n instanceof TextField).findFirst().orElseThrow();
				TextField replace = (TextField) allNodes(bar).stream()
						.filter(n -> n instanceof TextField).skip(1).findFirst().orElseThrow();
				find.setText("stone");
				replace.setText("stone_bricks");
			});
			fill.play();

			PauseTransition act = new PauseTransition(Duration.millis(800));
			act.setOnFinished(e -> {
				Button findAll = button(bar, Translation.DIALOG_NBT_EDITOR_FIND_ALL.toString());
				System.out.println("find all button: " + (findAll == null ? "MISSING" : findAll.getText()));
				if (findAll != null) {
					findAll.fire();
				}
				System.out.println("matches: " + tree.getMatchCount() + " selected=" + (tree.getMatchIndex() + 1));
				if (scenario.equals("replaceall")) {
					Button replaceAll = button(bar, Translation.DIALOG_NBT_EDITOR_REPLACE_ALL.toString());
					System.out.println("replace all button: " + (replaceAll == null ? "MISSING" : replaceAll.getText()));
					if (replaceAll != null) {
						replaceAll.fire();
					}
					// the palette has to read minecraft:stone_bricks exactly once, a second pass
					// would turn it into minecraft:stone_bricks_bricks
					CompoundTag sections = (CompoundTag) tree.getRoot().getValue().getRef();
					ListTag sectionList = (ListTag) sections.get("sections");
					CompoundTag firstSection = (CompoundTag) sectionList.get(0);
					ListTag palette = (ListTag) firstSection.get("palette");
					System.out.println("value after replace all: " + ((CompoundTag) palette.get(1)).get("Name"));
					System.out.println("matches after replace all: " + tree.getMatchCount());
				}
			});
			act.play();

			PauseTransition shot = new PauseTransition(Duration.millis(1300));
			shot.setOnFinished(e -> {
				// geometry dump: a clipped button or a status line squeezed to zero width is the
				// failure mode this layout has to survive, so report it next to the image
				for (javafx.scene.Node n : allNodes(bar)) {
					String text = n instanceof javafx.scene.control.Labeled l && l.getText() != null ? l.getText() : "";
					javafx.geometry.Bounds b = n.localToScene(n.getLayoutBounds());
					System.out.printf("NODE %-12s x=%6.1f w=%6.1f%s%n", n.getClass().getSimpleName(),
							b.getMinX(), b.getWidth(), text.isEmpty() ? "" : " text=[" + text + "]");
				}
				try {
					writePpm(root.snapshot(null, null), out);
				} catch (Exception ex) {
					ex.printStackTrace();
				}
				Platform.exit();
				latch.countDown();
			});
			shot.play();
		});

		latch.await();
	}
}
