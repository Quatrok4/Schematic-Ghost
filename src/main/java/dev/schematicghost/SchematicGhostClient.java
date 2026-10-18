package dev.schematicghost;

import dev.schematicghost.client.GhostCommands;
import dev.schematicghost.client.GhostRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SchematicGhostClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("schematic-ghost");

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                GhostCommands.register(dispatcher));
        GhostRenderer.init();
        LOGGER.info("Schematic Ghost carregado (client only)");
    }
}
