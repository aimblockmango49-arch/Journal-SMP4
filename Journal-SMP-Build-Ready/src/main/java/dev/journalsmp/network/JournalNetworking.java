package dev.journalsmp.network;

import dev.journalsmp.ability.AbilityManager;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class JournalNetworking {
    private JournalNetworking() {
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(JumpscarePayload.TYPE, JumpscarePayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(CooldownSyncPayload.TYPE, CooldownSyncPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(PokemonMemoirMusicPayload.TYPE,
                PokemonMemoirMusicPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(ActivateUltimatePayload.TYPE, ActivateUltimatePayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ActivateUltimatePayload.TYPE, (payload, context) ->
                context.server().execute(() -> AbilityManager.activateHeldUltimate(context.player())));
    }
}
