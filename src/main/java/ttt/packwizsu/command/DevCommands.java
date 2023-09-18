package ttt.packwizsu.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import ttt.packwizsu.Packwizsu;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
import static ttt.packwizsu.Packwizsu.*;
import static ttt.packwizsu.Packwizsu.getConfigHandler;

public final class DevCommands {

    private static final MutableText RESTART_AND_UPDATE = Text.literal("Updating the packwiz modpack and shutting down the server").formatted(Formatting.GREEN);
    private static final MutableText UPDATED_TOML_LINK = Text.literal("Successfully linked a Packwiz modpack! Use /packwizsu update for the changes to take effect").formatted(Formatting.GREEN);
    private static final MutableText NO_BOOTSTRAPPER = Text.literal("packwiz-installer-bootstrap.jar wasn't found within the root directory of the server").formatted(Formatting.RED);
    private static final MutableText NO_PACK_TOML = Text.literal("There is no pack.toml link to update from. Add this using /packwizsu link [url]").formatted(Formatting.RED);
    private static final MutableText FILE_UPDATE_FAILED = Text.literal("Failed to update the packwiz-server-updater.properties file within the root directory of the server").formatted(Formatting.RED);
    private static final MutableText PACKWIZ_UPDATE_FAILED = Text.literal("Update command failed. Check the server console for errors").formatted(Formatting.RED);

    private static final SimpleCommandExceptionType MALFORMED_URL = new SimpleCommandExceptionType(Text.literal("The link submitted is not a valid URL"));

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("packwizsu")
                .then(literal("link")
                        .then(argument("url", StringArgumentType.greedyString())
                                .executes(DevCommands::setTomlLink)
                        )
                )
                .then(literal("update")
                        .executes(DevCommands::restartAndUpdate)
                )
                .requires(source -> source.hasPermissionLevel(4))
        );
    }

    private static int setTomlLink(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        var commandOutput = getCommandOutput(ctx);
        try {
            var url = new URL(StringArgumentType.getString(ctx, "url"));
            getConfigHandler().setValue("pack_toml", url.toExternalForm());
            getConfigHandler().update();
        }
        catch (MalformedURLException e) {
            throw MALFORMED_URL.create();
        }
        catch (Exception e) {
            e.printStackTrace();
            commandOutput.sendMessage(FILE_UPDATE_FAILED);
        }
        commandOutput.sendMessage(UPDATED_TOML_LINK);
        return Command.SINGLE_SUCCESS;
    }

    private static int restartAndUpdate(CommandContext<ServerCommandSource> ctx) {
        var commandOutput = getCommandOutput(ctx);
        if(GAME_DIR_FILE.exists()) {
            String packToml = getConfigHandler().getValue("pack_toml");
            File bootstrapper = new File("packwiz-installer-bootstrap.jar");

            if(bootstrapper.exists()) {
                if(packToml.contains("pack.toml")) {
                    commandOutput.sendMessage(RESTART_AND_UPDATE);
                    if(updatePackwiz(packToml))
                        ctx.getSource().getServer().stop(false);
                    else
                        commandOutput.sendMessage(PACKWIZ_UPDATE_FAILED);
                } else
                    commandOutput.sendMessage(NO_PACK_TOML);
            } else
                commandOutput.sendMessage(NO_BOOTSTRAPPER);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static boolean updatePackwiz(String packToml) {
        boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
        String shellCommand = "java -jar packwiz-installer-bootstrap.jar -g -s server " + packToml;

        ProcessBuilder builder = new ProcessBuilder();
        if (isWindows)
            builder.command("cmd.exe", "/c", shellCommand);
        else
            builder.command("sh", "-c", shellCommand);

        boolean updated = false;
        builder.directory(GAME_DIR_FILE);

        try {
            var process = builder.start();
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.submit(new StreamConsumer(process.getInputStream(), System.out::println));
            int exitCode = process.waitFor();
            executor.shutdown();

            if(exitCode == 0)
                updated = true;
            else
                LOGGER.error("Failed to release the packwiz modpack executor thread with exit code: " + exitCode);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return updated;
    }

    private static CommandOutput getCommandOutput(CommandContext<ServerCommandSource> ctx) {
        if(ctx.getSource().getEntity() instanceof ServerPlayerEntity player)
            return player;
        else
            return ctx.getSource().getServer();
    }

    private record StreamConsumer(InputStream inputStream, Consumer<String> consumer) implements Runnable {
        @Override
        public void run() {
            new BufferedReader(new InputStreamReader(inputStream)).lines().forEach(consumer);
        }
    }
}
