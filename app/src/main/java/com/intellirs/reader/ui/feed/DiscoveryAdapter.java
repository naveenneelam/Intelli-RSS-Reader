package com.intellirs.reader.ui.feed;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.intellirs.reader.R;
import com.intellirs.reader.data.network.FeedCandidate;
import com.intellirs.reader.util.TextUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DiscoveryAdapter extends RecyclerView.Adapter<DiscoveryAdapter.Holder> {
    public interface SelectionListener {
        void onSelectionChanged(int selectedCount);
    }

    private final List<FeedCandidate> items = new ArrayList<>();
    private final Set<String> selected = new HashSet<>();
    private final SelectionListener listener;

    public DiscoveryAdapter(SelectionListener listener) {
        this.listener = listener;
    }

    public void setItems(List<FeedCandidate> candidates) {
        items.clear();
        selected.clear();
        if (candidates != null) {
            items.addAll(candidates);
            for (FeedCandidate candidate : candidates) {
                selected.add(candidate.feedUrl);
            }
        }
        notifyDataSetChanged();
        listener.onSelectionChanged(selected.size());
    }

    public List<FeedCandidate> selectedCandidates() {
        List<FeedCandidate> result = new ArrayList<>();
        for (FeedCandidate candidate : items) {
            if (selected.contains(candidate.feedUrl)) {
                result.add(candidate);
            }
        }
        return result;
    }

    public void selectAll(boolean select) {
        selected.clear();
        if (select) {
            for (FeedCandidate candidate : items) {
                selected.add(candidate.feedUrl);
            }
        }
        notifyDataSetChanged();
        listener.onSelectionChanged(selected.size());
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_discovery, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        FeedCandidate candidate = items.get(position);
        holder.bind(candidate, selected.contains(candidate.feedUrl), checked -> {
            if (checked) {
                selected.add(candidate.feedUrl);
            } else {
                selected.remove(candidate.feedUrl);
            }
            listener.onSelectionChanged(selected.size());
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        interface Toggle {
            void onToggle(boolean checked);
        }

        private final CheckBox check;
        private final TextView title;
        private final TextView description;
        private final TextView meta;
        private final TextView url;

        Holder(@NonNull View itemView) {
            super(itemView);
            check = itemView.findViewById(R.id.discovery_check);
            title = itemView.findViewById(R.id.discovery_title);
            description = itemView.findViewById(R.id.discovery_description);
            meta = itemView.findViewById(R.id.discovery_meta);
            url = itemView.findViewById(R.id.discovery_url);
        }

        void bind(FeedCandidate candidate, boolean checked, Toggle toggle) {
            Context context = itemView.getContext();
            title.setText(candidate.title);
            String desc = TextUtils.summary(candidate.description, 180);
            description.setText(desc);
            description.setVisibility(desc.isEmpty() ? View.GONE : View.VISIBLE);
            meta.setText(context.getString(R.string.add_feed_direct_type,
                    typeLabel(context, candidate.feedType))
                    + "  \u2022  " + context.getString(R.string.add_feed_articles_sampled,
                    candidate.articleCount)
                    + "  \u2022  " + context.getString(R.string.add_feed_suggested_category,
                    candidate.suggestedCategory));
            url.setText(candidate.feedUrl);
            check.setOnCheckedChangeListener(null);
            check.setChecked(checked);
            check.setOnCheckedChangeListener((buttonView, isChecked) -> toggle.onToggle(isChecked));
            itemView.setOnClickListener(view -> check.setChecked(!check.isChecked()));
        }

        private String typeLabel(Context context, String type) {
            if ("ATOM".equalsIgnoreCase(type)) {
                return context.getString(R.string.feed_type_atom);
            }
            if ("RDF".equalsIgnoreCase(type)) {
                return context.getString(R.string.feed_type_rdf);
            }
            return context.getString(R.string.feed_type_rss);
        }
    }
}
