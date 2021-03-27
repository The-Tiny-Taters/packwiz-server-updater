package ttt.packwizsu;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ttt.packwizsu.config.ConfigHandler;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class Packwizsu implements PreLaunchEntrypoint {

	private static final Logger logger = LogManager.getLogger();
	private static String packToml;
	public final static File GAME_DIR_FILE = FabricLoader.getInstance().getGameDir().toFile();

	@Override
	public void onPreLaunch() {
		logger.info("======== STARTING PACKWIZ UPDATER ========");
		ConfigHandler.init();

		if(GAME_DIR_FILE.exists()) {
			boolean shouldUpdate = Boolean.parseBoolean(ConfigHandler.getValue("should_update"));
			packToml = ConfigHandler.getValue("pack_toml");

			File bootstrapper = new File("packwiz-installer-bootstrap.jar");
			if(!bootstrapper.exists()) {
				logger.warn("packwiz-installer-bootstrap.jar isn't found in the root directory for the server");
			}

			if(shouldUpdate && packToml.contains("pack.toml")) {
				updatePackwiz();
			}
			else if(shouldUpdate && !packToml.contains("pack.toml")) {
				logger.warn("Cannot find a pack.toml file to update packwiz from");
			}
			else if(!shouldUpdate){
				logger.info("Packwiz updates are disabled, enable within: packwiz-server-updater.properties");
			}
		}
	}

	private void updatePackwiz() {
		boolean isWindows = System.getProperty("os.name")
				.toLowerCase().startsWith("windows");

		String shellCommand = "java -jar packwiz-installer-bootstrap.jar -g -s server " + packToml;

		ProcessBuilder builder = new ProcessBuilder();
		if (isWindows) {
			builder.command("cmd.exe", "/c", shellCommand);
		}
		else {
			builder.command("sh", "-c", shellCommand);
		}
		builder.directory(GAME_DIR_FILE);

		try {
			Process process = builder.start();
			StreamConsumer streamConsumer = new StreamConsumer(process.getInputStream(), System.out::println);
			Executors.newSingleThreadExecutor().submit(streamConsumer);

			int exitCode = process.waitFor();
			assert exitCode == 0;
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static class StreamConsumer implements Runnable {
		private final InputStream inputStream;
		private final Consumer<String> consumer;

		public StreamConsumer(InputStream inputStream, Consumer<String> consumer) {
			this.inputStream = inputStream;
			this.consumer = consumer;
		}

		@Override
		public void run() {
			new BufferedReader(new InputStreamReader(inputStream)).lines()
					.forEach(consumer);
		}
	}
}
