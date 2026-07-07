package com.adaptor.deadrecall.client;

import com.adaptor.deadrecall.network.CopperGolemOperationPayload;
import com.adaptor.deadrecall.network.CopperWrenchBindingsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CopperWrenchBindingsScreen extends Screen {
    public static CopperWrenchBindingsScreen CURRENT = null;

    private static final int PANEL_WIDTH = 430;
    private static final int PANEL_HEIGHT = 252;
    private static final int PANEL_PADDING = 16;
    private static final int HEADER_HEIGHT = 38;
    private static final int CARD_HEIGHT = 42;
    private static final int CARD_GAP = 6;
    private static final int FOOTER_HEIGHT = 30;

    private UUID golemId;
    private boolean running;
    private List<CopperWrenchBindingsPayload.BindingEntry> bindings;
    private Button operationButton;
    private Button doneButton;
    private int scrollOffset = 0;

    public CopperWrenchBindingsScreen(CopperWrenchBindingsPayload payload) {
        super(Component.translatable("container.deadrecall.copper_wrench.bindings"));
        this.golemId = payload.golemId();
        this.running = payload.running();
        this.bindings = new ArrayList<>(payload.bindings());
        CURRENT = this;
    }

    @Override
    public void removed() {
        super.removed();
        if (CURRENT == this) {
            CURRENT = null;
        }
    }

    public boolean isFor(UUID targetGolemId) {
        return this.golemId.equals(targetGolemId);
    }

    public void applyPayload(CopperWrenchBindingsPayload payload) {
        this.golemId = payload.golemId();
        this.running = payload.running();
        this.bindings = new ArrayList<>(payload.bindings());
        this.scrollOffset = Math.min(this.scrollOffset, getMaxScroll());
        updateOperationButton();
    }

    @Override
    protected void init() {
        int panelX = panelX();
        int panelY = panelY();

        this.operationButton = Button.builder(operationButtonText(), button -> toggleOperation())
                .bounds(panelX + PANEL_WIDTH - PANEL_PADDING - 74, panelY + 10, 74, 20)
                .build();
        this.addRenderableWidget(this.operationButton);

        this.doneButton = Button.builder(Component.translatable("gui.done"), button -> this.onClose())
                .bounds(panelX + PANEL_WIDTH / 2 - 45, panelY + PANEL_HEIGHT - 24, 90, 20)
                .build();
        this.addRenderableWidget(this.doneButton);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        extractor.fill(0, 0, this.width, this.height, 0xA0000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        int panelX = panelX();
        int panelY = panelY();
        int listX = panelX + PANEL_PADDING;
        int listY = panelY + HEADER_HEIGHT + 8;
        int listWidth = PANEL_WIDTH - PANEL_PADDING * 2;
        int listHeight = PANEL_HEIGHT - HEADER_HEIGHT - FOOTER_HEIGHT - 16;

        extractor.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xE0181818);
        extractor.outline(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 0xFF6A6A6A);
        extractor.text(this.font, this.title, panelX + PANEL_PADDING, panelY + 14, 0xFFFFFFFF);
        extractor.text(this.font, operationStatusText(), panelX + PANEL_WIDTH - PANEL_PADDING - 150, panelY + 16, operationStatusColor());

        extractor.fill(listX, listY, listX + listWidth, listY + listHeight, 0x80101010);
        extractor.outline(listX, listY, listWidth, listHeight, 0xFF3A3A3A);

        if (this.bindings.isEmpty()) {
            extractor.centeredText(this.font, Component.translatable("message.deadrecall.copper_wrench.binding_list_empty"),
                    panelX + PANEL_WIDTH / 2, listY + listHeight / 2 - 4, 0xFFB8B8B8);
        } else {
            extractor.enableScissor(listX + 1, listY + 1, listX + listWidth - 1, listY + listHeight - 1);
            for (int i = 0; i < this.bindings.size(); i++) {
                int cardY = listY + 7 + i * (CARD_HEIGHT + CARD_GAP) - this.scrollOffset;
                if (cardY + CARD_HEIGHT < listY || cardY > listY + listHeight) {
                    continue;
                }
                drawBindingCard(extractor, this.bindings.get(i), i, listX + 8, cardY, listWidth - 16, mouseX, mouseY);
            }
            extractor.disableScissor();
        }

        if (getMaxScroll() > 0) {
            drawScrollBar(extractor, listX + listWidth - 6, listY + 4, listHeight - 8);
        }

        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxScroll = getMaxScroll();
        if (maxScroll <= 0) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        if (verticalAmount < 0) {
            this.scrollOffset = Math.min(maxScroll, this.scrollOffset + 14);
            return true;
        }
        if (verticalAmount > 0) {
            this.scrollOffset = Math.max(0, this.scrollOffset - 14);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void toggleOperation() {
        this.running = !this.running;
        updateOperationButton();
        if (ClientPlayNetworking.canSend(CopperGolemOperationPayload.TYPE)) {
            ClientPlayNetworking.send(new CopperGolemOperationPayload(this.golemId, this.running));
        }
    }

    private void updateOperationButton() {
        if (this.operationButton != null) {
            this.operationButton.setMessage(operationButtonText());
        }
    }

    private Component operationButtonText() {
        return Component.translatable(this.running
                ? "message.deadrecall.copper_wrench.action_stop"
                : "message.deadrecall.copper_wrench.action_start");
    }

    private Component operationStatusText() {
        return Component.translatable(this.running
                ? "message.deadrecall.copper_wrench.operation_running"
                : "message.deadrecall.copper_wrench.operation_stopped");
    }

    private int operationStatusColor() {
        return this.running ? 0xFF64D26D : 0xFFFF6B6B;
    }

    private void drawBindingCard(GuiGraphicsExtractor extractor, CopperWrenchBindingsPayload.BindingEntry entry, int index,
                                 int x, int y, int width, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + CARD_HEIGHT;
        int borderColor = entry.available() ? 0xFF4C8A53 : entry.loaded() ? 0xFF9A4D4D : 0xFF777777;
        extractor.fill(x, y, x + width, y + CARD_HEIGHT, hovered ? 0xC02A2A2A : 0xB0222222);
        extractor.outline(x, y, width, CARD_HEIGHT, borderColor);

        extractor.fill(x + 8, y + 8, x + 28, y + 28, 0xB0000000);
        extractor.item(iconStack(entry.itemId()), x + 10, y + 10);

        String title = (index + 1) + ". " + entry.blockId();
        extractor.text(this.font, trimToWidth(title, width - 142), x + 38, y + 8, 0xFFFFFFFF);
        extractor.text(this.font, entry.dimension(), x + 38, y + 22, 0xFFB8B8B8);
        extractor.text(this.font, entry.x() + ", " + entry.y() + ", " + entry.z(), x + width - 112, y + 8, 0xFFE0E0E0);
        extractor.text(this.font, statusText(entry), x + width - 112, y + 22, statusColor(entry));

        if (hovered) {
            extractor.setComponentTooltipForNextFrame(this.font, List.of(
                    Component.literal(entry.blockId()),
                    Component.translatable("message.deadrecall.copper_wrench.binding_dimension", entry.dimension()),
                    Component.translatable("message.deadrecall.copper_wrench.binding_position", entry.x(), entry.y(), entry.z()),
                    Component.translatable("message.deadrecall.copper_wrench.binding_status", Component.literal(statusText(entry)))
            ), mouseX, mouseY);
        }
    }

    private void drawScrollBar(GuiGraphicsExtractor extractor, int x, int y, int height) {
        int contentHeight = getContentHeight();
        int listHeight = getListHeight();
        int thumbHeight = Math.max(18, height * listHeight / Math.max(listHeight, contentHeight));
        int thumbTravel = Math.max(1, height - thumbHeight);
        int thumbY = y + thumbTravel * this.scrollOffset / Math.max(1, getMaxScroll());
        extractor.fill(x, y, x + 3, y + height, 0x80333333);
        extractor.fill(x, thumbY, x + 3, thumbY + thumbHeight, 0xFF9A9A9A);
    }

    private ItemStack iconStack(String itemId) {
        Identifier identifier = Identifier.tryParse(itemId);
        Item item = identifier == null
                ? Items.BARRIER
                : BuiltInRegistries.ITEM.getOptional(identifier).orElse(Items.BARRIER);
        if (item == Items.AIR) {
            item = Items.BARRIER;
        }
        return new ItemStack(item);
    }

    private String statusText(CopperWrenchBindingsPayload.BindingEntry entry) {
        if (!entry.loaded()) {
            return Component.translatable("message.deadrecall.copper_wrench.binding_status_unloaded").getString();
        }
        return Component.translatable(entry.available()
                ? "message.deadrecall.copper_wrench.binding_status_available"
                : "message.deadrecall.copper_wrench.binding_status_unavailable").getString();
    }

    private int statusColor(CopperWrenchBindingsPayload.BindingEntry entry) {
        if (!entry.loaded()) {
            return 0xFFB8B8B8;
        }
        return entry.available() ? 0xFF64D26D : 0xFFFF6B6B;
    }

    private int getListHeight() {
        return PANEL_HEIGHT - HEADER_HEIGHT - FOOTER_HEIGHT - 16;
    }

    private int getContentHeight() {
        return this.bindings.size() * (CARD_HEIGHT + CARD_GAP) + 14;
    }

    private int getMaxScroll() {
        return Math.max(0, getContentHeight() - getListHeight());
    }

    private int panelX() {
        return this.width / 2 - PANEL_WIDTH / 2;
    }

    private int panelY() {
        return this.height / 2 - PANEL_HEIGHT / 2;
    }

    private String trimToWidth(String text, int maxWidth) {
        return this.font.plainSubstrByWidth(text, Math.max(0, maxWidth));
    }
}
