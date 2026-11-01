package com.intellirs.reader.ui.common;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.intellirs.reader.data.entity.FeedEntity;
import com.intellirs.reader.util.FileUtils;
import com.intellirs.reader.util.LetterIconDrawable;

import java.io.File;

public final class IconLoader {
    private IconLoader() {
    }

    public static void loadFeedIcon(ImageView view, FeedEntity feed, float cornerDp) {
        Context context = view.getContext();
        int size = Math.max(view.getWidth(),
                Math.round(44 * context.getResources().getDisplayMetrics().density));
        Drawable fallback = new LetterIconDrawable(feed.title, size);
        File file = FileUtils.resolvePrivate(context, feed.faviconPath);
        if (file != null && file.isFile()) {
            Glide.with(view)
                    .load(file)
                    .apply(RequestOptions.bitmapTransform(new RoundedCorners(
                            Math.round(cornerDp * context.getResources()
                                    .getDisplayMetrics().density))))
                    .placeholder(fallback)
                    .error(fallback)
                    .into(view);
        } else {
            Glide.with(view).clear(view);
            view.setImageDrawable(fallback);
        }
    }

    public static Drawable letterIcon(Context context, String title, int sizeDp) {
        return new LetterIconDrawable(title,
                Math.round(sizeDp * context.getResources().getDisplayMetrics().density));
    }
}
