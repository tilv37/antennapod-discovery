package de.danoeh.antennapod.net.discovery;

import androidx.annotation.Nullable;

public final class TopEpisode {
    public final String title;
    public final String showTitle;
    @Nullable
    public final String imageUrl;
    public final String feedUrl;
    public final int durationMs;
    public final long pubDateMillis;

    public TopEpisode(String title, String showTitle, @Nullable String imageUrl,
                      String feedUrl, int durationMs, long pubDateMillis) {
        this.title = title;
        this.showTitle = showTitle;
        this.imageUrl = imageUrl;
        this.feedUrl = feedUrl;
        this.durationMs = durationMs;
        this.pubDateMillis = pubDateMillis;
    }
}
