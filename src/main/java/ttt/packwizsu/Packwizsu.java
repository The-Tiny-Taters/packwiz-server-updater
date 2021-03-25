package ttt.packwizsu;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import ttt.packwizsu.config.ConfigHandler;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class Packwizsu implements PreLaunchEntrypoint {

	private static String packToml;
	public final static File GAME_DIR_FILE = FabricLoader.getInstance().getGameDir().toFile();

	@Override
	public void onPreLaunch() {
		ConfigHandler.init();

		if(GAME_DIR_FILE.exists()) {
			boolean shouldUpdate = Boolean.parseBoolean(ConfigHandler.getValue("should_update"));
			packToml = ConfigHandler.getValue("pack_toml");

			if(shouldUpdate && packToml.contains("pack.toml")) {
				updatePackwiz();
			}
			else if(shouldUpdate && !packToml.contains("pack.toml")) {
				Scanner input = new Scanner(System.in);
				System.out.println("Packwiz pack.toml file link:");
				packToml = input.nextLine();

				ConfigHandler.setValue("pack_toml", packToml);
				ConfigHandler.update();
				updatePackwiz();
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
			StreamConsumer streamConsumer = new StreamConsumer();
			streamConsumer.init(process.getInputStream(), System.out::println);
			Executors.newSingleThreadExecutor().submit(streamConsumer);

			int exitCode = process.waitFor();
			assert exitCode == 0;
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static class StreamConsumer implements Runnable {
		private InputStream inputStream;
		private Consumer<String> consumer;

		public void init(InputStream inputStream, Consumer<String> consumer) {
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
