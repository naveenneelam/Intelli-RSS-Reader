package com.intellirs.reader.domain.filtering;

import com.intellirs.reader.data.entity.FeedImportance;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Ordered rule engine. Rules run from lowest priority number to highest; later boosts are
 * additive and a mute action always wins over an enable action.
 */
public class FilterEngine {

    public FilterEvaluation evaluate(List<FilterRule> rules, FilterContext context) {
        FilterEvaluation result = new FilterEvaluation();
        if (rules == null || context == null) {
            return result;
        }
        List<FilterRule> ordered = new ArrayList<>(rules);
        ordered.sort(Comparator.comparingInt(rule -> rule.priority));
        int runningScore = context.relevanceScore;

        for (FilterRule rule : ordered) {
            if (rule == null || !rule.enabled || !rule.hasConditions()) {
                continue;
            }
            FilterContext snapshot = copyContext(context, runningScore);
            if (!matches(rule, snapshot)) {
                continue;
            }
            result.appliedRuleNames.add(rule.name);
            result.reasons.add("Rule \"" + rule.name + "\" matched");
            for (FilterAction action : rule.actions) {
                if (action == null || action.type == null) {
                    continue;
                }
                switch (action.type) {
                    case BOOST_RELEVANCE: {
                        int boost = parseInt(action.value, 0);
                        boost = Math.max(-100, Math.min(100, boost));
                        result.scoreAdjustment = Math.max(-100,
                                Math.min(100, result.scoreAdjustment + boost));
                        runningScore = Math.max(0, Math.min(100, runningScore + boost));
                        result.reasons.add("relevance " + (boost >= 0 ? "+" : "") + boost);
                        break;
                    }
                    case MUTE_NOTIFICATION:
                        result.muteNotification = true;
                        result.reasons.add("notifications muted");
                        break;
                    case ENABLE_NOTIFICATION:
                        result.forceNotify = true;
                        result.reasons.add("notifications enabled");
                        break;
                    case MARK_READ:
                        result.markRead = true;
                        result.reasons.add("marked read");
                        break;
                    case ARCHIVE:
                        result.archive = true;
                        result.reasons.add("archive requested");
                        break;
                    case ADD_TAG:
                        if (action.value != null && !action.value.trim().isEmpty()) {
                            result.tags.add(action.value.trim());
                            result.reasons.add("tag \"" + action.value.trim() + "\"");
                        }
                        break;
                    default:
                        break;
                }
            }
        }
        if (result.muteNotification) {
            result.forceNotify = false;
            result.reasons.add("Mute wins over enable where rules conflict");
        }
        return result;
    }

    private boolean matches(FilterRule rule, FilterContext context) {
        boolean any = false;
        for (FilterCondition condition : rule.conditions) {
            boolean conditionResult = evaluateCondition(condition, context);
            if (rule.matchAll && !conditionResult) {
                return false;
            }
            if (!rule.matchAll && conditionResult) {
                any = true;
            }
        }
        return rule.matchAll || any;
    }

