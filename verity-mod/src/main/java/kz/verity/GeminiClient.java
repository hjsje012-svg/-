package kz.verity;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GeminiClient {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    private static final int MAX_HISTORY = 8;

    private static final Map<UUID, Deque<String[]>> HISTORY = new HashMap<>();
    private static final Map<UUID, Long> LAST_ASK = new HashMap<>();

    private static final String TOOLS_JSON = """
            [{"functionDeclarations":[
              {"name":"give_item","description":"Выдать игроку предметы в инвентарь.",
               "parameters":{"type":"OBJECT","properties":{
                 "item":{"type":"STRING","description":"Английский id предмета Minecraft, например diamond, iron_ingot, oak_log"},
                 "count":{"type":"INTEGER","description":"Количество"}},"required":["item"]}},
              {"name":"spawn_mob","description":"Призвать мобов рядом с игроком.",
               "parameters":{"type":"OBJECT","properties":{
                 "mob":{"type":"STRING","description":"Английский id моба, например cow, zombie, creeper, iron_golem"},
                 "count":{"type":"INTEGER","description":"Сколько штук"}},"required":["mob"]}},
              {"name":"build","description":"Построить простую постройку перед игроком.",
               "parameters":{"type":"OBJECT","properties":{
                 "structure":{"type":"STRING","enum":["house","tower","wall","platform","pyramid"]},
                 "material":{"type":"STRING","description":"Английский id блока, например oak_planks, stone_bricks, glass"},
                 "width":{"type":"INTEGER"},"height":{"type":"INTEGER"},"depth":{"type":"INTEGER"}},
                 "required":["structure"]}}
            ]}]""";

    private static final String TOOLS_HINT = " Если игрок просит выдать предметы, призвать моба или что-то построить, "
            + "вызови подходящую функцию (give_item, spawn_mob, build) и добавь короткую фразу. "
            + "Названия предметов, мобов и блоков передавай английскими id Minecraft.";

    private record Result(String text, List<JsonObject[]> calls) {}

    public static void ask(ServerPlayerEntity player, String question, VerityEntity verity) {
        VerityConfig cfg = VerityConfig.get();
        MinecraftServer server = player.getServer();
        if (server == null) return;

        String key = cfg.resolvedKey();
        if (key.isEmpty() || key.startsWith("ВСТАВЬТЕ")) {
            say(server, "Мне нужен API-ключ Gemini. Впиши его в config/verity.json", verity, true);
            return;
        }

        long now = System.currentTimeMillis();
        Long last = LAST_ASK.get(player.getUuid());
        if (last != null && now - last < cfg.cooldownSeconds * 1000L) return;
        LAST_ASK.put(player.getUuid(), now);

        boolean canAct = Actions.canAct(player);

        Deque<String[]> hist = HISTORY.computeIfAbsent(player.getUuid(), k -> new ArrayDeque<>());
        hist.addLast(new String[]{"user", player.getName().getString() + ": " + question});
        while (hist.size() > MAX_HISTORY) hist.removeFirst();

        JsonObject body = new JsonObject();
        JsonObject sys = new JsonObject();
        JsonArray sysParts = new JsonArray();
        JsonObject sysText = new JsonObject();
        sysText.addProperty("text", cfg.systemPrompt + (canAct ? TOOLS_HINT : ""));
        sysParts.add(sysText);
        sys.add("parts", sysParts);
        body.add("systemInstruction", sys);

        JsonArray contents = new JsonArray();
        for (String[] m : hist) {
            JsonObject c = new JsonObject();
            c.addProperty("role", m[0]);
            JsonArray parts = new JsonArray();
            JsonObject p = new JsonObject();
            p.addProperty("text", m[1]);
            parts.add(p);
            c.add("parts", parts);
            contents.add(c);
        }
        body.add("contents", contents);
        if (canAct) body.add("tools", JsonParser.parseString(TOOLS_JSON));
        JsonObject gen = new JsonObject();
        gen.addProperty("maxOutputTokens", 600);
        body.add("generationConfig", gen);

        HttpRequest req = HttpRequest.newBuilder(URI.create(
                        "https://generativelanguage.googleapis.com/v1beta/models/" + cfg.model + ":generateContent"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", key)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept(resp -> {
            if (resp.statusCode() != 200) {
                VerityMod.LOGGER.warn("Gemini HTTP {}: {}", resp.statusCode(), resp.body());
                server.execute(() -> say(server,
                        "Ой, Gemini ответил ошибкой (" + resp.statusCode() + "). Проверь ключ и модель.", verity, true));
                return;
            }
            Result res = parse(resp.body());
            server.execute(() -> {
                StringBuilder record = new StringBuilder(res.text());
                if (!res.text().isBlank()) say(server, res.text(), verity, false);
                for (JsonObject[] call : res.calls()) {
                    String name = call[0].get("name").getAsString();
                    String out = Actions.run(player, name, call[1]);
                    record.append(" [").append(out).append("]");
                    server.getPlayerManager().broadcast(
                            Text.literal("  ✦ " + out).formatted(Formatting.GRAY, Formatting.ITALIC), false);
                    if (verity != null && verity.isAlive()) verity.startTalking(40);
                }
                if (res.text().isBlank() && res.calls().isEmpty()) say(server, "Хм, мне нечего ответить.", verity, false);
                hist.addLast(new String[]{"model", record.toString().isBlank() ? "..." : record.toString()});
                while (hist.size() > MAX_HISTORY) hist.removeFirst();
            });
        }).exceptionally(ex -> {
            VerityMod.LOGGER.warn("Gemini request failed", ex);
            server.execute(() -> say(server, "Не получилось достучаться до Gemini.", verity, true));
            return null;
        });
    }

    private static Result parse(String json) {
        StringBuilder sb = new StringBuilder();
        List<JsonObject[]> calls = new ArrayList<>();
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject cand = root.getAsJsonArray("candidates").get(0).getAsJsonObject();
            for (JsonElement part : cand.getAsJsonObject("content").getAsJsonArray("parts")) {
                JsonObject po = part.getAsJsonObject();
                if (po.has("text")) sb.append(po.get("text").getAsString());
                if (po.has("functionCall")) {
                    JsonObject fc = po.getAsJsonObject("functionCall");
                    JsonObject args = fc.has("args") ? fc.getAsJsonObject("args") : new JsonObject();
                    calls.add(new JsonObject[]{fc, args});
                }
            }
        } catch (Exception e) {
            return new Result("Не смогла разобрать ответ Gemini.", calls);
        }
        String s = sb.toString().replace("*", "").trim();
        if (s.length() > 600) s = s.substring(0, 600) + "…";
        if (calls.size() > 5) calls = calls.subList(0, 5);
        return new Result(s, calls);
    }

    private static void say(MinecraftServer server, String text, VerityEntity verity, boolean error) {
        server.getPlayerManager().broadcast(
                Text.literal("<Верити> ").formatted(Formatting.AQUA)
                        .append(Text.literal(text).formatted(error ? Formatting.RED : Formatting.WHITE)),
                false);
        if (verity != null && verity.isAlive()) verity.startTalking(Math.min(200, 40 + text.length() * 2));
    }
}
