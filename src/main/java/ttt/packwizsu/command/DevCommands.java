package ttt.packwizsu.command;

import com.moandjiezana.toml.Toml;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
import static ttt.packwizsu.Packwizsu.*;

public final class DevCommands {

    private static final MutableText UPDATE_START = Text.literal("Updating Packwiz...").formatted(Formatting.GREEN);
    private static final MutableText UPDATED_TOML_LINK = Text.literal("Successfully linked a Packwiz modpack! Use /packwizsu update for the changes to take effect").formatted(Formatting.GREEN);

    private static final SimpleCommandExceptionType FILE_UPDATE_FAILED = new SimpleCommandExceptionType(Text.literal("Failed to update the packwiz-server-updater.properties file within the root directory of the server"));
    private static final SimpleCommandExceptionType UPDATE_IN_PROGRESS_ERROR = new SimpleCommandExceptionType(Text.literal("Packwiz update is already in progress"));
    private static final SimpleCommandExceptionType NO_PACK_TOML = new SimpleCommandExceptionType(Text.literal("There is no pack.toml link to update from. Add this using /packwizsu link [url]"));
    private static final SimpleCommandExceptionType NO_BOOTSTRAPPER = new SimpleCommandExceptionType(Text.literal("packwiz-installer-bootstrap.jar wasn't found within the root directory of the server"));
    private static final SimpleCommandExceptionType PACKWIZ_UPDATE_FAILED = new SimpleCommandExceptionType(Text.literal("Failed to update Packwiz. Check the server console for errors"));
    private static final SimpleCommandExceptionType PROCESS_INTERRUPTED = new SimpleCommandExceptionType(Text.literal("Failed to update Packwiz. Process was interrupted. Check the server console for details"));
    private static final SimpleCommandExceptionType FILE_HANDLING_ERROR = new SimpleCommandExceptionType(Text.literal("Failed to update Packwiz. Read/write process failed. Check the server console for details"));

    private static final AtomicBoolean UPDATE_IN_PROGRESS = new AtomicBoolean(false);
    private static final Set<String> PACK_TOML_REQUIRED_KEYS = Set.of( "name", "author", "version", "index");

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
        if(GAME_DIR_FILE.exists()) {
            String packTomlLink = getConfigHandler().getValue("pack_toml");
            var bootstrapFile = new File("packwiz-installer-bootstrap.jar");

            if(bootstrapFile.exists()) {
                if(packTomlLink.contains("pack.toml")) {
                    getCommandOutput(ctx).sendMessage(UPDATE_START);

                    if(UPDATE_IN_PROGRESS.compareAndSet(false, true)) {
                        try {
                            updatePackwiz(packTomlLink);
                            ctx.getSource().getServer().stop(false);
                        } catch (Exception e) {
                            var cause = e.getCause();
                            cause.printStackTrace();

                            if (cause instanceof InterruptedException)
                                throw PROCESS_INTERRUPTED.create();
                            else if (cause instanceof PackTomlURLException ptfe)
                                throw new SimpleCommandExceptionType(Text.literal(ptfe.getMessage())).create();
                            else if (cause instanceof ProcessExitCodeException pece)
                                throw new SimpleCommandExceptionType(Text.literal(pece.getMessage())).create();
                            else if (cause instanceof IOException)
                                throw FILE_HANDLING_ERROR.create();
                            else
                                throw PACKWIZ_UPDATE_FAILED.create();
                        } finally {
                            UPDATE_IN_PROGRESS.set(false);
                        }
                    } else
                        throw UPDATE_IN_PROGRESS_ERROR.create();
                } else
                    throw NO_PACK_TOML.create();
            } else
                throw NO_BOOTSTRAPPER.create();
        }
        return Command.SINGLE_SUCCESS;
    }

    private static void restartServer(String startupArguments) {

    }

    private static void updatePackwiz(@NotNull final String packTomllink) throws Exception {
        final String[] command = new String[] { "java", "-jar", "packwiz-installer-bootstrap.jar", "-g", "-s", "server", packTomllink };

        var packwizFuture = CompletableFuture.runAsync(() -> {
            try {
                testPackTomlLink(packTomllink);

                var process = new ProcessBuilder(command).inheritIO().start();
                try (var bufferedReader = process.inputReader()) {
                    bufferedReader.lines().forEach(LOGGER::info);
                }
                int exitCode = process.waitFor();
                if (exitCode !=0)
                    throw new ProcessExitCodeException("Packwiz update process failed with exit code: " + exitCode);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        try {
            packwizFuture.join();
        } catch (CompletionException ce) {
            if (ce.getCause() instanceof Exception e)
                throw e;
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

    private static CommandOutput getCommandOutput(CommandContext<ServerCommandSource> ctx) {
        if(ctx.getSource().getEntity() instanceof ServerPlayerEntity player)
            return player;
        else
            return ctx.getSource().getServer();
    }

    private static class PackTomlURLException extends Exception {
        private static final String EXCEPTION_START = "Failed to read the Packwiz pack.toml file. ";

        public PackTomlURLException(String message) {
            super(EXCEPTION_START + message);
        }
    }

    private static class ProcessExitCodeException extends RuntimeException {
        public ProcessExitCodeException(String message) {
            super(message);
        }
    }
}
