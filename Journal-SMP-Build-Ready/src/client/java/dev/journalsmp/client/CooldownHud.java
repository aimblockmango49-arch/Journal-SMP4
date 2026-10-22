package dev.journalsmp.client;

import dev.journalsmp.JournalSmpMod;
import dev.journalsmp.network.CooldownSyncPayload;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

public final class CooldownHud {
    private static int journalOrdinal = -1;
    private static int remainingMillis;

    private CooldownHud() {
    }

    public static void register() {
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(JournalSmpMod.MOD_ID, "ultimate_cooldown"),
                (graphics, tickCounter) -> render(graphics));
    }

    public static void update(CooldownSyncPayload payload) {
        journalOrdinal = payload.journalOrdinal();
        remainingMillis = Math.max(0, payload.remainingMillis());
    }

    private static void render(GuiGraphics graphics) {
        if (journalOrdinal < 0) {
            return;
        }

        long remaining = remainingMillis;
        String text = remaining == 0L
                ? "ULTIMATE READY"
                : "ULTIMATE: " + ((remaining + 999L) / 1_000L) + "s REMAINING";

        Minecraft client = Minecraft.getInstance();
        int color = remaining == 0L ? 0x55FF55 : 0xFFFFFF;
        graphics.drawCenteredString(client.font, text, graphics.guiWidth() / 2,
                graphics.guiHeight() - 52, color);
    }
}
