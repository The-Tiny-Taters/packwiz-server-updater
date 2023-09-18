package ttt.packwizsu.config;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Properties;

public class ConfigHandler {

    private static final String HEADER = "Packwiz serverside updater";
    private final ConfigFile configFile;

    public ConfigHandler() {
        var defaultProperties = new Properties();
        defaultProperties.setProperty("pack_toml", "");
        configFile = new ConfigFile("packwiz-server-updater", defaultProperties, HEADER);
    }

    public synchronized void setValue(String key, String value) {
        configFile.setPropertyValue(key, value);
    }

    @NotNull
    public synchronized String getValue(String key) {
        return configFile.getPropertyValue(key);
    }

    public synchronized void update() throws Exception {
        configFile.save();
        configFile.load();
    }

    public void resetToDefaults() {
        configFile.setToDefaults();
    }
}
