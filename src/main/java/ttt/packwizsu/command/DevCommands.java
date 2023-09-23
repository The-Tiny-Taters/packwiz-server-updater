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

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
import static ttt.packwizsu.Packwizsu.*;

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
            commandOutput.sendMessage(UPDATED_TOML_LINK);
        }
        catch (MalformedURLException e) {
            throw MALFORMED_URL.create();
        }
        catch (Exception e) {
            e.printStackTrace();
            commandOutput.sendMessage(FILE_UPDATE_FAILED);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int restartAndUpdate(CommandContext<ServerCommandSource> ctx) {
        var commandOutput = getCommandOutput(ctx);
        if(GAME_DIR_FILE.exists()) {
            String packTomlLink = getConfigHandler().getValue("pack_toml");
            var bootstrapFile = new File("packwiz-installer-bootstrap.jar");

            if(bootstrapFile.exists()) {
                if(packTomlLink.contains("pack.toml")) {
                    commandOutput.sendMessage(RESTART_AND_UPDATE);
                    try {
                        updatePackwiz(packTomlLink);
                        ctx.getSource().getServer().stop(false);
                    } catch (Exception e) {
                        e.printStackTrace();
                        commandOutput.sendMessage(PACKWIZ_UPDATE_FAILED);
                    }
                } else
                    commandOutput.sendMessage(NO_PACK_TOML);
            } else
                commandOutput.sendMessage(NO_BOOTSTRAPPER);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static void updatePackwiz(String packTomllink) throws Exception {
        boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
        String shellCommand = "java -jar packwiz-installer-bootstrap.jar -g -s server " + packTomllink;

        var processBuilder = new ProcessBuilder();
        processBuilder.directory(GAME_DIR_FILE);

        if (isWindows)
            processBuilder.command("cmd.exe", "/c", shellCommand);
        else
            processBuilder.command("sh", "-c", shellCommand);

        var process = processBuilder.start();
        var executorService = Executors.newSingleThreadExecutor();

        executorService.submit(new StreamConsumer(process.getInputStream(), System.out::println));
        process.waitFor();
        executorService.shutdown();
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
