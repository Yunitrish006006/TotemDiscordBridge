package com.adaptor.deadrecall.discord;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class MinecraftZhTwLanguageClasspathProbeTest {
    private static final String ZH_TW_PATH = "/assets/minecraft/lang/zh_tw.json";

    @Test
    void minecraftRuntimeProvidesTraditionalChineseLanguageTable() throws Exception {
        try (InputStream stream = Component.class.getResourceAsStream(ZH_TW_PATH)) {
            assertNotNull(stream,
                    "Minecraft 26.2 runtime does not provide " + ZH_TW_PATH
                            + "; Discord localization must use a tested bundled or generated fallback");
        }
    }
}
