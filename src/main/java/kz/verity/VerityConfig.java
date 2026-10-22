package kz.verity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Конфиг: config/verity.json */
public class VerityConfig {
    public String apiKey = "ВСТАВЬТЕ_СЮДА_GEMINI_API_KEY";
    public String model = "gemini-2.5-flash";
    public String systemPrompt = "Ты Верити, дружелюбная круглая помощница в мире Minecraft. "
            + "Отвечай коротко (1-3 предложения), на языке собеседника, по делу и с лёгким юмором.";
    public boolean answerAllNearby = false;
    public int cooldownSeconds = 3;

    // Действия (выдать предметы, призвать мобов, построить)
    public boolean actionsEnabled = true;
    /** false = только операторы (уровень 2+). true = любой игрок. */
    public boolean allowActionsForAll = false;
    public int maxItems = 256;
    public int maxMobs = 10;
    public int maxBuildSize = 20;
    public int maxBlocks = 3000;
    public List<String> bannedMobs = List.of("minecraft:ender_dragon", "minecraft:wither");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static VerityConfig instance = new VerityConfig();

    public static VerityConfig get() { return instance; }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("verity.json");
        try {
            if (Files.exists(path)) {
                instance = GSON.fromJson(Files.readString(path), VerityConfig.class);
                // дописываем в файл новые поля, если конфиг старый
                Files.writeString(path, GSON.toJson(instance));
            } else {
                Files.writeString(path, GSON.toJson(instance));
                VerityMod.LOGGER.info("Создан конфиг {}. Впишите туда Gemini API key.", path);
            }
        } catch (Exception e) {
            VerityMod.LOGGER.error("Не удалось прочитать конфиг", e);
        }
    }

    public String resolvedKey() {
        String env = System.getenv("GEMINI_API_KEY");
        if (env != null && !env.isBlank()) return env.trim();
        return apiKey == null ? "" : apiKey.trim();
    }
}
