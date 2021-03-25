package ttt.packwizsu.config;

import ttt.packwizsu.Packwizsu;

import java.io.*;
import java.nio.file.Files;
import java.util.Properties;

public class ConfigFile {

    private final File file;
    private final Properties defaults;
    private Properties properties;
    private final String headerComments;

    ConfigFile(String title, Properties defaults, String headerComments) {
        this.defaults = defaults;
        this.headerComments = headerComments;

        this.file = new File(Packwizsu.GAME_DIR_FILE.toString() + "/" + title + ".properties");

        try {
            if(Files.notExists(Packwizsu.GAME_DIR_FILE.toPath())) { // Create the main config directory if it doesn't exist
                Files.createDirectory(Packwizsu.GAME_DIR_FILE.toPath());
            }

            if(file.createNewFile()) { // Create file & set to defaults if it doesn't exist
                setToDefaults();
                save();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        load();
    }

    void save()
    {
        try {
            OutputStream output = new FileOutputStream(file);
            properties.store(output, headerComments);
            output.close();
        } catch (IOException e){
            e.printStackTrace();
        }
    }

    void load() {
        try {
            if(file.createNewFile()) { // If no file currently exists
                setToDefaults();
            } else {
                InputStream input = new FileInputStream(file);
                properties = new Properties();
                properties.load(input);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void setToDefaults()
    {
        properties = defaults;
    }

    void setPropertyValue(String key, String value) { properties.setProperty(key, value); }

    void removePropertyValue(String key) { properties.remove(key); }

    String getPropertyValue(String key) { return properties.getProperty(key); }
}
