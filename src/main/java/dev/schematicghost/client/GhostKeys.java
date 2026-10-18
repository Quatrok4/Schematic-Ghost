package dev.schematicghost.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/** Tecla G (configurável em Opções > Controles) abre o painel do mod. */
public final class GhostKeys {
    private static KeyMapping openPanel;

    private GhostKeys() {}

    public static void init() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("schematic-ghost", "main"));

        openPanel = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.schematic-ghost.open_panel",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_G,
                category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openPanel.consumeClick()) {
                if (client.player != null) {
                    client.gui.setScreen(new GhostScreen());
                }
            }
        });
    }
}
