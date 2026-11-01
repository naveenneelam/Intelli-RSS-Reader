package com.intellirs.reader.ui.articles;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.google.android.material.color.MaterialColors;
import com.intellirs.reader.R;
import com.intellirs.reader.data.entity.ArticleEntity;
import com.intellirs.reader.data.entity.ArticleRelevanceLevel;
import com.intellirs.reader.util.DateUtils;
import com.intellirs.reader.util.FileUtils;
import com.intellirs.reader.util.TextUtils;

import java.io.File;

public class ArticleAdapter extends ListAdapter<ArticleEntity, RecyclerView.ViewHolder> {
    public interface Listener {
        void onArticleClick(ArticleEntity article);

        void onBookmarkClick(ArticleEntity article);
    }

    private static final DiffUtil.ItemCallback<ArticleEntity> DIFF =
            new DiffUtil.ItemCallback<ArticleEntity>() {
                @Override
                public boolean areItemsTheSame(@NonNull ArticleEntity oldItem,
                                               @NonNull ArticleEntity newItem) {
                    return oldItem.id == newItem.id;
                }

                @Override
                public boolean areContentsTheSame(@NonNull ArticleEntity oldItem,
                                                  @NonNull ArticleEntity newItem) {
                    return oldItem.id == newItem.id
                            && oldItem.isRead == newItem.isRead
                            && oldItem.isBookmarked == newItem.isBookmarked
                            && oldItem.isArchived == newItem.isArchived
                            && oldItem.relevanceScore == newItem.relevanceScore
                            && java.util.Objects.equals(oldItem.relevanceLevel,
                            newItem.relevanceLevel)
                            && oldItem.publishedAt == newItem.publishedAt
                            && oldItem.hasAudio == newItem.hasAudio
                            && oldItem.hasVideo == newItem.hasVideo
                            && equals(oldItem.title, newItem.title)
                            && equals(oldItem.description, newItem.description)
                            && equals(oldItem.contentPath, newItem.contentPath)
                            && equals(oldItem.thumbnailUrl, newItem.thumbnailUrl)
                            && equals(oldItem.thumbnailPath, newItem.thumbnailPath);
                }

                private boolean equals(String left, String right) {
                    return left == null ? right == null : left.equals(right);
                }
            };

    private String density = "comfortable";
    private boolean showThumbnails = true;
    private boolean showSummaries = true;
    private boolean allowHttp;
    private boolean showPublisher = true;
    private final Listener listener;

