package ttt.packwizsu.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;
import ttt.packwizsu.config.ConfigHandler;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.minecraft.server.command.CommandManager.literal;

public final class DevCommands {

    private static final AtomicBoolean cooldownFinished = new AtomicBoolean(false);
    private static final SimpleCommandExceptionType RESTART_COOLDOWN_NOT_FINISHED = new SimpleCommandExceptionType(
            new LiteralText("Restart command cooldown period has not ended yet")
    );

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        if(Boolean.parseBoolean(ConfigHandler.getValue("enable_restarts"))) {
            startCooldown();
            dispatcher.register(literal("restart")
                    .requires(source -> source.hasPermissionLevel(2))
                    .executes(DevCommands::restart)
            );
        }
    }

    private static int restart(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        if(cooldownFinished.get()) {
            MinecraftServer server = ctx.getSource().getMinecraftServer();
            server.sendSystemMessage(new LiteralText("Restarting the server..."), ctx.getSource().getPlayer().getUuid());
            server.stop(false);
            return 1;
        }
        else {
            throw RESTART_COOLDOWN_NOT_FINISHED.create();
        }
    }

    private static void startCooldown() {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                cooldownFinished.set(true);
            }
        }, 60000 * Integer.parseInt(ConfigHandler.getValue("restart_cooldown")));
    }
}
