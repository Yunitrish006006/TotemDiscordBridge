package dev.totem.discord.mixin;

import dev.totem.discord.domain.DiscordEventNotifications;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class VillagerEntityMixin {

    @Unique
    private int totem$previousVillagerLevel = -1;

    @Inject(method = "increaseMerchantCareer", at = @At("HEAD"))
    private void totem$captureVillagerLevel(ServerLevel world, CallbackInfo ci) {
        Villager self = (Villager) (Object) this;
        this.totem$previousVillagerLevel = self.getVillagerData().level();
    }

    @Inject(method = "increaseMerchantCareer", at = @At("TAIL"))
    private void totem$notifyVillagerLevelUp(ServerLevel world, CallbackInfo ci) {
        Villager self = (Villager) (Object) this;
        int previousLevel = this.totem$previousVillagerLevel;
        int currentLevel = self.getVillagerData().level();

        this.totem$previousVillagerLevel = -1;

        if (previousLevel >= 0 && currentLevel > previousLevel) {
            String customName = self.hasCustomName() && self.getCustomName() != null
                    ? self.getCustomName().getString()
                    : "";
            String professionPath = self.getVillagerData().profession()
                    .unwrapKey()
                    .map(key -> key.identifier().getPath())
                    .orElse("none");
            DiscordEventNotifications.villagerLevelUp(
                    customName,
                    professionPath,
                    previousLevel,
                    currentLevel
            );
        }
    }
}

