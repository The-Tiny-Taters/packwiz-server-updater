package ttt.packwizsu;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class Packwizsu implements PreLaunchEntrypoint {

	private final static String PACK_TOML = "https://raw.githubusercontent.com/The-Tiny-Taters/TTT-Creative/main/pack.toml";
	private final static String SHELL_COMMAND = "java -jar packwiz-installer-bootstrap.jar -g -s server " + PACK_TOML;

	@Override
	public void onPreLaunch() {
		boolean isWindows = System.getProperty("os.name")
				.toLowerCase().startsWith("windows");

		ProcessBuilder builder = new ProcessBuilder();
		if (isWindows) {
			builder.command("cmd.exe", "/c", SHELL_COMMAND);
		}
		else {
			builder.command("sh", "-c", SHELL_COMMAND);
		}
		builder.directory(FabricLoader.getInstance().getGameDir().toFile());

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
