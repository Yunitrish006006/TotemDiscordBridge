package com.adaptor.deadrecall.discord;

import com.adaptor.deadrecall.Deadrecall;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DiscordLocalizationService {
    private static final List<String> BUNDLED_TABLES = List.of(
            "/assets/deadrecall/lang/discord_zh_tw/adventure.json",
            "/assets/deadrecall/lang/discord_zh_tw/end.json",
            "/assets/deadrecall/lang/discord_zh_tw/husbandry.json",
            "/assets/deadrecall/lang/discord_zh_tw/nether.json",
            "/assets/deadrecall/lang/discord_zh_tw/story.json",
            "/assets/deadrecall/lang/discord_zh_tw/system.json"
    );
    private static final Pattern PLACEHOLDER = Pattern.compile("%(?:(\\d+)\\$)?s|%%");
    private static final Map<String, String> TRANSLATIONS = loadBundledTranslations();

    private DiscordLocalizationService() {
    }

    public static String render(Component component) {
        if (component == null) {
            return "";
        }
        try {
            StringBuilder result = new StringBuilder();
            appendComponent(result, component);
            return normalize(result.toString());
        } catch (RuntimeException exception) {
            Deadrecall.LOGGER.warn("[DiscordBridge] 無法解析 Discord zh_tw Component", exception);
            return "未知訊息";
        }
    }

    public static String translate(String key) {
        String translated = TRANSLATIONS.get(key);
        return translated == null ? safeFallback(key) : translated;
    }

    public static int translationCount() {
        return TRANSLATIONS.size();
    }

    private static void appendComponent(StringBuilder output, Component component) {
        ComponentContents contents = component.getContents();
        if (contents instanceof PlainTextContents plainText) {
            output.append(plainText.text());
        } else if (contents instanceof TranslatableContents translatable) {
            output.append(renderTranslatable(translatable));
        } else {
            String fallback = component.getString();
            if (!fallback.isBlank()) {
                output.append(fallback);
            }
        }

        for (Component sibling : component.getSiblings()) {
            appendComponent(output, sibling);
        }
    }

    private static String renderTranslatable(TranslatableContents contents) {
        String template = TRANSLATIONS.get(contents.getKey());
        if (template == null) {
            return safeFallback(contents.getKey());
        }

        Object[] rawArguments = contents.getArgs();
        String[] arguments = new String[rawArguments.length];
        for (int index = 0; index < rawArguments.length; index++) {
            arguments[index] = renderArgument(rawArguments[index]);
        }
        return applyPlaceholders(template, arguments);
    }

    private static String renderArgument(Object argument) {
        if (argument instanceof Component component) {
            return render(component);
        }
        return argument == null ? "" : String.valueOf(argument);
    }

    private static String applyPlaceholders(String template, String[] arguments) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuffer result = new StringBuffer();
        int sequentialIndex = 0;
        while (matcher.find()) {
            if ("%%".equals(matcher.group())) {
                matcher.appendReplacement(result, "%");
                continue;
            }
            int argumentIndex;
            String explicitIndex = matcher.group(1);
            if (explicitIndex == null) {
                argumentIndex = sequentialIndex++;
            } else {
                argumentIndex = Integer.parseInt(explicitIndex) - 1;
            }
            String replacement = argumentIndex >= 0 && argumentIndex < arguments.length
                    ? arguments[argumentIndex]
                    : "";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String safeFallback(String key) {
        if (key == null || key.isBlank()) {
            return "未知訊息";
        }
        if (key.startsWith("advancements.") && key.endsWith(".title")) {
            return "未知進度";
        }
        if (key.startsWith("entity.")) {
            return "未知實體";
        }
        if (key.startsWith("death.")) {
            return "死亡訊息";
        }
        return "未知訊息";
    }

    private static Map<String, String> loadBundledTranslations() {
        Map<String, String> translations = new LinkedHashMap<>();
        for (String path : BUNDLED_TABLES) {
            try (InputStream stream = DiscordLocalizationService.class.getResourceAsStream(path)) {
                if (stream == null) {
                    Deadrecall.LOGGER.warn("[DiscordBridge] 缺少 zh_tw 翻譯資源 {}", path);
                    continue;
                }
                try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    JsonObject table = JsonParser.parseReader(reader).getAsJsonObject();
                    for (Map.Entry<String, JsonElement> entry : table.entrySet()) {
                        if (entry.getValue().isJsonPrimitive()) {
                            translations.put(entry.getKey(), entry.getValue().getAsString());
                        }
                    }
                }
            } catch (Exception exception) {
                Deadrecall.LOGGER.warn("[DiscordBridge] 無法載入 zh_tw 翻譯資源 {}", path, exception);
            }
        }
        return Map.copyOf(translations);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }
}
