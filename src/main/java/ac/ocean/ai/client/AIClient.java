package ac.ocean.ai.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import ac.ocean.OceanPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class AIClient {

    private final OceanPlugin plugin;
    private final Gson gson;

    public AIClient(OceanPlugin plugin) {
        this.plugin = plugin;
        this.gson = new Gson();
    }

    public interface AICallback {
        void onSuccess(String response);
        void onError(String errorMessage);
    }

    public void sendChatCompletion(JsonArray messages, AICallback callback) {
        String endpoint = plugin.getConfig().getString("ai.endpoint", "https://openrouter.ai/api/v1/chat/completions");
        String apiKey = plugin.getConfig().getString("ai.api-key", "");
        String model = plugin.getConfig().getString("ai.model", "openai/gpt-4o-mini");
        double temperature = plugin.getConfig().getDouble("ai.temperature", 0.3);
        int maxTokens = plugin.getConfig().getInt("ai.max-tokens", 600);

        try {
            URL url = new URL(endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);

            boolean isAgentRouter = endpoint.contains("agentrouter") || plugin.getConfig().getBoolean("ai.agentrouter-mode", false);

            if (isAgentRouter) {
                conn.setRequestProperty("User-Agent", plugin.getConfig().getString("ai.custom-user-agent", "claude-cli/0.2.29 (external, cli)"));
                conn.setRequestProperty("x-app", "cli");
                conn.setRequestProperty("anthropic-version", "2023-06-01");
                conn.setRequestProperty("anthropic-beta", "claude-code-20250219,interleaved-thinking-2025-05-14");
                conn.setRequestProperty("anthropic-dangerous-direct-browser-access", "true");
                conn.setRequestProperty("X-Stainless-Lang", "js");
                conn.setRequestProperty("X-Stainless-Package-Version", "0.2.29");
                conn.setRequestProperty("X-Stainless-OS", "Windows");
                conn.setRequestProperty("X-Stainless-Arch", "x64");
                conn.setRequestProperty("X-Stainless-Runtime", "node");
                conn.setRequestProperty("X-Stainless-Runtime-Version", "v20.18.0");
            } else {
                String customUserAgent = plugin.getConfig().getString("ai.custom-user-agent", "");
                if (customUserAgent != null && !customUserAgent.trim().isEmpty()) {
                    conn.setRequestProperty("User-Agent", customUserAgent.trim());
                } else {
                    conn.setRequestProperty("User-Agent", "Ocean-Minecraft-Plugin/1.0");
                }

                String siteUrl = plugin.getConfig().getString("ai.site-url", "https://anticheat.ac");
                String siteName = plugin.getConfig().getString("ai.site-name", "Ocean Freeze AntiCheat");
                conn.setRequestProperty("HTTP-Referer", siteUrl);
                conn.setRequestProperty("X-Title", siteName);
            }

            if (plugin.getConfig().isConfigurationSection("ai.custom-headers")) {
                org.bukkit.configuration.ConfigurationSection sec = plugin.getConfig().getConfigurationSection("ai.custom-headers");
                for (String key : sec.getKeys(false)) {
                    conn.setRequestProperty(key, sec.getString(key));
                }
            }

            conn.setConnectTimeout(45000);
            conn.setReadTimeout(45000);
            conn.setDoOutput(true);

            JsonObject body = new JsonObject();
            body.addProperty("model", model);
            body.add("messages", messages);
            body.addProperty("temperature", temperature);
            body.addProperty("max_tokens", maxTokens);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = gson.toJson(body).getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();

            if (responseCode >= 200 && responseCode < 300) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder resp = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    resp.append(line);
                }
                br.close();

                JsonObject json = gson.fromJson(resp.toString(), JsonObject.class);
                // if your ide marks a warnings here ( 'json.getAsJsonArray("choices").size() > 0' can be replaced with '!json.getAsJsonArray("choices").isEmpty()' )
                // please don't replace, if you change this, the plugin will sent a warn.
                if (json.has("choices") && json.getAsJsonArray("choices").size() > 0) {
                    JsonObject choice = json.getAsJsonArray("choices").get(0).getAsJsonObject();
                    if (choice.has("message") && choice.get("message").isJsonObject()) {
                        JsonObject msg = choice.getAsJsonObject("message");
                        if (msg.has("content") && !msg.get("content").isJsonNull()) {
                            String text = msg.get("content").getAsString();
                            if (!text.trim().isEmpty()) {
                                callback.onSuccess(text);
                                return;
                            }
                        }
                        if (msg.has("reasoning_content") && !msg.get("reasoning_content").isJsonNull()) {
                            String text = msg.get("reasoning_content").getAsString();
                            if (!text.trim().isEmpty()) {
                                callback.onSuccess(text);
                                return;
                            }
                        }
                    }
                }
                callback.onError("AI provider returned an empty completion.");
            } else {
                StringBuilder err = new StringBuilder();
                if (conn.getErrorStream() != null) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
                    String line;
                    while ((line = br.readLine()) != null) {
                        err.append(line);
                    }
                    br.close();
                }
                callback.onError("HTTP " + responseCode + " - " + parseAiError(err.toString()));
            }

            conn.disconnect();

        } catch (Exception e) {
            callback.onError(e.getMessage());
        }
    }

    private String parseAiError(String raw) {
        try {
            JsonObject json = gson.fromJson(raw, JsonObject.class);
            if (json.has("error")) {
                JsonElement el = json.get("error");
                if (el.isJsonObject() && el.getAsJsonObject().has("message")) {
                    return el.getAsJsonObject().get("message").getAsString();
                } else {
                    return el.getAsString();
                }
            }
        } catch (Exception ignored) {
        }
        return raw.isEmpty() ? "Unknown API error" : raw;
    }
}
