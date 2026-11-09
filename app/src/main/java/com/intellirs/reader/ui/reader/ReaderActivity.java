package com.intellirs.reader.ui.reader;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.intellirs.reader.R;
import com.intellirs.reader.data.ai.LocalAiClient;
import com.intellirs.reader.data.ai.LocalAiException;
import com.intellirs.reader.data.entity.ArticleEntity;
import com.intellirs.reader.data.entity.ArticleTagEntity;
import com.intellirs.reader.data.entity.DownloadType;
import com.intellirs.reader.data.model.ArticleSource;
import com.intellirs.reader.domain.model.ArticleFilter;
import com.intellirs.reader.ui.BaseActivity;
import com.intellirs.reader.ui.settings.SettingsActivity;
import com.intellirs.reader.util.DateUtils;
import com.intellirs.reader.util.FileUtils;
import com.intellirs.reader.util.TextUtils;
import com.intellirs.reader.util.UrlUtils;
import com.intellirs.reader.worker.WorkScheduler;

import org.jsoup.Jsoup;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Distraction-free native reader with article rendering, media, notes, tags and versions. */
public class ReaderActivity extends BaseActivity {
    private ReaderViewModel viewModel;
    private ReaderState currentState;
    private ReaderStyle style;
    private Toolbar toolbar;
    private NestedScrollView scrollView;
    private LinearProgressIndicator progressBar;
    private TextView titleView;
    private TextView metaView;
    private TextView progressText;
    private android.widget.ImageView featuredImage;
    private LinearLayout bodyContainer;
    private TextView fallbackNotice;
    private TextView emptyContent;
    private MaterialCardView videoCard;
    private MaterialCardView audioCard;
    private MaterialCardView aiSummaryCard;
    private TextView aiSummaryText;
    private MaterialButton audioPlay;
    private MaterialButton audioDownload;
    private SeekBar audioSeek;
    private TextView audioTime;
    private LinearLayout notesCard;
    private TextView noteText;
    private ChipGroup tagGroup;
    private LinearLayout sourcesContainer;
    private TextView sourcesTitle;
    private LinearLayout relatedContainer;
    private TextView relatedTitle;
    private TextView relatedEmpty;
    private LinearLayout bottomBar;
    private GestureDetector swipeDetector;
    private TextView sourcesHeading;
    private TextView tagsHeading;

    private TextToSpeech textToSpeech;
    private boolean ttsReady;
    private boolean ttsSpeaking;
    private String pendingSpeech;