    public ArticleAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
        setHasStableIds(true);
    }

    public void configure(String density, boolean showThumbnails, boolean showSummaries,
                          boolean allowHttp) {
        configure(density, showThumbnails, showSummaries, allowHttp, true);
    }

    public void configure(String density, boolean showThumbnails, boolean showSummaries,
                          boolean allowHttp, boolean showPublisher) {
        boolean richChanged = !density.equals(this.density);
        this.density = density;
        this.showThumbnails = showThumbnails;
        this.showSummaries = showSummaries;
        this.allowHttp = allowHttp;
        this.showPublisher = showPublisher;
        if (richChanged) {
            notifyDataSetChanged();
        } else {
            notifyItemRangeChanged(0, getItemCount());
        }
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).id;
    }

    @Override
    public int getItemViewType(int position) {
        return "image_rich".equals(density) ? 1 : 0;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == 1) {
            return new ArticleHolder(inflater.inflate(R.layout.item_article_rich, parent, false),
                    true);
        }
        return new ArticleHolder(inflater.inflate(R.layout.item_article, parent, false), false);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ArticleEntity article = getItem(position);
        ((ArticleHolder) holder).bind(article, density, showThumbnails, showSummaries,
                allowHttp, showPublisher, listener);
    }

    private static class ArticleHolder extends RecyclerView.ViewHolder {
        final View unreadDot;
        final ImageView thumbnail;
        final TextView title;
        final TextView summary;
        final TextView publisher;
        final TextView time;
        final TextView relevance;
        final ImageView media;
        final ImageView offline;
        final ImageView bookmark;
        final boolean rich;

        ArticleHolder(@NonNull View itemView, boolean rich) {
            super(itemView);
            this.rich = rich;
            unreadDot = itemView.findViewById(R.id.unread_dot);
            thumbnail = itemView.findViewById(R.id.thumbnail);
            title = itemView.findViewById(R.id.article_title);
            summary = itemView.findViewById(R.id.article_summary);
            publisher = itemView.findViewById(R.id.article_publisher);
            time = itemView.findViewById(R.id.article_time);
            relevance = itemView.findViewById(R.id.relevance_chip);
            media = itemView.findViewById(R.id.media_icon);
            offline = itemView.findViewById(R.id.offline_icon);
            bookmark = itemView.findViewById(R.id.bookmark_icon);
        }

        void bind(ArticleEntity article, String density, boolean showThumbnails,
                  boolean showSummaries, boolean allowHttp, boolean showPublisher,
                  Listener listener) {
            Context context = itemView.getContext();
            String displayTitle = article.title == null || article.title.isEmpty()
                    ? context.getString(R.string.untitled) : article.title;
            title.setText(displayTitle);
            int maxLines = "compact".equals(density) ? 1 : 2;
            title.setMaxLines(rich ? 2 : maxLines);

            String summaryText = TextUtils.summary(article.description, 220);
            boolean hasSummary = showSummaries && !summaryText.isEmpty();
            summary.setVisibility(hasSummary ? View.VISIBLE : View.GONE);
            summary.setText(summaryText);
            summary.setMaxLines(rich ? 2 : maxLines);

            int primaryText = MaterialColors.getColor(itemView,
                    com.google.android.material.R.attr.colorOnSurface);
            int secondaryText = MaterialColors.getColor(itemView,
                    com.google.android.material.R.attr.colorOnSurfaceVariant);
            int readTitle = ColorUtils.setAlphaComponent(primaryText, 170);
            int readSecondary = ColorUtils.setAlphaComponent(secondaryText, 190);
            title.setTextColor(article.isRead ? readTitle : primaryText);
            title.setTypeface(null, article.isRead ? Typeface.NORMAL : Typeface.BOLD);
            summary.setTextColor(article.isRead ? readSecondary : secondaryText);
            time.setTextColor(article.isRead ? readSecondary : secondaryText);

            unreadDot.setVisibility(article.isRead ? View.INVISIBLE : View.VISIBLE);
            publisher.setVisibility(showPublisher ? View.VISIBLE : View.GONE);
            publisher.setText(article.publisher == null ? "" : article.publisher);
            publisher.setTextColor(article.isRead ? readSecondary : secondaryText);
            time.setText(DateUtils.relative(context, article.publishedAt));

            bindRelevance(context, article);
            bindMedia(article);
            offline.setVisibility(article.isArchived ? View.VISIBLE : View.GONE);
            bookmark.setImageResource(article.isBookmarked
                    ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_border);
            bookmark.setContentDescription(context.getString(article.isBookmarked
                    ? R.string.action_remove_bookmark : R.string.action_bookmark));
            bookmark.setColorFilter(ContextCompat.getColor(context, article.isBookmarked
                    ? R.color.bookmark : R.color.on_surface_variant));
            bookmark.setOnClickListener(view -> listener.onBookmarkClick(article));

            boolean hideThumbnail = !showThumbnails || "compact".equals(density)
                    || (!hasLocalThumbnail(context, article)
                    && !hasRemoteThumbnail(context, article, allowHttp));
            loadThumbnail(context, article, hideThumbnail, allowHttp);

            itemView.setOnClickListener(view -> listener.onArticleClick(article));
            itemView.setContentDescription(context.getString(article.isRead
                    ? R.string.article_read_indicator : R.string.article_unread_indicator)
                    + ". " + displayTitle);
        }

        private void bindRelevance(Context context, ArticleEntity article) {
            if (article.relevanceLevel == null) {
                relevance.setVisibility(View.GONE);
                return;
            }
            relevance.setVisibility(View.VISIBLE);
            relevance.setText(String.valueOf(article.relevanceScore));
            int color = colorForLevel(context, article.relevanceLevel);
            relevance.setTextColor(color);
            android.graphics.drawable.Drawable background = relevance.getBackground();
            if (background != null) {
                background = background.mutate();
                background.setTint((color & 0x00FFFFFF) | 0x22000000);
                relevance.setBackground(background);
            }
            relevance.setContentDescription(context.getString(R.string.article_relevance,
                    article.relevanceScore));
        }

        private void bindMedia(ArticleEntity article) {
            if (article.hasAudio) {
                media.setVisibility(View.VISIBLE);
                media.setImageResource(R.drawable.ic_headphones);
                media.setContentDescription(null);
            } else if (article.hasVideo) {
                media.setVisibility(View.VISIBLE);
                media.setImageResource(R.drawable.ic_video_library);
                media.setContentDescription(null);
            } else {
                media.setVisibility(View.GONE);
            }
        }

        private void loadThumbnail(Context context, ArticleEntity article, boolean hide,
                                   boolean allowHttp) {
            if (hide) {
                Glide.with(thumbnail).clear(thumbnail);
                thumbnail.setVisibility(View.GONE);
                return;
            }
            thumbnail.setVisibility(View.VISIBLE);
            File local = FileUtils.resolvePrivate(context, article.thumbnailPath);
            if (local != null && local.isFile()) {
                Glide.with(thumbnail)
                        .load(local)
                        .transition(DrawableTransitionOptions.withCrossFade(120))
                        .placeholder(R.drawable.bg_thumbnail)
                        .error(R.drawable.bg_thumbnail)
                        .centerCrop()
                        .into(thumbnail);
                return;
            }
            if (hasRemoteThumbnail(context, article, allowHttp)) {
                Glide.with(thumbnail)
                        .load(article.thumbnailUrl)
                        .transition(DrawableTransitionOptions.withCrossFade(120))
                        .placeholder(R.drawable.bg_thumbnail)
                        .error(R.drawable.bg_thumbnail)
                        .centerCrop()
                        .into(thumbnail);
            } else {
                Glide.with(thumbnail).clear(thumbnail);
                thumbnail.setImageResource(R.drawable.bg_thumbnail);
            }
        }

        private boolean hasLocalThumbnail(Context context, ArticleEntity article) {
            File local = FileUtils.resolvePrivate(context, article.thumbnailPath);
            return local != null && local.isFile();
        }

        private boolean hasRemoteThumbnail(Context context, ArticleEntity article,
                                           boolean allowHttp) {
            if (article.thumbnailUrl == null || article.thumbnailUrl.isEmpty()) {
                return false;
            }
            return allowHttp || article.thumbnailUrl.startsWith("https://");
        }

        private int colorForLevel(Context context, ArticleRelevanceLevel level) {
            switch (level) {
                case CRITICAL:
                    return ContextCompat.getColor(context, R.color.relevance_critical);
                case HIGH:
                    return ContextCompat.getColor(context, R.color.relevance_high);
                case LOW:
                    return ContextCompat.getColor(context, R.color.relevance_low);
                case IRRELEVANT:
                    return ContextCompat.getColor(context, R.color.relevance_irrelevant);
                case NORMAL:
                default:
                    return ContextCompat.getColor(context, R.color.relevance_normal);
            }
        }
    }
}
