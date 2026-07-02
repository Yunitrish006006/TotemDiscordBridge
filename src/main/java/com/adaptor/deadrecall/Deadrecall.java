package com.adaptor.deadrecall;

import com.adaptor.deadrecall.item.ModItems;
import com.adaptor.deadrecall.network.DiscordConfigSyncPayload;
import com.adaptor.deadrecall.network.RequestDiscordConfigPayload;
import com.adaptor.deadrecall.network.SaveDiscordConfigPayload;
import com.adaptor.deadrecall.recipe.ModRecipes;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class Deadrecall implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("DeadRecall");
    private static final int BOOKSHELF_REPLACE_INTERVAL_TICKS = 20;
    private static int bookshelfReplaceTicker = 0;

    @Override
    public void onInitialize() {
        // 註冊物品
        ModItems.registerModItems();
        ModRecipes.registerModRecipes();

        // 初始化 Discord 橋接
        DiscordBridge.init(FabricLoader.getInstance().getConfigDir());

        // 註冊自定義封包
        PayloadTypeRegistry.serverboundPlay().register(
                RequestDiscordConfigPayload.TYPE, RequestDiscordConfigPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
                SaveDiscordConfigPayload.TYPE, SaveDiscordConfigPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
                DiscordConfigSyncPayload.TYPE, DiscordConfigSyncPayload.CODEC);

        // 收到客戶端請求時，回傳目前設定
        ServerPlayNetworking.registerGlobalReceiver(RequestDiscordConfigPayload.TYPE,
                (payload, context) -> {
                    ServerPlayer player = context.player();
                    ServerPlayNetworking.send(player, new DiscordConfigSyncPayload(
                            DiscordBridge.isEnabled(),
                            DiscordBridge.getWorkerUrl(),
                            DiscordBridge.getApiKey()
                    ));
                });

        // 收到客戶端儲存請求時，更新設定
        ServerPlayNetworking.registerGlobalReceiver(SaveDiscordConfigPayload.TYPE,
                (payload, context) -> {
                    try {
                        DiscordBridge.updateConfig(payload.enabled(), payload.workerUrl(), payload.apiKey());
                        context.player().sendSystemMessage(Component.literal("§aDiscord Bridge 設定已更新"));
                    } catch (IllegalArgumentException e) {
                        context.player().sendSystemMessage(Component.literal("§c" + e.getMessage()));
                    } catch (Exception e) {
                        context.player().sendSystemMessage(Component.literal("§c更新失敗：" + e.getMessage()));
                        LOGGER.error("[DiscordBridge] 更新設定失敗", e);
                    }
                });

        // 註冊死亡背包功能 - 當玩家死亡時收集掉落物品
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayer player) {
                DeathLocationManager.setDeathLocation(player, player.blockPosition(), player.level());
                handlePlayerDeath(player);
            }
        });

        // 監聽玩家聊天訊息，轉發到 Discord
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            String username = sender.getName().getString();
            String content = message.decoratedContent().getString();
            DiscordBridge.sendChatMessage(username, content);
        });

        // 生存模式不允許持有一般書櫃：統一替換為書本（每個書櫃 3 本）
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            bookshelfReplaceTicker++;
            if (bookshelfReplaceTicker < BOOKSHELF_REPLACE_INTERVAL_TICKS) {
                return;
            }
            bookshelfReplaceTicker = 0;

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.getAbilities().instabuild) {
                    continue;
                }
                replaceVanillaBookshelfInInventory(player);
            }
        });

        // 註冊 /back 指令
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("back")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    DeathLocationManager.DeathLocation loc = DeathLocationManager.getDeathLocation(player);
                    if (loc == null) {
                        player.sendSystemMessage(Component.literal("§c沒有死亡座標可傳送！"));
                        return 0;
                    }

                    ServerLevel world = (ServerLevel) player.level();
                    if (world == null) {
                        player.sendSystemMessage(Component.literal("§c找不到死亡世界！"));
                        return 0;
                    }
                    player.teleportTo(world, loc.pos.getX() + 0.5, loc.pos.getY(), loc.pos.getZ() + 0.5, Relative.DELTA, player.getYRot(), player.getXRot(), false);
                    player.sendSystemMessage(Component.literal("§a已傳送回死亡地點！"));
                    DeathLocationManager.clearDeathLocation(player);
                    return 1;
                })
            );

            dispatcher.register(
                    Commands.literal("discordbridge")
                            .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                            .then(Commands.literal("reload")
                                    .executes(context -> {
                                        DiscordBridge.reload();
                                        context.getSource().sendSuccess(() -> Component.literal("§aDiscord Bridge 設定已重新載入"), true);
                                        return 1;
                                    }))
                            .then(Commands.literal("set")
                                    .then(Commands.argument("enabled", BoolArgumentType.bool())
                                            .then(Commands.argument("workerUrl", StringArgumentType.string())
                                                    .then(Commands.argument("apiKey", StringArgumentType.string())
                                                            .executes(context -> {
                                                                boolean enabled = BoolArgumentType.getBool(context, "enabled");
                                                                String workerUrl = StringArgumentType.getString(context, "workerUrl");
                                                                String apiKey = StringArgumentType.getString(context, "apiKey");
                                                                try {
                                                                    DiscordBridge.updateConfig(enabled, workerUrl, apiKey);
                                                                    context.getSource().sendSuccess(() -> Component.literal("§aDiscord Bridge 設定已更新"), true);
                                                                    return 1;
                                                                } catch (IllegalArgumentException e) {
                                                                    context.getSource().sendFailure(Component.literal("§c" + e.getMessage()));
                                                                    return 0;
                                                                } catch (Exception e) {
                                                                    context.getSource().sendFailure(Component.literal("§c更新失敗：" + e.getMessage()));
                                                                    LOGGER.error("[DiscordBridge] 更新設定失敗", e);
                                                                    return 0;
                                                                }
                                                            })))))
            );
        });
    }

    private void handlePlayerDeath(ServerPlayer player) {
        ServerLevel world = (ServerLevel) player.level();
        BlockPos deathPos = player.blockPosition();

        LOGGER.info("Player {} died at {}, starting death backpack collection", player.getName().getString(), deathPos);

        // 延遲 2 秒後收集掉落物品（給物品掉落時間）
        world.getServer().execute(() -> {
            world.getServer().execute(() -> {
                LOGGER.info("Collecting dropped items for player {} at {}", player.getName().getString(), deathPos);

                // 收集死亡地點附近的掉落物品
                AABB searchBox = new AABB(deathPos).inflate(10.0); // 搜尋範圍 10 格
                List<ItemEntity> droppedItems = world.getEntitiesOfClass(ItemEntity.class, searchBox, entity -> true);

                LOGGER.info("Found {} dropped items for player {}", droppedItems.size(), player.getName().getString());

                if (!droppedItems.isEmpty()) {
                    // 創建死亡背包
                    ItemStack deathBackpack = new ItemStack(ModItems.DEATH_BACKPACK);
                    List<ItemStack> collectedItems = new ArrayList<>();

                    // 收集物品並移除實體
                    for (ItemEntity itemEntity : droppedItems) {
                        collectedItems.add(itemEntity.getItem().copy());
                        itemEntity.discard(); // 移除實體
                        LOGGER.info("Collected item: {} x{}", itemEntity.getItem().getItem().getName(itemEntity.getItem()).getString(), itemEntity.getItem().getCount());
                    }

                    // 將物品存儲到背包中
                    if (!collectedItems.isEmpty()) {
                        deathBackpack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(collectedItems));

                        // 在死亡地點生成背包實體
                        ItemEntity backpackEntity = new ItemEntity(world,
                            deathPos.getX() + 0.5, deathPos.getY() + 0.5, deathPos.getZ() + 0.5,
                            deathBackpack);

                        // 設置物品所有者，防止立即消失
                        backpackEntity.setPickUpDelay(40); // 2 秒拾取延遲
                        // 將死亡背包標記為不可被傷害/摧毀（例如仙人掌傷害）的一個額外保護
                        backpackEntity.setUnlimitedLifetime();
                        world.addFreshEntity(backpackEntity);

                        LOGGER.info("Created death backpack for player {} with {} items at {}",
                            player.getName().getString(), collectedItems.size(), deathPos);

                        // 通知玩家
                        player.sendSystemMessage(Component.literal("§e你的物品已被收集到死亡背包中！"));
                    }
                } else {
                    LOGGER.info("No dropped items found for player {} at {}", player.getName().getString(), deathPos);
                }
            });
        });
    }

    private void replaceVanillaBookshelfInInventory(ServerPlayer player) {
        boolean changed = false;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(Items.BOOKSHELF)) {
                continue;
            }
            int booksToGive = stack.getCount() * 3;
            player.getInventory().setItem(slot, ItemStack.EMPTY);
            while (booksToGive > 0) {
                int batch = Math.min(booksToGive, Items.BOOK.getDefaultMaxStackSize());
                ItemStack books = new ItemStack(Items.BOOK, batch);
                if (!player.getInventory().add(books)) {
                    player.drop(books, false);
                }
                booksToGive -= batch;
            }
            changed = true;
        }
        if (changed) {
            player.getInventory().setChanged();
        }
    }
}