    private MediaPlayer mediaPlayer;
    private String currentAudioSource;
    private boolean audioPreparing;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable audioTicker = new Runnable() {
        @Override
        public void run() {
            updateAudioProgress();
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                handler.postDelayed(this, 500);
            }
        }
    };
    private final Runnable progressTicker = new Runnable() {
        @Override
        public void run() {
            saveReadingProgress();
            handler.postDelayed(this, 15_000);
        }
    };
    private long lastProgressTick;
    private long currentRenderedId = -1L;
    private int archivePollAttempts;
    private boolean pollNeedArticle;
    private boolean pollNeedAudio;
    private final Runnable archivePoll = new Runnable() {
        @Override
        public void run() {
            long id = currentRenderedId;
            if (id <= 0) {
                return;
            }
            container.ioExecutor().execute(() -> {
                ArticleEntity article = container.articleRepository().getArticle(id);
                boolean articleDone = !pollNeedArticle || (article != null && article.isArchived);
                boolean audioDone = !pollNeedAudio || (article != null && article.audioPath != null);
                if (articleDone && audioDone) {
                    pollNeedArticle = false;
                    pollNeedAudio = false;
                    runOnUiThread(() -> viewModel.reloadCurrent(false));
                } else if (archivePollAttempts++ < 20) {
                    handler.postDelayed(archivePoll, 3000);
                }
            });
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reader);
        long articleId = getIntent().getLongExtra(ReaderContract.EXTRA_ARTICLE_ID, -1L);
        if (articleId <= 0) {
            finish();
            return;
        }
        Object filterArg = getIntent().getSerializableExtra(ReaderContract.EXTRA_FILTER);
        ArticleFilter filter = filterArg instanceof ArticleFilter
                ? (ArticleFilter) filterArg : ArticleFilter.all();
        bindViews();
        setupToolbar();
        setupButtons();
        setupScroll();
        setupTextToSpeech();

        viewModel = new ViewModelProvider(this).get(ReaderViewModel.class);
        viewModel.state().observe(this, this::renderState);
        viewModel.load(articleId, filter, true);
    }

    private void bindViews() {
        toolbar = findViewById(R.id.reader_toolbar);
        scrollView = findViewById(R.id.reader_scroll);
        progressBar = findViewById(R.id.reading_progress);
        titleView = findViewById(R.id.article_title);
        metaView = findViewById(R.id.article_meta);
        progressText = findViewById(R.id.progress_text);
        featuredImage = findViewById(R.id.featured_image);
        bodyContainer = findViewById(R.id.body_container);
        fallbackNotice = findViewById(R.id.fallback_notice);
        emptyContent = findViewById(R.id.empty_content_message);
        videoCard = findViewById(R.id.video_card);
        audioCard = findViewById(R.id.audio_card);
        aiSummaryCard = findViewById(R.id.ai_summary_card);
        aiSummaryText = findViewById(R.id.ai_summary_text);
        audioPlay = findViewById(R.id.audio_play);
        audioDownload = findViewById(R.id.audio_download);
        audioSeek = findViewById(R.id.audio_seek);
        audioTime = findViewById(R.id.audio_time);
        notesCard = findViewById(R.id.notes_card);
        noteText = findViewById(R.id.note_text);
        tagGroup = findViewById(R.id.tag_group);
        sourcesContainer = findViewById(R.id.sources_container);
        sourcesTitle = findViewById(R.id.sources_title);
        sourcesHeading = sourcesTitle;
        tagsHeading = findViewById(R.id.tags_title);
        relatedContainer = findViewById(R.id.related_container);
        relatedTitle = findViewById(R.id.related_title);
        relatedEmpty = findViewById(R.id.related_empty);
        bottomBar = findViewById(R.id.reader_bottom_bar);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(view -> {
            flushProgress(currentRenderedId);
            finish();
        });
    }

    private void setupButtons() {
        titleView.setOnClickListener(view -> openOriginalArticle());
        findViewById(R.id.note_edit).setOnClickListener(view -> showNoteDialog());
        noteText.setOnClickListener(view -> showNoteDialog());
        audioPlay.setOnClickListener(view -> toggleAudio());
        audioDownload.setOnClickListener(view -> downloadAudio());
        audioSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null && !audioPreparing) {
                    try {
                        mediaPlayer.seekTo(progress);
                    } catch (IllegalStateException ignored) {
                        // Player may have completed between events.
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        videoCard.setOnClickListener(view -> {
            if (currentState != null && currentState.article.videoUrl != null) {
                openExternal(currentState.article.videoUrl);
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupScroll() {
        scrollView.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener)
                (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                    updateProgressIndicator();
                });
        swipeDetector = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(MotionEvent event) {
                        return true;
                    }

                    @Override
                    public boolean onFling(MotionEvent start, MotionEvent end,
                                           float velocityX, float velocityY) {
                        if (start == null || end == null) {
                            return false;
                        }
                        float deltaX = end.getX() - start.getX();
                        float deltaY = end.getY() - start.getY();
                        if (Math.abs(deltaX) < dp(72)
                                || Math.abs(deltaX) < Math.abs(deltaY) * 1.4f
                                || Math.abs(velocityX) < dp(180)) {
                            return false;
                        }
                        if (currentState == null || currentRenderedId <= 0) {
                            return false;
                        }
                        long target = deltaX > 0
                                ? currentState.newerArticleId : currentState.olderArticleId;
                        if (target <= 0) {
                            return false;
                        }
                        openArticle(target);
                        return true;
                    }
                });
        scrollView.setOnTouchListener((view, event) -> {
            swipeDetector.onTouchEvent(event);
            return false;
        });
    }

    private void setupTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            ttsReady = status == TextToSpeech.SUCCESS;
            if (ttsReady) {
                textToSpeech.setLanguage(Locale.getDefault());
                if (pendingSpeech != null) {
                    String speech = pendingSpeech;
                    pendingSpeech = null;
                    speak(speech);
                }
            } else if (pendingSpeech != null) {
                pendingSpeech = null;
                Toast.makeText(this, R.string.reader_tts_unavailable, Toast.LENGTH_LONG).show();
            }
            invalidateOptionsMenu();
        });
        textToSpeech.setOnUtteranceProgressListener(
                new android.speech.tts.UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) {
                        runOnUiThread(() -> {
                            ttsSpeaking = true;
                            invalidateOptionsMenu();
                        });
                    }

                    @Override
                    public void onDone(String utteranceId) {
                        runOnUiThread(() -> {
                            ttsSpeaking = false;
                            invalidateOptionsMenu();
                        });
                    }

                    @Override
                    public void onError(String utteranceId) {
                        runOnUiThread(() -> {
                            ttsSpeaking = false;
                            invalidateOptionsMenu();
                        });
                    }
                });
    }

    private void renderState(ReaderState state) {
        if (state == null) {
            return;
        }
        if (state.error != null) {
            Snackbar.make(findViewById(R.id.reader_root), state.error, Snackbar.LENGTH_LONG)
                    .show();
            return;
        }
        boolean newArticle = currentRenderedId != state.article.id;
        int restoreProgress = 0;
        if (newArticle) {
            if (currentRenderedId > 0) {
                flushProgress(currentRenderedId);
                stopTts();
            }
            currentRenderedId = state.article.id;
            if (state.savedProgress > 0.02f && state.savedProgress < 0.92f) {
                restoreProgress = Math.round(state.savedProgress * 1000f);
            }
            archivePollAttempts = 0;
            container.notificationHelper();
            com.intellirs.reader.service.NotificationHelper.cancel(this, state.article.id);
        } else {
            restoreProgress = progressBar.getProgress();
        }
        currentState = state;
        style = ReaderStyle.resolve(this, settings);
        applyReaderTheme();
        bindContent(state);
        updateProgressIndicator();
        invalidateOptionsMenu();
        final int scrollTarget = restoreProgress;
        scrollView.post(() -> {
            View child = scrollView.getChildAt(0);
            if (child != null && scrollTarget > 0) {
                int max = Math.max(0, child.getMeasuredHeight() - scrollView.getHeight());
                scrollView.scrollTo(0, Math.round(max * scrollTarget / 1000f));
            } else if (newArticle) {
                scrollView.scrollTo(0, 0);
            }
            updateProgressIndicator();
        });
        lastProgressTick = SystemClock.elapsedRealtime();
    }

    private void applyReaderTheme() {
        if (style == null) {
            return;
        }
        findViewById(R.id.reader_root).setBackgroundColor(style.background);
        scrollView.setBackgroundColor(style.background);
        toolbar.setBackgroundColor(style.background);
        toolbar.setTitleTextColor(style.text);
        Drawable nav = toolbar.getNavigationIcon();
        if (nav != null) {
            nav = DrawableCompat.wrap(nav).mutate();
            DrawableCompat.setTint(nav, style.text);
            toolbar.setNavigationIcon(nav);
        }
        toolbar.setOverflowIcon(tintedDrawable(R.drawable.ic_more_vert, style.text));
        titleView.setTextColor(style.text);
        metaView.setTextColor(style.secondaryText);
        progressText.setTextColor(style.secondaryText);
        fallbackNotice.setTextColor(style.secondaryText);
        emptyContent.setTextColor(style.secondaryText);
        sourcesTitle.setTextColor(style.text);
        relatedTitle.setTextColor(style.text);
        relatedEmpty.setTextColor(style.secondaryText);
        tagsHeading.setTextColor(style.text);
        bottomBar.setBackgroundColor(style.card);
        progressBar.setIndicatorColor(style.accent);
        progressBar.setTrackColor(style.divider);
        styleCard(videoCard);
        styleCard(audioCard);
        styleCard(aiSummaryCard);
        GradientDrawable notesBackground = new GradientDrawable();
        notesBackground.setColor(style.card);
        notesBackground.setCornerRadius(dp(10));
        notesBackground.setStroke(dp(1), style.divider);
        notesCard.setBackground(notesBackground);
        noteText.setTextColor(style.secondaryText);
        audioTime.setTextColor(style.secondaryText);
        aiSummaryText.setTextColor(style.secondaryText);
        tintLabels(audioCard);
        tintLabels(videoCard);
        tintLabels(aiSummaryCard);
        tintLabels(notesCard);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(!style.dark);
        controller.setAppearanceLightNavigationBars(!style.dark);
        try {
            getWindow().setStatusBarColor(style.background);
            getWindow().setNavigationBarColor(style.background);
        } catch (RuntimeException ignored) {
            // Android 15 may ignore explicit system bar colors; insets still pad content.
        }
    }

    private void tintLabels(View root) {
        if (root == null) {
            return;
        }
        if (root instanceof MaterialButton) {
            return;
        }
        if (root instanceof TextView) {
            ((TextView) root).setTextColor(style.text);
        }
        if (root instanceof ImageView) {
            ((ImageView) root).setImageTintList(ColorStateList.valueOf(style.secondaryText));
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                tintLabels(group.getChildAt(i));
            }
        }
    }

    private void styleCard(MaterialCardView card) {
        if (card == null) {
            return;
        }
        card.setCardBackgroundColor(style.card);
        card.setStrokeColor(style.divider);
        card.setStrokeWidth(dp(1));
    }

    private void bindContent(ReaderState state) {
        ArticleEntity article = state.article;
        titleView.setText(article.title == null || article.title.isEmpty()
                ? getString(R.string.untitled) : article.title);
        List<String> meta = new ArrayList<>();
        String publisher = state.feed != null ? state.feed.title : article.publisher;
        if (publisher != null && !publisher.isEmpty()) {
            meta.add(publisher);
        }
        if (article.author != null && !article.author.isEmpty()) {
            meta.add(getString(R.string.article_by, article.author));
        }
        if (article.publishedAt > 0) {
            meta.add(DateUtils.formatFullDate(article.publishedAt));
        }
        metaView.setText(String.join("  \u2022  ", meta));

        String localThumb = article.thumbnailPath;
        File local = FileUtils.resolvePrivate(this, localThumb);
        String remote = null;
        if (article.thumbnailUrl != null
                && (settings.isAllowHttp() || article.thumbnailUrl.startsWith("https://"))) {
            remote = article.thumbnailUrl;
        }
        if ((local != null && local.isFile()) || remote != null) {
            featuredImage.setVisibility(View.VISIBLE);
            Glide.with(this)
                    .load(local != null && local.isFile() ? local : remote)
                    .placeholder(R.drawable.bg_thumbnail)
                    .error(R.drawable.bg_thumbnail)
                    .centerCrop()
                    .into(featuredImage);
        } else {
            featuredImage.setVisibility(View.GONE);
        }

        fallbackNotice.setVisibility(state.fallbackContent ? View.VISIBLE : View.GONE);
        ReaderBodyRenderer renderer = new ReaderBodyRenderer(this, style,
                container.archiveManager(), settings.isAllowHttp());
        renderer.render(bodyContainer, state.html, state.baseUrl);
        emptyContent.setVisibility(bodyContainer.getChildCount() == 0
                ? View.VISIBLE : View.GONE);
        String fallbackText = article.description == null ? ""
                : Jsoup.parse(article.description).text();
        if (bodyContainer.getChildCount() == 0) {
            emptyContent.setText(fallbackText.isEmpty()
                    ? getString(R.string.reader_no_content_body) : fallbackText);
        }

        videoCard.setVisibility(article.videoUrl != null ? View.VISIBLE : View.GONE);
        bindAudio(article);
        bindNotes(state);
        bindTags(state);
        bindSources(state);
        bindRelated(state);
        if (article.aiSummary != null && !article.aiSummary.trim().isEmpty()) {
            aiSummaryCard.setVisibility(View.VISIBLE);
            aiSummaryText.setText(article.aiSummary);
        } else {
            aiSummaryCard.setVisibility(View.GONE);
        }
    }

    private void bindNotes(ReaderState state) {
        if (state.note != null && state.note.note != null && !state.note.note.trim().isEmpty()) {
            noteText.setText(state.note.note);
            noteText.setTextColor(style.secondaryText);
        } else {
            noteText.setText(R.string.reader_add_note);
            noteText.setTextColor(style.secondaryText);
        }
    }

    private void bindTags(ReaderState state) {
        tagGroup.removeAllViews();
        for (ArticleTagEntity tag : state.tags) {
            Chip chip = new Chip(this);
            chip.setText(tag.tag);
            chip.setCheckable(false);
            chip.setCloseIconVisible(true);
            styleChip(chip);
            chip.setOnCloseIconClickListener(view -> viewModel.removeTag(tag.tag));
            tagGroup.addView(chip);
        }
        Chip add = new Chip(this);
        add.setText(R.string.reader_add_tag);
        add.setChipIconResource(R.drawable.ic_add);
        add.setCheckable(false);
        add.setChipIconTint(ColorStateList.valueOf(style.accent));
        add.setTextColor(style.accent);
        add.setChipBackgroundColor(ColorStateList.valueOf(withAlpha(style.accent, 0.12f)));
        add.setOnClickListener(view -> showTagDialog());
        tagGroup.addView(add);
    }

    private void styleChip(Chip chip) {
        chip.setChipBackgroundColor(ColorStateList.valueOf(style.card));
        chip.setTextColor(style.text);
        chip.setCloseIconTint(ColorStateList.valueOf(style.secondaryText));
        chip.setChipStrokeColor(ColorStateList.valueOf(style.divider));
        chip.setChipStrokeWidth(dp(1));
    }

    private void bindSources(ReaderState state) {
        sourcesContainer.removeAllViews();
        boolean show = settings.showAllSources()
                && state.sources != null && state.sources.size() > 1;
        sourcesTitle.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) {
            return;
        }
        for (ArticleSource source : state.sources) {
            TextView view = new TextView(this);
            boolean primary = state.article.primaryFeedId != null
                    && state.article.primaryFeedId == source.feedId;
            view.setText(source.feedTitle + (primary ? "  \u2022  "
                    + getString(R.string.reader_primary_source) : ""));
            view.setTextColor(style.secondaryText);
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            view.setPadding(0, dp(6), 0, dp(6));
            view.setClickable(true);
            view.setFocusable(true);
            view.setOnClickListener(v -> {
                if (primary) {
                    return;
                }
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.reader_prefer_source_title)
                        .setMessage(source.feedTitle + "\n\n"
                                + getString(R.string.reader_prefer_source_message))
                        .setPositiveButton(R.string.reader_prefer_source_confirm,
                                (dialog, which) -> {
                                    viewModel.preferSource(source);
                                    Snackbar.make(findViewById(R.id.reader_root),
                                            R.string.reader_prefer_source_done,
                                            Snackbar.LENGTH_SHORT).show();
                                })
                        .setNegativeButton(R.string.action_cancel, null)
                        .show();
            });
            sourcesContainer.addView(view);
        }
    }

    private void bindRelated(ReaderState state) {
        relatedContainer.removeAllViews();
        boolean enabled = settings.similarArticlesEnabled();
        relatedTitle.setVisibility(enabled ? View.VISIBLE : View.GONE);
        if (!enabled) {
            relatedEmpty.setVisibility(View.GONE);
            relatedContainer.setVisibility(View.GONE);
            return;
        }
        relatedContainer.setVisibility(View.VISIBLE);
        if (state.related == null || state.related.isEmpty()) {
            relatedEmpty.setVisibility(View.VISIBLE);
            return;
        }
        relatedEmpty.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (ArticleEntity related : state.related) {
            View row = inflater.inflate(R.layout.item_related_article, relatedContainer, false);
            TextView title = row.findViewById(R.id.related_title);
            TextView meta = row.findViewById(R.id.related_meta);
            title.setText(related.title);
            String publisher = related.publisher == null ? "" : related.publisher;
            meta.setText(publisher + (related.publishedAt > 0
                    ? "  \u2022  " + DateUtils.relative(this, related.publishedAt) : ""));
            title.setTextColor(style.text);
            meta.setTextColor(style.secondaryText);
            row.setOnClickListener(view -> openArticle(related.id));
            relatedContainer.addView(row);
        }
    }

    private void bindAudio(ArticleEntity article) {
        boolean hasAudio = article.audioUrl != null || article.audioPath != null;
        audioCard.setVisibility(hasAudio ? View.VISIBLE : View.GONE);
        if (!hasAudio) {
            releaseAudio();
            return;
        }
        String source = resolvedAudioSource(article);
        if (source == null) {
            audioCard.setVisibility(View.GONE);
            releaseAudio();
            return;
        }
        if (currentAudioSource != null && !currentAudioSource.equals(source)) {
            releaseAudio();
        }
        audioDownload.setText(article.audioPath != null
                ? R.string.download_status_complete : R.string.action_download_audio);
        audioDownload.setEnabled(article.audioPath == null);
        audioPlay.setIconResource(mediaPlayer != null && mediaPlayer.isPlaying()
                ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
        if (mediaPlayer != null && mediaPlayer.getDuration() > 0) {
            audioSeek.setMax(mediaPlayer.getDuration());
            audioSeek.setProgress(mediaPlayer.getCurrentPosition());
            audioTime.setText(DateUtils.formatDuration(mediaPlayer.getCurrentPosition())
                    + " / " + DateUtils.formatDuration(mediaPlayer.getDuration()));
        } else if (audioSeek.getMax() == 0 || audioSeek.getMax() == 1000) {
            audioTime.setText(DateUtils.formatDuration(article.audioDurationMs));
        }
    }

    private void showNoteDialog() {
        if (currentState == null) {
            return;
        }
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_text_input, null);
        TextInputEditText input = view.findViewById(R.id.dialog_input);
        if (currentState.note != null) {
            input.setText(currentState.note.note);
            input.setSelection(input.length());
        }
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.reader_notes)
                .setView(view)
                .setPositiveButton(R.string.action_save, (dialog, which) -> {
                    viewModel.saveNote(input.getText() == null
                            ? "" : input.getText().toString());
                    Snackbar.make(findViewById(R.id.reader_root), R.string.reader_note_saved,
                            Snackbar.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.action_cancel, null);
        if (currentState.note != null) {
            builder.setNeutralButton(R.string.action_delete, (dialog, which) -> {
                viewModel.saveNote("");
                Snackbar.make(findViewById(R.id.reader_root), R.string.reader_note_deleted,
                        Snackbar.LENGTH_SHORT).show();
            });
        }
        builder.show();
    }

    private void showTagDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_text_input, null);
        com.google.android.material.textfield.TextInputLayout layout =
                (com.google.android.material.textfield.TextInputLayout) view;
        layout.setHint(getString(R.string.reader_tag_hint));
        TextInputEditText input = view.findViewById(R.id.dialog_input);
        input.setSingleLine(true);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.reader_add_tag)
                .setView(view)
                .setPositiveButton(R.string.action_add, (dialog, which) -> {
                    String tag = input.getText() == null ? "" : input.getText().toString().trim();
                    if (!tag.isEmpty()) {
                        viewModel.addTag(tag);
                        Snackbar.make(findViewById(R.id.reader_root), R.string.reader_tag_added,
                                Snackbar.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void toggleAudio() {
        if (currentState == null) {
            return;
        }
        String source = resolvedAudioSource(currentState.article);
        if (source == null) {
            Snackbar.make(findViewById(R.id.reader_root), R.string.reader_audio_unsupported,
                    Snackbar.LENGTH_LONG).show();
            return;
        }
        if (mediaPlayer == null || !source.equals(currentAudioSource)) {
            releaseAudio();
            currentAudioSource = source;
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build());
            try {
                if (source.startsWith("/") || source.startsWith("file:")) {
                    mediaPlayer.setDataSource(source.replace("file:", ""));
                } else {
                    mediaPlayer.setDataSource(source);
                }
                audioPreparing = true;
                mediaPlayer.setOnPreparedListener(player -> {
                    audioPreparing = false;
                    audioSeek.setMax(player.getDuration());
                    audioSeek.setProgress(0);
                    audioPlay.setIconResource(R.drawable.ic_pause);
                    player.start();
                    handler.post(audioTicker);
                });
                mediaPlayer.setOnCompletionListener(player -> {
                    audioPlay.setIconResource(R.drawable.ic_play_arrow);
                    audioSeek.setProgress(0);
                    audioTime.setText(DateUtils.formatDuration(player.getDuration()));
                });
                mediaPlayer.setOnErrorListener((player, what, extra) -> {
                    audioPreparing = false;
                    Snackbar.make(findViewById(R.id.reader_root), R.string.reader_audio_unsupported,
                            Snackbar.LENGTH_LONG).show();
                    releaseAudio();
                    return true;
                });
                mediaPlayer.prepareAsync();
                audioTime.setText(R.string.loading);
            } catch (Exception e) {
                releaseAudio();
                Snackbar.make(findViewById(R.id.reader_root), R.string.reader_audio_unsupported,
                        Snackbar.LENGTH_LONG).show();
            }
            return;
        }
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            audioPlay.setIconResource(R.drawable.ic_play_arrow);
        } else {
            mediaPlayer.start();
            audioPlay.setIconResource(R.drawable.ic_pause);
            handler.post(audioTicker);
        }
    }

    private String resolvedAudioSource(ArticleEntity article) {
        File local = FileUtils.resolvePrivate(this, article.audioPath);
        if (local != null && local.isFile()) {
            return local.getAbsolutePath();
        }
        if (article.audioUrl != null
                && (settings.isAllowHttp() || article.audioUrl.startsWith("https://"))) {
            return article.audioUrl;
        }
        return null;
    }

    private void updateAudioProgress() {
        if (mediaPlayer == null) {
            return;
        }
        try {
            if (mediaPlayer.isPlaying()) {
                int position = mediaPlayer.getCurrentPosition();
                int duration = mediaPlayer.getDuration();
                audioSeek.setMax(Math.max(1, duration));
                audioSeek.setProgress(position);
                audioTime.setText(DateUtils.formatDuration(position) + " / "
                        + DateUtils.formatDuration(duration));
            }
        } catch (IllegalStateException ignored) {
            // The player may be transitioning between prepared states.
        }
    }

    private void releaseAudio() {
        handler.removeCallbacks(audioTicker);
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
            } catch (IllegalStateException ignored) {
                // Already stopped.
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
        currentAudioSource = null;
        audioPreparing = false;
        audioPlay.setIconResource(R.drawable.ic_play_arrow);
        audioSeek.setProgress(0);
    }

    private void downloadAudio() {
        if (currentState == null) {
            return;
        }
        long feedId = currentState.article.primaryFeedId == null
                ? -1L : currentState.article.primaryFeedId;
        long articleId = currentState.article.id;
        container.ioExecutor().execute(() ->
                container.downloadRepository().enqueueAudio(articleId, feedId, true));
        Snackbar.make(findViewById(R.id.reader_root), R.string.reader_downloading,
                Snackbar.LENGTH_SHORT).show();
        pollNeedArticle = false;
        pollNeedAudio = true;
        handler.removeCallbacks(archivePoll);
        archivePollAttempts = 0;
        handler.postDelayed(archivePoll, 2000);
    }

    private void openArticle(long articleId) {
        flushProgress(currentRenderedId);
        currentRenderedId = -1L;
        pollNeedArticle = false;
        pollNeedAudio = false;
        viewModel.load(articleId, viewModel.filter(), true);
        scrollView.scrollTo(0, 0);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent == null) {
            return;
        }
        long articleId = intent.getLongExtra(ReaderContract.EXTRA_ARTICLE_ID, -1L);
        Object arg = intent.getSerializableExtra(ReaderContract.EXTRA_FILTER);
        ArticleFilter filter = arg instanceof ArticleFilter ? (ArticleFilter) arg
                : viewModel.filter();
        if (articleId > 0) {
            flushProgress(currentRenderedId);
            currentRenderedId = -1L;
            pollNeedArticle = false;
            pollNeedAudio = false;
            viewModel.load(articleId, filter, true);
        }
    }

    private void updateProgressIndicator() {
        View child = scrollView.getChildAt(0);
        if (child == null) {
            return;
        }
        int max = Math.max(0, child.getMeasuredHeight() - scrollView.getHeight());
        float ratio = max <= 0 ? 0f
                : Math.max(0f, Math.min(1f, scrollView.getScrollY() / (float) max));
        if (scrollView.getScrollY() + scrollView.getHeight() >= child.getMeasuredHeight() - dp(24)) {
            ratio = 1f;
        }
        progressBar.setProgressCompat(Math.round(ratio * 1000f), true);
        progressText.setText(Math.round(ratio * 100) + "%");
    }

    private float currentRatio() {
        View child = scrollView.getChildAt(0);
        if (child == null) {
            return 0f;
        }
        int max = Math.max(0, child.getMeasuredHeight() - scrollView.getHeight());
        return max <= 0 ? 0f : Math.max(0f, Math.min(1f,
                scrollView.getScrollY() / (float) max));
    }

    private void saveReadingProgress() {
        long now = SystemClock.elapsedRealtime();
        long seconds = lastProgressTick <= 0 ? 0 : Math.max(0, (now - lastProgressTick) / 1000);
        if (seconds > 300) {
            seconds = 300;
        }
        final long safeSeconds = seconds;
        if (currentRenderedId > 0 && safeSeconds > 0) {
            float ratio = currentRatio();
            long id = currentRenderedId;
            container.ioExecutor().execute(() ->
                    container.articleRepository().updateProgress(id, ratio, safeSeconds));
        }
        lastProgressTick = now;
    }

    private void flushProgress(long articleId) {
        if (articleId <= 0) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        long seconds = lastProgressTick <= 0 ? 0 : Math.max(0, (now - lastProgressTick) / 1000);
        if (seconds > 600) {
            seconds = 600;
        }
        final long safeSeconds = seconds;
        float ratio = articleId == currentRenderedId ? currentRatio() : 0f;
        container.ioExecutor().execute(() ->
                container.articleRepository().updateProgress(articleId, ratio, safeSeconds));
        lastProgressTick = now;
    }

    @Override
    protected void onResume() {
        super.onResume();
        lastProgressTick = SystemClock.elapsedRealtime();
        handler.removeCallbacks(progressTicker);
        handler.postDelayed(progressTicker, 15_000);
        if (currentState != null) {
            applyReaderTheme();
        }
        if ((pollNeedArticle || pollNeedAudio) && currentRenderedId > 0) {
            handler.removeCallbacks(archivePoll);
            archivePollAttempts = 0;
            handler.postDelayed(archivePoll, 2500);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(progressTicker);
        flushProgress(currentRenderedId);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            audioPlay.setIconResource(R.drawable.ic_play_arrow);
        }
        stopTts();
        handler.removeCallbacks(archivePoll);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        releaseAudio();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }

    private void startOrStopTts() {
        if (ttsSpeaking) {
            stopTts();
            return;
        }
        if (currentState == null) {
            return;
        }
        String plain = Jsoup.parse(currentState.html).text();
        if (plain.length() < 20) {
            Snackbar.make(findViewById(R.id.reader_root), R.string.reader_text_too_short,
                    Snackbar.LENGTH_LONG).show();
            return;
        }
        String text = currentState.article.title + ". " + plain;
        if (!ttsReady) {
            pendingSpeech = text;
            Snackbar.make(findViewById(R.id.reader_root), R.string.reader_tts_unavailable,
                    Snackbar.LENGTH_SHORT).show();
            return;
        }
        speak(text);
    }

    private void speak(String text) {
        if (textToSpeech == null) {
            return;
        }
        int result = textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null,
                "intellirss-article");
        if (result == TextToSpeech.SUCCESS) {
            ttsSpeaking = true;
            invalidateOptionsMenu();
        } else {
            Snackbar.make(findViewById(R.id.reader_root), R.string.reader_tts_unavailable,
                    Snackbar.LENGTH_LONG).show();
        }
    }

    private void stopTts() {
        if (textToSpeech != null) {
            textToSpeech.stop();
        }
        ttsSpeaking = false;
        invalidateOptionsMenu();
    }

    private void shareArticle() {
        if (currentState == null) {
            return;
        }
        ArticleEntity article = currentState.article;
        StringBuilder text = new StringBuilder(article.title);
        if (article.originalUrl != null) {
            text.append("\n\n").append(article.originalUrl);
        }
        String summary = TextUtils.summary(article.description, 300);
        if (!summary.isEmpty()) {
            text.append("\n\n").append(summary);
        }
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, article.title);
        share.putExtra(Intent.EXTRA_TEXT, text.toString());
        startActivity(Intent.createChooser(share, getString(R.string.share_chooser_title)));
    }

    private void copyArticle() {
        if (currentState == null) {
            return;
        }
        String text = Jsoup.parse(currentState.html).text();
        if (text.trim().isEmpty()) {
            text = TextUtils.stripHtmlOrEmpty(currentState.article.description);
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(
                Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(currentState.article.title, text));
        Snackbar.make(findViewById(R.id.reader_root), R.string.reader_copied,
                Snackbar.LENGTH_SHORT).show();
    }

    private void openOriginalArticle() {
        if (currentState == null) {
            return;
        }
        // The title is tappable and the overflow action both open the article's page.
        // Prefer the original URL, then fall back to the canonical URL when a feed
        // omitted the original link.
        String url = currentState.article.originalUrl;
        if (!UrlUtils.isValidHttpUrl(url)) {
            url = currentState.article.canonicalUrl;
        }
        if (url == null || !UrlUtils.isValidHttpUrl(url)) {
            Snackbar.make(findViewById(R.id.reader_root), R.string.reader_no_original_url,
                    Snackbar.LENGTH_LONG).show();
            return;
        }
        openExternal(url);
    }

    private void openExternal(String url) {
        if (!UrlUtils.isValidHttpUrl(url)) {
            Snackbar.make(findViewById(R.id.reader_root), R.string.invalid_link,
                    Snackbar.LENGTH_LONG).show();
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (android.content.ActivityNotFoundException e) {
            Snackbar.make(findViewById(R.id.reader_root), R.string.could_not_open_link,
                    Snackbar.LENGTH_LONG).show();
        }
    }

    private void downloadArticle() {
        if (currentState == null) {
            return;
        }
        if (currentState.article.isArchived) {
            Snackbar.make(findViewById(R.id.reader_root), R.string.reader_downloaded,
                    Snackbar.LENGTH_SHORT).show();
            return;
        }
        long feedId = currentState.article.primaryFeedId == null
                ? -1L : currentState.article.primaryFeedId;
        long articleId = currentState.article.id;
        container.ioExecutor().execute(() ->
                container.downloadRepository().enqueueArticle(articleId, feedId, true));
        Snackbar.make(findViewById(R.id.reader_root), R.string.reader_downloading,
                Snackbar.LENGTH_SHORT).show();
        pollNeedArticle = true;
        pollNeedAudio = false;
        archivePollAttempts = 0;
        handler.removeCallbacks(archivePoll);
        handler.postDelayed(archivePoll, 2000);
    }

    private void confirmDeleteCache() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.action_delete_cached)
                .setMessage(R.string.reader_delete_cache_confirm)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                    if (currentState == null) {
                        return;
                    }
                    long id = currentState.article.id;
                    container.ioExecutor().execute(() -> {
                        container.archiveManager().deleteCachedContent(id);
                        runOnUiThread(() -> {
                            viewModel.reloadCurrent(false);
                            Snackbar.make(findViewById(R.id.reader_root),
                                    R.string.reader_cached_deleted, Snackbar.LENGTH_LONG).show();
                        });
                    });
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void showFontDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_reader_settings,
                null, false);
        SeekBar fontSeek = view.findViewById(R.id.font_size_seek);
        SeekBar spacingSeek = view.findViewById(R.id.line_spacing_seek);
        Spinner familySpinner = view.findViewById(R.id.font_family_spinner);
        Spinner themeSpinner = view.findViewById(R.id.reader_theme_spinner);
        TextView fontLabel = view.findViewById(R.id.font_size_label);
        TextView spacingLabel = view.findViewById(R.id.line_spacing_label);
        String[] families = new String[]{"serif", "sans", "mono"};
        String[] familyLabels = new String[]{getString(R.string.font_serif),
                getString(R.string.font_sans), getString(R.string.font_mono)};
        String[] themes = new String[]{"dark", "light", "sepia"};
        String[] themeLabels = new String[]{getString(R.string.reader_theme_dark),
                getString(R.string.reader_theme_light), getString(R.string.reader_theme_sepia)};
        ArrayAdapter<String> familyAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, familyLabels);
        familyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        familySpinner.setAdapter(familyAdapter);
        ArrayAdapter<String> themeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, themeLabels);
        themeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        themeSpinner.setAdapter(themeAdapter);
        int fontSize = settings.getReaderFontSize();
        fontSeek.setMax(20);
        fontSeek.setProgress(Math.max(0, Math.min(20, fontSize - 12)));
        int spacingProgress = Math.round((settings.getReaderLineSpacing() - 1f) * 100f);
        spacingSeek.setMax(120);
        spacingSeek.setProgress(Math.max(0, Math.min(120, spacingProgress)));
        for (int i = 0; i < families.length; i++) {
            if (families[i].equals(settings.getReaderFontFamily())) {
                familySpinner.setSelection(i);
                break;
            }
        }
        for (int i = 0; i < themes.length; i++) {
            if (themes[i].equals(settings.getReaderTheme())) {
                themeSpinner.setSelection(i);
                break;
            }
        }
        fontLabel.setText(getString(R.string.reader_font_size) + ": "
                + (12 + fontSeek.getProgress()) + " sp");
        spacingLabel.setText(getString(R.string.reader_line_spacing) + ": "
                + String.format(Locale.getDefault(), "%.2f", 1f + spacingSeek.getProgress() / 100f));
        fontSeek.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                fontLabel.setText(getString(R.string.reader_font_size) + ": "
                        + (12 + progress) + " sp");
            }
        });
        spacingSeek.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                spacingLabel.setText(getString(R.string.reader_line_spacing) + ": "
                        + String.format(Locale.getDefault(), "%.2f",
                        1f + progress / 100f));
            }
        });
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.reader_font_settings)
                .setView(view)
                .setPositiveButton(R.string.action_apply, (dialog, which) -> {
                    settings.setReaderFontSize(12 + fontSeek.getProgress());
                    settings.setReaderLineSpacing(1f + spacingSeek.getProgress() / 100f);
                    settings.setReaderFontFamily(families[familySpinner.getSelectedItemPosition()]);
                    settings.setReaderTheme(themes[themeSpinner.getSelectedItemPosition()]);
                    viewModel.reloadCurrent(false);
                })
                .setNegativeButton(R.string.action_cancel, null);
        builder.show();
    }

    private void showAiTools() {
        if (!settings.localAiEnabled()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.reader_ai_tools)
                    .setMessage(R.string.reader_ai_disabled)
                    .setPositiveButton(R.string.snackbar_settings, (dialog, which) ->
                            startActivity(new Intent(this, SettingsActivity.class)))
                    .setNegativeButton(R.string.action_cancel, null)
                    .show();
            return;
        }
        if (currentState == null) {
            return;
        }
        String[] actions = new String[]{getString(R.string.reader_ai_summarize),
                getString(R.string.reader_ai_suggest_tags)};
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.reader_ai_choose)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        runAiSummary();
                    } else {
                        runAiTags();
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void runAiSummary() {
        if (currentState == null) {
            return;
        }
        ReaderState snapshot = currentState;
        String text = Jsoup.parse(snapshot.html).text();
        if (text.length() < 40) {
            Snackbar.make(findViewById(R.id.reader_root), R.string.ai_no_text,
                    Snackbar.LENGTH_LONG).show();
            return;
        }
        Snackbar progress = Snackbar.make(findViewById(R.id.reader_root),
                R.string.reader_ai_loading, Snackbar.LENGTH_INDEFINITE);
        progress.show();
        LocalAiClient client = container.localAiClient();
        container.ioExecutor().execute(() -> {
            try {
                String summary = client.summarize(snapshot.article.title, text);
                container.articleRepository().saveAiSummary(snapshot.article.id, summary);
                runOnUiThread(() -> {
                    progress.dismiss();
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.reader_summary)
                            .setMessage(summary)
                            .setPositiveButton(R.string.action_close, null)
                            .show();
                    viewModel.reloadCurrent(false);
                });
            } catch (LocalAiException e) {
                runOnUiThread(() -> {
                    progress.dismiss();
                    Snackbar.make(findViewById(R.id.reader_root),
                            getString(R.string.reader_ai_failed, e.getMessage()),
                            Snackbar.LENGTH_LONG).show();
                });
            }
        });
    }

    private void runAiTags() {
        if (currentState == null) {
            return;
        }
        ReaderState snapshot = currentState;
        String text = Jsoup.parse(snapshot.html).text();
        if (text.length() < 40) {
            Snackbar.make(findViewById(R.id.reader_root), R.string.ai_no_text,
                    Snackbar.LENGTH_LONG).show();
            return;
        }
        Snackbar progress = Snackbar.make(findViewById(R.id.reader_root),
                R.string.reader_ai_loading, Snackbar.LENGTH_INDEFINITE);
        progress.show();
        LocalAiClient client = container.localAiClient();
        container.ioExecutor().execute(() -> {
            try {
                List<String> suggestions = client.suggestTags(snapshot.article.title, text);
                runOnUiThread(() -> {
                    progress.dismiss();
                    if (suggestions.isEmpty()) {
                        Snackbar.make(findViewById(R.id.reader_root),
                                R.string.reader_ai_no_tags, Snackbar.LENGTH_LONG).show();
                        return;
                    }
                    final boolean[] checked = new boolean[suggestions.size()];
                    java.util.Arrays.fill(checked, true);
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.reader_ai_tags_title)
                            .setMultiChoiceItems(suggestions.toArray(new String[0]), checked,
                                    (dialog, which, isChecked) -> checked[which] = isChecked)
                            .setPositiveButton(R.string.action_add, (dialog, which) -> {
                                container.ioExecutor().execute(() -> {
                                    for (int i = 0; i < suggestions.size(); i++) {
                                        if (checked[i]) {
                                            container.articleRepository()
                                                    .addTag(snapshot.article.id, suggestions.get(i));
                                        }
                                    }
                                    container.articleRepository().saveAiTags(snapshot.article.id,
                                            String.join(", ", suggestions));
                                    runOnUiThread(() -> {
                                        viewModel.reloadCurrent(false);
                                        Snackbar.make(findViewById(R.id.reader_root),
                                                R.string.reader_ai_tags_added,
                                                Snackbar.LENGTH_SHORT).show();
                                    });
                                });
                            })
                            .setNegativeButton(R.string.action_cancel, null)
                            .show();
                });
            } catch (LocalAiException e) {
                runOnUiThread(() -> {
                    progress.dismiss();
                    Snackbar.make(findViewById(R.id.reader_root),
                            getString(R.string.reader_ai_failed, e.getMessage()),
                            Snackbar.LENGTH_LONG).show();
                });
            }
        });
    }

    private void markUnread() {
        if (currentState == null) {
            return;
        }
        viewModel.setRead(false);
        Snackbar.make(findViewById(R.id.reader_root), R.string.reader_marked_unread,
                Snackbar.LENGTH_SHORT).show();
    }

    private void showRelevanceDetails() {
        if (currentState == null) {
            return;
        }
        ArticleEntity article = currentState.article;
        String explanation = article.relevanceReason;
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.reader_relevance_details))
                .setMessage(getString(R.string.article_relevance, article.relevanceScore)
                        + "  \u2022  " + article.relevanceLevel.name()
                        + "\n\n" + (explanation == null || explanation.isEmpty()
                        ? getString(R.string.not_available) : explanation)
                        + "\n\n" + getString(R.string.reader_relevance_independent))
                .setPositiveButton(R.string.action_close, null)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.reader_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (currentState == null) {
            return super.onPrepareOptionsMenu(menu);
        }
        ArticleEntity article = currentState.article;
        MenuItem bookmark = menu.findItem(R.id.action_bookmark);
        if (bookmark != null) {
            bookmark.setIcon(article.isBookmarked
                    ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_border);
            bookmark.setTitle(article.isBookmarked
                    ? R.string.action_remove_bookmark : R.string.action_bookmark);
        }
        MenuItem download = menu.findItem(R.id.action_download);
        if (download != null) {
            download.setVisible(!article.isArchived);
        }
        MenuItem deleteCache = menu.findItem(R.id.action_delete_cache);
        if (deleteCache != null) {
            deleteCache.setVisible(article.isArchived);
        }
        MenuItem versions = menu.findItem(R.id.action_versions);
        if (versions != null) {
            versions.setVisible(currentState.versions != null
                    && !currentState.versions.isEmpty());
        }
        MenuItem tts = menu.findItem(R.id.action_tts);
        if (tts != null) {
            tts.setTitle(ttsSpeaking ? R.string.action_stop : R.string.action_speak);
        }
        int iconColor = style == null
                ? ContextCompat.getColor(this, R.color.on_surface) : style.text;
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            Drawable icon = item.getIcon();
            if (icon != null) {
                Drawable tinted = DrawableCompat.wrap(icon).mutate();
                DrawableCompat.setTint(tinted, iconColor);
                item.setIcon(tinted);
            }
        }
        MenuItem markedBookmark = menu.findItem(R.id.action_bookmark);
        if (markedBookmark != null && article.isBookmarked) {
            Drawable icon = markedBookmark.getIcon();
            if (icon != null) {
                Drawable tinted = DrawableCompat.wrap(icon).mutate();
                DrawableCompat.setTint(tinted, ContextCompat.getColor(this, R.color.bookmark));
                markedBookmark.setIcon(tinted);
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_bookmark) {
            viewModel.toggleBookmark();
            Snackbar.make(findViewById(R.id.reader_root),
                    currentState != null && currentState.article.isBookmarked
                            ? R.string.reader_bookmark_removed : R.string.reader_bookmark_added,
                    Snackbar.LENGTH_SHORT).show();
            return true;
        }
        if (id == R.id.action_download) {
            downloadArticle();
            return true;
        }
        if (id == R.id.action_share) {
            shareArticle();
            return true;
        }
        if (id == R.id.action_font) {
            showFontDialog();
            return true;
        }
        if (id == R.id.action_tts) {
            startOrStopTts();
            return true;
        }
        if (id == R.id.action_copy) {
            copyArticle();
            return true;
        }
        if (id == R.id.action_open_original) {
            openOriginalArticle();
            return true;
        }
        if (id == R.id.action_versions) {
            if (currentState != null) {
                Intent intent = new Intent(this, VersionHistoryActivity.class);
                intent.putExtra(VersionHistoryActivity.EXTRA_ARTICLE_ID,
                        currentState.article.id);
                startActivity(intent);
            }
            return true;
        }
        if (id == R.id.action_ai) {
            showAiTools();
            return true;
        }
        if (id == R.id.action_relevance) {
            showRelevanceDetails();
            return true;
        }
        if (id == R.id.action_mark_unread) {
            markUnread();
            return true;
        }
        if (id == R.id.action_delete_cache) {
            confirmDeleteCache();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private Drawable tintedDrawable(int resource, int color) {
        Drawable drawable = ContextCompat.getDrawable(this, resource);
        if (drawable == null) {
            return null;
        }
        drawable = DrawableCompat.wrap(drawable).mutate();
        DrawableCompat.setTint(drawable, color);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static int withAlpha(int color, float alpha) {
        return (color & 0x00FFFFFF) | (Math.round(alpha * 255) << 24);
    }

    private static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }
}
