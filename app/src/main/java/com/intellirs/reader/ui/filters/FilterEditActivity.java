package com.intellirs.reader.ui.filters;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.intellirs.reader.R;
import com.intellirs.reader.data.entity.FilterRuleEntity;
import com.intellirs.reader.domain.filtering.FilterAction;
import com.intellirs.reader.domain.filtering.FilterActionType;
import com.intellirs.reader.domain.filtering.FilterCondition;
import com.intellirs.reader.domain.filtering.FilterField;
import com.intellirs.reader.domain.filtering.FilterOperator;
import com.intellirs.reader.domain.filtering.FilterRule;
import com.intellirs.reader.domain.filtering.FilterRuleCodec;
import com.intellirs.reader.ui.BaseActivity;

import java.util.ArrayList;
import java.util.List;

public class FilterEditActivity extends BaseActivity {
    public static final String EXTRA_RULE_ID = "rule_id";

    private TextInputEditText nameInput;
    private MaterialSwitch enabledSwitch;
    private MaterialSwitch matchAllSwitch;
    private LinearLayout conditionsContainer;
    private CheckBox boost;
    private TextInputEditText boostValue;
    private CheckBox mute;
    private CheckBox notify;
    private CheckBox markRead;
    private CheckBox archive;
    private CheckBox tag;
    private TextInputEditText tagValue;
    private FilterRuleEntity editing;
    private final List<ConditionRow> rows = new ArrayList<>();
    private final FilterField[] fields = FilterField.values();
    private final FilterOperator[] operators = FilterOperator.values();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_filter_edit);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(view -> finish());

        nameInput = findViewById(R.id.filter_name_input);
        enabledSwitch = findViewById(R.id.filter_enabled_switch);
        matchAllSwitch = findViewById(R.id.filter_match_all_switch);
        conditionsContainer = findViewById(R.id.conditions_container);
        boost = findViewById(R.id.action_boost);
        boostValue = findViewById(R.id.action_boost_value);
        mute = findViewById(R.id.action_mute);
        notify = findViewById(R.id.action_notify);
        markRead = findViewById(R.id.action_mark_read);
        archive = findViewById(R.id.action_archive);
        tag = findViewById(R.id.action_tag);
        tagValue = findViewById(R.id.action_tag_value);
        boostValue.setText("15");
        findViewById(R.id.add_condition).setOnClickListener(view -> addCondition(null));
        findViewById(R.id.save_filter).setOnClickListener(view -> save());

        long ruleId = getIntent().getLongExtra(EXTRA_RULE_ID, -1L);
        if (ruleId > 0) {
            toolbar.setTitle(R.string.filter_edit_title);
            container.ioExecutor().execute(() -> {
                FilterRuleEntity loaded = container.database().filterRuleDao().getById(ruleId);
                runOnUiThread(() -> populate(loaded));
            });
        } else {
            toolbar.setTitle(R.string.filter_new_title);
            enabledSwitch.setChecked(true);
            matchAllSwitch.setChecked(true);
            addCondition(null);
        }
    }

    private void populate(FilterRuleEntity entity) {
        if (entity == null) {
            finish();
            return;
        }
        editing = entity;
        FilterRule rule = FilterRuleCodec.fromEntity(entity);
        nameInput.setText(rule.name);
        enabledSwitch.setChecked(rule.enabled);
        matchAllSwitch.setChecked(rule.matchAll);
        conditionsContainer.removeAllViews();
        rows.clear();
        for (FilterCondition condition : rule.conditions) {
            addCondition(condition);
        }
        if (rows.isEmpty()) {
            addCondition(null);
        }
        for (FilterAction action : rule.actions) {
            if (action.type == null) {
                continue;
            }
            switch (action.type) {
                case BOOST_RELEVANCE:
                    boost.setChecked(true);
                    boostValue.setText(action.value);
                    break;
                case MUTE_NOTIFICATION:
                    mute.setChecked(true);
                    break;
                case ENABLE_NOTIFICATION:
                    notify.setChecked(true);
                    break;
                case MARK_READ:
                    markRead.setChecked(true);
                    break;
                case ARCHIVE:
                    archive.setChecked(true);
                    break;
                case ADD_TAG:
                    tag.setChecked(true);
                    tagValue.setText(action.value);
                    break;
                default:
                    break;
            }
        }
    }

    private void addCondition(@Nullable FilterCondition initial) {
        View view = LayoutInflater.from(this).inflate(R.layout.item_filter_condition,
                conditionsContainer, false);
        Spinner fieldSpinner = view.findViewById(R.id.condition_field);
        Spinner operatorSpinner = view.findViewById(R.id.condition_operator);
        TextInputEditText valueInput = view.findViewById(R.id.condition_value);
        view.findViewById(R.id.condition_remove).setOnClickListener(v -> {
            ConditionRow row = findRow(view);
            if (row != null) {
                rows.remove(row);
                conditionsContainer.removeView(view);
            }
        });

        String[] fieldLabels = getResources().getStringArray(R.array.filter_field_labels);
        ArrayAdapter<String> fieldAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, fieldLabels);
        fieldAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        fieldSpinner.setAdapter(fieldAdapter);
        String[] operatorLabels = getResources().getStringArray(R.array.filter_operator_labels);
        ArrayAdapter<String> operatorAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, operatorLabels);
        operatorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        operatorSpinner.setAdapter(operatorAdapter);

        FilterField field = initial == null ? FilterField.TITLE : initial.field;
        FilterOperator operator = initial == null ? FilterOperator.CONTAINS : initial.operator;
        fieldSpinner.setSelection(field == null ? 0 : field.ordinal());
        operatorSpinner.setSelection(operator == null ? 0 : operator.ordinal());
        if (initial != null) {
            valueInput.setText(initial.value);
        }
        ConditionRow row = new ConditionRow(view, fieldSpinner, operatorSpinner, valueInput);
        rows.add(row);
        applyFieldBehavior(row);
        fieldSpinner.setOnItemSelectedListener(new SimpleItemSelected() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View selected,
                                       int position, long id) {
                applyFieldBehavior(row);
            }
        });
        conditionsContainer.addView(view);
    }

    private void applyFieldBehavior(ConditionRow row) {
        FilterField field = fields[row.field.getSelectedItemPosition()];
        boolean booleanField = field == FilterField.HAS_IMAGES || field == FilterField.HAS_VIDEO
                || field == FilterField.HAS_AUDIO;
        if (booleanField) {
            row.operator.setSelection(FilterOperator.IS_TRUE.ordinal());
            row.value.setEnabled(false);
            row.value.setText("true");
        } else {
            row.value.setEnabled(true);
            if ("true".equals(text(row.value))) {
                row.value.setText("");
            }
        }
    }

    private ConditionRow findRow(View view) {
        for (ConditionRow row : rows) {
            if (row.root == view) {
                return row;
            }
        }
        return null;
    }

    private void save() {
        String name = text(nameInput);
        if (name.isEmpty()) {
            nameInput.setError(getString(R.string.filter_invalid));
            return;
        }
        List<FilterCondition> conditions = new ArrayList<>();
        for (ConditionRow row : rows) {
            FilterField field = fields[row.field.getSelectedItemPosition()];
            FilterOperator operator = operators[row.operator.getSelectedItemPosition()];
            String value = text(row.value);
            boolean booleanField = field == FilterField.HAS_IMAGES
                    || field == FilterField.HAS_VIDEO || field == FilterField.HAS_AUDIO;
            if (value.isEmpty() && !booleanField) {
                showError(R.string.filter_condition_incomplete);
                return;
            }
            if (booleanField) {
                value = "true";
            }
            conditions.add(new FilterCondition(field, operator, value));
        }
        if (conditions.isEmpty()) {
            showError(R.string.filter_invalid);
            return;
        }
        FilterRule rule = new FilterRule();
        rule.name = name;
        rule.enabled = enabledSwitch.isChecked();
        rule.matchAll = matchAllSwitch.isChecked();
        rule.conditions.addAll(conditions);
        if (boost.isChecked()) {
            String valueText = text(boostValue);
            if (valueText.isEmpty()) {
                showError(R.string.filter_invalid);
                return;
            }
            try {
                Integer.parseInt(valueText);
            } catch (NumberFormatException e) {
                showError(R.string.filter_invalid);
                return;
            }
            rule.actions.add(new FilterAction(FilterActionType.BOOST_RELEVANCE, valueText));
        }
        if (mute.isChecked()) {
            rule.actions.add(new FilterAction(FilterActionType.MUTE_NOTIFICATION, ""));
        }
        if (notify.isChecked()) {
            rule.actions.add(new FilterAction(FilterActionType.ENABLE_NOTIFICATION, ""));
        }
        if (markRead.isChecked()) {
            rule.actions.add(new FilterAction(FilterActionType.MARK_READ, ""));
        }
        if (archive.isChecked()) {
            rule.actions.add(new FilterAction(FilterActionType.ARCHIVE, ""));
        }
        if (tag.isChecked()) {
            String tagText = text(tagValue);
            if (tagText.isEmpty()) {
                showError(R.string.filter_invalid);
                return;
            }
            rule.actions.add(new FilterAction(FilterActionType.ADD_TAG, tagText));
        }
        if (rule.actions.isEmpty()) {
            showError(R.string.filter_invalid);
            return;
        }

        FilterRuleEntity entity = editing == null ? new FilterRuleEntity() : editing;
        entity.ruleName = name;
        entity.enabled = rule.enabled;
        entity.conditionJson = FilterRuleCodec.encodeConditions(rule);
        entity.actionJson = FilterRuleCodec.encodeActions(rule);
        entity.updatedAt = System.currentTimeMillis();
        final boolean isNew = editing == null;
        if (isNew) {
            entity.createdAt = System.currentTimeMillis();
        }
        container.ioExecutor().execute(() -> {
            if (isNew) {
                entity.priority = container.database().filterRuleDao().nextPriority();
                container.database().filterRuleDao().insert(entity);
            } else {
                container.database().filterRuleDao().update(entity);
            }
            runOnUiThread(() -> {
                Toast.makeText(this, R.string.filter_saved, Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }

    private void showError(int message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.filter_edit_title)
                .setMessage(message)
                .setPositiveButton(R.string.action_close, null)
                .show();
    }

    private static String text(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private static class ConditionRow {
        final View root;
        final Spinner field;
        final Spinner operator;
        final TextInputEditText value;

        ConditionRow(View root, Spinner field, Spinner operator, TextInputEditText value) {
            this.root = root;
            this.field = field;
            this.operator = operator;
            this.value = value;
        }
    }

    private abstract static class SimpleItemSelected
            implements android.widget.AdapterView.OnItemSelectedListener {
        @Override
        public void onNothingSelected(android.widget.AdapterView<?> parent) {
        }
    }
}
