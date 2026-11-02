package de.danoeh.antennapod.net.discovery;

import android.content.Context;
import android.util.Log;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.net.common.AntennapodHttpClient;
import de.danoeh.antennapod.parser.feed.FeedHandler;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.CacheControl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class TopEpisodesLoader {
    private static final String TAG = "TopEpisodesLoader";
    private static final int MAX_CONCURRENCY = 4;
    private final Context context;

    public TopEpisodesLoader(Context context) {
        this.context = context;
    }

    public List<TopEpisode> loadTopEpisodes(List<PodcastSearchResult> shows, int maxShows) {
        if (shows.isEmpty() || maxShows <= 0) {
            return new ArrayList<>();
        }
        List<PodcastSearchResult> targets = shows.subList(0, Math.min(shows.size(), maxShows));
        return Observable.fromIterable(targets)
                .flatMap(show ->
                        Observable.fromCallable(() -> Optional.ofNullable(loadNewestEpisode(show)))
                                .subscribeOn(Schedulers.io())
                                .filter(Optional::isPresent)
                                .map(Optional::get)
                                .onErrorResumeNext(throwable -> Observable.<TopEpisode>empty()), MAX_CONCURRENCY)
                .toList()
                .blockingGet();
    }

    private TopEpisode loadNewestEpisode(PodcastSearchResult show) throws Exception {
        String rssUrl = show.feedUrl;
        if (rssUrl == null) {
            return null;
        }
        if (PodcastSearcherRegistry.urlNeedsLookup(rssUrl)) {
            rssUrl = PodcastSearcherRegistry.lookupUrl(rssUrl).blockingGet();
        }
        OkHttpClient client = AntennapodHttpClient.getHttpClient();
        Request request = new Request.Builder()
                .cacheControl(new CacheControl.Builder().maxStale(1, TimeUnit.DAYS).build())
                .url(rssUrl)
                .build();
        File tmp = File.createTempFile("top_episode", ".xml", context.getCacheDir());
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return null;
            }
            try (InputStream in = response.body().byteStream()) {
                copyToFile(in, tmp);
            }
            Feed feed = new Feed(rssUrl, null);
            feed.setLocalFileUrl(tmp.getAbsolutePath());
            Feed parsed = new FeedHandler().parseFeed(feed).feed;
            FeedItem newest = null;
            for (FeedItem item : parsed.getItems()) {
                if (newest == null || after(item.getPubDate(), newest.getPubDate())) {
                    newest = item;
                }
            }
            if (newest == null) {
                return null;
            }
            int durationMs = 0;
            if (newest.getMedia() != null) {
                durationMs = newest.getMedia().getDuration();
            }
            String imageUrl = parsed.getImageUrl();
            if (imageUrl == null) {
                imageUrl = show.imageUrl;
            }
            long pubDateMillis = newest.getPubDate() == null ? 0 : newest.getPubDate().getTime();
            return new TopEpisode(newest.getTitle(), show.title, imageUrl, rssUrl,
                    durationMs, pubDateMillis);
        } finally {
            if (!tmp.delete()) {
                Log.d(TAG, "Failed to delete temp file");
            }
        }
    }

    private static void copyToFile(InputStream in, File tmp) throws IOException {
        try (OutputStream out = new FileOutputStream(tmp)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    private static boolean after(Date date, Date other) {
        if (date == null) {
            return false;
        }
        if (other == null) {
            return true;
        }
        return date.after(other);
    }
}
