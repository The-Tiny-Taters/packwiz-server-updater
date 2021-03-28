package ttt.packwizsu;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import ttt.packwizsu.command.DevCommands;

public class Packwizsu implements DedicatedServerModInitializer {

    @Override
    public void onInitializeServer() {
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
            DevCommands.register(dispatcher);
        });
    }
}