    private boolean evaluateCondition(FilterCondition condition, FilterContext context) {
        if (condition == null || condition.field == null) {
            return false;
        }
        if (condition.field == FilterField.HAS_IMAGES
                || condition.field == FilterField.HAS_VIDEO
                || condition.field == FilterField.HAS_AUDIO) {
            boolean actual = condition.field == FilterField.HAS_IMAGES ? context.hasImages
                    : condition.field == FilterField.HAS_VIDEO ? context.hasVideo : context.hasAudio;
            if (condition.operator == FilterOperator.IS_TRUE) {
                return actual;
            }
            if (condition.operator == FilterOperator.EQUALS
                    || condition.operator == FilterOperator.NOT_EQUALS) {
                boolean expected = Boolean.parseBoolean(condition.value);
                return condition.operator == FilterOperator.EQUALS
                        ? actual == expected : actual != expected;
            }
            return actual;
        }
        if (condition.field == FilterField.RELEVANCE) {
            int expected = parseInt(condition.value, -1);
            if (expected < 0) {
                return false;
            }
            if (condition.operator == FilterOperator.GREATER_THAN) {
                return context.relevanceScore > expected;
            }
            if (condition.operator == FilterOperator.LESS_THAN) {
                return context.relevanceScore < expected;
            }
            return compareNumbers(context.relevanceScore, expected, condition.operator);
        }
        if (condition.field == FilterField.PUBLISHED_AT) {
            long boundary = parseDate(condition.value);
            if (boundary < 0) {
                return false;
            }
            if (condition.operator == FilterOperator.BEFORE) {
                return context.publishedAt > 0 && context.publishedAt < boundary;
            }
            if (condition.operator == FilterOperator.AFTER) {
                return context.publishedAt >= boundary;
            }
            return compareNumbers(context.publishedAt, boundary, condition.operator);
        }
        if (condition.field == FilterField.FEED_IMPORTANCE) {
            FeedImportance expected = FeedImportance.fromName(
                    condition.value == null ? "" : condition.value.trim().toUpperCase(Locale.ROOT),
                    FeedImportance.NORMAL);
            if (condition.operator == FilterOperator.EQUALS) {
                return context.feedImportance == expected;
            }
            if (condition.operator == FilterOperator.NOT_EQUALS) {
                return context.feedImportance != expected;
            }
            if (condition.operator == FilterOperator.GREATER_THAN) {
                return context.feedImportance.rank() < expected.rank();
            }
            if (condition.operator == FilterOperator.LESS_THAN) {
                return context.feedImportance.rank() > expected.rank();
            }
            return false;
        }

        String actual = actualText(condition.field, context);
        String expected = condition.value == null ? "" : condition.value.trim();
        switch (condition.operator) {
            case CONTAINS:
                return !expected.isEmpty()
                        && actual.toLowerCase(Locale.ROOT)
                        .contains(expected.toLowerCase(Locale.ROOT));
            case NOT_CONTAINS:
                return expected.isEmpty()
                        || !actual.toLowerCase(Locale.ROOT)
                        .contains(expected.toLowerCase(Locale.ROOT));
            case EQUALS:
                return actual.equalsIgnoreCase(expected);
            case NOT_EQUALS:
                return !actual.equalsIgnoreCase(expected);
            case IS_TRUE:
                return !actual.isEmpty();
            default:
                return false;
        }
    }

    private static String actualText(FilterField field, FilterContext context) {
        switch (field) {
            case TITLE:
                return context.title == null ? "" : context.title;
            case DESCRIPTION:
                return context.description == null ? "" : context.description;
            case CONTENT:
                return (context.description == null ? "" : context.description) + " "
                        + (context.content == null ? "" : context.content);
            case AUTHOR:
                return context.author == null ? "" : context.author;
            case PUBLISHER:
                return context.publisher == null ? "" : context.publisher;
            case CATEGORY:
                return context.category == null ? "" : context.category;
            default:
                return "";
        }
    }

    private static FilterContext copyContext(FilterContext context, int relevance) {
        FilterContext copy = new FilterContext();
        copy.title = context.title;
        copy.description = context.description;
        copy.content = context.content;
        copy.author = context.author;
        copy.publisher = context.publisher;
        copy.category = context.category;
        copy.feedImportance = context.feedImportance;
        copy.relevanceScore = relevance;
        copy.publishedAt = context.publishedAt;
        copy.hasImages = context.hasImages;
        copy.hasVideo = context.hasVideo;
        copy.hasAudio = context.hasAudio;
        copy.now = context.now;
        return copy;
    }

    private static boolean compareNumbers(long actual, long expected, FilterOperator operator) {
        if (operator == FilterOperator.GREATER_THAN) {
            return actual > expected;
        }
        if (operator == FilterOperator.LESS_THAN) {
            return actual < expected;
        }
        if (operator == FilterOperator.EQUALS) {
            return actual == expected;
        }
        if (operator == FilterOperator.NOT_EQUALS) {
            return actual != expected;
        }
        return false;
    }

    private static long parseDate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return -1;
        }
        try {
            return LocalDate.parse(value.trim())
                    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (DateTimeParseException e) {
            return -1;
        }
    }

    private static int parseInt(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
