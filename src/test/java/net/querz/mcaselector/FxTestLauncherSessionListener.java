package net.querz.mcaselector;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

/**
 * Brings the JavaFX toolkit up before the first test class runs, so that the first test to open a
 * window can never leave the platform shut down behind it for the rest of the run.
 */
public class FxTestLauncherSessionListener implements LauncherSessionListener {

	@Override
	public void launcherSessionOpened(LauncherSession session) {
		FxTestSupport.start();
	}
}
