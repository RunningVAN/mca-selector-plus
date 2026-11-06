package net.querz.mcaselector.nbtstudio;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.util.Duration;
import net.querz.mcaselector.ui.dialog.NbtStudioDialog;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.net.URL;
import java.util.concurrent.CountDownLatch;

/** Renders the dialog to a PPM file so the layout can be inspected without a display server. */
public class NbtStudioDialogShot {

	private static void dump(javafx.scene.Node node, int depth) {
		String text = "";
		if (node instanceof javafx.scene.control.Labeled l) {
			text = l.getText();
		}
		javafx.geometry.Bounds inScene = node.localToScene(node.getLayoutBounds());
		System.out.printf("%s%s sy=%.1f sh=%.1f x=%.1f w=%.1f%s%n",
				"  ".repeat(depth), node.getClass().getSimpleName(),
				inScene.getMinY(), inScene.getHeight(),
				inScene.getMinX(), inScene.getWidth(),
				text == null || text.isEmpty() ? "" : " text=[" + text.substring(0, Math.min(24, text.length())) + "]");
		if (node instanceof javafx.scene.Parent parent) {
			for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
				dump(child, depth + 1);
			}
		} else if (node instanceof javafx.scene.control.DialogPane) {
			// handled by Parent
		}
	}

	private static javafx.scene.Node modes(NbtStudioDialog dialog) {
		for (javafx.scene.Node n : allNodes(dialog.getDialogPane())) {
			if (n instanceof javafx.scene.control.ChoiceBox<?> cb && cb.getItems().size() == 5) {
				return cb;
			}
		}
		return null;
	}

	private static javafx.scene.control.ChoiceBox<?> typeFilter(NbtStudioDialog dialog) {
		for (javafx.scene.Node n : allNodes(dialog.getDialogPane())) {
			if (n instanceof javafx.scene.control.ChoiceBox<?> cb && cb.getItems().size() == 6) {
				return cb;
			}
		}
		return null;
	}

	private static void uncheck(javafx.scene.Node node, String text) {
		if (node instanceof javafx.scene.control.CheckBox cb && text.equals(cb.getText())) {
			cb.setSelected(false);
		}
		if (node instanceof javafx.scene.Parent parent) {
			for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
				uncheck(child, text);
			}
		}
	}

	private static java.util.List<javafx.scene.Node> allNodes(javafx.scene.Node root) {
		java.util.List<javafx.scene.Node> out = new java.util.ArrayList<>();
		out.add(root);
		if (root instanceof javafx.scene.Parent parent) {
			for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
				out.addAll(allNodes(child));
			}
		}
		return out;
	}

	private static void toggleRegex(NbtStudioDialog dialog) {
		for (javafx.scene.Node n : allNodes(dialog.getDialogPane())) {
			if (n instanceof javafx.scene.control.CheckBox cb && cb.getText() != null && cb.getText().contains("\u6b63\u5219")) {
				cb.setSelected(true);
				return;
			}
		}
		for (javafx.scene.Node n : allNodes(dialog.getDialogPane())) {
			if (n instanceof javafx.scene.control.CheckBox cb && cb.getText() != null && cb.getText().toLowerCase().contains("regex")) {
				cb.setSelected(true);
				return;
			}
		}
	}

	private static void collectFields(javafx.scene.Node node, java.util.List<javafx.scene.control.TextField> out) {
		if (node instanceof javafx.scene.control.TextField tf) {
			out.add(tf);
		}
		if (node instanceof javafx.scene.Parent parent) {
			for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
				collectFields(child, out);
			}
		}
	}

	public static void main(String[] args) throws Exception {
		String out = args[0];
		String scenario = args.length > 1 ? args[1] : "error";
		java.util.Locale locale = args.length > 2 ? java.util.Locale.forLanguageTag(args[2]) : java.util.Locale.SIMPLIFIED_CHINESE;
		// the dialog renders translated text, without loading the language files it would only show keys
		net.querz.mcaselector.text.Translation.load(locale);
		CountDownLatch latch = new CountDownLatch(1);

		Platform.startup(() -> {
			Stage owner = new Stage();
			URL base = NbtStudioDialogShot.class.getClassLoader().getResource("style/base.css");
			Scene ownerScene = new Scene(new javafx.scene.layout.StackPane(), 100, 100);
			if (base != null) {
				ownerScene.getStylesheets().add(base.toExternalForm());
			}
			owner.setScene(ownerScene);
			owner.show();

			NbtStudioDialog dialog = new NbtStudioDialog(owner, null);
			dialog.show();

			if (scenario.equals("geometry")) {
				PauseTransition geo = new PauseTransition(Duration.millis(500));
				geo.setOnFinished(ev -> {
					dump(dialog.getDialogPane().getScene().getRoot(), 0);
					// also report the preferred and actual dialog size
					System.out.println("DIALOG pref=" + dialog.getDialogPane().prefWidth(-1) + "x" + dialog.getDialogPane().prefHeight(-1)
							+ " actual=" + dialog.getDialogPane().getWidth() + "x" + dialog.getDialogPane().getHeight());
					System.out.flush();
					System.exit(0);
				});
				geo.play();
				return;
			}

			PauseTransition fill = new PauseTransition(Duration.millis(300));
			fill.setOnFinished(e -> {
				java.util.List<javafx.scene.control.TextField> fields = new java.util.ArrayList<>();
				collectFields(dialog.getDialogPane(), fields);
				// the dialog has three text fields: tag name, tag value and replacement
				if (fields.size() < 3) {
					return;
				}
				javafx.scene.control.TextField name = fields.get(0);
				javafx.scene.control.TextField value = fields.get(1);
				javafx.scene.control.TextField replacement = fields.get(2);
				if (scenario.equals("filled")) {
					name.setText("id");
					value.setText("minecraft:chest");
					replacement.setText("minecraft:barrel");
				} else if (scenario.equals("valueonly")) {
					// the case that used to find nothing: only the visible text, no field name
					value.setText("\u8c1c");
					replacement.setText("test");
				} else if (scenario.equals("badregex")) {
					toggleRegex(dialog);
					name.setText("palette");
					value.setText("([a-z");
					replacement.setText("minecraft:dirt");
				} else if (scenario.equals("scope")) {
					name.setText("palette");
					replacement.setText("minecraft:dirt");
					uncheck(dialog.getDialogPane(), "\u5b9e\u4f53");
				}
			});
			fill.play();

			PauseTransition p = new PauseTransition(Duration.millis(600));
			p.setOnFinished(e -> {
				// the query the dialog would execute is part of the contract as well: it has to carry
				// the scope, the type filter and the limit that are set in the controls
				try {
					java.lang.reflect.Method build = NbtStudioDialog.class.getDeclaredMethod("buildQuery");
					build.setAccessible(true);
					System.out.println("QUERY " + ((NbtQuery) build.invoke(dialog)).canonical());
				} catch (ReflectiveOperationException ex) {
					System.out.println("QUERY <unavailable>");
				}
				// the button states are part of the validation contract, so dump them next to the image
				for (javafx.scene.Node n : dialog.getDialogPane().getButtonTypes().stream()
						.map(dialog.getDialogPane()::lookupButton).toList()) {
					if (n instanceof javafx.scene.control.Button b) {
						System.out.println("BUTTON [" + b.getText() + "] disabled=" + b.isDisabled());
					}
				}
				try (FileOutputStream fos = new FileOutputStream(out)) {
					WritableImage img = dialog.getDialogPane().getScene().getRoot().snapshot(null, null);
					PixelReader pr = img.getPixelReader();
					int w = (int) img.getWidth();
					int h = (int) img.getHeight();
					ByteArrayOutputStream header = new ByteArrayOutputStream();
					header.write(("P6\n" + w + " " + h + "\n255\n").getBytes("ASCII"));
					fos.write(header.toByteArray());
					for (int y = 0; y < h; y++) {
						for (int x = 0; x < w; x++) {
							int argb = pr.getArgb(x, y);
							fos.write((argb >> 16) & 0xff);
							fos.write((argb >> 8) & 0xff);
							fos.write(argb & 0xff);
						}
					}
					System.out.println("SHOT " + w + "x" + h);
				} catch (Exception ex) {
					ex.printStackTrace();
				}
				// closing the dialog and the owner stage can block the FX thread on macOS, so the
				// harness just ends the JVM once the image is on disk
				System.out.flush();
				System.exit(0);
			});
			p.play();
		});

		latch.await();
		Platform.exit();
	}
}
