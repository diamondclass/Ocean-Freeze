package ac.ocean.ai.client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class DocsCache {

    private volatile String cachedDocs = "";
    private volatile long lastDocsFetchTime = 0L;
    private static final long DOCS_CACHE_TTL_MS = 15 * 60 * 1000L;

    public String fetchDocumentationQuietly() {
        long now = System.currentTimeMillis();
        if (!cachedDocs.isEmpty() && (now - lastDocsFetchTime < DOCS_CACHE_TTL_MS)) {
            return cachedDocs;
        }

        try {
            URL u = new URL("https://anticheat.ac/docs");
            HttpURLConnection conn = (HttpURLConnection) u.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(7000);

            if (conn.getResponseCode() == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder rawHtml = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    rawHtml.append(line).append("\n");
                }
                br.close();

                String cleaned = cleanHtmlToText(rawHtml.toString());
                if (!cleaned.isEmpty()) {
                    cachedDocs = cleaned;
                    lastDocsFetchTime = now;
                    return cleaned;
                }
            }
        } catch (Exception ignored) {}

        return cachedDocs;
    }

    private String cleanHtmlToText(String html) {
        if (html == null || html.isEmpty()) return "";
        String stripped = html.replaceAll("(?is)<script.*?</script>", " ")
                              .replaceAll("(?is)<style.*?</style>", " ")
                              .replaceAll("(?is)<svg.*?</svg>", " ")
                              .replaceAll("(?is)<noscript.*?</noscript>", " ")
                              .replaceAll("<[^>]+>", " ")
                              .replace("&nbsp;", " ")
                              .replace("&amp;", "&")
                              .replace("&quot;", "\"")
                              .replace("&apos;", "'")
                              .replace("&lt;", "<")
                              .replace("&gt;", ">");

        stripped = stripped.replaceAll("\\s+", " ").trim();

        if (stripped.length() > 6000) {
            stripped = stripped.substring(0, 6000);
        }
        return stripped;
    }
}
