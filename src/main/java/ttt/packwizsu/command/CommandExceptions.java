package ttt.packwizsu.command;

import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.text.Text;

class CommandExceptions {
     static final SimpleCommandExceptionType FILE_UPDATE_FAILED = new SimpleCommandExceptionType(Text.literal("Failed to update the packwiz-server-updater.properties file within the root directory of the server"));
     static final SimpleCommandExceptionType UPDATE_IN_PROGRESS_ERROR = new SimpleCommandExceptionType(Text.literal("Packwiz update is already in progress"));
     static final SimpleCommandExceptionType NO_PACK_TOML = new SimpleCommandExceptionType(Text.literal("There is no pack.toml link to update from. Add this using /packwizsu link [url]"));
     static final SimpleCommandExceptionType NO_BOOTSTRAPPER = new SimpleCommandExceptionType(Text.literal("packwiz-installer-bootstrap.jar wasn't found within the root directory of the server"));
     static final SimpleCommandExceptionType DIRECTORY_SECURITY_ERROR = new SimpleCommandExceptionType(Text.literal("Packwiz server updater does not have permission to access the game directory and modify files"));
     static final SimpleCommandExceptionType DIRECTORY_BLANK_ERROR = new SimpleCommandExceptionType(Text.literal("Failed to update Packwiz. Game directory doesn't exist or has changed since server startup. Check the server console for details"));
}
