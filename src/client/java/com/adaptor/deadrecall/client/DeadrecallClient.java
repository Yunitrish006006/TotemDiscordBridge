package com.adaptor.deadrecall.client;

import com.adaptor.deadrecall.network.DiscordConfigSyncPayload;
import com.adaptor.deadrecall.network.RequestDiscordConfigPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class DeadrecallClient implements ClientModInitializer {

    public static KeyMapping openDiscordConfigKey;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("deadrecall", "category")
        );

        // 預設無綁定鍵（GLFW_KEY_UNKNOWN = -1）
        openDiscordConfigKey = new KeyMapping(
                "key.deadrecall.discord_config",
                GLFW.GLFW_KEY_UNKNOWN,
                category
        );

        // 把快捷鍵插入 Options.keyMappings 陣列由 OptionsMixin 處理

        // 每 tick 檢查快捷鍵是否被按下
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openDiscordConfigKey.consumeClick()) {
                openDiscordConfigUi(mc);
            }
        });

        // 收到伺服器回傳的設定後，填入已開啟的畫面
        ClientPlayNetworking.registerGlobalReceiver(DiscordConfigSyncPayload.TYPE,
                (payload, context) -> {
                    net.minecraft.client.Minecraft mc = context.client();
                    mc.execute(() -> {
                        DiscordConfigScreen screen = DiscordConfigScreen.CURRENT;
                        if (screen != null) {
                            screen.applyServerConfig(payload.enabled(), payload.workerUrl(), payload.apiKey());
                        }
                    });
                });

        // 保留指令方式開啟
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommands.literal("discordbridgeui")
                        .executes(context -> {
                            net.minecraft.client.Minecraft mc = context.getSource().getClient();
                            // 延到下一 tick 才開畫面，避免聊天欄關閉時把剛開的 UI 蓋掉
                            mc.execute(() -> openDiscordConfigUi(mc));
                            return 1;
                        })));
    }

    private void openDiscordConfigUi(net.minecraft.client.Minecraft mc) {
        DiscordConfigScreen screen = new DiscordConfigScreen();
        mc.setScreenAndShow(screen);
        // 向伺服器請求目前設定
        if (ClientPlayNetworking.canSend(RequestDiscordConfigPayload.TYPE)) {
            ClientPlayNetworking.send(new RequestDiscordConfigPayload());
        }
    }
}
