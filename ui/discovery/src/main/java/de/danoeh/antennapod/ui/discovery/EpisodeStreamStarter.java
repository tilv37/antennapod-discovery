package de.danoeh.antennapod.ui.discovery;

import android.content.Context;
import android.util.Log;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting;
import de.danoeh.antennapod.model.playback.MediaType;
import de.danoeh.antennapod.net.discovery.ItunesPodcastSearcher;
import de.danoeh.antennapod.net.discovery.PodcastSearchResult;
import de.danoeh.antennapod.net.discovery.PodcastSearcherRegistry;
import de.danoeh.antennapod.net.discovery.TopEpisode;
import de.danoeh.antennapod.playback.service.PlaybackServiceInterface;
import de.danoeh.antennapod.playback.service.PlaybackServiceStarter;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.FeedDatabaseWriter;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class EpisodeStreamStarter {
    private static final String TAG = "EpisodeStreamStarter";

    private EpisodeStreamStarter() {
    }

    public static Disposable start(Context context, TopEpisode episode, Runnable onFailure) {
        Context appContext = context.getApplicationContext();
        return Single.fromCallable(() -> findOrCreateMedia(appContext, episode))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(media -> {
                    if (media == null) {
                        onFailure.run();
                        return;
                    }
                    new PlaybackServiceStarter(context, media).callEvenIfRunning(true).start();
                    if (media.getMediaType() == MediaType.VIDEO) {
                        context.startActivity(PlaybackServiceInterface.getPlayerActivityIntent(context, media));
                    }
                }, error -> {
                    Log.e(TAG, Log.getStackTraceString(error));
                    onFailure.run();
                });
    }

    private static FeedMedia findOrCreateMedia(Context context, TopEpisode episode) {
        if (episode.audioUrl == null || episode.audioUrl.isEmpty()) {
            return null;
        }
        String feedUrl = episode.feedUrl;
        if (feedUrl == null) {
            feedUrl = resolveFeedUrl(episode);
        }
        if (feedUrl == null) {
            return null;
        }
        FeedMedia existing = findEpisodeMedia(feedUrl, episode.audioUrl);
        if (existing != null) {
            return existing;
        }
        Feed feed = new Feed(feedUrl, null, meaningfulTitle(episode.showTitle));
        feed.setState(Feed.STATE_NOT_SUBSCRIBED);
        if (episode.imageUrl != null && !episode.imageUrl.isEmpty()) {
            feed.setImageUrl(episode.imageUrl);
        }
        feed.setPreferences(new FeedPreferences(0, FeedPreferences.AutoDownloadSetting.GLOBAL,
                FeedPreferences.AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF,
                FeedPreferences.NewEpisodesAction.GLOBAL, null, null));
        FeedItem item = new FeedItem(0, episode.title, episode.audioUrl, null,
                episode.pubDateMillis > 0 ? new Date(episode.pubDateMillis) : null,
                FeedItem.UNPLAYED, feed);
        if (episode.description != null && !episode.description.isEmpty()) {
            item.setDescriptionIfLonger(episode.description);
        }
        FeedMedia media = new FeedMedia(item, episode.audioUrl, 0, mimeType(episode.audioUrl));
        item.setMedia(media);
        feed.setItems(Collections.singletonList(item));
        Feed resultFeed = FeedDatabaseWriter.updateFeed(context, feed, false);
        DBReader.loadFeedDataOfFeedItemList(resultFeed.getItems());
        return findMediaByUrl(resultFeed.getItems(), episode.audioUrl);
    }

    private static FeedMedia findEpisodeMedia(String feedUrl, String audioUrl) {
        for (Feed feed : DBReader.getFeedList()) {
            if (!feedUrl.equals(feed.getIdentifyingValue())) {
                continue;
            }
            List<FeedItem> items = DBReader.getFeedItemList(feed, FeedItemFilter.unfiltered(),
                    SortOrder.DATE_NEW_OLD, 0, Integer.MAX_VALUE);
            DBReader.loadFeedDataOfFeedItemList(items);
            FeedMedia media = findMediaByUrl(items, audioUrl);
            if (media != null) {
                return media;
            }
        }
        return null;
    }

    private static FeedMedia findMediaByUrl(List<FeedItem> items, String audioUrl) {
        if (items == null) {
            return null;
        }
        for (FeedItem item : items) {
            FeedMedia media = item.getMedia();
            if (media != null
                    && (audioUrl.equals(media.getDownloadUrl()) || audioUrl.equals(media.getStreamUrl()))) {
                return media;
            }
        }
        return null;
    }

    private static String resolveFeedUrl(TopEpisode episode) {
        try {
            if (episode.showId != null) {
                return PodcastSearcherRegistry.lookupUrl(
                        "https://podcasts.apple.com/cn/podcast/x/id" + episode.showId)
                        .blockingGet();
            }
            for (PodcastSearchResult result : new ItunesPodcastSearcher().search(episode.showTitle)
                    .blockingGet()) {
                if (result.feedUrl != null) {
                    return result.feedUrl;
                }
            }
        } catch (Exception e) {
            Log.d(TAG, Log.getStackTraceString(e));
        }
        return null;
    }

    private static String meaningfulTitle(String title) {
        if (title == null || title.trim().isEmpty() || "Unknown".equals(title.trim())) {
            return null;
        }
        return title;
    }

    private static String mimeType(String audioUrl) {
        String lower = audioUrl.toLowerCase(Locale.US);
        if (lower.endsWith(".mp3")) {
            return "audio/mpeg";
        } else if (lower.endsWith(".m4a")) {
            return "audio/mp4";
        } else if (lower.endsWith(".mp4") || lower.endsWith(".m4v") || lower.endsWith(".mov")) {
            return "video/mp4";
        }
        return null;
    }
}
