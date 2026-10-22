package dev.journalsmp.network;

import dev.journalsmp.JournalSmpMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record CooldownSyncPayload(int journalOrdinal, int remainingMillis) implements CustomPacketPayload {
    public static final Type<CooldownSyncPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(JournalSmpMod.MOD_ID, "cooldown_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CooldownSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, CooldownSyncPayload::journalOrdinal,
                    ByteBufCodecs.VAR_INT, CooldownSyncPayload::remainingMillis,
                    CooldownSyncPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
