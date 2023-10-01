package ttt.packwizsu.command;

import com.moandjiezana.toml.Toml;
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
import org.jetbrains.annotations.NotNull;
import ttt.packwizsu.util.TickCounter;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
import static ttt.packwizsu.Packwizsu.*;
import static ttt.packwizsu.command.CommandExceptions.*;

public final class PackwizsuCommands {

    private static final MutableText UPDATE_START = Text.literal("Updating Packwiz...").formatted(Formatting.GREEN);
    private static final MutableText UPDATE_FINISHED = Text.literal("Packwiz has finished updating! Restart the server for changes to take effect").formatted(Formatting.GREEN);
    private static final MutableText UPDATED_TOML_LINK = Text.literal("Successfully linked a Packwiz modpack! Use /packwizsu update for the changes to take effect").formatted(Formatting.GREEN);
    private static final MutableText PACKWIZ_UPDATE_FAILED = Text.literal("Failed to update Packwiz. Check the server console for errors").formatted(Formatting.RED);
    private static final MutableText PROCESS_INTERRUPTED = Text.literal("Failed to update Packwiz. Process was interrupted. Check the server console for details").formatted(Formatting.RED);
    private static final MutableText FILE_HANDLING_ERROR = Text.literal("Failed to update Packwiz. Read/write process failed. Check the server console for details").formatted(Formatting.RED);

    private static final Set<String> PACK_TOML_REQUIRED_KEYS = Set.of( "name", "author", "version", "index");

    private static final AtomicBoolean UPDATE_IN_PROGRESS = new AtomicBoolean(false);
    private static final TickCounter POLL_COUNTER = new TickCounter(10);

    private static AsyncCommandTask updateTask;

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("packwizsu")
                .then(literal("link")
                        .then(argument("url", StringArgumentType.greedyString())
                                .executes(PackwizsuCommands::setTomlLink)
                        )
                )
                .then(literal("update")
                        .executes(PackwizsuCommands::restartAndUpdate)
                )
                .requires(source -> source.hasPermissionLevel(4))
        );
    }

    private static int setTomlLink(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        try {
            var url = testPackTomlLink(StringArgumentType.getString(ctx, "url"));
            getConfigHandler().setValue("pack_toml", url.toExternalForm());
            getConfigHandler().update();
            getCommandOutput(ctx).sendMessage(UPDATED_TOML_LINK);
            return Command.SINGLE_SUCCESS;
        }
        catch (PackTomlURLException ptue) {
            throw new SimpleCommandExceptionType(Text.literal(ptue.getMessage())).create();
        } catch (Exception e) {
            e.printStackTrace();
            throw FILE_UPDATE_FAILED.create();
        }
    }

    private static int restartAndUpdate(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        try {
            if(!GAME_DIR_FILE.exists())
                throw DIRECTORY_BLANK_ERROR.create();
        } catch (SecurityException se) {
            throw DIRECTORY_SECURITY_ERROR.create();
        }

        String packTomlLink = getConfigHandler().getValue("pack_toml");
        var bootstrapFile = new File("packwiz-installer-bootstrap.jar");

        if (!bootstrapFile.exists())
            throw NO_BOOTSTRAPPER.create();
        if (!packTomlLink.contains("pack.toml"))
            throw NO_PACK_TOML.create();
        if (!UPDATE_IN_PROGRESS.compareAndSet(false, true))
            throw UPDATE_IN_PROGRESS_ERROR.create();

        var commandOutput = getCommandOutput(ctx);
        commandOutput.sendMessage(UPDATE_START);
        tryUpdatePackwiz(packTomlLink, commandOutput);

        return Command.SINGLE_SUCCESS;
    }

    private static void tryUpdatePackwiz(@NotNull final String packTomllink, final CommandOutput co) {
        final String[] command = new String[] { "java", "-jar", "packwiz-installer-bootstrap.jar", "-g", "-s", "server", packTomllink };

        updateTask = new AsyncCommandTask(CompletableFuture.runAsync(() -> {
            try {
                testPackTomlLink(packTomllink);

                var process = new ProcessBuilder(command).inheritIO().start();
                try (var bufferedReader = process.inputReader()) {
                    bufferedReader.lines().forEach(LOGGER::info);
                }
                int exitCode = process.waitFor();
                if (exitCode != 0)
                    throw new ProcessExitCodeException("Packwiz update process failed with exit code: " + exitCode);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }), co);
    }

    private static @NotNull URL testPackTomlLink(@NotNull final String packTomllink) throws PackTomlURLException {
        try {
            var url = new URL(packTomllink);
            var connection = url.openConnection();
            var toml = new Toml().read(connection.getInputStream());

            if (!PACK_TOML_REQUIRED_KEYS.stream().allMatch(toml::contains)) {
                String requiredKeys = String.join(", ", PACK_TOML_REQUIRED_KEYS);
                throw new PackTomlURLException("The file does not contain all the required keys: " + requiredKeys);
            }
            return url;
        } catch (MalformedURLException mue) {
            throw new PackTomlURLException("The link submitted is not a valid URL");
        } catch (IOException ioe) {
            throw new PackTomlURLException("Check this file exists and is a valid TOML file");
        } catch (IllegalStateException ise) {
            throw new PackTomlURLException("The file contains invalid data");
        }
    }

    public static void pollCommandStatus() {
        if (POLL_COUNTER.test() && (UPDATE_IN_PROGRESS.get() && isPackwizUpdateComplete())) {
            var co = updateTask.commandOutput;
            Text message = UPDATE_FINISHED;
            Exception exception = null;

            try {
                updateTask.future().join();
            } catch (CompletionException e) {
                var cause = e.getCause();
                exception = e;

                if (cause instanceof InterruptedException)
                    message = PROCESS_INTERRUPTED;
                else if (cause instanceof PackTomlURLException ptfe)
                    message = Text.literal(ptfe.getMessage());
                else if (cause instanceof ProcessExitCodeException pece)
                    message = Text.literal(pece.getMessage());
                else if (cause instanceof IOException)
                    message = FILE_HANDLING_ERROR;
                else
                    message = PACKWIZ_UPDATE_FAILED;
            }
            if (co != null) co.sendMessage(message);
            if (exception != null) exception.printStackTrace();

            UPDATE_IN_PROGRESS.set(false);
        }
        POLL_COUNTER.increment();
    }

    private static boolean isPackwizUpdateComplete() {
        if (updateTask != null)
            return updateTask.future != null && updateTask.future.isDone();
        else
            return false;
    }

    private static CommandOutput getCommandOutput(CommandContext<ServerCommandSource> ctx) {
        if(ctx.getSource().getEntity() instanceof ServerPlayerEntity player)
            return player;
        else
            return ctx.getSource().getServer();
    }

    private record AsyncCommandTask(CompletableFuture<Void> future, CommandOutput commandOutput) {}

    private static class PackTomlURLException extends Exception {
        private static final String EXCEPTION_START = "Failed to read the Packwiz pack.toml file. ";

        public PackTomlURLException(String message) {
            super(EXCEPTION_START + message);
        }
    }

    private static class ProcessExitCodeException extends Exception {
        public ProcessExitCodeException(String message) {
            super(message);
        }
    }
}
