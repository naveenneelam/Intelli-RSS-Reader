package com.intellirs.reader.ui.archive;

import android.net.Uri;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.intellirs.reader.R;
import com.intellirs.reader.data.archive.ArchiveExporter;
import com.intellirs.reader.data.archive.CleanupResult;
import com.intellirs.reader.data.archive.StorageInfo;
import com.intellirs.reader.ui.BaseActivity;
import com.intellirs.reader.util.TextUtils;

import java.io.OutputStream;

public class StorageActivity extends BaseActivity {
    private TextView total;
    private TextView limit;
    private TextView articles;
    private TextView html;
    private TextView images;
    private TextView audio;
    private TextView icons;
    private TextView other;
    private TextView free;
    private ProgressBar progress;
    private ArchiveExporter.Format pendingFormat;
    private ActivityResultLauncher<String> createDocument;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_storage);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(view -> finish());
        total = findViewById(R.id.storage_total);
        limit = findViewById(R.id.storage_limit);
        articles = findViewById(R.id.storage_articles);
        html = findViewById(R.id.storage_html);
        images = findViewById(R.id.storage_images);
        audio = findViewById(R.id.storage_audio);
        icons = findViewById(R.id.storage_icons);
        other = findViewById(R.id.storage_other);
        free = findViewById(R.id.storage_free);
        progress = findViewById(R.id.storage_progress);

        createDocument = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("*/*"), this::onExportTarget);
        findViewById(R.id.storage_cleanup).setOnClickListener(view -> confirmCleanup());
        findViewById(R.id.storage_export).setOnClickListener(view -> chooseExportFormat());
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        container.ioExecutor().execute(() -> {
            StorageInfo info = container.archiveManager().getStorageInfo();
            runOnUiThread(() -> bind(info));
        });
    }

    private void bind(StorageInfo info) {
        total.setText(TextUtils.humanBytes(info.totalBytes));
        limit.setText(getString(R.string.storage_limit_helper,
                settings.getArchiveMaxMb()));
        articles.setText(getString(R.string.storage_articles) + ": " + info.archivedArticles);
        html.setText(getString(R.string.storage_html) + ": "
                + TextUtils.humanBytes(info.htmlBytes));
        images.setText(getString(R.string.storage_images) + ": "
                + TextUtils.humanBytes(info.imageBytes));
        audio.setText(getString(R.string.storage_audio) + ": "
                + TextUtils.humanBytes(info.audioBytes));
        icons.setText(getString(R.string.storage_icons) + ": "
                + TextUtils.humanBytes(info.iconBytes));
        other.setText(getString(R.string.storage_cache) + ": "
                + TextUtils.humanBytes(info.otherBytes));
        free.setText(getString(R.string.storage_free_space,
                TextUtils.humanBytes(info.freeDeviceBytes)));
        long max = Math.max(1, settings.getArchiveMaxBytes());
        int ratio = (int) Math.min(1000, info.totalBytes * 1000L / max);
        progress.setProgress(ratio);
    }

    private void confirmCleanup() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.storage_cleanup)
                .setMessage(R.string.settings_archive_cleanup_hint)
                .setPositiveButton(R.string.action_apply, (dialog, which) ->
                        container.ioExecutor().execute(() -> {
                            CleanupResult result = container.archiveManager().cleanupIfNeeded();
                            runOnUiThread(() -> {
                                Snackbar.make(total, getString(
                                        R.string.settings_archive_cleanup_result,
                                        TextUtils.humanBytes(result.bytesFreed),
                                        result.articlesRemoved), Snackbar.LENGTH_LONG).show();
                                refresh();
                            });
                        }))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void chooseExportFormat() {
        String[] labels = new String[]{
                getString(R.string.settings_archive_export_html),
                getString(R.string.settings_archive_export_json),
                getString(R.string.settings_archive_export_zip)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_archive_export_format)
                .setItems(labels, (dialog, which) -> {
                    pendingFormat = which == 0 ? ArchiveExporter.Format.HTML
                            : which == 1 ? ArchiveExporter.Format.JSON
                            : ArchiveExporter.Format.ZIP;
                    createDocument.launch(ArchiveExporter.suggestedFileName(pendingFormat));
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void onExportTarget(Uri uri) {
        if (uri == null || pendingFormat == null) {
            return;
        }
        ArchiveExporter.Format format = pendingFormat;
        Snackbar.make(total, R.string.export_progress, Snackbar.LENGTH_INDEFINITE).show();
        container.ioExecutor().execute(() -> {
            try (OutputStream output = getContentResolver().openOutputStream(uri)) {
                if (output == null) {
                    throw new IllegalStateException("Cannot open export destination");
                }
                int count = container.archiveExporter().export(output, format);
                runOnUiThread(() -> {
                    int message = format == ArchiveExporter.Format.HTML
                            ? R.string.archive_export_html_success
                            : format == ArchiveExporter.Format.ZIP
                            ? R.string.archive_export_zip_success
                            : R.string.archive_export_json_success;
                    Snackbar.make(total, getString(message) + " (" + count + ")",
                            Snackbar.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Snackbar.make(total,
                        getString(R.string.archive_export_error, e.getMessage()),
                        Snackbar.LENGTH_LONG).show());
            }
        });
    }
}
