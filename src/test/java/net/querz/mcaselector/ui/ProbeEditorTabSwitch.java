package net.querz.mcaselector.ui;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Labeled;
import javafx.scene.control.TabPane;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;
import net.querz.mcaselector.config.ConfigProvider;
import net.querz.mcaselector.config.WorldConfig;
import net.querz.mcaselector.io.WorldDirectories;
import net.querz.mcaselector.nbtstudio.NbtMatchMode;
import net.querz.mcaselector.nbtstudio.NbtPattern;
import net.querz.mcaselector.nbtstudio.NbtQuery;
import net.querz.mcaselector.text.Translation;
import net.querz.mcaselector.ui.dialog.NBTEditorDialog;
import net.querz.mcaselector.util.point.Point2i;
import javax.imageio.ImageIO;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Opens the real chunk editor on a chunk whose search hit only lives in entities and prints which
 * tab is in front, so the "opens on region with 0 matches" bug can be checked on screen.
 */
public class ProbeEditorTabSwitch {

	private static List<Node> allNodes(Node root) {
		List<Node> out = new ArrayList<>();
		out.add(root);
		if (root instanceof Parent parent) {
			for (Node child : parent.getChildrenUnmodifiable()) {
				out.addAll(allNodes(child));
			}
		}
		return out;
	}

	public static void main(String[] args) throws Exception {
		String out = args[0];
		Point2i chunk = new Point2i(Integer.parseInt(args[1]), Integer.parseInt(args[2]));
		File world = new File(System.getProperty("world",
				System.getProperty("user.home") + "/Documents/Minecraft/科塔丘实验室4demo（联壁汉化1.2）"));

		Translation.load(java.util.Locale.SIMPLIFIED_CHINESE);
		WorldConfig cfg = new WorldConfig();
		ConfigProvider.WORLD = cfg;
		cfg.setWorldDirs(new WorldDirectories(new File(world, "region"), new File(world, "poi"),
				new File(world, "entities")));

		String term = args.length > 3 ? args[3] : "扳手";
		NbtQuery query = NbtQuery.create(null, null,
				NbtPattern.of(term, NbtMatchMode.CONTAINS, true),
				NbtQuery.ROOT_ALL, null, NbtQuery.DEFAULT_LIMIT, false, false);

		CountDownLatch latch = new CountDownLatch(1);
		Platform.startup(() -> {
			Stage stage = new Stage();
			stage.setScene(new Scene(new StackPane(), 200, 120));
			stage.show();

			NBTEditorDialog dialog = new NBTEditorDialog(null, stage, query, chunk);
			dialog.show();

			PauseTransition wait = new PauseTransition(Duration.seconds(3));
			wait.setOnFinished(e -> {
				TabPane tabs = null;
				for (Node n : allNodes(dialog.getDialogPane())) {
					if (n instanceof TabPane tp) {
						tabs = tp;
						break;
					}
				}
				if (tabs == null) {
					System.out.println("RESULT no tab pane found");
				} else {
					System.out.println("RESULT selected tab = " + tabs.getSelectionModel().getSelectedItem().getText());
					for (javafx.scene.control.Tab t : tabs.getTabs()) {
						System.out.println("RESULT tab " + t.getText() + " selected=" + t.isSelected());
					}
				}
				for (Node n : allNodes(dialog.getDialogPane())) {
					if (n instanceof Labeled l && l.getText() != null && l.getText().contains("匹配")) {
						System.out.println("RESULT status = [" + l.getText() + "]");
					}
				}
				try {
					WritableImage img = dialog.getDialogPane().getScene().snapshot(null);
					ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", new File(out));
					System.out.println("SHOT " + (int) img.getWidth() + "x" + (int) img.getHeight() + " -> " + out);
				} catch (Exception ex) {
					ex.printStackTrace();
				}
				dialog.close();
				stage.close();
				Platform.exit();
				latch.countDown();
			});
			wait.play();
		});
		latch.await();
		System.exit(0);
	}
}
