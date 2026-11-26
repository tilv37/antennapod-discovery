package de.danoeh.antennapod.ui.discovery;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.SearchView;
import androidx.core.text.HtmlCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.danoeh.antennapod.net.discovery.ItunesPodcastSearcher;
import de.danoeh.antennapod.net.discovery.PodcastSearchResult;
import de.danoeh.antennapod.net.discovery.PodcastSearcher;
import de.danoeh.antennapod.net.discovery.PodcastSearcherRegistry;
import de.danoeh.antennapod.net.discovery.TopEpisode;
import de.danoeh.antennapod.ui.appstartintent.OnlineFeedviewActivityStarter;
import de.danoeh.antennapod.ui.common.Converter;
import de.danoeh.antennapod.ui.common.Keyboard;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class OnlineSearchFragment extends Fragment {

    private static final String TAG = "FyydSearchFragment";
    private static final String ARG_SEARCHER = "searcher";
    private static final String ARG_QUERY = "query";
    private static final String CONTENT_SHOWS = "shows";
    private static final String CONTENT_EPISODES = "episodes";

    /**
     * Adapter responsible with the search results
     */
    private OnlineSearchAdapter adapter;
    private EpisodeSearchAdapter episodeAdapter;
    private PodcastSearcher searchProvider;
    private GridView gridView;
    private ProgressBar progressBar;
    private TextView txtvError;
    private Button butRetry;
    private TextView txtvEmpty;
    private ChipGroup contentChipGroup;

    /**
     * List of podcasts retreived from the search
     */
    private List<PodcastSearchResult> searchResults;
    private List<TopEpisode> currentEpisodes;
    private final Map<String, String> showFeedCache = new HashMap<>();
    private Disposable disposable;
    private Disposable tapDisposable;
    private String contentType = CONTENT_SHOWS;
    private String currentQuery;

    public static OnlineSearchFragment newInstance(Class<? extends PodcastSearcher> searchProvider) {
        return newInstance(searchProvider, null);
    }

    public static OnlineSearchFragment newInstance(Class<? extends PodcastSearcher> searchProvider, String query) {
        OnlineSearchFragment fragment = new OnlineSearchFragment();
        Bundle arguments = new Bundle();
        arguments.putString(ARG_SEARCHER, searchProvider.getName());
        arguments.putString(ARG_QUERY, query);
        fragment.setArguments(arguments);
        return fragment;
    }

    /**
     * Constructor
     */
    public OnlineSearchFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        for (PodcastSearcherRegistry.SearcherInfo info : PodcastSearcherRegistry.getSearchProviders()) {
            if (info.searcher.getClass().getName().equals(getArguments().getString(ARG_SEARCHER))) {
                searchProvider = info.searcher;
                break;
            }
        }
        if (searchProvider == null) {
            throw new IllegalArgumentException("Podcast searcher not found");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_online_search, container, false);
        root.findViewById(R.id.genreScroll).setVisibility(View.GONE);
        contentChipGroup = root.findViewById(R.id.contentChipGroup);
        setupContentChips();
        gridView = root.findViewById(R.id.gridView);
        adapter = new OnlineSearchAdapter(getActivity(), new ArrayList<>());
        episodeAdapter = new EpisodeSearchAdapter(getActivity(), new ArrayList<>());
        episodeAdapter.setEpisodeClickListener(new EpisodeSearchAdapter.EpisodeClickListener() {
            @Override
            public void onEpisodeClicked(int position) {
                showEpisodeDialog(position);
            }

            @Override
            public void onShowClicked(int position) {
                openEpisodeShow(position);
            }
        });
        gridView.setAdapter(CONTENT_EPISODES.equals(contentType) ? episodeAdapter : adapter);

        //Show information about the podcast when the list item is clicked
        gridView.setOnItemClickListener((parent, view1, position, id) -> {
            if (CONTENT_EPISODES.equals(contentType)) {
                showEpisodeDialog(position);
                return;
            }
            PodcastSearchResult podcast = searchResults.get(position);
            startActivity(new OnlineFeedviewActivityStarter(getContext(), podcast.feedUrl).getIntent());
        });
        progressBar = root.findViewById(R.id.progressBar);
        txtvError = root.findViewById(R.id.txtvError);
        butRetry = root.findViewById(R.id.butRetry);
        txtvEmpty = root.findViewById(android.R.id.empty);
        TextView txtvPoweredBy = root.findViewById(R.id.search_powered_by);
        txtvPoweredBy.setText(getString(R.string.search_powered_by, searchProvider.getName()));
        setupToolbar(root.findViewById(R.id.toolbar));

        gridView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == SCROLL_STATE_TOUCH_SCROLL) {
                    Keyboard.hide(getActivity());
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            }
        });
        return root;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (disposable != null) {
            disposable.dispose();
        }
        if (tapDisposable != null) {
            tapDisposable.dispose();
        }
        adapter = null;
        episodeAdapter = null;
    }

    private void setupToolbar(MaterialToolbar toolbar) {
        toolbar.inflateMenu(R.menu.online_search);
        toolbar.setNavigationOnClickListener(v -> getParentFragmentManager().popBackStack());

        MenuItem searchItem = toolbar.getMenu().findItem(R.id.action_search);
        final SearchView sv = (SearchView) searchItem.getActionView();
        sv.setQueryHint(getString(R.string.search_podcast_hint));
        sv.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                sv.clearFocus();
                search(s);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                return false;
            }
        });
        sv.setOnQueryTextFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                Keyboard.show(getContext(), view.findFocus());
            }
        });
        searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                getActivity().getSupportFragmentManager().popBackStack();
                return true;
            }
        });
        searchItem.expandActionView();

        if (getArguments().getString(ARG_QUERY, null) != null) {
            sv.setQuery(getArguments().getString(ARG_QUERY, null), true);
        }
    }

    private void setupContentChips() {
        contentChipGroup.removeAllViews();
        addContentChip(R.string.discover_content_shows, CONTENT_SHOWS);
        addContentChip(R.string.discover_content_episodes, CONTENT_EPISODES);
    }

    private void addContentChip(int labelRes, String type) {
        Chip chip = new Chip(getContext());
        chip.setText(labelRes);
        chip.setCheckable(true);
        chip.setChecked(type.equals(contentType));
        chip.setOnClickListener(v -> {
            contentType = type;
            for (int j = 0; j < contentChipGroup.getChildCount(); j++) {
                Chip other = (Chip) contentChipGroup.getChildAt(j);
                other.setChecked(other == chip);
            }
            gridView.setAdapter(CONTENT_EPISODES.equals(contentType) ? episodeAdapter : adapter);
            if (currentQuery != null) {
                search(currentQuery);
            }
        });
        contentChipGroup.addView(chip);
    }

    private void search(String query) {
        currentQuery = query;
        if (CONTENT_EPISODES.equals(contentType)) {
            searchEpisodes(query);
            return;
        }
        if (disposable != null) {
            disposable.dispose();
        }
        showOnlyProgressBar();
        disposable = searchProvider.search(query).subscribe(result -> {
            searchResults = result;
            progressBar.setVisibility(View.GONE);
            adapter.clear();
            adapter.addAll(searchResults);
            adapter.notifyDataSetInvalidated();
            gridView.setVisibility(!searchResults.isEmpty() ? View.VISIBLE : View.GONE);
            txtvEmpty.setVisibility(searchResults.isEmpty() ? View.VISIBLE : View.GONE);
            txtvEmpty.setText(getString(R.string.no_results_for_query, query));
        }, error -> {
                Log.e(TAG, Log.getStackTraceString(error));
                progressBar.setVisibility(View.GONE);
                txtvError.setText(error.toString());
                txtvError.setVisibility(View.VISIBLE);
                butRetry.setOnClickListener(v -> search(query));
                butRetry.setVisibility(View.VISIBLE);
            });
    }

    private void searchEpisodes(String query) {
        if (disposable != null) {
            disposable.dispose();
        }
        showOnlyProgressBar();
        disposable = searchProvider.searchEpisodes(query).subscribe(episodes -> {
            progressBar.setVisibility(View.GONE);
            updateEpisodeData(episodes, query);
        }, error -> {
                Log.e(TAG, Log.getStackTraceString(error));
                progressBar.setVisibility(View.GONE);
                txtvError.setText(error.toString());
                txtvError.setVisibility(View.VISIBLE);
                butRetry.setOnClickListener(v -> search(query));
                butRetry.setVisibility(View.VISIBLE);
            });
    }

    private void updateEpisodeData(List<TopEpisode> episodes, String query) {
        List<PodcastSearchResult> results = toSearchResults(episodes);
        searchResults = results;
        episodeAdapter.clear();
        if (!results.isEmpty()) {
            gridView.setVisibility(View.VISIBLE);
            txtvEmpty.setVisibility(View.GONE);
            for (PodcastSearchResult p : results) {
                episodeAdapter.add(p);
            }
            episodeAdapter.notifyDataSetInvalidated();
        } else {
            gridView.setVisibility(View.GONE);
            txtvEmpty.setVisibility(View.VISIBLE);
            txtvEmpty.setText(getString(R.string.no_results_for_query, query));
        }
    }

    private List<PodcastSearchResult> toSearchResults(List<TopEpisode> episodes) {
        currentEpisodes = episodes;
        List<PodcastSearchResult> results = new ArrayList<>();
        for (TopEpisode episode : episodes) {
            String author = episode.showTitle;
            if (episode.durationMs > 0) {
                author += " · " + Converter.getDurationStringMinutesSeconds(episode.durationMs);
            }
            results.add(new PodcastSearchResult(episode.title, episode.imageUrl,
                    episode.feedUrl, author));
        }
        return results;
    }

    private void showEpisodeDialog(int position) {
        if (currentEpisodes == null || position < 0 || position >= currentEpisodes.size()) {
            return;
        }
        TopEpisode episode = currentEpisodes.get(position);
        StringBuilder message = new StringBuilder(episode.showTitle);
        if (episode.durationMs > 0) {
            message.append(" · ").append(Converter.getDurationStringMinutesSeconds(episode.durationMs));
        }
        if (episode.description != null && !episode.description.trim().isEmpty()) {
            message.append("\n\n").append(HtmlCompat.fromHtml(episode.description,
                    HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim());
        }
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext())
                .setTitle(episode.title)
                .setMessage(message.toString())
                .setPositiveButton(R.string.open_podcast, (dialog, which) -> openEpisodeShow(position))
                .setNegativeButton(R.string.cancel_label, null);
        if (episode.audioUrl != null && !episode.audioUrl.isEmpty()) {
            builder.setNeutralButton(R.string.play_label, (dialog, which) -> playEpisodeInApp(episode));
        }
        builder.show();
    }

    private void playEpisodeInApp(TopEpisode episode) {
        if (tapDisposable != null) {
            tapDisposable.dispose();
        }
        tapDisposable = EpisodeStreamStarter.start(getContext(), episode, () -> playEpisodeAudio(episode));
    }

    private void playEpisodeAudio(TopEpisode episode) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(Uri.parse(episode.audioUrl), "audio/*"));
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, Log.getStackTraceString(e));
            Toast.makeText(getContext(), R.string.playback_error_generic, Toast.LENGTH_LONG).show();
        }
    }

    private void openEpisodeShow(int position) {
        if (currentEpisodes == null || position < 0 || position >= currentEpisodes.size()) {
            return;
        }
        TopEpisode episode = currentEpisodes.get(position);
        if (episode.feedUrl != null) {
            startActivity(new OnlineFeedviewActivityStarter(getContext(), episode.feedUrl).getIntent());
            return;
        }
        String cacheKey = episode.showId != null ? episode.showId : episode.showTitle;
        String cached = showFeedCache.get(cacheKey);
        if (cached != null) {
            startActivity(new OnlineFeedviewActivityStarter(getContext(), cached).getIntent());
            return;
        }
        if (tapDisposable != null) {
            tapDisposable.dispose();
        }
        progressBar.setVisibility(View.VISIBLE);
        final String key = cacheKey;
        tapDisposable = resolveShowFeed(episode)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(feedUrl -> {
                    progressBar.setVisibility(View.GONE);
                    if (feedUrl != null) {
                        showFeedCache.put(key, feedUrl);
                        startActivity(new OnlineFeedviewActivityStarter(
                                getContext(), feedUrl).getIntent());
                    } else {
                        Toast.makeText(getContext(), R.string.search_status_no_results,
                                Toast.LENGTH_SHORT).show();
                    }
                }, error -> {
                    progressBar.setVisibility(View.GONE);
                    Log.e(TAG, Log.getStackTraceString(error));
                    Toast.makeText(getContext(), R.string.search_status_no_results,
                            Toast.LENGTH_SHORT).show();
                });
    }

    private Single<String> resolveShowFeed(TopEpisode episode) {
        return Single.fromCallable(() -> {
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
            return null;
        });
    }

    private void showOnlyProgressBar() {
        gridView.setVisibility(View.GONE);
        txtvError.setVisibility(View.GONE);
        butRetry.setVisibility(View.GONE);
        txtvEmpty.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
    }
}
