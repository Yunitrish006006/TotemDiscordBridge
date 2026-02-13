package com.adaptor.deadrecall.mixin;

import com.adaptor.deadrecall.DeathLocationManager;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {
    @Inject(method = "onDeath", at = @At("HEAD"))
    private void onDeathRecord(DamageSource source, CallbackInfo ci) {
        ServerPlayerEntity serverPlayer = (ServerPlayerEntity)(Object)this;
        BlockPos pos = serverPlayer.getBlockPos();
        World world = serverPlayer.getWorld();
        DeathLocationManager.setDeathLocation(serverPlayer, pos, world);
    }
}

