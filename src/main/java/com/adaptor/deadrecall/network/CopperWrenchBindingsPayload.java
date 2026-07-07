package com.adaptor.deadrecall.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record CopperWrenchBindingsPayload(UUID golemId, boolean running, List<BindingEntry> bindings)
        implements CustomPacketPayload {

    public static final Type<CopperWrenchBindingsPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("deadrecall", "copper_wrench_bindings"));

    public record BindingEntry(String dimension, int x, int y, int z, String blockId, String itemId, boolean loaded, boolean available) {
    }

    public static final StreamCodec<FriendlyByteBuf, CopperWrenchBindingsPayload> CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUUID(payload.golemId());
                        buf.writeBoolean(payload.running());
                        buf.writeInt(payload.bindings().size());
                        for (BindingEntry binding : payload.bindings()) {
                            buf.writeUtf(binding.dimension(), 128);
                            buf.writeInt(binding.x());
                            buf.writeInt(binding.y());
                            buf.writeInt(binding.z());
                            buf.writeUtf(binding.blockId(), 128);
                            buf.writeUtf(binding.itemId(), 128);
                            buf.writeBoolean(binding.loaded());
                            buf.writeBoolean(binding.available());
                        }
                    },
                    buf -> new CopperWrenchBindingsPayload(
                            buf.readUUID(),
                            buf.readBoolean(),
                            readBindings(buf)
                    )
            );

    private static List<BindingEntry> readBindings(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<BindingEntry> bindings = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            bindings.add(new BindingEntry(
                    buf.readUtf(128),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readUtf(128),
                    buf.readUtf(128),
                    buf.readBoolean(),
                    buf.readBoolean()
            ));
        }
        return bindings;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
