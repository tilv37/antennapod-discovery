package de.danoeh.antennapod.net.discovery;

import io.reactivex.rxjava3.core.Single;
import java.util.ArrayList;
import java.util.List;

public interface PodcastSearcher {
    Single<List<PodcastSearchResult>> search(String query);

    default Single<List<TopEpisode>> searchEpisodes(String query) {
        return Single.just(new ArrayList<>());
    }

    Single<String> lookupUrl(String resultUrl);

    boolean urlNeedsLookup(String resultUrl);

    String getName();
}
