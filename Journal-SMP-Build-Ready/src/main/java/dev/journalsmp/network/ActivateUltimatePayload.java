package dev.journalsmp.network;

import dev.journalsmp.JournalSmpMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ActivateUltimatePayload() implements CustomPacketPayload {
    public static final ActivateUltimatePayload INSTANCE = new ActivateUltimatePayload();
    public static final Type<ActivateUltimatePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(JournalSmpMod.MOD_ID, "activate_ultimate"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ActivateUltimatePayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
