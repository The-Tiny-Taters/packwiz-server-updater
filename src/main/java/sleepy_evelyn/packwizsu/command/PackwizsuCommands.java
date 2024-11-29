package sleepy_evelyn.packwizsu.command;

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
import sleepy_evelyn.packwizsu.util.HashedFileDownloader;
import sleepy_evelyn.packwizsu.util.TickCounter;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Predicate;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
import static sleepy_evelyn.packwizsu.Packwizsu.*;

public final class PackwizsuCommands {

    private static final String BOOTSTRAP_URL = "https://github.com/packwiz/packwiz-installer-bootstrap/releases/download/v0.0.3/packwiz-installer-bootstrap.jar";
    private static final String BOOTSTRAP_HASH = "a8fbb24dc604278e97f4688e82d3d91a318b98efc08d5dbfcbcbcab6443d116c";
    private static final String BOOTSTRAP_TASK_NAME = "downloadBootstrap";
    private static final String UPDATE_PACKWIZ_TASK_NAME = "updatePackwiz";

    private static final MutableText UPDATE_START = Text.literal("Updating modpack. This may take a while...").formatted(Formatting.GRAY);
    private static final MutableText UPDATE_START_NO_BOOTSTRAP = Text.literal("Downloading the Packwiz Bootstrap and updating the modpack. This may take a while...").formatted(Formatting.GRAY);
    private static final MutableText UPDATE_FINISHED = Text.literal("Packwiz has finished updating. Restart the server for changes to take effect.").formatted(Formatting.GREEN);
    private static final MutableText BOOTSTRAP_DOWNLOAD_FINISHED = Text.literal("Bootstrap downloaded successfully.");
    private static final MutableText UPDATED_TOML_LINK = Text.literal("Successfully linked a Packwiz modpack. Use /packwiz update for the changes to take effect.").formatted(Formatting.GREEN);
    private static final MutableText COMMAND_FAILED = Text.literal("Command failed. Check the server console for errors.").formatted(Formatting.RED);
    private static final MutableText PROCESS_INTERRUPTED = Text.literal("Process was interrupted. Check the server console for details.").formatted(Formatting.RED);
    private static final MutableText FILE_HANDLING_ERROR = Text.literal("Read/write process failed. Check the server console for details.").formatted(Formatting.RED);

    private static final Set<String> PACK_TOML_REQUIRED_KEYS = Set.of( "name", "author", "version", "index");

