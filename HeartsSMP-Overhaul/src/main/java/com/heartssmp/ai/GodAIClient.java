package com.heartssmp.ai;

import com.heartssmp.HeartsSMPPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Async HTTP client for querying an AI provider (Anthropic / OpenAI / Gemini).
 * All HTTP calls happen off the main server thread via CompletableFuture.
 * The callback fires back on the Bukkit scheduler (main thread) so it is safe
 * to call Bukkit API methods inside the onResponse consumer.
 */
public class GodAIClient {

    public enum Provider { ANTHROPIC, OPENAI, GEMINI }

    private final HeartsSMPPlugin plugin;
    private final Provider provider;
    private final String apiKey;
    private final String model;
    private final int maxTokens;
    private final String systemPrompt;
    private final HttpClient httpClient;

    // Per-player conversation history for contextual follow-up (capped at 6 turns)
    private final Map<UUID, List<Map<String, String>>> conversationHistory = new HashMap<>();
    private static final int MAX_HISTORY_TURNS = 6;

    public GodAIClient(HeartsSMPPlugin plugin) {
        this.plugin = plugin;

        String providerStr = plugin.getConfig().getString("ai.provider", "anthropic").toUpperCase();
        this.provider = switch (providerStr) {
            case "OPENAI" -> Provider.OPENAI;
            case "GEMINI" -> Provider.GEMINI;
            default       -> Provider.ANTHROPIC;
        };

        this.apiKey      = plugin.getConfig().getString("ai.api-key", "");
        this.model       = plugin.getConfig().getString("ai.model", "claude-haiku-4-5-20251001");
        this.maxTokens   = plugin.getConfig().getInt("ai.max-tokens", 300);
        this.systemPrompt = plugin.getConfig().getString("ai.system-prompt",
                "You are a cryptic, omnipotent God in a Minecraft world. Speak in short, dramatic sentences.");

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Send a player's message to the AI asynchronously.
     *
     * @param playerUUID  UUID for conversation history tracking
     * @param playerName  Player name for prompt context
     * @param playerMessage What the player said
     * @param onResponse  Called on the main thread with the AI's response text
     * @param onError     Called on the main thread if the request fails
     */
    public void askAsync(UUID playerUUID, String playerName, String playerMessage,
                         Consumer<String> onResponse, Consumer<String> onError) {

        if (apiKey == null || apiKey.isBlank() || apiKey.equals("YOUR_API_KEY_HERE")) {
            scheduleCallback(() -> onError.accept("§cAI API key not configured. Set ai.api-key in config.yml."));
            return;
        }

        // Build conversation history
        List<Map<String, String>> history = conversationHistory.computeIfAbsent(playerUUID, k -> new ArrayList<>());
        history.add(Map.of("role", "user", "content", "[" + playerName + "]: " + playerMessage));

        // Cap history
        while (history.size() > MAX_HISTORY_TURNS * 2) {
            history.remove(0);
        }

        // Take a snapshot for the async thread (avoid CME)
        List<Map<String, String>> historyCopy = new ArrayList<>(history);

        CompletableFuture.supplyAsync(() -> {
            try {
                return switch (provider) {
                    case ANTHROPIC -> callAnthropic(historyCopy);
                    case OPENAI    -> callOpenAI(historyCopy);
                    case GEMINI    -> callGemini(historyCopy);
                };
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).thenAccept(response -> {
            // Add assistant turn to history on main thread
            scheduleCallback(() -> {
                history.add(Map.of("role", "assistant", "content", response));
                onResponse.accept(response);
            });
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "AI request failed", ex);
            scheduleCallback(() -> onError.accept("§c*The Divine falls silent for a moment...*"));
            return null;
        });
    }

    /** Clear conversation history for a player (called when God despawns) */
    public void clearHistory(UUID playerUUID) {
        conversationHistory.remove(playerUUID);
    }

    /** Clear all conversation history */
    public void clearAllHistory() {
        conversationHistory.clear();
    }

    // ── Provider implementations ──────────────────────────────────────────────

    private String callAnthropic(List<Map<String, String>> messages) throws Exception {
        // Build messages JSON array
        StringBuilder messagesJson = new StringBuilder("[");
        for (int i = 0; i < messages.size(); i++) {
            Map<String, String> m = messages.get(i);
            if (i > 0) messagesJson.append(",");
            messagesJson.append("{\"role\":\"").append(escape(m.get("role")))
                    .append("\",\"content\":\"").append(escape(m.get("content"))).append("\"}");
        }
        messagesJson.append("]");

        String body = "{\"model\":\"" + model + "\","
                + "\"max_tokens\":" + maxTokens + ","
                + "\"system\":\"" + escape(systemPrompt) + "\","
                + "\"messages\":" + messagesJson + "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.anthropic.com/v1/messages"))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return parseAnthropicResponse(response.body());
    }

