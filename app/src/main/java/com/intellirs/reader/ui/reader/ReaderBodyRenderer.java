package com.intellirs.reader.ui.reader;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.CornerFamily;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.intellirs.reader.R;
import com.intellirs.reader.data.archive.ArchiveManager;
import com.intellirs.reader.util.FileUtils;
import com.intellirs.reader.util.UrlUtils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders sanitized article HTML into a native LinearLayout. The app never uses a WebView,
 * so text, images and links are real Android views that participate in accessibility and
 * theme changes.
 */
public class ReaderBodyRenderer {
    private static final Pattern BARE_URL = Pattern.compile(
            "(?i)\\b(?:https?://|www\\.)[^\\s<>\"'\\u00A0\\u200B]+");

    private final Context context;
    private final ReaderStyle style;
    private final ArchiveManager archiveManager;
    private final boolean allowHttp;
    private String baseUrl;
    private LinearLayout container;

    public ReaderBodyRenderer(Context context, ReaderStyle style, ArchiveManager archiveManager,
                              boolean allowHttp) {
        this.context = context;
        this.style = style;
        this.archiveManager = archiveManager;
        this.allowHttp = allowHttp;
    }

    public void render(LinearLayout container, String html, String baseUrl) {
        this.container = container;
        this.baseUrl = baseUrl;
        container.removeAllViews();
        if (html == null || html.trim().isEmpty()) {
            return;
        }
        Document body = Jsoup.parseBodyFragment(html, baseUrl);
        Elements children = body.body().children();
        if (children.isEmpty()) {
            String text = body.body().text();
            if (!text.trim().isEmpty()) {
                addText(text, style.fontSp, false, false);
            }
            return;
        }
        for (Element child : children) {
            renderElement(child);
        }
    }

    private void renderElement(Element element) {
        String tag = element.tagName().toLowerCase(java.util.Locale.ROOT);
        switch (tag) {
            case "h1":
            case "h2":
                addHeading(element, style.fontSp + 8);
                break;
            case "h3":
                addHeading(element, style.fontSp + 5);
                break;
            case "h4":
            case "h5":
            case "h6":
                addHeading(element, style.fontSp + 2);
                break;
            case "p":
                renderTextAndImages(element, style.fontSp, false);
                break;
            case "blockquote":
                addBlockquote(element);
                break;
            case "pre":
                addPre(element);
                break;
            case "ul":
            case "ol":
                addList(element, "ol".equals(tag));
                break;
            case "figure":
                addFigure(element);
                break;
            case "img":
                addImage(element.attr("src"), element.attr("alt"));
                break;
            case "hr":
                addDivider();
                break;
            case "table":
                addTable(element);
                break;
            case "br":
                addSpacing(dp(6));
                break;
            case "div":
            case "section":
            case "article":
            case "main":
            case "figcaption":
                if (element.children().isEmpty()) {
                    if (!element.text().trim().isEmpty()) {
                        addText(element.html(), style.fontSp, false, false);
                    }
                } else if (hasDirectText(element)) {
                    // Mixed text and inline markup (common in feed footers) must be
                    // rendered together or the surrounding text is silently dropped.
                    renderTextAndImages(element, style.fontSp, false);
                } else {
                    for (Element child : element.children()) {
                        renderElement(child);
                    }
                }
                break;
            default:
                if (!element.text().trim().isEmpty()) {
                    renderTextAndImages(element, style.fontSp, false);
                }
                break;
        }
    }

    private void addHeading(Element element, int sizeSp) {
        TextView view = newTextView(sizeSp, true, false);
        setLinkedText(view, linkifyHtml(element.html()));
        LinearLayout.LayoutParams params = params();
        params.topMargin = dp(18);
        params.bottomMargin = dp(6);
        addView(view, params);
    }

    private void renderTextAndImages(Element element, int sizeSp, boolean italic) {
        Element clone = element.clone();
        Elements images = clone.select("img");
        Set<String> imageSources = new HashSet<>();
        for (Element image : images) {
            String source = image.attr("src");
            if (!source.isEmpty()) {
                imageSources.add(source);
            }
            image.remove();
        }
        String tag = element.tagName().toLowerCase(Locale.ROOT);
        // For an inline element such as <a> or <strong>, keep its own markup so a link
        // that sits directly inside a container is not flattened to plain text.
        String html = (isInlineTag(tag) ? clone.outerHtml() : clone.html()).trim();
        if (!html.isEmpty() && !clone.text().trim().isEmpty()) {
            addText(html, sizeSp, false, italic);
        }
        for (String source : imageSources) {
            addImage(source, "");
        }
    }

