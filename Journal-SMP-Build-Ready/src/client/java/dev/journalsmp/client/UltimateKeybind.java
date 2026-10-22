
package dev.journalsmp.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.journalsmp.JournalSmpMod;
import dev.journalsmp.network.ActivateUltimatePayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class UltimateKeybind {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(JournalSmpMod.MOD_ID, "key_categories")
    );

    private static final KeyMapping ACTIVATE = KeyBindingHelper.registerKeyBinding(
            new KeyMapping(
                    "key.journalsmp.activate_ultimate",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_R,
                    CATEGORY
            )
    );

    private UltimateKeybind() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (ACTIVATE.consumeClick()) {
                if (client.player != null && !client.isPaused()) {
                    ClientPlayNetworking.send(new ActivateUltimatePayload());
                }
            }
        });
    }
}
