package ttt.packwizsu;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttt.packwizsu.command.DevCommands;
import ttt.packwizsu.config.ConfigHandler;

import java.io.File;

public class Packwizsu implements DedicatedServerModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("packwizsu");
    public static final File GAME_DIR_FILE = FabricLoader.getInstance().getGameDir().toFile();
    private static ConfigHandler configHandler;

    @Override
    public void onInitializeServer() {
        configHandler = new ConfigHandler();

        if(configHandler.getValue("pack_toml").isEmpty())
            LOGGER.info("Packwiz Server Updater loaded without a pack.toml file to update from");
        else
            LOGGER.info("Packwiz Server Updater loaded with pack.toml link: " + configHandler.getValue("pack_toml"));
    }

    public static ConfigHandler getConfigHandler() {
        return configHandler;
    }
}
