package dev.totem.discord.bootstrap;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.totem.discord.transport.DiscordAccountLinkResult;
import dev.totem.discord.transport.DiscordAccountLinkService;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Player-facing, client-mod-free commands for private Discord account linking. */
final class DiscordAccountLinkCommands {
    private DiscordAccountLinkCommands() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("discordlink")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .then(Commands.literal("verify")
                        .then(Commands.argument("code", StringArgumentType.word())
                                .executes(context -> start(
                                        context.getSource(),
                                        DiscordAccountLinkService.verify(
                                                context.getSource().getPlayerOrException().getUUID(),
                                                context.getSource().getPlayerOrException().getGameProfile().name(),
                                                StringArgumentType.getString(context, "code")
                                        ),
                                        "正在安全驗證 Discord 綁定…"
                                ))))
                .then(Commands.literal("status")
                        .executes(context -> start(
                                context.getSource(),
                                DiscordAccountLinkService.status(
                                        context.getSource().getPlayerOrException().getUUID()),
                                "正在查詢 Discord 綁定狀態…"
                        )))
                .then(Commands.literal("unlink")
                        .executes(context -> start(
                                context.getSource(),
                                DiscordAccountLinkService.unlink(
                                        context.getSource().getPlayerOrException().getUUID()),
                                "正在解除 Discord 綁定…"
                        ))));
    }

    private static int start(
            CommandSourceStack source,
            CompletableFuture<DiscordAccountLinkResult> request,
            String progressMessage
    ) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        UUID playerId = player.getUUID();
        player.sendSystemMessage(Component.literal(progressMessage).withStyle(ChatFormatting.GRAY));
        request.whenComplete((result, error) -> server.execute(() -> {
            ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(playerId);
            if (onlinePlayer == null) return;
            DiscordAccountLinkResult safeResult = error == null && result != null
                    ? result
                    : new DiscordAccountLinkResult(
                            DiscordAccountLinkResult.Status.UNAVAILABLE,
                            "",
                            0
                    );
            onlinePlayer.sendSystemMessage(message(safeResult));
        }));
        return 1;
    }

    static Component message(DiscordAccountLinkResult result) {
        return switch (result.status()) {
            case LINKED -> Component.literal(result.discordName().isBlank()
                            ? "✅ Discord 帳號已綁定。"
                            : "✅ Discord 帳號已綁定：" + result.discordName())
                    .withStyle(ChatFormatting.GREEN);
            case UNLINKED -> Component.literal(result.discordName().isBlank()
                            ? "✅ Discord 帳號綁定已解除。"
                            : "✅ 已解除 Discord 帳號綁定：" + result.discordName())
                    .withStyle(ChatFormatting.GREEN);
            case NOT_LINKED -> Component.literal("ℹ️ 目前沒有綁定 Discord 帳號。")
                    .withStyle(ChatFormatting.YELLOW);
            case INVALID_CODE -> Component.literal("❌ 驗證碼無效或已過期，請回 Discord 重新執行 /bind。")
                    .withStyle(ChatFormatting.RED);
            case INVALID_REQUEST -> Component.literal("❌ 驗證碼格式不正確。請直接複製 Discord 私密回覆中的 8 碼驗證碼。")
                    .withStyle(ChatFormatting.RED);
            case MINECRAFT_NAME_MISMATCH -> Component.literal("❌ 目前登入的 Minecraft 帳號與 Discord /bind 填寫的名稱不符。")
                    .withStyle(ChatFormatting.RED);
            case MINECRAFT_ALREADY_BOUND -> Component.literal("❌ 這個 Minecraft 帳號已綁定其他 Discord 帳號。")
                    .withStyle(ChatFormatting.RED);
            case RATE_LIMITED -> Component.literal(result.retryAfterSeconds() > 0
                            ? "⏳ 驗證嘗試過多，請在 " + result.retryAfterSeconds() + " 秒後再試。"
                            : "⏳ 驗證嘗試過多，請稍後再試。")
                    .withStyle(ChatFormatting.RED);
            case BUSY -> Component.literal("⏳ 前一個 Discord 帳號操作仍在處理中。")
                    .withStyle(ChatFormatting.YELLOW);
            case UNAVAILABLE -> Component.literal("❌ Discord 帳號服務目前未啟用或暫時無法連線。")
                    .withStyle(ChatFormatting.RED);
        };
    }
}