    private boolean isInlineTag(String tag) {
        switch (tag) {
            case "a":
            case "span":
            case "strong":
            case "b":
            case "em":
            case "i":
            case "u":
            case "s":
            case "small":
            case "code":
            case "sup":
            case "sub":
            case "abbr":
            case "font":
                return true;
            default:
                return false;
        }
    }

    private boolean hasDirectText(Element element) {
        for (org.jsoup.nodes.Node node : element.childNodes()) {
            if (node instanceof org.jsoup.nodes.TextNode
                    && !((org.jsoup.nodes.TextNode) node).text().trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void addBlockquote(Element element) {
        TextView view = newTextView(style.fontSp, false, true);
        setLinkedText(view, linkifyHtml(element.html()));
        view.setTextColor(style.secondaryText);
        view.setPadding(dp(14), dp(10), dp(10), dp(10));
        android.graphics.drawable.GradientDrawable background =
                new android.graphics.drawable.GradientDrawable();
        background.setColor(style.card);
        background.setCornerRadius(dp(10));
        background.setStroke(dp(1), style.divider);
        view.setBackground(background);
        LinearLayout.LayoutParams params = params();
        params.topMargin = dp(10);
        params.bottomMargin = dp(10);
        addView(view, params);
    }

    private void addPre(Element element) {
        TextView view = new TextView(context);
        view.setText(element.wholeText());
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, style.fontSp - 1);
        view.setTextColor(style.text);
        view.setTypeface(Typeface.MONOSPACE);
        view.setLineSpacing(0, 1.25f);
        view.setHorizontallyScrolling(true);
        view.setPadding(dp(12), dp(10), dp(12), dp(10));
        android.graphics.drawable.GradientDrawable background =
                new android.graphics.drawable.GradientDrawable();
        background.setColor(style.codeBackground);
        background.setCornerRadius(dp(10));
        view.setBackground(background);
        LinearLayout.LayoutParams params = params();
        params.topMargin = dp(8);
        params.bottomMargin = dp(12);
        addView(view, params);
    }

    private void addList(Element element, boolean ordered) {
        int index = 1;
        for (Element item : element.children()) {
            if (!"li".equalsIgnoreCase(item.tagName())) {
                continue;
            }
            SpannableStringBuilder builder = new SpannableStringBuilder();
            builder.append(ordered ? index + ". " : "\u2022 ");
            builder.append(linkifyHtml(item.html()));
            TextView view = newTextView(style.fontSp, false, false);
            setLinkedText(view, builder);
            LinearLayout.LayoutParams params = params();
            params.setMarginStart(dp(8));
            params.bottomMargin = dp(4);
            addView(view, params);
            index++;
        }
        addSpacing(dp(4));
    }

    private void addFigure(Element figure) {
        for (Element image : figure.select("img")) {
            addImage(image.attr("src"), image.attr("alt"));
        }
        Element caption = figure.selectFirst("figcaption");
        if (caption != null && !caption.text().trim().isEmpty()) {
            TextView view = newTextView(style.fontSp - 3, false, true);
            setLinkedText(view, linkifyHtml(caption.html()));
            view.setTextColor(style.secondaryText);
            LinearLayout.LayoutParams params = params();
            params.bottomMargin = dp(12);
            addView(view, params);
        }
    }

    private void addTable(Element table) {
        TextView view = newTextView(style.fontSp - 1, false, false);
        StringBuilder builder = new StringBuilder();
        for (Element row : table.select("tr")) {
            StringBuilder line = new StringBuilder();
            for (Element cell : row.select("th, td")) {
                if (line.length() > 0) {
                    line.append("  \u00b7  ");
                }
                line.append(cell.text());
            }
            if (line.length() > 0) {
                builder.append(line).append('\n');
            }
        }
        setLinkedText(view, linkifyPlainText(builder.toString().trim()));
        view.setPadding(dp(12), dp(8), dp(12), dp(8));
        android.graphics.drawable.GradientDrawable background =
                new android.graphics.drawable.GradientDrawable();
        background.setColor(style.card);
        background.setCornerRadius(dp(8));
        view.setBackground(background);
        LinearLayout.LayoutParams params = params();
        params.topMargin = dp(8);
        params.bottomMargin = dp(12);
        addView(view, params);
    }

    private void addText(String html, int sizeSp, boolean bold, boolean italic) {
        CharSequence text = linkifyHtml(html);
        if (text.toString().trim().isEmpty()) {
            return;
        }
        TextView view = newTextView(sizeSp, bold, italic);
        setLinkedText(view, text);
        LinearLayout.LayoutParams params = params();
        params.bottomMargin = dp(10);
        addView(view, params);
    }

    /**
     * Converts an HTML fragment into a span that keeps real anchors clickable and also
     * turns plain-text URLs into links. Feed footers such as "Comments" or "Read more" are
     * frequently plain text, so relying on anchor tags alone is not enough.
     */
    private CharSequence linkifyHtml(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        Spanned parsed = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT);
        SpannableStringBuilder builder = new SpannableStringBuilder(parsed);
        replaceAnchorSpans(builder);
        linkifyBareUrls(builder);
        return builder;
    }

    private CharSequence linkifyPlainText(String text) {
        SpannableStringBuilder builder = new SpannableStringBuilder(text == null ? "" : text);
        linkifyBareUrls(builder);
        return builder;
    }

    private void replaceAnchorSpans(SpannableStringBuilder builder) {
        URLSpan[] anchors = builder.getSpans(0, builder.length(), URLSpan.class);
        for (URLSpan anchor : anchors) {
            int start = builder.getSpanStart(anchor);
            int end = builder.getSpanEnd(anchor);
            if (start < 0 || end <= start) {
                continue;
            }
            String href = anchor.getURL();
            builder.removeSpan(anchor);
            String resolved = resolveLink(href);
            if (resolved != null) {
                builder.setSpan(new ExternalLinkSpan(resolved), start, end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (isNonWebLink(href)) {
                // mailto: and tel: links are still useful and Android can route them.
                builder.setSpan(new URLSpan(href), start, end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    @Nullable
    private String resolveLink(String href) {
        if (href == null) {
            return null;
        }
        String value = href.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.startsWith("//")) {
            value = "https:" + value;
        }
        if (UrlUtils.isValidHttpUrl(value)) {
            return value;
        }
        if (baseUrl != null && !baseUrl.isEmpty()) {
            String resolved = UrlUtils.resolve(baseUrl, value);
            if (UrlUtils.isValidHttpUrl(resolved)) {
                return resolved;
            }
        }
        return null;
    }

    private boolean isNonWebLink(String href) {
        if (href == null) {
            return false;
        }
        String value = href.trim().toLowerCase(Locale.ROOT);
        return value.startsWith("mailto:") || value.startsWith("tel:");
    }

    private void linkifyBareUrls(SpannableStringBuilder builder) {
        Matcher matcher = BARE_URL.matcher(builder);
        while (matcher.find()) {
            int start = matcher.start();
            int end = trimTrailingPunctuation(builder, start, matcher.end());
            if (end <= start || hasClickableSpan(builder, start, end)) {
                continue;
            }
            String visible = builder.subSequence(start, end).toString();
            String url = visible.regionMatches(true, 0, "www.", 0, 4)
                    ? "https://" + visible : visible;
            if (UrlUtils.isValidHttpUrl(url)) {
                builder.setSpan(new ExternalLinkSpan(url), start, end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    private boolean hasClickableSpan(SpannableStringBuilder builder, int start, int end) {
        ClickableSpan[] spans = builder.getSpans(start, end, ClickableSpan.class);
        return spans != null && spans.length > 0;
    }

    private int trimTrailingPunctuation(CharSequence text, int start, int end) {
        int last = end;
        while (last > start) {
            char c = text.charAt(last - 1);
            boolean punctuation = c == '.' || c == ',' || c == ';' || c == ':'
                    || c == '!' || c == '?' || c == '\'' || c == '\"'
                    || c == '\u2019' || c == '\u201d' || c == '\u00bb'
                    || c == ']' || c == '}';
            if (c == ')') {
                // Keep a closing parenthesis that balances one inside the URL.
                punctuation = count(text, start, last - 1, '(')
                        <= count(text, start, last - 1, ')');
            }
            if (!punctuation) {
                break;
            }
            last--;
        }
        return last;
    }

    private int count(CharSequence text, int start, int end, char target) {
        int total = 0;
        for (int i = start; i < end; i++) {
            if (text.charAt(i) == target) {
                total++;
            }
        }
        return total;
    }

    private void setLinkedText(TextView view, CharSequence text) {
        boolean hasLinks = text instanceof Spanned
                && ((Spanned) text).getSpans(0, text.length(), ClickableSpan.class).length > 0;
        view.setText(text);
        if (hasLinks) {
            // A selectable TextView installs an editor that swallows the touch events
            // LinkMovementMethod needs, so selection is disabled only when links exist.
            view.setTextIsSelectable(false);
            view.setMovementMethod(LinkMovementMethod.getInstance());
        } else {
            view.setTextIsSelectable(true);
        }
        view.setLinkTextColor(style.accent);
    }

    private void openExternal(String url) {
        if (!UrlUtils.isValidHttpUrl(url)) {
            Toast.makeText(context, R.string.invalid_link, Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, R.string.could_not_open_link, Toast.LENGTH_LONG).show();
        }
    }

    private final class ExternalLinkSpan extends ClickableSpan {
        private final String url;

        ExternalLinkSpan(String url) {
            this.url = url;
        }

        @Override
        public void onClick(@NonNull View widget) {
            openExternal(url);
        }

        @Override
        public void updateDrawState(@NonNull TextPaint ds) {
            super.updateDrawState(ds);
            ds.setColor(style.accent);
            ds.setUnderlineText(true);
        }
    }

    private TextView newTextView(int sizeSp, boolean bold, boolean italic) {
        TextView view = new TextView(context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(11, sizeSp));
        view.setTextColor(style.text);
        int typefaceStyle = bold && italic ? Typeface.BOLD_ITALIC
                : bold ? Typeface.BOLD : italic ? Typeface.ITALIC : Typeface.NORMAL;
        view.setTypeface(style.typeface, typefaceStyle);
        view.setLineSpacing(0, style.lineSpacing);
        view.setIncludeFontPadding(false);
        return view;
    }

    private void addImage(String source, String alt) {
        if (source == null || source.trim().isEmpty()) {
            return;
        }
        File local = source.startsWith("archive/")
                ? FileUtils.resolvePrivate(context, source) : null;
        String remote = null;
        if (local == null || !local.isFile()) {
            String resolved = UrlUtils.resolve(baseUrl, source);
            if (resolved != null && (allowHttp || resolved.startsWith("https://"))) {
                remote = resolved;
            }
        }
        if ((local == null || !local.isFile()) && remote == null) {
            return;
        }
        ShapeableImageView image = new ShapeableImageView(context);
        image.setShapeAppearanceModel(new ShapeAppearanceModel.Builder()
                .setAllCorners(CornerFamily.ROUNDED, dp(12))
                .build());
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setAdjustViewBounds(true);
        image.setMinimumHeight(dp(120));
        image.setMaxHeight(dp(520));
        image.setContentDescription(alt == null ? context.getString(R.string.image)
                : alt);
        int padding = dp(1);
        image.setPadding(padding, padding, padding, padding);
        image.setBackgroundResource(R.drawable.bg_thumbnail);
        LinearLayout.LayoutParams params = params();
        params.topMargin = dp(8);
        params.bottomMargin = dp(12);
        addView(image, params);
        Glide.with(image)
                .load(local != null && local.isFile() ? local : remote)
                .placeholder(R.drawable.bg_thumbnail)
                .error(R.drawable.bg_thumbnail)
                .fitCenter()
                .into(image);
        final String target = remote;
        image.setOnClickListener(view -> {
            if (target != null) {
                try {
                    context.startActivity(new android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(target)));
                } catch (android.content.ActivityNotFoundException ignored) {
                    // The image remains visible offline even without a viewer app.
                }
            }
        });
    }

    private void addDivider() {
        View divider = new View(context);
        divider.setBackgroundColor(style.divider);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        params.topMargin = dp(12);
        params.bottomMargin = dp(12);
        addView(divider, params);
    }

    private void addSpacing(int height) {
        View space = new View(context);
        space.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, height));
        container.addView(space);
    }

    private LinearLayout.LayoutParams params() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void addView(View view, LinearLayout.LayoutParams params) {
        container.addView(view, params);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
