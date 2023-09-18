package ttt.packwizsu;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ttt.packwizsu.command.DevCommands;
import ttt.packwizsu.config.ConfigHandler;

import java.io.File;

public class Packwizsu implements DedicatedServerModInitializer {

    public static final File GAME_DIR_FILE = FabricLoader.getInstance().getGameDir().toFile();
    private static ConfigHandler configHandler;

    @Override
    public void onInitializeServer() {
        configHandler = new ConfigHandler();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment)
                -> DevCommands.register(dispatcher));
    }

    public static ConfigHandler getConfigHandler() {
        return configHandler;
    }
}
