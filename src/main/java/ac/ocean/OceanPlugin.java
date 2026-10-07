package ac.ocean;

import lombok.AccessLevel;
import lombok.Getter;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;
import ac.ocean.commands.FreezeCommand;
import ac.ocean.commands.OceanCommand;
import ac.ocean.discord.WebhookManager;
import ac.ocean.listeners.FreezeListener;
import ac.ocean.manager.FreezeManager;
import ac.ocean.api.AntiCheatAPI;
import ac.ocean.api.UserLookupAPI;
import ac.ocean.api.DatabaseAPI;
import ac.ocean.ai.AIManager;
import ac.ocean.gui.ConfigGUI;
import ac.ocean.gui.FreezeGUI;
import ac.ocean.manager.MessageManager;

import java.util.ArrayList;
import java.util.List;

@Getter
public class OceanPlugin extends JavaPlugin {

    @Getter
    private static OceanPlugin instance;
    private FreezeManager freezeManager;
    private MessageManager messageManager;
    private AntiCheatAPI antiCheatAPI;
    private UserLookupAPI userLookupAPI;
    private DatabaseAPI databaseAPI;
    @Getter(AccessLevel.NONE)
    private AIManager aiManager;
    private WebhookManager webhookManager;
    private FreezeListener freezeListener;
    private FreezeGUI freezeGUI;

    @Deprecated
    public AIManager getAIManager() {
        return aiManager;
    }

    public AIManager getAiManager() {
        return aiManager;
    }

    public void applyCommandAliases() {
        if (!getConfig().isSet("commands.ss-aliases")) {
            return;
        }
        if (getCommand("ss") == null) {
            log(LogLevel.WARN, "Cannot apply command aliases: 'ss' is not declared in plugin.yml");
            return;
        }
        List<String> valid = new ArrayList<>();
        for (String raw : getConfig().getStringList("commands.ss-aliases")) {
            if (raw == null) {
                continue;
            }
            String alias = raw.trim().toLowerCase();
            if (alias.isEmpty() || alias.equals("ss") || valid.contains(alias)) {
                continue;
            }
            if (alias.equals("ocean") || !alias.matches("[a-z0-9_-]+")) {
                log(LogLevel.WARN, "Ignoring invalid ss alias '" + raw + "'");
                continue;
            }
            valid.add(alias);
        }
        getCommand("ss").setAliases(valid);
        log(LogLevel.INFO, "Freeze command aliases: " + valid);
    }

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();

        messageManager = new MessageManager(this);
        freezeManager = new FreezeManager(this);
        antiCheatAPI = new AntiCheatAPI(this);
        userLookupAPI = new UserLookupAPI(this);
        databaseAPI = new DatabaseAPI(this);
        aiManager = new AIManager(this);
        webhookManager = new WebhookManager(this);

        FreezeCommand freezeCommand = new FreezeCommand(this);
        if (getCommand("ss") != null) {
            getCommand("ss").setExecutor(freezeCommand);
        }

        OceanCommand oceanCommand = new OceanCommand(this);
        if (getCommand("ocean") != null) {
            getCommand("ocean").setExecutor(oceanCommand);
            getCommand("ocean").setTabCompleter(oceanCommand);
        }
        applyCommandAliases();

        freezeListener = new FreezeListener(this);
        freezeGUI = new FreezeGUI(this);
        getServer().getPluginManager().registerEvents(freezeListener, this);
        getServer().getPluginManager().registerEvents(freezeGUI, this);
        getServer().getPluginManager().registerEvents(new ConfigGUI(this), this);

        printStartupBanner();
    }

    @Override
    public void onDisable() {
        if (freezeManager != null) {
            freezeManager.unfreezeAll();
        }

        log(LogLevel.INFO, "╔════════════════════════════════════════╗");
        log(LogLevel.INFO, "&c║     Ocean - Disabled            ║");
        log(LogLevel.INFO, "&b║     Thanks for using Ocean!            ║");
        log(LogLevel.INFO, "╚════════════════════════════════════════╝");
    }

    public enum LogLevel {
        INFO, WARN
    }

    public void log(LogLevel level, String message) {
        String colored = ChatColor.translateAlternateColorCodes('&', message);
        if (level == LogLevel.WARN) {
            getLogger().warning(colored);
        } else {
            getLogger().info(colored);
        }
    }

    private void printStartupBanner() {
        String version = getDescription().getVersion();
        String author = getDescription().getAuthors().isEmpty() ? "Ocean Development" : getDescription().getAuthors().get(0);
        String freezeMode = getConfig().getString("settings.freeze-mode", "AUTO");
        String apiKey = getConfig().getString("anticheat.api-key", "YOUR_API_KEY_HERE");
        boolean apiConfigured = !apiKey.equals("YOUR_API_KEY_HERE") && !apiKey.isEmpty();
        String webhookUrl = getConfig().getString("discord.webhook-url", "none");
        boolean webhookConfigured = !webhookUrl.equals("none") && !webhookUrl.isEmpty();
        boolean aiConfigured = aiManager != null && aiManager.isConfigured();

        log(LogLevel.INFO, "╔════════════════════════════════════════╗");
        log(LogLevel.INFO, "║                                        ║");
        log(LogLevel.INFO, "║        &b🌊 OCEAN PLUGIN 🌊&r       ║");
        log(LogLevel.INFO, "║                                        ║");
        log(LogLevel.INFO, "╠════════════════════════════════════════╣");
        log(LogLevel.INFO, "║  &fVersion: " + String.format("%-28s", version) + "&r ║");
        log(LogLevel.INFO, "║  &fAuthor: " + String.format("%-28s", author) + "&r  ║");
        log(LogLevel.INFO, "╠════════════════════════════════════════╣");
        log(LogLevel.INFO, "║  &b📋 CONFIGURATION STATUS&r               ║");
        log(LogLevel.INFO, "║  ├─ &fFreeze Mode: " + String.format("%-20s", freezeMode) + " ║");
        String configured = "&a✓ Configured&r";
        String notSet = "&c✗ Not Set&r";
        log(LogLevel.INFO, "║  ├─ &fOcean API: " + String.format("%-15s", apiConfigured ? configured : notSet) + " ║");
        log(LogLevel.INFO, "║  ├─ &fDiscord Webhook: " + String.format("%-13s", webhookConfigured ? configured : notSet) + " ║");
        log(LogLevel.INFO, "║  └─ &fAI Assistant: " + String.format("%-16s", aiConfigured ? configured : notSet) + " ║");
        log(LogLevel.INFO, "╠════════════════════════════════════════╣");
        log(LogLevel.INFO, "║  &a🚀 Plugin successfully initialized!&r   ║");
        log(LogLevel.INFO, "╚════════════════════════════════════════╝");

        if (!apiConfigured) {
            log(LogLevel.WARN, "&e⚠ Ocean API key not configured!&r");
            log(LogLevel.WARN, "&e⚠ Set 'anticheat.api-key' in config.yml&r");
        }
    }
}
