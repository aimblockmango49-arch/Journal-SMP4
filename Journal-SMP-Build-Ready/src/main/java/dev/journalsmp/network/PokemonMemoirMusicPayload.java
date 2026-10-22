package dev.journalsmp.network;

import dev.journalsmp.JournalSmpMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PokemonMemoirMusicPayload(boolean playing) implements CustomPacketPayload {
    public static final Type<PokemonMemoirMusicPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(JournalSmpMod.MOD_ID, "pokemon_memoir_music"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PokemonMemoirMusicPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, PokemonMemoirMusicPayload::playing,
                    PokemonMemoirMusicPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
