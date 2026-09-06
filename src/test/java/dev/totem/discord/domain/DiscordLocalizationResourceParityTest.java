package dev.totem.discord.domain;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DiscordLocalizationResourceParityTest {
    private static final String LANGUAGE_ROOT = "/assets/deadrecall/lang/";
    private static final Pattern FORMAT_PLACEHOLDER = Pattern.compile("%(?:(\\d+)\\$)?[a-zA-Z]|%%");

    @Test
    void customDiscordSystemTablesHaveMatchingKeysAndPlaceholders() {
        assertLocaleParity("discord_en_us/system.json", "discord_es_es/system.json");
        assertLocaleParity("discord_en_us/system.json", "discord_zh_tw/system.json");
    }

    @Test
    void discordConfigurationScreensHaveMatchingKeysAndPlaceholders() {
        assertLocaleParity("en_us.json", "es_es.json");
        assertLocaleParity("en_us.json", "ja_jp.json");
        assertLocaleParity("en_us.json", "zh_tw.json");
    }

    private static void assertLocaleParity(String baseResource, String localizedResource) {
        JsonObject base = language(baseResource);
        JsonObject localized = language(localizedResource);

        assertEquals(base.keySet(), localized.keySet(), localizedResource + " must match " + baseResource);
        for (String key : base.keySet()) {
            assertEquals(
                    placeholders(base.get(key).getAsString()),
                    placeholders(localized.get(key).getAsString()),
                    localizedResource + " must preserve placeholders for " + key
            );
        }
    }

    private static JsonObject language(String resource) {
        InputStream stream = DiscordLocalizationResourceParityTest.class.getResourceAsStream(LANGUAGE_ROOT + resource);
        assertNotNull(stream, "Missing language resource " + resource);
        try (InputStream closeable = stream;
             InputStreamReader reader = new InputStreamReader(closeable, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception exception) {
            throw new AssertionError("Could not read language resource " + resource, exception);
        }
    }

    private static List<String> placeholders(String text) {
        Matcher matcher = FORMAT_PLACEHOLDER.matcher(text);
        List<String> result = new ArrayList<>();
        while (matcher.find()) {
            result.add(matcher.group());
        }
        result.sort(String::compareTo);
        return result;
    }
}
