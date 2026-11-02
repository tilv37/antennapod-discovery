package de.danoeh.antennapod.net.discovery;

import androidx.annotation.Nullable;

public final class TopEpisode {
    public final String title;
    public final String showTitle;
    @Nullable
    public final String imageUrl;
    @Nullable
    public final String feedUrl;
    public final int durationMs;
    public final long pubDateMillis;
    @Nullable
    public final String audioUrl;
    @Nullable
    public final String description;

    public TopEpisode(String title, String showTitle, @Nullable String imageUrl,
                      String feedUrl, int durationMs, long pubDateMillis) {
        this(title, showTitle, imageUrl, feedUrl, null, null, durationMs, pubDateMillis);
    }

    public TopEpisode(String title, String showTitle, @Nullable String imageUrl,
                      String feedUrl, @Nullable String audioUrl, int durationMs, long pubDateMillis) {
        this(title, showTitle, imageUrl, feedUrl, audioUrl, null, durationMs, pubDateMillis);
    }

    public TopEpisode(String title, String showTitle, @Nullable String imageUrl,
                      String feedUrl, @Nullable String audioUrl, @Nullable String description,
                      int durationMs, long pubDateMillis) {
        this.title = title;
        this.showTitle = showTitle;
        this.imageUrl = imageUrl;
        this.feedUrl = feedUrl;
        this.audioUrl = audioUrl;
        this.description = description;
        this.durationMs = durationMs;
        this.pubDateMillis = pubDateMillis;
    }
}
