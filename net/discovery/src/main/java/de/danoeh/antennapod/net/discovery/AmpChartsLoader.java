package de.danoeh.antennapod.net.discovery;

import android.util.Base64;
import android.util.Log;
import de.danoeh.antennapod.net.common.AntennapodHttpClient;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AmpChartsLoader {
    private static final String TAG = "AmpCharts";
    private static final long TOKEN_REFRESH_MARGIN_SEC = 86400;
    private static String cachedToken;
    private static long cachedTokenExp;
    private static final Pattern JS_BUNDLE_PATTERN = Pattern.compile("/assets/index~[a-z0-9]+\\.js");
    private static final Pattern JWT_PATTERN = Pattern.compile("eyJ[a-zA-Z0-9_\\-.]+");

    private AmpChartsLoader() {
    }

    public static List<TopEpisode> loadTopEpisodes(String country, String genreId, int limit)
            throws IOException, JSONException {
        OkHttpClient client = AntennapodHttpClient.getHttpClient();
        String token = getToken(client);
        StringBuilder url = new StringBuilder("https://amp-api.podcasts.apple.com/v1/catalog/")
                .append(country.toLowerCase())
                .append("/charts?types=podcast-episodes&limit=")
                .append(Math.max(1, Math.min(limit, 200)));
        if (genreId != null && !genreId.isEmpty() && !"0".equals(genreId)) {
            url.append("&genre=").append(genreId);
        }
        Request request = new Request.Builder()
                .url(url.toString())
                .header("Authorization", "Bearer " + token)
                .header("Origin", "https://podcasts.apple.com")
                .build();
        String body;
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Charts request failed: " + response);
            }
            body = response.body().string();
        }
        List<TopEpisode> episodes = new ArrayList<>();
        JSONArray charts = new JSONObject(body).getJSONObject("results").getJSONArray("podcast-episodes");
        if (charts.length() == 0) {
            return episodes;
        }
        JSONArray data = charts.getJSONObject(0).getJSONArray("data");
        for (int i = 0; i < data.length(); i++) {
            JSONObject attributes = data.getJSONObject(i).getJSONObject("attributes");
            String title = attributes.optString("name", "");
            String showTitle = attributes.optString("artistName", "");
            if (title.isEmpty()) {
                continue;
            }
            String imageUrl = null;
            JSONObject artwork = attributes.optJSONObject("artwork");
            if (artwork != null) {
                imageUrl = artwork.optString("url", null);
                if (imageUrl != null) {
                    imageUrl = imageUrl.replace("{w}x{h}", "400x400").replace("{f}", "jpg");
                }
            }
            episodes.add(new TopEpisode(title, showTitle, imageUrl, null,
                    attributes.optString("assetUrl", null),
                    attributes.optInt("durationInMilliseconds", 0), 0));
        }
        return episodes;
    }

    private static synchronized String getToken(OkHttpClient client) throws IOException {
        long now = System.currentTimeMillis() / 1000;
        if (cachedToken != null && now < cachedTokenExp - TOKEN_REFRESH_MARGIN_SEC) {
            return cachedToken;
        }
        String html = get(client, "https://podcasts.apple.com/cn/charts", null);
        Matcher bundle = JS_BUNDLE_PATTERN.matcher(html);
        if (!bundle.find()) {
            throw new IOException("Charts app bundle not found");
        }
        String js = get(client, "https://podcasts.apple.com" + bundle.group(), null);
        Matcher candidates = JWT_PATTERN.matcher(js);
        while (candidates.find()) {
            String token = candidates.group();
            long exp = jwtExpiry(token);
            if (exp > now) {
                cachedToken = token;
                cachedTokenExp = exp;
                return token;
            }
        }
        throw new IOException("Charts token not found");
    }

    private static long jwtExpiry(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return 0;
            }
            String payload = new String(Base64.decode(parts[1], Base64.URL_SAFE), StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(payload);
            if (!json.has("iss")) {
                return 0;
            }
            return json.optLong("exp", 0);
        } catch (Exception e) {
            Log.d(TAG, "Not a usable token");
            return 0;
        }
    }

    private static String get(OkHttpClient client, String url, String bearer) throws IOException {
        Request.Builder builder = new Request.Builder().url(url);
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer)
                    .header("Origin", "https://podcasts.apple.com");
        }
        try (Response response = client.newCall(builder.build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Request failed: " + response);
            }
            return response.body().string();
        }
    }
}
