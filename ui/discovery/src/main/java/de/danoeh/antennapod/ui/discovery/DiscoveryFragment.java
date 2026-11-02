package de.danoeh.antennapod.ui.discovery;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AbsListView;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;
import de.danoeh.antennapod.net.discovery.BuildConfig;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.event.DiscoveryDefaultUpdateEvent;
import de.danoeh.antennapod.net.discovery.ItunesTopListLoader;
import de.danoeh.antennapod.net.discovery.PodcastSearchResult;
import de.danoeh.antennapod.net.discovery.TopEpisode;
import de.danoeh.antennapod.net.discovery.TopEpisodesLoader;
import de.danoeh.antennapod.ui.appstartintent.OnlineFeedviewActivityStarter;
import de.danoeh.antennapod.ui.common.Converter;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Searches iTunes store for top podcasts and displays results in a list.
 */
public class DiscoveryFragment extends Fragment implements Toolbar.OnMenuItemClickListener {
    public static final String TAG = "DiscoveryFragment";
    private static final int INITIAL_TOP_PODCASTS = 50;
    private static final int TOP_PODCASTS_STEP = 50;
    private static final int MAX_TOP_PODCASTS = 200;
    private static final int INITIAL_EPISODE_WINDOW = 12;
    private static final int EPISODE_WINDOW_STEP = 12;
    private static final int MAX_EPISODE_WINDOW = 96;
    private static final String CONTENT_SHOWS = "shows";
    private static final String CONTENT_EPISODES = "episodes";
    private int currentLimit = INITIAL_TOP_PODCASTS;
    private int episodeWindow = INITIAL_EPISODE_WINDOW;
    private boolean loadingMore = false;
    private boolean meteredToastShown = false;
    private SharedPreferences prefs;

    /**
     * Adapter responsible with the search results.
     */
    private OnlineSearchAdapter adapter;
    private GridView gridView;
    private ProgressBar progressBar;
    private TextView txtvError;
    private Button butRetry;
    private TextView txtvEmpty;

    /**
     * List of podcasts retreived from the search.
     */
    private List<PodcastSearchResult> searchResults;
    private List<PodcastSearchResult> topList;
    private Disposable disposable;
    private String countryCode = "CN";
    private String genreId = "";
    private String contentType = CONTENT_SHOWS;
    private boolean hidden;
    private boolean needsConfirm;
    private MaterialToolbar toolbar;
    private ChipGroup genreChipGroup;
    private ChipGroup contentChipGroup;

    private static final String[] GENRE_IDS = {"", "1318", "1321", "1489", "1303", "1324", "1302", "1317", "1305"};
    private static final int[] GENRE_LABELS = {R.string.discover_genre_all, R.string.discover_genre_technology,
        R.string.discover_genre_business, R.string.discover_genre_news, R.string.discover_genre_comedy,
        R.string.discover_genre_society, R.string.discover_genre_education, R.string.discover_genre_health,
        R.string.discover_genre_kids};

    public DiscoveryFragment() {
        // Required empty public constructor
    }

