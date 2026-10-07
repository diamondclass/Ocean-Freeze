package ac.ocean.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ac.ocean.OceanPlugin;
import ac.ocean.ai.client.AIClient;
import ac.ocean.ai.client.DocsCache;
import ac.ocean.ai.client.PlayerContextService;

public class AIManager {

    private final OceanPlugin plugin;
    private final Gson gson;
    private final AIClient client;
    private final PlayerContextService context;
    private final DocsCache docs;

    public AIManager(OceanPlugin plugin) {
        this.plugin = plugin;
        this.gson = new Gson();
        this.client = new AIClient(plugin);
        this.context = new PlayerContextService(plugin);
        this.docs = new DocsCache();
    }

    public boolean isConfigured() {
        if (!plugin.getConfig().getBoolean("ai.enabled", true)) {
            return false;
        }
        String apiKey = plugin.getConfig().getString("ai.api-key", "");
        return apiKey != null && !apiKey.trim().isEmpty() && !apiKey.equalsIgnoreCase("YOUR_AI_API_KEY_HERE");
    }

    public void ask(CommandSender sender, String query) {
        if (!isConfigured()) {
            sender.sendMessage(colorize(plugin.getMessageManager().getMessage("ai-not-configured",
                    "&c✗ AI integration is not configured! Please set 'ai.api-key' in config.yml.")));
            return;
        }

        sender.sendMessage(colorize(plugin.getMessageManager().getMessage("ai-thinking",
                "&7[AI] &eConsulting AI assistant, please wait...")));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String contextData = context.extractAndFetchContext(query);

                String liveDocs = docs.fetchDocumentationQuietly();

                String basePrompt = plugin.getConfig().getString("ai.system-prompt",
                        "You are Ocean AI, an expert screenshare forensic security assistant for Minecraft server staff. " +
                        "Provide concise, authoritative advice formatted cleanly for Minecraft chat using bullet points. " +
                        "When evaluating a PIN, scan report, or detections, always provide a clear Verdict [CLEAN / SUSPICIOUS / HIGH RISK / CONFIRMED CHEATER], " +
                        "a Decision [Ban / Tempban / Watch / Release], and specific technical reasons from Ocean anti-cheat.");

                String systemPrompt = basePrompt +
                        (liveDocs.isEmpty() ? "" : "\n\n=== Live Documentation Read from https://anticheat.ac/docs ===\n" + liveDocs) +
                        (contextData.isEmpty() ? "" : "\n\n=== Live Scan / Player Intelligence ===\n" + contextData);

                JsonArray messages = new JsonArray();
                JsonObject sysMsg = new JsonObject();
                sysMsg.addProperty("role", "system");
                sysMsg.addProperty("content", systemPrompt);
                messages.add(sysMsg);

                JsonObject userMsg = new JsonObject();
                userMsg.addProperty("role", "user");
                userMsg.addProperty("content", query);
                messages.add(userMsg);

                client.sendChatCompletion(messages, new AIClient.AICallback() {
                    @Override
                    public void onSuccess(String response) {
                        Bukkit.getScheduler().runTask(plugin, () -> displayAIResponse(sender, "&6&l[Ocean AI Answer]", response));
                    }

                    @Override
                    public void onError(String errorMessage) {
                        Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(colorize(plugin.getMessageManager().getMessage("ai-error",
                                "&c[AI Error]: &f%error%").replace("%error%", errorMessage))));
                    }
                });

            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(colorize(plugin.getMessageManager().getMessage("ai-error",
                        "&c[AI Error]: &f%error%").replace("%error%", e.getMessage()))));
            }
        });
    }

    public void analyzeScan(CommandSender sender, String pin, String targetPlayerName, JsonObject scanResults) {
        if (!isConfigured()) {
            if (sender != null) {
                sender.sendMessage(colorize(plugin.getMessageManager().getMessage("ai-not-configured",
                        "&c✗ AI integration is not configured! Please set 'ai.api-key' in config.yml.")));
            }
            return;
        }

        if (sender != null) {
            sender.sendMessage(colorize(plugin.getMessageManager().getMessage("ai-analyzing-scan",
                    "&7[AI] &eGenerating forensic AI analysis for &f%player% &7(PIN: &e%pin%&7)...")
                    .replace("%player%", targetPlayerName)
                    .replace("%pin%", pin)));
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                JsonObject finalScanResults = scanResults;
                if (finalScanResults == null || !finalScanResults.has("data") || finalScanResults.get("data").isJsonNull()) {
                    String fetchedJson = context.fetchPinResultsQuietly(pin);
                    if (!fetchedJson.isEmpty()) {
                        try {
                            finalScanResults = gson.fromJson(fetchedJson, JsonObject.class);
                        } catch (Exception ignored) {}
                    }
                }

                String pinStatus = context.fetchPinStatusQuietly(pin);

                StringBuilder fullContext = new StringBuilder();
                fullContext.append("=== COMPREHENSIVE SCAN REPORT ===\n");
                fullContext.append("PIN: ").append(pin).append("\n");
                fullContext.append("Target Player: ").append(targetPlayerName).append("\n");
                if (!pinStatus.isEmpty()) {
                    fullContext.append("Live Scan Progress & Status: ").append(pinStatus).append("\n");
                }

                if (finalScanResults != null) {
                    if (finalScanResults.has("result")) {
                        fullContext.append("Overall Result: ").append(finalScanResults.get("result").getAsString()).append("\n");
                    }

                    if (finalScanResults.has("data") && !finalScanResults.get("data").isJsonNull()) {
                        JsonObject data = finalScanResults.getAsJsonObject("data");

                        if (data.has("detections") && data.get("detections").isJsonArray()) {
                            JsonArray arr = data.getAsJsonArray("detections");
                            fullContext.append("Direct/Confirmed Detections (").append(arr.size()).append("): ");
                            for (int i = 0; i < arr.size(); i++) {
                                JsonElement el = arr.get(i);
                                fullContext.append(el.isJsonPrimitive() ? el.getAsString() : el.toString()).append("; ");
                            }
                            fullContext.append("\n");
                        }

                        if (data.has("suspicious") && data.get("suspicious").isJsonArray()) {
                            JsonArray arr = data.getAsJsonArray("suspicious");
                            fullContext.append("Suspicious Logs/Processes (").append(arr.size()).append("): ");
                            for (int i = 0; i < arr.size(); i++) {
                                JsonElement el = arr.get(i);
                                fullContext.append(el.isJsonPrimitive() ? el.getAsString() : el.toString()).append("; ");
                            }
                            fullContext.append("\n");
                        }

                        if (data.has("warnings") && data.get("warnings").isJsonArray()) {
                            JsonArray arr = data.getAsJsonArray("warnings");
                            fullContext.append("Warning Logs & Evasion Indicators (").append(arr.size()).append("): ");
                            for (int i = 0; i < arr.size(); i++) {
                                JsonElement el = arr.get(i);
                                fullContext.append(el.isJsonPrimitive() ? el.getAsString() : el.toString()).append("; ");
                            }
                            fullContext.append("\n");
                        }
                        if (data.has("integrity") && data.get("integrity").isJsonArray()) {
                            JsonArray arr = data.getAsJsonArray("integrity");
                            fullContext.append("Integrity Checks / RUIN Violations (").append(arr.size()).append("): ");
                            for (int i = 0; i < arr.size(); i++) {
                                JsonElement el = arr.get(i);
                                fullContext.append(el.isJsonPrimitive() ? el.getAsString() : el.toString()).append("; ");
                            }
                            fullContext.append("\n");
                        }

                        if (data.has("vpn") && !data.get("vpn").isJsonNull()) fullContext.append("VPN Detected: ").append(data.get("vpn").getAsString()).append("\n");
                        if (data.has("country") && !data.get("country").isJsonNull()) fullContext.append("Country: ").append(data.get("country").getAsString()).append("\n");
                        if (data.has("windows") && !data.get("windows").isJsonNull()) fullContext.append("Windows Build: ").append(data.get("windows").getAsString()).append("\n");
                        if (data.has("scantime") && !data.get("scantime").isJsonNull()) fullContext.append("Scan Duration: ").append(data.get("scantime").getAsString()).append("\n");

                        if (data.has("discord") && data.get("discord").isJsonArray()) {
                            JsonArray discordArr = data.getAsJsonArray("discord");
                            for (int i = 0; i < Math.min(discordArr.size(), 2); i++) {
                                JsonObject acc = discordArr.get(i).getAsJsonObject();
                                if (acc.has("id")) {
                                    String dId = acc.get("id").getAsString();
                                    String user = acc.has("username") ? acc.get("username").getAsString() : dId;
                                    fullContext.append("\nLinked Discord Account: ").append(user).append(" (ID: ").append(dId).append(")");
                                    String dbInfo = context.fetchDbQuietly(dId);
                                    if (!dbInfo.isEmpty()) {
                                        fullContext.append("\n- Cheater Database Profile: ").append(dbInfo);
                                    }
                                    String riskInfo = context.fetchRiskQuietly(dId);
                                    if (!riskInfo.isEmpty()) {
                                        fullContext.append("\n- Calculated Risk Score: ").append(riskInfo);
                                    }
                                }
                            }
                        }
                    }
                }

                String liveDocs = docs.fetchDocumentationQuietly();
                String systemPrompt = "You are Ocean AI, an expert Minecraft AntiCheat and screenshare forensics investigator. " +
                        (liveDocs.isEmpty() ? "" : "\nUse the following official Ocean documentation read from https://anticheat.ac/docs to interpret all detection types, warning logs, suspicious residues, and integrity results:\n" + liveDocs + "\n") +
                        "Evaluate ALL possible results in this scan: Detections, Suspicious tools, Warnings (Prefetch/ActivitiesCache), Integrity (RUIN, hooks, memory injection), Network/VPN, and Linked Discord history. " +
                        "Make a definitive decision following this structure for Minecraft chat: " +
                        "\n1. Verdict: [CLEAN / SUSPICIOUS / HIGH RISK / CONFIRMED CHEATER]" +
                        "\n2. Decision: [Permanent Ban / 7-Day Tempban / Watch / Release]" +
                        "\n3. Decisive Reasons: (2-3 concise bullet points detailing why this decision was reached based on the scan findings)" +
                        "\n4. Action for Staff: (1 clear operational next step, e.g. 'Execute /ban <player> Cheating [PIN]', or 'Request modlist check')" +
                        "\nKeep it concise and readable in Minecraft chat. Avoid markdown headers (#) and code blocks.";

                JsonArray messages = new JsonArray();
                JsonObject sysMsg = new JsonObject();
                sysMsg.addProperty("role", "system");
                sysMsg.addProperty("content", systemPrompt);
                messages.add(sysMsg);

                JsonObject userMsg = new JsonObject();
                userMsg.addProperty("role", "user");
                userMsg.addProperty("content", "Please analyze this scan:\n" + fullContext);
                messages.add(userMsg);

                client.sendChatCompletion(messages, new AIClient.AICallback() {
                    @Override
                    public void onSuccess(String response) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            String header = "&6&l[Ocean AI Analysis - PIN: " + pin + "]";
                            if (sender != null) {
                                displayAIResponse(sender, header, response);
                            } else {
                                for (Player p : Bukkit.getOnlinePlayers()) {
                                    if (p.hasPermission("ocean.staff")) {
                                        displayAIResponse(p, header, response);
                                    }
                                }
                            }
                        });
                    }

                    @Override
                    public void onError(String errorMessage) {
                        if (sender != null) {
                            Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(colorize(plugin.getMessageManager().getMessage("ai-error",
                                    "&c[AI Error]: &f%error%").replace("%error%", errorMessage))));
                        }
                    }
                });

            } catch (Exception e) {
                if (sender != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(colorize(plugin.getMessageManager().getMessage("ai-error",
                            "&c[AI Error]: &f%error%").replace("%error%", e.getMessage()))));
                }
            }
        });
    }

    public void analyzeDiscordId(CommandSender sender, String discordId) {
        if (!isConfigured()) {
            sender.sendMessage(colorize(plugin.getMessageManager().getMessage("ai-not-configured",
                    "&c✗ AI integration is not configured! Please set 'ai.api-key' in config.yml.")));
            return;
        }

        sender.sendMessage(colorize(plugin.getMessageManager().getMessage("ai-analyzing-user",
                "&7[AI] &eInvestigating Discord ID &f%discordid% &ewith AI intelligence...")
                .replace("%discordid%", discordId)));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String dbData = context.fetchDbQuietly(discordId);
                String riskData = context.fetchRiskQuietly(discordId);
                String lookupData = context.fetchLookupQuietly(discordId);

                String fullContext = "Discord ID: " + discordId +
                        "\nCheater Database Query:\n" + dbData +
                        "\nRisk Score Analysis:\n" + riskData +
                        "\nScanned User History:\n" + lookupData;

                String liveDocs = docs.fetchDocumentationQuietly();
                String systemPrompt = "You are Ocean AI Security Auditor. Analyze all intelligence for this Discord user. " +
                        (liveDocs.isEmpty() ? "" : "\nUse the official Ocean documentation read from https://anticheat.ac/docs to interpret detections:\n" + liveDocs + "\n") +
                        "Summarize their threat level, ban history, confirmed cheater status, and previous detections in 3-5 concise bullet points for Minecraft staff.";

                JsonArray messages = new JsonArray();
                JsonObject sysMsg = new JsonObject();
                sysMsg.addProperty("role", "system");
                sysMsg.addProperty("content", systemPrompt);
                messages.add(sysMsg);

                JsonObject userMsg = new JsonObject();
                userMsg.addProperty("role", "user");
                userMsg.addProperty("content", "Analyze this player profile:\n" + fullContext);
                messages.add(userMsg);

                client.sendChatCompletion(messages, new AIClient.AICallback() {
                    @Override
                    public void onSuccess(String response) {
                        Bukkit.getScheduler().runTask(plugin, () -> displayAIResponse(sender, "&6&l[Ocean AI Profile Check: " + discordId + "]", response));
                    }

                    @Override
                    public void onError(String errorMessage) {
                        Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(colorize(plugin.getMessageManager().getMessage("ai-error",
                                "&c[AI Error]: &f%error%").replace("%error%", errorMessage))));
                    }
                });

            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(colorize(plugin.getMessageManager().getMessage("ai-error",
                        "&c[AI Error]: &f%error%").replace("%error%", e.getMessage()))));
            }
        });
    }

    private void displayAIResponse(CommandSender sender, String title, String content) {
        if (content == null || content.trim().isEmpty()) {
            sender.sendMessage(colorize(plugin.getMessageManager().getMessage("ai-no-response", "&c[AI]: No response was generated.")));
            return;
        }

        sender.sendMessage(colorize(plugin.getMessageManager().getMessage("ai-header", "&7&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")));
        sender.sendMessage(colorize(title));
        sender.sendMessage("");

        String[] lines = content.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue; 

            String formatted = trimmed.replaceAll("^#+\\s*", "")
                                      .replaceAll("\\*\\*(.*?)\\*\\*", "§e$1§f")
                                      .replaceAll("\\*(.*?)\\*", "§7$1§f")
                                      .replaceAll("`([^`]+)`", "§b$1§f");
            sender.sendMessage(colorize("&f" + formatted));
        }

        sender.sendMessage("");
        sender.sendMessage(colorize(plugin.getMessageManager().getMessage("ai-footer", "&7&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")));
    }

    private String colorize(String msg) {
        return msg.replace("&", "\u00a7");
    }
}
