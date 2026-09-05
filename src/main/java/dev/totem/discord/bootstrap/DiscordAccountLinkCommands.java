package dev.totem.discord.bootstrap;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.totem.discord.domain.DiscordLocalizationService;
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
                                        "discord.deadrecall.account_link.verifying"
                                ))))
                .then(Commands.literal("status")
                        .executes(context -> start(
                                context.getSource(),
                                DiscordAccountLinkService.status(
                                        context.getSource().getPlayerOrException().getUUID()),
                                "discord.deadrecall.account_link.checking_status"
                        )))
                .then(Commands.literal("unlink")
                        .executes(context -> start(
                                context.getSource(),
                                DiscordAccountLinkService.unlink(
                                        context.getSource().getPlayerOrException().getUUID()),
                                "discord.deadrecall.account_link.unlinking"
                        ))));
    }

    private static int start(
            CommandSourceStack source,
            CompletableFuture<DiscordAccountLinkResult> request,
            String progressTranslationKey
    ) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        UUID playerId = player.getUUID();
        player.sendSystemMessage(Component.literal(
                DiscordLocalizationService.translate(progressTranslationKey)
        ).withStyle(ChatFormatting.GRAY));
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
                            ? DiscordLocalizationService.translate("discord.deadrecall.account_link.linked")
                            : DiscordLocalizationService.format(
                                    "discord.deadrecall.account_link.linked.named",
                                    result.discordName()
                            ))
                    .withStyle(ChatFormatting.GREEN);
            case UNLINKED -> Component.literal(result.discordName().isBlank()
                            ? DiscordLocalizationService.translate("discord.deadrecall.account_link.unlinked")
                            : DiscordLocalizationService.format(
                                    "discord.deadrecall.account_link.unlinked.named",
                                    result.discordName()
                            ))
                    .withStyle(ChatFormatting.GREEN);
            case NOT_LINKED -> Component.literal(DiscordLocalizationService.translate("discord.deadrecall.account_link.not_linked"))
                    .withStyle(ChatFormatting.YELLOW);
            case INVALID_CODE -> Component.literal(DiscordLocalizationService.translate("discord.deadrecall.account_link.invalid_code"))
                    .withStyle(ChatFormatting.RED);
            case INVALID_REQUEST -> Component.literal(DiscordLocalizationService.translate("discord.deadrecall.account_link.invalid_request"))
                    .withStyle(ChatFormatting.RED);
            case MINECRAFT_NAME_MISMATCH -> Component.literal(DiscordLocalizationService.translate("discord.deadrecall.account_link.name_mismatch"))
                    .withStyle(ChatFormatting.RED);
            case MINECRAFT_ALREADY_BOUND -> Component.literal(DiscordLocalizationService.translate("discord.deadrecall.account_link.already_bound"))
                    .withStyle(ChatFormatting.RED);
            case RATE_LIMITED -> Component.literal(result.retryAfterSeconds() > 0
                            ? DiscordLocalizationService.format(
                                    "discord.deadrecall.account_link.rate_limited.wait",
                                    String.valueOf(result.retryAfterSeconds())
                            )
                            : DiscordLocalizationService.translate("discord.deadrecall.account_link.rate_limited"))
                    .withStyle(ChatFormatting.RED);
            case BUSY -> Component.literal(DiscordLocalizationService.translate("discord.deadrecall.account_link.busy"))
                    .withStyle(ChatFormatting.YELLOW);
            case UNAVAILABLE -> Component.literal(DiscordLocalizationService.translate("discord.deadrecall.account_link.unavailable"))
                    .withStyle(ChatFormatting.RED);
        };
    }
}
