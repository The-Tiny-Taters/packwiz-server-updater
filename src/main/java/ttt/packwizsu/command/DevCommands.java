package ttt.packwizsu.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import ttt.packwizsu.config.ConfigHandler;

import static net.minecraft.server.command.CommandManager.literal;

public final class DevCommands {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("packwizsu")
                .then(literal("update")
                        .executes(DevCommands::restartAndUpdate)
                )
                .requires(source -> source.hasPermissionLevel(4))
        );
    }

    private static int restartAndUpdate(CommandContext<ServerCommandSource> ctx) {
        ConfigHandler.setValue("trigger_update", "true");
        ConfigHandler.update();

        var server = ctx.getSource().getServer();
        server.sendMessage(Text.of("Restarting and updating the server..."));
        server.stop(false);
        return 1;
    }
}
