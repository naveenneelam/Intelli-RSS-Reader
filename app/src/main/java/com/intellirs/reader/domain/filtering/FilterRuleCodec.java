package com.intellirs.reader.domain.filtering;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.intellirs.reader.data.entity.FilterRuleEntity;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Version-tolerant JSON codec for user-authored filter rules. */
public final class FilterRuleCodec {
    private static final Logger LOG = Logger.getLogger(FilterRuleCodec.class.getName());
    private static final Gson GSON = new Gson();
    private static final Type CONDITION_LIST =
            new TypeToken<List<FilterCondition>>() {
            }.getType();
    private static final Type ACTION_LIST =
            new TypeToken<List<FilterAction>>() {
            }.getType();

    private FilterRuleCodec() {
    }

    public static FilterRule fromEntity(FilterRuleEntity entity) {
        FilterRule rule = new FilterRule();
        if (entity == null) {
            return rule;
        }
        rule.id = entity.id;
        rule.name = entity.ruleName;
        rule.enabled = entity.enabled;
        rule.priority = entity.priority;
        try {
            JsonElement conditionsElement = JsonParser.parseString(entity.conditionJson);
            if (conditionsElement.isJsonArray()) {
                List<FilterCondition> conditions = GSON.fromJson(conditionsElement, CONDITION_LIST);
                if (conditions != null) {
                    rule.conditions.addAll(conditions);
                }
                rule.matchAll = true;
            } else if (conditionsElement.isJsonObject()) {
                JsonObject object = conditionsElement.getAsJsonObject();
                if (object.has("matchAll")) {
                    rule.matchAll = object.get("matchAll").getAsBoolean();
                }
                if (object.has("conditions")) {
                    List<FilterCondition> conditions = GSON.fromJson(
                            object.get("conditions"), CONDITION_LIST);
                    if (conditions != null) {
                        rule.conditions.addAll(conditions);
                    }
                }
            }
            List<FilterAction> actions = GSON.fromJson(entity.actionJson, ACTION_LIST);
            if (actions != null) {
                rule.actions.addAll(actions);
            }
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Ignoring malformed filter rule " + entity.id, e);
            rule.conditions.clear();
            rule.actions.clear();
        }
        return rule;
    }

    public static String encodeConditions(FilterRule rule) {
        JsonObject root = new JsonObject();
        root.addProperty("matchAll", rule.matchAll);
        JsonArray conditions = new JsonArray();
        for (FilterCondition condition : rule.conditions) {
            JsonObject item = new JsonObject();
            item.addProperty("field",
                    condition.field == null ? FilterField.TITLE.name() : condition.field.name());
            item.addProperty("operator",
                    condition.operator == null ? FilterOperator.CONTAINS.name()
                            : condition.operator.name());
            item.addProperty("value", condition.value == null ? "" : condition.value);
            conditions.add(item);
        }
        root.add("conditions", conditions);
        return GSON.toJson(root);
    }

    public static String encodeActions(FilterRule rule) {
        JsonArray actions = new JsonArray();
        for (FilterAction action : rule.actions) {
            JsonObject item = new JsonObject();
            item.addProperty("type",
                    action.type == null ? FilterActionType.MUTE_NOTIFICATION.name()
                            : action.type.name());
            item.addProperty("value", action.value == null ? "" : action.value);
            actions.add(item);
        }
        return GSON.toJson(actions);
    }

    public static List<FilterRule> decodeList(List<FilterRuleEntity> entities) {
        List<FilterRule> rules = new ArrayList<>();
        if (entities == null) {
            return rules;
        }
        for (FilterRuleEntity entity : entities) {
            rules.add(fromEntity(entity));
        }
        return rules;
    }
}
