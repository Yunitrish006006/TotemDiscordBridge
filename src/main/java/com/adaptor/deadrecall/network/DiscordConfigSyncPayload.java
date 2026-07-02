package com.adaptor.deadrecall.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 伺服器 → 客戶端：回傳目前 Discord Bridge 設定
 */
public record DiscordConfigSyncPayload(boolean enabled, String workerUrl, String apiKey)
        implements CustomPacketPayload {

    public static final Type<DiscordConfigSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("deadrecall", "discord_config_sync"));

    public static final StreamCodec<FriendlyByteBuf, DiscordConfigSyncPayload> CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeBoolean(payload.enabled());
                        buf.writeUtf(payload.workerUrl());
                        buf.writeUtf(payload.apiKey());
                    },
                    buf -> new DiscordConfigSyncPayload(
                            buf.readBoolean(),
                            buf.readUtf(),
                            buf.readUtf()
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
