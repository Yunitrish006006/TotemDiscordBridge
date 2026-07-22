package dev.totem.discord.mixin;

import dev.totem.discord.bootstrap.TotemDiscordBridgeBootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerPublishMixin {
    @Inject(method = "publishServer", at = @At("RETURN"))
    private void totemDiscordBridge$notifyPublishedServer(
            MinecraftServer.MultiplayerScope scope, GameType gameMode, boolean cheats, int port,
            CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) {
            TotemDiscordBridgeBootstrap.onServerPublished((MinecraftServer) (Object) this);
        }
    }

    @Inject(method = "unpublishServer", at = @At("RETURN"))
    private void totemDiscordBridge$notifyUnpublishedServer(CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) {
            TotemDiscordBridgeBootstrap.onServerUnpublished((MinecraftServer) (Object) this);
        }
    }
}

