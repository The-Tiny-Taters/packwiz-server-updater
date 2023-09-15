package ttt.packwizsu.config;

import org.jetbrains.annotations.NotNull;

import java.util.Properties;

public class ConfigHandler {

    private static ConfigFile configFile;
    private static final String header = "Packwiz serverside updater";

    public static void init()
    {
        Properties defaults = new Properties();
        defaults.setProperty("pack_toml", "");
        defaults.setProperty("trigger_update", "false");
        configFile = new ConfigFile("packwiz-server-updater", defaults, header);
    }

    public synchronized static void setValue(String key, String value)
    {
        if(configFile != null)
            configFile.setPropertyValue(key, value);
    }

    @NotNull
    public synchronized static String getValue(String key)
    {
        if(configFile != null) {
            return configFile.getPropertyValue(key);
        }
        else {
            return "";
        }
    }

    public synchronized static void update() {
        if(configFile != null)
        {
            configFile.save();
            configFile.load();
        }
    }

    public static void ResetToDefaults() {
        if(configFile != null)
        {
            configFile.setToDefaults();
        }
    }
}
