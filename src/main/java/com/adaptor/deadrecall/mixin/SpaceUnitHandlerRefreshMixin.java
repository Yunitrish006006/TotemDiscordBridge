package com.adaptor.deadrecall.mixin;

import com.adaptor.deadrecall.space.SpaceUnitHandler;
import com.adaptor.deadrecall.space.SpaceUnitStructureRefresh;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(SpaceUnitHandler.class)
public abstract class SpaceUnitHandlerRefreshMixin {
    @Inject(
            method = "sendSpaceUnitMap(Lnet/minecraft/server/level/ServerPlayer;Ljava/util/UUID;)V",
            at = @At("HEAD")
    )
    private static void deadrecall$refreshMapSource(
            ServerPlayer player,
            UUID sourceUnitId,
            CallbackInfo ci
    ) {
        SpaceUnitStructureRefresh.refresh(player.level().getServer(), sourceUnitId);
    }

    @Inject(
            method = "startTeleport(Lnet/minecraft/server/level/ServerPlayer;Ljava/lang/String;Ljava/util/UUID;Ljava/util/UUID;)V",
            at = @At("HEAD")
    )
    private static void deadrecall$refreshTeleportRoute(
            ServerPlayer player,
            String sourceType,
            UUID sourceUnitId,
            UUID targetUnitId,
            CallbackInfo ci
    ) {
        if (SpaceUnitHandler.SOURCE_TYPE_LODESTONE.equals(sourceType)) {
            SpaceUnitStructureRefresh.refresh(player.level().getServer(), sourceUnitId);
        }
        SpaceUnitStructureRefresh.refresh(player.level().getServer(), targetUnitId);
    }
}
