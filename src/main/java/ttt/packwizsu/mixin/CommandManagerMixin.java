package ttt.packwizsu.mixin;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ttt.packwizsu.command.DevCommands;

@Mixin(CommandManager.class)
public abstract class CommandManagerMixin {
    @Shadow @Final
    private CommandDispatcher<ServerCommandSource> dispatcher;

    /**
     * Wait an inject in a constructor?
     * This is a new addition to Fabric's fork of mixin.
     * If you are not using fabric's fork of mixin this will fail.
     *
     * @reason Add commands before ambiguities are calculated.
     */
    @Inject(at = @At(value = "INVOKE", target = "Lcom/mojang/brigadier/CommandDispatcher;setConsumer(Lcom/mojang/brigadier/ResultConsumer;)V", remap = false), method = "<init>")
    private void addCommands(CommandManager.RegistrationEnvironment environment, CommandRegistryAccess registryAccess, CallbackInfo ci) {
        DevCommands.register(this.dispatcher);
    }
}
