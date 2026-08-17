package dev.totem.discord.domain;

import static dev.totem.discord.TotemDiscordBridge.LOGGER;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DiscordLocalizationService {
    private static final List<String> BUNDLED_TABLES = List.of(
            "/assets/deadrecall/lang/discord_zh_tw/system.json"
    );
    private static final String SERVER_DATA_DIRECTORY = "deadrecall/discord_zh_tw";
    private static final List<String> MOD_LANGUAGE_PREFERENCE = List.of("en_us", "zh_tw");
    private static final Identifier RELOAD_LISTENER_ID =
            Identifier.fromNamespaceAndPath("deadrecall", "discord_zh_tw");
    private static final Pattern PLACEHOLDER = Pattern.compile("%(?:(\\d+)\\$)?s|%%");
    private static final Map<String, String> BUNDLED_TRANSLATIONS = loadBundledTranslations();
    private static volatile Map<String, String> translations = BUNDLED_TRANSLATIONS;
    private static volatile Map<String, String> serverDataOverrides = Map.of();
    private static volatile Path vanillaLanguageCache;
    private static final int MAX_MISSING_KEY_WARNINGS = 128;
    private static final Set<String> WARNED_MISSING_KEYS = new LinkedHashSet<>();

    private DiscordLocalizationService() {
    }

    public static void registerReloadListener(Path configDirectory) {
        String minecraftVersion = FabricLoader.getInstance().getRawGameVersion();
        vanillaLanguageCache = configDirectory
                .resolve("totem-discord-bridge")
                .resolve("lang")
                .resolve("minecraft-" + safeFileName(minecraftVersion) + "-zh_tw.json");
        publishCombinedSnapshot();
        ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(
                RELOAD_LISTENER_ID,
                (ResourceManagerReloadListener) DiscordLocalizationService::reloadFromServerData
        );
        MojangVanillaLanguageDownloader.refreshAsync(
                minecraftVersion,
                vanillaLanguageCache,
                LOGGER,
                DiscordLocalizationService::publishCombinedSnapshot
        );
    }

    public static String render(Component component) {
        if (component == null) {
            return "";
        }
        try {
            StringBuilder result = new StringBuilder();
            appendComponent(result, component, translations);
            return normalize(result.toString());
        } catch (RuntimeException exception) {
            LOGGER.warn("[DiscordBridge] 無法解析 Discord zh_tw Component", exception);
            return "未知訊息";
        }
    }

    public static String renderAdvancementTitle(Component component, String advancementId) {
        String idFallback = readableAdvancementId(advancementId);
        if (component == null) {
            return idFallback;
        }

        Map<String, String> snapshot = translations;
        try {
            ComponentContents contents = component.getContents();
            if (contents instanceof TranslatableContents translatable
                    && !snapshot.containsKey(translatable.getKey())) {
                warnMissingKey(translatable.getKey());
                String fallback = normalize(translatable.getFallback());
                if (!fallback.isEmpty()) {
                    fallback = applyPlaceholders(fallback, renderArguments(translatable, snapshot));
                } else {
                    fallback = idFallback;
                }
                if (fallback.isEmpty()) {
                    fallback = safeFallback(translatable.getKey());
                }

                StringBuilder result = new StringBuilder(fallback);
                for (Component sibling : component.getSiblings()) {
                    appendComponent(result, sibling, snapshot);
                }
                return normalize(result.toString());
            }

            StringBuilder result = new StringBuilder();
            appendComponent(result, component, snapshot);
            String rendered = normalize(result.toString());
            return rendered.isEmpty() ? idFallback : rendered;
        } catch (RuntimeException exception) {
            LOGGER.warn("[DiscordBridge] 無法解析 Discord 進度標題", exception);
            return idFallback.isEmpty()
                    ? translate("discord.deadrecall.advancement.unknown")
                    : idFallback;
        }
    }

    public static String translate(String key) {
        String translated = translations.get(key);
        if (translated != null) {
            return translated;
        }
        warnMissingKey(key);
        return safeFallback(key);
    }

    public static int translationCount() {
        return translations.size();
    }

    private static void appendComponent(
            StringBuilder output,
            Component component,
            Map<String, String> snapshot
    ) {
        ComponentContents contents = component.getContents();
        if (contents instanceof PlainTextContents plainText) {
            output.append(plainText.text());
        } else if (contents instanceof TranslatableContents translatable) {
            output.append(renderTranslatable(translatable, snapshot));
        } else {
            String fallback = component.getString();
            if (!fallback.isBlank()) {
                output.append(fallback);
            }
        }

        for (Component sibling : component.getSiblings()) {
            appendComponent(output, sibling, snapshot);
        }
    }

    private static String renderTranslatable(
            TranslatableContents contents,
            Map<String, String> snapshot
    ) {
        String template = snapshot.get(contents.getKey());
        if (template == null) {
            warnMissingKey(contents.getKey());
            return safeFallback(contents.getKey());
        }

        return applyPlaceholders(template, renderArguments(contents, snapshot));
    }

    private static String[] renderArguments(
            TranslatableContents contents,
            Map<String, String> snapshot
    ) {
        Object[] rawArguments = contents.getArgs();
        String[] arguments = new String[rawArguments.length];
        for (int index = 0; index < rawArguments.length; index++) {
            arguments[index] = renderArgument(rawArguments[index], snapshot);
        }
        return arguments;
    }

    private static String renderArgument(Object argument, Map<String, String> snapshot) {
        if (argument instanceof Component component) {
            StringBuilder result = new StringBuilder();
            appendComponent(result, component, snapshot);
            return normalize(result.toString());
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

    private static String readableAdvancementId(String advancementId) {
        String normalized = normalize(advancementId);
        int namespaceSeparator = normalized.indexOf(':');
        String path = namespaceSeparator >= 0 ? normalized.substring(namespaceSeparator + 1) : normalized;
        int directorySeparator = path.lastIndexOf('/');
        if (directorySeparator >= 0) {
            path = path.substring(directorySeparator + 1);
        }
        path = normalize(path.replace('_', ' ').replace('-', ' '));
        if (path.isEmpty()) {
            return "";
        }

        StringBuilder readable = new StringBuilder(path.length());
        for (String word : path.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!readable.isEmpty()) {
                readable.append(' ');
            }
            readable.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                readable.append(word.substring(1));
            }
        }
        return readable.toString();
    }

    private static synchronized void warnMissingKey(String key) {
        if (key == null
                || key.isBlank()
                || WARNED_MISSING_KEYS.contains(key)
                || WARNED_MISSING_KEYS.size() >= MAX_MISSING_KEY_WARNINGS) {
            return;
        }
        WARNED_MISSING_KEYS.add(key);
        LOGGER.warn("[DiscordBridge] zh_tw 翻譯缺少 key {}，使用安全 fallback", key);
    }

    private static void reloadFromServerData(ResourceManager resourceManager) {
        Map<String, String> overrides = new LinkedHashMap<>();
        int overrideCount = 0;
        try {
            Map<Identifier, Resource> resources = resourceManager.listResources(
                    SERVER_DATA_DIRECTORY,
                    id -> "deadrecall".equals(id.getNamespace()) && id.getPath().endsWith(".json")
            );
            List<Map.Entry<Identifier, Resource>> orderedResources = new ArrayList<>(resources.entrySet());
            orderedResources.sort(Comparator.comparing(entry -> entry.getKey().toString()));

            for (Map.Entry<Identifier, Resource> entry : orderedResources) {
                try (Reader reader = entry.getValue().openAsReader()) {
                    overrideCount += mergeTranslationTable(reader, overrides);
                } catch (Exception exception) {
                    LOGGER.warn(
                            "[DiscordBridge] 無法載入 zh_tw data resource {}",
                            entry.getKey(),
                            exception
                    );
                }
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("[DiscordBridge] 無法列舉 zh_tw data resources，保留目前 snapshot", exception);
            return;
        }

        serverDataOverrides = Map.copyOf(overrides);
        publishCombinedSnapshot();
        LOGGER.info(
                "[DiscordBridge] 已原子載入 {} 個 zh_tw 翻譯（{} 個 data resource key 覆寫）",
                translations.size(),
                overrideCount
        );
    }

    private static Map<String, String> loadBundledTranslations() {
        Map<String, String> translations = new LinkedHashMap<>();
        for (String path : BUNDLED_TABLES) {
            try (InputStream stream = DiscordLocalizationService.class.getResourceAsStream(path)) {
                if (stream == null) {
                    LOGGER.warn("[DiscordBridge] 缺少 zh_tw 翻譯資源 {}", path);
                    continue;
                }
                try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    mergeTranslationTable(reader, translations);
                }
            } catch (Exception exception) {
                LOGGER.warn("[DiscordBridge] 無法載入 zh_tw 翻譯資源 {}", path, exception);
            }
        }
        return Map.copyOf(translations);
    }

    private static Map<String, String> loadTranslationsFromInstalledMods() {
        Map<String, String> candidate = new LinkedHashMap<>();
        List<Path> roots = new ArrayList<>();
        int modCount = 0;
        try {
            List<ModContainer> mods = new ArrayList<>(FabricLoader.getInstance().getAllMods());
            mods.sort(Comparator.comparing(mod -> mod.getMetadata().getId()));
            modCount = mods.size();
            for (ModContainer mod : mods) {
                roots.addAll(mod.getRootPaths());
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("[DiscordBridge] 無法列舉已安裝模組語言檔，保留內建翻譯", exception);
        }

        int loaded = mergeModLanguageRoots(roots, candidate, List.of("en_us"));
        int vanillaLoaded = mergeCachedVanillaLanguage(candidate);
        loaded += mergeModLanguageRoots(roots, candidate, List.of("zh_tw"));
        candidate.putAll(BUNDLED_TRANSLATIONS);
        LOGGER.info(
                "[DiscordBridge] 已從 {} 個模組載入 {} 個語言項目，官方原版 zh_tw 載入 {} 個",
                modCount,
                loaded,
                vanillaLoaded
        );
        return candidate;
    }

    static int mergeModLanguageRoots(Iterable<Path> roots, Map<String, String> output) {
        return mergeModLanguageRoots(roots, output, MOD_LANGUAGE_PREFERENCE);
    }

    private static int mergeModLanguageRoots(
            Iterable<Path> roots,
            Map<String, String> output,
            Iterable<String> languages
    ) {
        List<Path> orderedRoots = new ArrayList<>();
        roots.forEach(orderedRoots::add);
        orderedRoots.sort(Comparator.comparing(Path::toString));

        int loaded = 0;
        for (String language : languages) {
            for (Path root : orderedRoots) {
                Path assets = root.resolve("assets");
                if (!Files.isDirectory(assets)) {
                    continue;
                }

                List<Path> languageFiles = new ArrayList<>();
                try (DirectoryStream<Path> namespaces = Files.newDirectoryStream(assets)) {
                    for (Path namespace : namespaces) {
                        Path languageFile = namespace.resolve("lang").resolve(language + ".json");
                        if (Files.isRegularFile(languageFile)) {
                            languageFiles.add(languageFile);
                        }
                    }
                } catch (Exception exception) {
                    LOGGER.warn("[DiscordBridge] 無法列舉模組語言目錄 {}", assets, exception);
                    continue;
                }

                languageFiles.sort(Comparator.comparing(Path::toString));
                for (Path languageFile : languageFiles) {
                    try (Reader reader = Files.newBufferedReader(languageFile, StandardCharsets.UTF_8)) {
                        loaded += mergeTranslationTable(reader, output);
                    } catch (Exception exception) {
                        LOGGER.warn("[DiscordBridge] 無法載入模組語言檔 {}", languageFile, exception);
                    }
                }
            }
        }
        return loaded;
    }

    private static int mergeCachedVanillaLanguage(Map<String, String> output) {
        Path cache = vanillaLanguageCache;
        if (cache == null || !Files.isRegularFile(cache)) {
            return 0;
        }
        try (Reader reader = Files.newBufferedReader(cache, StandardCharsets.UTF_8)) {
            return mergeTranslationTable(reader, output);
        } catch (Exception exception) {
            LOGGER.warn("[DiscordBridge] 無法載入 Minecraft 官方 zh_tw 快取 {}", cache, exception);
            return 0;
        }
    }

    private static int mergeTranslationTable(Reader reader, Map<String, String> output) {
        JsonElement parsed = JsonParser.parseReader(reader);
        if (!parsed.isJsonObject()) {
            throw new JsonParseException("translation table must be a JSON object");
        }

        int loaded = 0;
        JsonObject table = parsed.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : table.entrySet()) {
            JsonElement value = entry.getValue();
            if (entry.getKey().isBlank()
                    || !value.isJsonPrimitive()
                    || !value.getAsJsonPrimitive().isString()) {
                continue;
            }
            output.put(entry.getKey(), value.getAsString());
            loaded++;
        }
        return loaded;
    }

    private static void publishSnapshot(Map<String, String> candidate) {
        translations = Map.copyOf(candidate);
        clearMissingKeyWarnings();
    }

    private static synchronized void publishCombinedSnapshot() {
        Map<String, String> candidate = new LinkedHashMap<>(loadTranslationsFromInstalledMods());
        candidate.putAll(serverDataOverrides);
        publishSnapshot(candidate);
    }

    private static synchronized void clearMissingKeyWarnings() {
        WARNED_MISSING_KEYS.clear();
    }

    static Map<String, String> snapshotForTesting() {
        return translations;
    }

    static void replaceSnapshotForTesting(Map<String, String> snapshot) {
        publishSnapshot(snapshot);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static String safeFileName(String value) {
        String normalized = normalize(value).replaceAll("[^A-Za-z0-9._-]", "_");
        return normalized.isEmpty() ? "unknown" : normalized;
    }
}
