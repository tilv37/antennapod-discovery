package de.danoeh.antennapod.ui.discovery;

import android.content.Context;
import android.widget.ArrayAdapter;
import androidx.annotation.NonNull;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import com.bumptech.glide.load.resource.bitmap.FitCenter;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;

import java.util.List;

import de.danoeh.antennapod.net.discovery.PodcastSearchResult;
import de.danoeh.antennapod.ui.common.ImagePlaceholder;

public class EpisodeSearchAdapter extends ArrayAdapter<PodcastSearchResult> {
    public interface EpisodeClickListener {
        void onEpisodeClicked(int position);

        void onShowClicked(int position);
    }

    private final Context context;
    private final List<PodcastSearchResult> data;
    private EpisodeClickListener listener;

    public EpisodeSearchAdapter(Context context, List<PodcastSearchResult> objects) {
        super(context, 0, objects);
        this.data = objects;
        this.context = context;
    }

    public void setEpisodeClickListener(EpisodeClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        PodcastSearchResult podcast = data.get(position);

        PodcastViewHolder viewHolder;
        View view;
        if (convertView == null) {
            view = View.inflate(context, R.layout.online_search_listitem, null);
            viewHolder = new PodcastViewHolder(view);
            view.setTag(viewHolder);
        } else {
            view = convertView;
            viewHolder = (PodcastViewHolder) view.getTag();
        }

        viewHolder.titleView.setText(podcast.title);
        if (podcast.author != null && !podcast.author.trim().isEmpty()) {
            viewHolder.authorView.setText(podcast.author);
            viewHolder.authorView.setVisibility(View.VISIBLE);
        } else {
            viewHolder.authorView.setVisibility(View.GONE);
        }
        viewHolder.authorView.setFocusable(false);
        viewHolder.authorView.setClickable(true);
        viewHolder.authorView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onShowClicked(position);
            }
        });
        view.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEpisodeClicked(position);
            }
        });

        float radius = 4 * context.getResources().getDisplayMetrics().density;
        Glide.with(context)
                .load(podcast.imageUrl)
                .apply(new RequestOptions()
                    .placeholder(ImagePlaceholder.getDrawable(context, radius))
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .transform(new FitCenter(),
                            new RoundedCorners((int) radius))
                    .dontAnimate())
                .into(viewHolder.coverView);

        return view;
    }

    static class PodcastViewHolder {
        final ImageView coverView;
        final TextView titleView;
        final TextView authorView;

        PodcastViewHolder(View view) {
            coverView = view.findViewById(R.id.imgvCover);
            titleView = view.findViewById(R.id.txtvTitle);
            authorView = view.findViewById(R.id.txtvAuthor);
        }
    }
}
