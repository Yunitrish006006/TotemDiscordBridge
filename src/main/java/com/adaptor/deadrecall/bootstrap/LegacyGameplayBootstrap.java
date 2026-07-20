package com.adaptor.deadrecall.bootstrap;

import com.adaptor.deadrecall.advancement.ModCriteriaTriggers;
import com.adaptor.deadrecall.alchemy.AlchemyHandler;
import com.adaptor.deadrecall.alchemy.CherryBrewInteractions;
import com.adaptor.deadrecall.alchemy.PigManureInteractions;
import com.adaptor.deadrecall.block.ModBlocks;
import com.adaptor.deadrecall.block.entity.ModBlockEntities;
import com.adaptor.deadrecall.effect.ModMobEffects;
import com.adaptor.deadrecall.item.ModItemGroups;
import com.adaptor.deadrecall.item.ModItems;
import com.adaptor.deadrecall.menu.ModMenus;
import com.adaptor.deadrecall.recipe.ModRecipes;

/**
 * Owns legacy gameplay registration until each remaining feature has an assigned module.
 */
public final class LegacyGameplayBootstrap {
    private LegacyGameplayBootstrap() {
    }

    public static void registerContent() {
        ModBlocks.registerModBlocks();
        ModBlockEntities.registerModBlockEntities();
        ModMobEffects.registerModEffects();
        ModCriteriaTriggers.registerModCriteriaTriggers();
        ModMenus.registerModMenus();
        ModItems.registerModItems();
        ModItemGroups.registerModItemGroups();
        AlchemyHandler.register();
        CherryBrewInteractions.register();
        PigManureInteractions.register();
    }

    public static void registerRecipes() {
        ModRecipes.registerModRecipes();
    }
}
