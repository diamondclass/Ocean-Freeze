package ac.ocean.ai.client;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ac.ocean.OceanPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class PlayerContextService {

    private final OceanPlugin plugin;

    public PlayerContextService(OceanPlugin plugin) {
        this.plugin = plugin;
    }

    public String extractAndFetchContext(String query) {
        StringBuilder ctx = new StringBuilder();

        String[] words = query.split("\\s+");
        for (String w : words) {
            String clean = w.replaceAll("[^a-zA-Z0-9]", "");
            if ((clean.length() >= 6 && clean.length() <= 8) && !clean.equalsIgnoreCase("frozen") && !clean.equalsIgnoreCase("freeze") && !clean.equalsIgnoreCase("analyze")) {
                String pinResults = fetchPinResultsQuietly(clean);
                if (!pinResults.isEmpty()) {
                    ctx.append("\nScan Results for PIN '").append(clean).append("':\n").append(pinResults);
                } else {
                    String pinStatus = fetchPinStatusQuietly(clean);
                    if (!pinStatus.isEmpty()) {
                        ctx.append("\nScan Status for PIN '").append(clean).append("':\n").append(pinStatus);
                    }
                }
            }
        }

        for (String w : words) {
            String clean = w.replaceAll("[^0-9]", "");
            if (clean.length() >= 5 && clean.length() <= 25) {
                String db = fetchDbQuietly(clean);
                String risk = fetchRiskQuietly(clean);
                String lookup = fetchLookupQuietly(clean);
                ctx.append("\nDiscord ID Mentioned (").append(clean).append("):");
                if (!db.isEmpty()) ctx.append("\n- Cheater DB: ").append(db);
                if (!risk.isEmpty()) ctx.append("\n- Risk Score: ").append(risk);
                if (!lookup.isEmpty()) ctx.append("\n- Scanned History: ").append(lookup);
            }
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (query.toLowerCase().contains(p.getName().toLowerCase())) {
                boolean frozen = plugin.getFreezeManager().isFrozen(p);
                String pin = "None";
                if (frozen && plugin.getFreezeManager().getFrozenPlayer(p.getUniqueId()) != null) {
                    pin = plugin.getFreezeManager().getFrozenPlayer(p.getUniqueId()).getScanPin();
                    if (pin != null && !pin.isEmpty() && !pin.equalsIgnoreCase("None")) {
                        String scanRes = fetchPinResultsQuietly(pin);
                        if (!scanRes.isEmpty()) {
                            ctx.append("\nActive Scan Data for ").append(p.getName()).append(" (PIN: ").append(pin).append("):\n").append(scanRes);
                        }
                    }
                }
                ctx.append("\nPlayer Mentioned: ").append(p.getName()).append(" (Frozen: ").append(frozen).append(", Active PIN: ").append(pin).append(")");
            }
        }

        return ctx.toString().trim();
    }

    public String fetchPinResultsQuietly(String pin) {
        String apiKey = plugin.getConfig().getString("anticheat.api-key");
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("YOUR_API_KEY_HERE")) return "";
        try {
            URL u = new URL("https://api.anticheat.ac/v1/pins/" + pin + "/results");
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setRequestMethod("GET");
            c.setRequestProperty("x-api-key", apiKey);
            c.setRequestProperty("Accept", "application/json");
            c.setConnectTimeout(6000);
            c.setReadTimeout(6000);
            if (c.getResponseCode() == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String l;
                while ((l = br.readLine()) != null) sb.append(l);
                br.close();
                return sb.toString();
            }
        } catch (Exception ignored) {}
        return "";
    }

    public String fetchPinStatusQuietly(String pin) {
        String apiKey = plugin.getConfig().getString("anticheat.api-key");
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("YOUR_API_KEY_HERE")) return "";
        try {
            URL u = new URL("https://api.anticheat.ac/v1/pins/" + pin + "/status");
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setRequestMethod("GET");
            c.setRequestProperty("x-api-key", apiKey);
            c.setRequestProperty("Accept", "application/json");
            c.setConnectTimeout(6000);
            c.setReadTimeout(6000);
            if (c.getResponseCode() == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String l;
                while ((l = br.readLine()) != null) sb.append(l);
                br.close();
                return sb.toString();
            }
        } catch (Exception ignored) {}
        return "";
    }

    public String fetchDbQuietly(String discordId) {
        String apiKey = plugin.getConfig().getString("anticheat.api-key");
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("YOUR_API_KEY_HERE")) return "";
        try {
            URL u = new URL("https://api.anticheat.ac/v1/db/query/" + discordId);
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setRequestMethod("GET");
            c.setRequestProperty("x-api-key", apiKey);
            c.setRequestProperty("Accept", "application/json");
            c.setConnectTimeout(6000);
            c.setReadTimeout(6000);
            if (c.getResponseCode() == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String l;
                while ((l = br.readLine()) != null) sb.append(l);
                br.close();
                return sb.toString();
            }
        } catch (Exception ignored) {}
        return "";
    }

    public String fetchRiskQuietly(String discordId) {
        String apiKey = plugin.getConfig().getString("anticheat.api-key");
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("YOUR_API_KEY_HERE")) return "";
        try {
            URL u = new URL("https://api.anticheat.ac/v1/users/" + discordId + "/risk-score");
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setRequestMethod("GET");
            c.setRequestProperty("x-api-key", apiKey);
            c.setRequestProperty("Accept", "application/json");
            c.setConnectTimeout(6000);
            c.setReadTimeout(6000);
            if (c.getResponseCode() == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String l;
                while ((l = br.readLine()) != null) sb.append(l);
                br.close();
                return sb.toString();
            }
        } catch (Exception ignored) {}
        return "";
    }

    public String fetchLookupQuietly(String discordId) {
        String apiKey = plugin.getConfig().getString("anticheat.api-key");
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("YOUR_API_KEY_HERE")) return "";
        try {
            URL u = new URL("https://api.anticheat.ac/v1/scanned-users/lookup/" + discordId);
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setRequestMethod("GET");
            c.setRequestProperty("x-api-key", apiKey);
            c.setRequestProperty("Accept", "application/json");
            c.setConnectTimeout(6000);
            c.setReadTimeout(6000);
            if (c.getResponseCode() == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String l;
                while ((l = br.readLine()) != null) sb.append(l);
                br.close();
                return sb.toString();
            }
        } catch (Exception ignored) {}
        return "";
    }
}
