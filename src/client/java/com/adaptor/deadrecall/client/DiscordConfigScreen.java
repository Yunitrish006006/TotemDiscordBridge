package com.adaptor.deadrecall.client;

import com.adaptor.deadrecall.network.SaveDiscordConfigPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class DiscordConfigScreen extends Screen {
    /** 目前開啟的畫面實例，供封包回調使用 */
    public static DiscordConfigScreen CURRENT = null;

    private EditBox workerUrlField;
    private EditBox apiKeyField;
    private boolean enabled = false;
    private Button enabledButton;

    private static final Component DESC_LINE1 = Component.literal("§7此介面用於設定 Discord Bridge 功能：");
    private static final Component DESC_LINE2 = Component.literal("§7將 Minecraft 伺服器聊天訊息同步到 Discord 頻道。");
    private static final Component DESC_LINE3 = Component.literal("§7需要填入 Cloudflare Worker 的 URL 與 API Key。");

    public DiscordConfigScreen() {
        super(Component.literal("Discord Bridge 設定"));
        CURRENT = this;
    }

    @Override
    public void removed() {
        super.removed();
        if (CURRENT == this) {
            CURRENT = null;
        }
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = this.height / 2 - 50;

        this.workerUrlField = new EditBox(this.font, centerX - 150, y, 300, 20, Component.literal("Worker URL"));
        this.workerUrlField.setHint(Component.literal("https://your-worker.workers.dev"));
        this.workerUrlField.setMaxLength(2048);
        this.addRenderableWidget(this.workerUrlField);

        this.apiKeyField = new EditBox(this.font, centerX - 150, y + 30, 300, 20, Component.literal("API Key"));
        this.apiKeyField.setHint(Component.literal("mc_ak_xxx"));
        this.apiKeyField.setMaxLength(512);
        this.addRenderableWidget(this.apiKeyField);

        this.enabledButton = Button.builder(Component.literal("啟用: 否"), button -> {
                    this.enabled = !this.enabled;
                    button.setMessage(Component.literal(this.enabled ? "啟用: 是" : "啟用: 否"));
                })
                .bounds(centerX - 150, y + 60, 145, 20)
                .build();
        this.addRenderableWidget(this.enabledButton);

        this.addRenderableWidget(Button.builder(Component.literal("儲存到伺服器"), button -> saveToServer())
                .bounds(centerX + 5, y + 60, 145, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("取消"), button -> this.onClose())
                .bounds(centerX - 50, y + 90, 100, 20)
                .build());

        this.setInitialFocus(this.workerUrlField);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        int topY = this.height / 2 - 50;

        // 標題
        extractor.centeredText(this.font, this.title, centerX, topY - 70, 0xFFFFFF);

        // 說明文字
        extractor.centeredText(this.font, DESC_LINE1, centerX, topY - 52, 0xAAAAAA);
        extractor.centeredText(this.font, DESC_LINE2, centerX, topY - 40, 0xAAAAAA);
        extractor.centeredText(this.font, DESC_LINE3, centerX, topY - 28, 0xAAAAAA);

        // 欄位標籤
        extractor.text(this.font, Component.literal("Worker URL"), centerX - 150, topY - 11, 0xA0A0A0);
        extractor.text(this.font, Component.literal("API Key"), centerX - 150, topY + 19, 0xA0A0A0);
    }

    /**
     * 伺服器回傳設定後，填入欄位
     */
    public void applyServerConfig(boolean serverEnabled, String serverWorkerUrl, String serverApiKey) {
        this.enabled = serverEnabled;
        if (this.enabledButton != null) {
            this.enabledButton.setMessage(Component.literal(this.enabled ? "啟用: 是" : "啟用: 否"));
        }
        if (this.workerUrlField != null) {
            this.workerUrlField.setValue(serverWorkerUrl);
        }
        if (this.apiKeyField != null) {
            this.apiKeyField.setValue(serverApiKey);
        }
    }

    private void saveToServer() {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        String workerUrl = this.workerUrlField.getValue().trim();
        String apiKey = this.apiKeyField.getValue().trim();
        // 使用自定義封包避免文字指令的字元長度限制
        ClientPlayNetworking.send(new SaveDiscordConfigPayload(this.enabled, workerUrl, apiKey));
        this.onClose();
    }

}