    private static final LinkedList<AsyncCommandTask> TASKS = new LinkedList<>();
    private static final Predicate<String> HAS_TASK = name -> TASKS.stream().anyMatch((task) -> task.hasName(name));

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("packwiz")
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
        } catch (PackTomlURLException ptue) {
            throw new SimpleCommandExceptionType(Text.literal(ptue.getMessage())).create();
        } catch (Exception e) {
            e.printStackTrace();
            throw sleepy_evelyn.packwizsu.command.CommandExceptions.FILE_UPDATE_FAILED.create();
        }
    }

    private static int restartAndUpdate(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        try {
            if(!GAME_DIR_FILE.exists())
                throw sleepy_evelyn.packwizsu.command.CommandExceptions.DIRECTORY_BLANK_ERROR.create();
        } catch (SecurityException se) {
            throw sleepy_evelyn.packwizsu.command.CommandExceptions.DIRECTORY_SECURITY_ERROR.create();
        }

        String packTomlLink = getConfigHandler().getValue("pack_toml");
        if (!packTomlLink.contains("pack.toml"))
            throw sleepy_evelyn.packwizsu.command.CommandExceptions.NO_PACK_TOML.create();
        if (HAS_TASK.test(UPDATE_PACKWIZ_TASK_NAME))
            throw sleepy_evelyn.packwizsu.command.CommandExceptions.UPDATE_IN_PROGRESS_ERROR.create();

        boolean hasBootstrap = new File("packwiz-installer-bootstrap.jar").exists();
        if (hasBootstrap)
            getCommandOutput(ctx).sendMessage(UPDATE_START);
        else
            getCommandOutput(ctx).sendMessage(UPDATE_START_NO_BOOTSTRAP);

        tryUpdatePackwiz(ctx, packTomlLink, hasBootstrap);
        return Command.SINGLE_SUCCESS;
    }

    private static void tryUpdatePackwiz(CommandContext<ServerCommandSource> ctx, @NotNull final String packTomllink, final boolean hasBootstrap) {
        final String[] command = new String[] { "java", "-jar", "packwiz-installer-bootstrap.jar", "-g", "-s", "server", packTomllink };

        if (!HAS_TASK.test(UPDATE_PACKWIZ_TASK_NAME)) {
            TASKS.add(new AsyncCommandTask(CompletableFuture.runAsync(() -> {
                try {
                    if (!hasBootstrap) {
                        var bootstrapPath = Path.of(GAME_DIR_FILE + "/packwiz-installer-bootstrap.jar");
                        var downloader = new HashedFileDownloader(BOOTSTRAP_URL, BOOTSTRAP_HASH, bootstrapPath);

                        downloader.download();
                        if (!downloader.hashesMatch()) {
                            var bootstrapFile = bootstrapPath.toFile();

                            if (bootstrapFile.exists()) {
                                if (!bootstrapFile.delete()) {
                                    throw new IOException("Cannot verify the integrity of downloaded file 'packwiz-installer-bootstrap.jar'" +
                                            "Please delete this file manually from your main server directory and replace with the correct file" +
                                            "from https://github.com/packwiz/packwiz-installer-bootstrap/releases as it may be malicious");
                                }
                            }
                            throw new FailedHashMatchException();
                        }
                    }
                    testPackTomlLink(packTomllink);

                    var process = new ProcessBuilder(command).inheritIO().start();
                    try (var bufferedReader = process.inputReader()) {
                        bufferedReader.lines().forEach(LOGGER::info);
                    }
                    int exitCode = process.waitFor();
                    if (exitCode != 0)
                        throw new ProcessExitCodeException("Process failed with exit code: " + exitCode);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }), UPDATE_PACKWIZ_TASK_NAME, 20, ctx));
        }
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
        var tasksIterator = TASKS.listIterator();
        Exception exception = null;
        Text message = null;

        while (tasksIterator.hasNext()) {
            var task = tasksIterator.next();
            task.tick();

            if (task.pollFinished()) {
                try {
                    task.getFuture().join();

                    if (task.hasName(UPDATE_PACKWIZ_TASK_NAME))
                        message = UPDATE_FINISHED;
                    else if (task.hasName(BOOTSTRAP_TASK_NAME))
                        message = BOOTSTRAP_DOWNLOAD_FINISHED;
                } catch (CompletionException e) {
                    var cause = e.getCause();
                    exception = e;

                    if (cause instanceof InterruptedException)
                        message = PROCESS_INTERRUPTED;
                    else if (cause instanceof IOException)
                        message = FILE_HANDLING_ERROR;

                    if (task.hasName(UPDATE_PACKWIZ_TASK_NAME)) {
                        if (cause instanceof PackTomlURLException ptfe)
                            message = Text.literal(ptfe.getMessage());
                        else if (cause instanceof ProcessExitCodeException pece)
                            message = Text.literal(pece.getMessage());
                        else if (cause instanceof FailedHashMatchException fhme)
                            message = Text.literal(fhme.getMessage());
                    }
                    if (message == null) message = COMMAND_FAILED;
                }
                task.sendMessage(message);
                if (exception != null) exception.printStackTrace();
                tasksIterator.remove();
            }
        }
    }

    private static class AsyncCommandTask {
        private final String name;
        private final CompletableFuture<Void> future;
        private final CommandOutput co;
        private final TickCounter tc;

        AsyncCommandTask(CompletableFuture<Void> future, String name, int pollTicks, CommandContext<ServerCommandSource> ctx) {
            this.future = future;
            this.name = name;
            this.co = getCommandOutput(ctx);
            this.tc = new TickCounter(pollTicks);
        }

        public void tick() { tc.increment(); }

        public boolean pollFinished() {
            return (tc.test() && future != null && future.isDone());
        }

        public void sendMessage(Text message) {
            if (message != null && co != null)
                co.sendMessage(message);
        }

        public CompletableFuture<Void> getFuture() { return future; }
        public boolean hasName(String name) { return this.name.equals(name); }
    }

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

    private static class FailedHashMatchException extends Exception {
        public FailedHashMatchException() {
            super("Failed to verify the Packwiz Bootstrap hashes match. Please manually download the " +
                    "bootstrapper from: https://github.com/packwiz/packwiz-installer-bootstrap/releases " +
                    "and place in the main directory for your server.");
        }
    }

    private static CommandOutput getCommandOutput(CommandContext<ServerCommandSource> ctx) {
        return (ctx.getSource().getEntity() instanceof ServerPlayerEntity player)
                ? player : ctx.getSource().getServer();
    }
}
