package ru.liko.pjmbasemod.common.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.faction.FactionSelectionSnapshot;

public record OpenFactionSelectionPacket(FactionSelectionSnapshot snapshot) implements CustomPacketPayload {

    public static final Type<OpenFactionSelectionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "open_faction_selection"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenFactionSelectionPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> FactionSelectionSnapshot.write(buf, packet.snapshot()),
            buf -> new OpenFactionSelectionPacket(FactionSelectionSnapshot.read(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
