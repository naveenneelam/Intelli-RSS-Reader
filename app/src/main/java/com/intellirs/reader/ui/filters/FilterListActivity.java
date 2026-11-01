package com.intellirs.reader.ui.filters;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.intellirs.reader.R;
import com.intellirs.reader.data.database.AppDatabase;
import com.intellirs.reader.data.entity.FilterRuleEntity;
import com.intellirs.reader.domain.filtering.FilterAction;
import com.intellirs.reader.domain.filtering.FilterRule;
import com.intellirs.reader.domain.filtering.FilterRuleCodec;
import com.intellirs.reader.ui.BaseActivity;

import java.util.ArrayList;
import java.util.List;

public class FilterListActivity extends BaseActivity {
    private FilterAdapter adapter;
    private View emptyState;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_filter_list);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(view -> finish());
        RecyclerView list = findViewById(R.id.filter_list);
        emptyState = findViewById(R.id.empty_state);
        adapter = new FilterAdapter(new FilterAdapter.Listener() {
            @Override
            public void onEdit(FilterRuleEntity rule) {
                Intent intent = new Intent(FilterListActivity.this, FilterEditActivity.class);
                intent.putExtra(FilterEditActivity.EXTRA_RULE_ID, rule.id);
                startActivity(intent);
            }

            @Override
            public void onToggle(FilterRuleEntity rule, boolean enabled) {
                container.ioExecutor().execute(() ->
                        container.database().filterRuleDao().setEnabled(rule.id, enabled,
                                System.currentTimeMillis()));
            }

            @Override
            public void onMove(FilterRuleEntity rule, boolean up) {
                moveRule(rule, up);
            }

            @Override
            public void onDelete(FilterRuleEntity rule) {
                confirmDelete(rule);
            }
        });
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        container.database().filterRuleDao().observeAll().observe(this, rules -> {
            adapter.submitList(rules);
            boolean empty = rules == null || rules.isEmpty();
            emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
            list.setVisibility(empty ? View.GONE : View.VISIBLE);
        });
        findViewById(R.id.fab_add_filter).setOnClickListener(view ->
                startActivity(new Intent(this, FilterEditActivity.class)));
    }

    private void moveRule(FilterRuleEntity rule, boolean up) {
        container.ioExecutor().execute(() -> {
            AppDatabase db = container.database();
            List<FilterRuleEntity> rules = db.filterRuleDao().getAll();
            int index = -1;
            for (int i = 0; i < rules.size(); i++) {
                if (rules.get(i).id == rule.id) {
                    index = i;
                    break;
                }
            }
            if (index < 0) {
                return;
            }
            int target = up ? index - 1 : index + 1;
            if (target < 0 || target >= rules.size()) {
                return;
            }
            FilterRuleEntity moving = rules.remove(index);
            rules.add(target, moving);
            db.runInTransaction(() -> {
                for (int i = 0; i < rules.size(); i++) {
                    db.filterRuleDao().setPriority(rules.get(i).id, i + 1);
                }
            });
        });
    }

    private void confirmDelete(FilterRuleEntity rule) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.filter_delete_confirm)
                .setMessage(rule.ruleName)
                .setPositiveButton(R.string.action_delete, (dialog, which) ->
                        container.ioExecutor().execute(() ->
                                container.database().filterRuleDao().delete(rule)))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private static class FilterAdapter
            extends ListAdapter<FilterRuleEntity, FilterAdapter.Holder> {
        interface Listener {
            void onEdit(FilterRuleEntity rule);

            void onToggle(FilterRuleEntity rule, boolean enabled);

            void onMove(FilterRuleEntity rule, boolean up);

            void onDelete(FilterRuleEntity rule);
        }

        private static final DiffUtil.ItemCallback<FilterRuleEntity> DIFF =
                new DiffUtil.ItemCallback<FilterRuleEntity>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull FilterRuleEntity oldItem,
                                                   @NonNull FilterRuleEntity newItem) {
                        return oldItem.id == newItem.id;
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull FilterRuleEntity oldItem,
                                                      @NonNull FilterRuleEntity newItem) {
                        return oldItem.id == newItem.id
                                && oldItem.enabled == newItem.enabled
                                && oldItem.priority == newItem.priority
                                && oldItem.ruleName.equals(newItem.ruleName)
                                && oldItem.conditionJson.equals(newItem.conditionJson)
                                && oldItem.actionJson.equals(newItem.actionJson);
                    }
                };

        private final Listener listener;

        FilterAdapter(Listener listener) {
            super(DIFF);
            this.listener = listener;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_filter_rule, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.bind(getItem(position), listener);
        }

        static class Holder extends RecyclerView.ViewHolder {
            final TextView name;
            final TextView summary;
            final MaterialSwitch enabled;
            final ImageButton up;
            final ImageButton down;
            final ImageButton edit;
            final ImageButton delete;

            Holder(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.rule_name);
                summary = itemView.findViewById(R.id.rule_summary);
                enabled = itemView.findViewById(R.id.rule_enabled);
                up = itemView.findViewById(R.id.rule_move_up);
                down = itemView.findViewById(R.id.rule_move_down);
                edit = itemView.findViewById(R.id.rule_edit);
                delete = itemView.findViewById(R.id.rule_delete);
            }

            void bind(FilterRuleEntity entity, Listener listener) {
                FilterRule rule = FilterRuleCodec.fromEntity(entity);
                name.setText(entity.ruleName);
                String match = itemView.getContext().getString(rule.matchAll
                        ? R.string.filter_summary_all : R.string.filter_summary_any,
                        rule.conditions.size());
                List<String> actions = new ArrayList<>();
                for (FilterAction action : rule.actions) {
                    if (action.type == null) {
                        continue;
                    }
                    switch (action.type) {
                        case BOOST_RELEVANCE:
                            actions.add(itemView.getContext().getString(
                                    R.string.filter_action_boost));
                            break;
                        case MUTE_NOTIFICATION:
                            actions.add(itemView.getContext().getString(
                                    R.string.filter_action_mute));
                            break;
                        case ENABLE_NOTIFICATION:
                            actions.add(itemView.getContext().getString(
                                    R.string.filter_action_notify));
                            break;
                        case MARK_READ:
                            actions.add(itemView.getContext().getString(
                                    R.string.filter_action_mark_read));
                            break;
                        case ARCHIVE:
                            actions.add(itemView.getContext().getString(
                                    R.string.filter_action_archive));
                            break;
                        case ADD_TAG:
                            actions.add(itemView.getContext().getString(
                                    R.string.filter_action_tag));
                            break;
                        default:
                            break;
                    }
                }
                summary.setText(match + "  \u2192  " + String.join(", ", actions));
                enabled.setOnCheckedChangeListener(null);
                enabled.setChecked(entity.enabled);
                enabled.setOnCheckedChangeListener((buttonView, isChecked) ->
                        listener.onToggle(entity, isChecked));
                up.setOnClickListener(view -> listener.onMove(entity, true));
                down.setOnClickListener(view -> listener.onMove(entity, false));
                edit.setOnClickListener(view -> listener.onEdit(entity));
                delete.setOnClickListener(view -> listener.onDelete(entity));
                itemView.setOnClickListener(view -> listener.onEdit(entity));
            }
        }
    }
}