    /**
     * Replace adapter data with provided search results from SearchTask.
     *
     * @param result List of Podcast objects containing search results
     */
    private void updateData(List<PodcastSearchResult> result) {
        this.searchResults = result;
        adapter.clear();
        if (result != null && result.size() > 0) {
            gridView.setVisibility(View.VISIBLE);
            txtvEmpty.setVisibility(View.GONE);
            for (PodcastSearchResult p : result) {
                adapter.add(p);
            }
            adapter.notifyDataSetInvalidated();
        } else {
            gridView.setVisibility(View.GONE);
            txtvEmpty.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getActivity().getSharedPreferences(ItunesTopListLoader.PREFS, Context.MODE_PRIVATE);
        countryCode = prefs.getString(ItunesTopListLoader.PREF_KEY_COUNTRY_CODE, "CN");
        genreId = prefs.getString(ItunesTopListLoader.PREF_KEY_GENRE_ID, "");
        contentType = prefs.getString(ItunesTopListLoader.PREF_KEY_CONTENT_TYPE, CONTENT_SHOWS);
        hidden = prefs.getBoolean(ItunesTopListLoader.PREF_KEY_HIDDEN_DISCOVERY_COUNTRY, false);
        needsConfirm = prefs.getBoolean(ItunesTopListLoader.PREF_KEY_NEEDS_CONFIRM, true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_online_search, container, false);
        gridView = root.findViewById(R.id.gridView);
        adapter = new OnlineSearchAdapter(getActivity(), new ArrayList<>());
        gridView.setAdapter(adapter);

        toolbar = root.findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> getParentFragmentManager().popBackStack());
        toolbar.inflateMenu(R.menu.countries_menu);
        MenuItem discoverHideItem = toolbar.getMenu().findItem(R.id.discover_hide_item);
        discoverHideItem.setChecked(hidden);
        toolbar.setOnMenuItemClickListener(this);

        genreChipGroup = root.findViewById(R.id.genreChipGroup);
        setupGenreChips();

        contentChipGroup = root.findViewById(R.id.contentChipGroup);
        setupContentChips();

        //Show information about the podcast when the list item is clicked
        gridView.setOnItemClickListener((parent, view1, position, id) -> {
            PodcastSearchResult podcast = searchResults.get(position);
            if (podcast.feedUrl == null) {
                return;
            }
            startActivity(new OnlineFeedviewActivityStarter(getContext(), podcast.feedUrl).getIntent());
        });
        gridView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisible, int visibleCount, int totalCount) {
                if (loadingMore || searchResults == null || searchResults.isEmpty()) {
                    return;
                }
                if (CONTENT_EPISODES.equals(contentType)) {
                    if (episodeWindow >= MAX_EPISODE_WINDOW || searchResults.size() < episodeWindow) {
                        return;
                    }
                    if (firstVisible + visibleCount >= totalCount - 4) {
                        if (isMeteredConnection()) {
                            if (!meteredToastShown) {
                                meteredToastShown = true;
                                Toast.makeText(getContext(), R.string.discover_metered_episodes,
                                        Toast.LENGTH_SHORT).show();
                            }
                            return;
                        }
                        loadingMore = true;
                        episodeWindow = Math.min(episodeWindow + EPISODE_WINDOW_STEP, MAX_EPISODE_WINDOW);
                        currentLimit = Math.min(MAX_TOP_PODCASTS, Math.max(currentLimit, episodeWindow));
                        loadToplist(countryCode, genreId);
                    }
                    return;
                }
                if (currentLimit >= MAX_TOP_PODCASTS || searchResults.size() < currentLimit) {
                    return;
                }
                if (firstVisible + visibleCount >= totalCount - 4) {
                    loadingMore = true;
                    currentLimit = Math.min(currentLimit + TOP_PODCASTS_STEP, MAX_TOP_PODCASTS);
                    loadToplist(countryCode, genreId);
                }
            }
        });

        progressBar = root.findViewById(R.id.progressBar);
        txtvError = root.findViewById(R.id.txtvError);
        butRetry = root.findViewById(R.id.butRetry);
        txtvEmpty = root.findViewById(android.R.id.empty);

        loadToplist(countryCode, genreId);
        return root;
    }

    private void setupGenreChips() {
        genreChipGroup.removeAllViews();
        for (int i = 0; i < GENRE_IDS.length; i++) {
            Chip chip = new Chip(getContext());
            chip.setText(GENRE_LABELS[i]);
            chip.setCheckable(true);
            chip.setChecked(GENRE_IDS[i].equals(genreId));
            final String id = GENRE_IDS[i];
            chip.setOnClickListener(v -> {
                genreId = id;
                resetPaging();
                prefs.edit().putString(ItunesTopListLoader.PREF_KEY_GENRE_ID, genreId).apply();
                for (int j = 0; j < genreChipGroup.getChildCount(); j++) {
                    Chip other = (Chip) genreChipGroup.getChildAt(j);
                    other.setChecked(other == chip);
                }
                loadToplist(countryCode, genreId);
            });
            genreChipGroup.addView(chip);
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
            resetPaging();
            prefs.edit().putString(ItunesTopListLoader.PREF_KEY_CONTENT_TYPE, contentType).apply();
            for (int j = 0; j < contentChipGroup.getChildCount(); j++) {
                Chip other = (Chip) contentChipGroup.getChildAt(j);
                other.setChecked(other == chip);
            }
            loadToplist(countryCode, genreId);
        });
        contentChipGroup.addView(chip);
    }

    private void resetPaging() {
        currentLimit = INITIAL_TOP_PODCASTS;
        episodeWindow = INITIAL_EPISODE_WINDOW;
        loadingMore = false;
        meteredToastShown = false;
    }

    private boolean isMeteredConnection() {
        if (getContext() == null) {
            return false;
        }
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        return connectivityManager != null && connectivityManager.isActiveNetworkMetered();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (disposable != null) {
            disposable.dispose();
        }
        adapter = null;
    }

    private void loadToplist(String country, String genre) {
        if (disposable != null) {
            disposable.dispose();
        }
        final boolean isAppend = loadingMore;
        if (!isAppend) {
            gridView.setVisibility(View.GONE);
            txtvError.setVisibility(View.GONE);
            butRetry.setVisibility(View.GONE);
            butRetry.setText(R.string.retry_label);
            txtvEmpty.setVisibility(View.GONE);
        }
        progressBar.setVisibility(View.VISIBLE);

        if (hidden) {
            gridView.setVisibility(View.GONE);
            txtvError.setVisibility(View.VISIBLE);
            txtvError.setText(getResources().getString(R.string.discover_is_hidden));
            butRetry.setVisibility(View.GONE);
            txtvEmpty.setVisibility(View.GONE);
            progressBar.setVisibility(View.GONE);
            return;
        }
        //noinspection ConstantConditions
        if (BuildConfig.FLAVOR.equals("free") && needsConfirm) {
            txtvError.setVisibility(View.VISIBLE);
            txtvError.setText("");
            butRetry.setVisibility(View.VISIBLE);
            butRetry.setText(R.string.discover_confirm);
            butRetry.setOnClickListener(v -> {
                prefs.edit().putBoolean(ItunesTopListLoader.PREF_KEY_NEEDS_CONFIRM, false).apply();
                needsConfirm = false;
                loadToplist(country, genre);
            });
            txtvEmpty.setVisibility(View.GONE);
            progressBar.setVisibility(View.GONE);
            return;
        }

        ItunesTopListLoader loader = new ItunesTopListLoader(getContext());
        final String loadGenre = genre;
        final int fetchLimit = CONTENT_EPISODES.equals(contentType)
                ? Math.max(currentLimit, episodeWindow) : currentLimit;
        disposable = Observable.fromCallable(() ->
                        loader.loadToplist(country, loadGenre, fetchLimit, DBReader.getFeedList()))
                .subscribeOn(Schedulers.io())
                .map(shows -> {
                    topList = shows;
                    if (CONTENT_EPISODES.equals(contentType)) {
                        TopEpisodesLoader episodesLoader = new TopEpisodesLoader(getContext());
                        return toSearchResults(episodesLoader.loadTopEpisodes(shows, episodeWindow));
                    }
                    return shows;
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    podcasts -> {
                        progressBar.setVisibility(View.GONE);
                        loadingMore = false;
                        updateData(podcasts);
                    }, error -> {
                        Log.e(TAG, Log.getStackTraceString(error));
                        progressBar.setVisibility(View.GONE);
                        loadingMore = false;
                        txtvError.setText(error.getMessage());
                        txtvError.setVisibility(View.VISIBLE);
                        butRetry.setOnClickListener(v -> loadToplist(country, loadGenre));
                        butRetry.setVisibility(View.VISIBLE);
                    });
    }

    private List<PodcastSearchResult> toSearchResults(List<TopEpisode> episodes) {
        List<PodcastSearchResult> results = new ArrayList<>();
        for (TopEpisode episode : episodes) {
            String author = episode.showTitle;
            if (episode.durationMs > 0) {
                author += " · " + Converter.getDurationStringShort(episode.durationMs,
                        episode.durationMs >= 3600000);
            }
            results.add(new PodcastSearchResult(episode.title, episode.imageUrl,
                    episode.feedUrl, author));
        }
        return results;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == R.id.discover_hide_item) {
            item.setChecked(!item.isChecked());
            hidden = item.isChecked();
            prefs.edit().putBoolean(ItunesTopListLoader.PREF_KEY_HIDDEN_DISCOVERY_COUNTRY, hidden).apply();

            EventBus.getDefault().post(new DiscoveryDefaultUpdateEvent());
            resetPaging();
            loadToplist(countryCode, genreId);
            return true;
        } else if (itemId == R.id.discover_countries_item) {

            LayoutInflater inflater = getLayoutInflater();
            View selectCountryDialogView = inflater.inflate(R.layout.select_country_dialog, null);
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext());
            builder.setView(selectCountryDialogView);

            List<String> countryCodeArray = new ArrayList<>(Arrays.asList(Locale.getISOCountries()));
            Map<String, String> countryCodeNames = new HashMap<>();
            Map<String, String> countryNameCodes = new HashMap<>();
            for (String code : countryCodeArray) {
                Locale locale = new Locale("", code);
                String countryName = locale.getDisplayCountry();
                countryCodeNames.put(code, countryName);
                countryNameCodes.put(countryName, code);
            }

            List<String> countryNamesSort = new ArrayList<>(countryCodeNames.values());
            Collections.sort(countryNamesSort);

            ArrayAdapter<String> dataAdapter =
                    new ArrayAdapter<>(this.getContext(), android.R.layout.simple_list_item_1, countryNamesSort);
            TextInputLayout textInput = selectCountryDialogView.findViewById(R.id.country_text_input);
            MaterialAutoCompleteTextView editText = (MaterialAutoCompleteTextView) textInput.getEditText();
            editText.setAdapter(dataAdapter);
            editText.setText(countryCodeNames.get(countryCode));
            editText.setOnClickListener(view -> {
                if (editText.getText().length() != 0) {
                    editText.setText("");
                    editText.postDelayed(editText::showDropDown, 100);
                }
            });
            editText.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    editText.setText("");
                    editText.postDelayed(editText::showDropDown, 100);
                }
            });

            builder.setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
                String countryName = editText.getText().toString();
                if (countryNameCodes.containsKey(countryName)) {
                    countryCode = countryNameCodes.get(countryName);
                    MenuItem discoverHideItem = toolbar.getMenu().findItem(R.id.discover_hide_item);
                    discoverHideItem.setChecked(false);
                    hidden = false;
                }

                prefs.edit().putBoolean(ItunesTopListLoader.PREF_KEY_HIDDEN_DISCOVERY_COUNTRY, hidden).apply();
                prefs.edit().putString(ItunesTopListLoader.PREF_KEY_COUNTRY_CODE, countryCode).apply();

                EventBus.getDefault().post(new DiscoveryDefaultUpdateEvent());
                resetPaging();
                loadToplist(countryCode, genreId);
            });
            builder.setNegativeButton(R.string.cancel_label, null);
            builder.show();
            return true;
        }
        return false;
    }
}