    private String callOpenAI(List<Map<String, String>> messages) throws Exception {
        StringBuilder messagesJson = new StringBuilder("[");
        messagesJson.append("{\"role\":\"system\",\"content\":\"").append(escape(systemPrompt)).append("\"}");
        for (Map<String, String> m : messages) {
            messagesJson.append(",{\"role\":\"").append(escape(m.get("role")))
                    .append("\",\"content\":\"").append(escape(m.get("content"))).append("\"}");
        }
        messagesJson.append("]");

        String body = "{\"model\":\"" + model + "\","
                + "\"max_tokens\":" + maxTokens + ","
                + "\"messages\":" + messagesJson + "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return parseOpenAIResponse(response.body());
    }

    private String callGemini(List<Map<String, String>> messages) throws Exception {
        StringBuilder partsJson = new StringBuilder("[");
        // Gemini uses 'contents' with 'parts'
        for (int i = 0; i < messages.size(); i++) {
            Map<String, String> m = messages.get(i);
            if (i > 0) partsJson.append(",");
            String role = m.get("role").equals("assistant") ? "model" : "user";
            partsJson.append("{\"role\":\"").append(role).append("\","
                    + "\"parts\":[{\"text\":\"").append(escape(m.get("content"))).append("\"}]}");
        }
        partsJson.append("]");

        String body = "{\"system_instruction\":{\"parts\":[{\"text\":\"" + escape(systemPrompt) + "\"}]},"
                + "\"contents\":" + partsJson + ","
                + "\"generationConfig\":{\"maxOutputTokens\":" + maxTokens + "}}";

        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + model + ":generateContent?key=" + apiKey;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return parseGeminiResponse(response.body());
    }

    // ── JSON response parsers (lightweight, no external dependency) ───────────

    private String parseAnthropicResponse(String json) {
        // {"content":[{"type":"text","text":"..."}], ...}
        int textIdx = json.indexOf("\"text\":");
        if (textIdx < 0) return fallbackResponse(json);
        int start = json.indexOf('"', textIdx + 7) + 1;
        int end = findJsonStringEnd(json, start);
        return unescapeJson(json.substring(start, end));
    }

    private String parseOpenAIResponse(String json) {
        // {"choices":[{"message":{"role":"assistant","content":"..."}}]}
        int contentIdx = json.indexOf("\"content\":");
        if (contentIdx < 0) return fallbackResponse(json);
        // skip the system prompt repetition — find second "content"
        contentIdx = json.indexOf("\"content\":", contentIdx + 10);
        if (contentIdx < 0) contentIdx = json.indexOf("\"content\":"); // fallback
        int start = json.indexOf('"', contentIdx + 10) + 1;
        int end = findJsonStringEnd(json, start);
        return unescapeJson(json.substring(start, end));
    }

    private String parseGeminiResponse(String json) {
        // {"candidates":[{"content":{"parts":[{"text":"..."}]}}]}
        int textIdx = json.lastIndexOf("\"text\":");
        if (textIdx < 0) return fallbackResponse(json);
        int start = json.indexOf('"', textIdx + 7) + 1;
        int end = findJsonStringEnd(json, start);
        return unescapeJson(json.substring(start, end));
    }

    private String fallbackResponse(String json) {
        plugin.getLogger().warning("Could not parse AI response: " + json.substring(0, Math.min(200, json.length())));
        return "§7*The Divine communes with forces beyond mortal comprehension...*";
    }

    /** Find end of a JSON string starting after the opening quote. */
    private int findJsonStringEnd(String json, int start) {
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\') { i++; continue; } // skip escaped char
            if (c == '"') return i;
        }
        return json.length();
    }

    private String unescapeJson(String s) {
        return s.replace("\\n", "\n").replace("\\\"", "\"")
                .replace("\\'", "'").replace("\\\\", "\\")
                .replace("\\t", "\t");
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "").replace("\t", "\\t");
    }

    private void scheduleCallback(Runnable r) {
        new BukkitRunnable() {
            @Override public void run() { r.run(); }
        }.runTask(plugin);
    }
}
