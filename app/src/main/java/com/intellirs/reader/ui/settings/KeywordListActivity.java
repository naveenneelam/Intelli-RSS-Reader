package com.intellirs.reader.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.intellirs.reader.R;
import com.intellirs.reader.data.entity.KeywordRuleEntity;
import com.intellirs.reader.data.entity.KeywordType;
import com.intellirs.reader.ui.BaseActivity;

public class KeywordListActivity extends BaseActivity {
    public static final String EXTRA_TYPE = "keyword_type";
    private KeywordType type = KeywordType.PRIORITY;
    private KeywordAdapter adapter;
    private View emptyState;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_keywords);
        String rawType = getIntent().getStringExtra(EXTRA_TYPE);
        if (rawType != null) {
            try {
                type = KeywordType.valueOf(rawType);
            } catch (IllegalArgumentException ignored) {
                type = KeywordType.PRIORITY;
            }
        }
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setTitle(titleFor(type));
        toolbar.setNavigationOnClickListener(view -> finish());
        RecyclerView list = findViewById(R.id.keyword_list);
        emptyState = findViewById(R.id.empty_state);
        adapter = new KeywordAdapter(new KeywordAdapter.Listener() {
            @Override
            public void onEdit(KeywordRuleEntity rule) {
                showDialog(rule);
            }

            @Override
            public void onDelete(KeywordRuleEntity rule) {
                new MaterialAlertDialogBuilder(KeywordListActivity.this)
                        .setTitle(R.string.keyword_muted)
                        .setMessage(rule.keyword)
                        .setPositiveButton(R.string.action_delete, (dialog, which) ->
                                container.ioExecutor().execute(() ->
                                        container.database().keywordRuleDao().delete(rule)))
                        .setNegativeButton(R.string.action_cancel, null)
                        .show();
            }
        });
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        container.database().keywordRuleDao().observeByType(type).observe(this, rules -> {
            adapter.submitList(rules);
            boolean empty = rules == null || rules.isEmpty();
            emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
            list.setVisibility(empty ? View.GONE : View.VISIBLE);
        });
        findViewById(R.id.fab_add_keyword).setOnClickListener(view -> showDialog(null));
    }

    private String titleFor(KeywordType type) {
        switch (type) {
            case MUTED:
                return getString(R.string.keywords_muted_title);
            case TOPIC:
                return getString(R.string.keywords_topics_title);
            case PRIORITY:
            default:
                return getString(R.string.keywords_priority_title);
        }
    }

    private void showDialog(@Nullable KeywordRuleEntity editing) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_keyword, null);
        TextInputEditText input = view.findViewById(R.id.keyword_input);
        SeekBar weight = view.findViewById(R.id.keyword_weight_seek);
        TextView label = view.findViewById(R.id.keyword_weight_label);
        int initialWeight = editing == null ? 50 : editing.weight;
        weight.setProgress(Math.max(1, Math.min(100, initialWeight)));
        label.setText(getString(R.string.keyword_weight) + ": " + weight.getProgress());
        weight.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                label.setText(getString(R.string.keyword_weight) + ": "
                        + Math.max(1, progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        if (editing != null) {
            input.setText(editing.keyword);
            input.setSelection(input.length());
        }
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(editing == null ? R.string.keyword_add : R.string.action_edit)
                .setView(view)
                .setPositiveButton(R.string.action_save, (dialog, which) -> {
                    String keyword = input.getText() == null ? ""
                            : input.getText().toString().trim();
                    if (keyword.isEmpty()) {
                        Toast.makeText(this, R.string.keyword_invalid, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int value = Math.max(1, weight.getProgress());
                    if (editing == null) {
                        KeywordRuleEntity entity = new KeywordRuleEntity();
                        entity.keyword = keyword;
                        entity.type = type;
                        entity.weight = value;
                        entity.createdAt = System.currentTimeMillis();
                        container.ioExecutor().execute(() -> {
                            long result = container.database().keywordRuleDao().insert(entity);
                            runOnUiThread(() -> Toast.makeText(this,
                                    result > 0 ? R.string.keyword_saved : R.string.keyword_exists,
                                    Toast.LENGTH_SHORT).show());
                        });
                    } else {
                        editing.keyword = keyword;
                        editing.weight = value;
                        container.ioExecutor().execute(() -> {
                            container.database().keywordRuleDao().update(editing);
                            runOnUiThread(() -> Toast.makeText(this, R.string.keyword_saved,
                                    Toast.LENGTH_SHORT).show());
                        });
                    }
                })
                .setNegativeButton(R.string.action_cancel, null);
        builder.show();
    }

    private static class KeywordAdapter
            extends ListAdapter<KeywordRuleEntity, KeywordAdapter.Holder> {
        interface Listener {
            void onEdit(KeywordRuleEntity rule);

            void onDelete(KeywordRuleEntity rule);
        }

        private static final DiffUtil.ItemCallback<KeywordRuleEntity> DIFF =
                new DiffUtil.ItemCallback<KeywordRuleEntity>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull KeywordRuleEntity oldItem,
                                                   @NonNull KeywordRuleEntity newItem) {
                        return oldItem.id == newItem.id;
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull KeywordRuleEntity oldItem,
                                                      @NonNull KeywordRuleEntity newItem) {
                        return oldItem.id == newItem.id
                                && oldItem.keyword.equals(newItem.keyword)
                                && oldItem.weight == newItem.weight;
                    }
                };

        private final Listener listener;

        KeywordAdapter(Listener listener) {
            super(DIFF);
            this.listener = listener;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_keyword, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.bind(getItem(position), listener);
        }

        static class Holder extends RecyclerView.ViewHolder {
            final TextView text;
            final TextView weight;
            final ImageButton edit;
            final ImageButton delete;

            Holder(@NonNull View itemView) {
                super(itemView);
                text = itemView.findViewById(R.id.keyword_text);
                weight = itemView.findViewById(R.id.keyword_weight);
                edit = itemView.findViewById(R.id.keyword_edit);
                delete = itemView.findViewById(R.id.keyword_delete);
            }

            void bind(KeywordRuleEntity rule, Listener listener) {
                text.setText(rule.keyword);
                weight.setText(itemView.getContext().getString(R.string.keyword_weight)
                        + ": " + rule.weight);
                edit.setOnClickListener(view -> listener.onEdit(rule));
                delete.setOnClickListener(view -> listener.onDelete(rule));
                itemView.setOnClickListener(view -> listener.onEdit(rule));
            }
        }
    }
}
