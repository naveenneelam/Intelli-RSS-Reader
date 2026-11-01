package com.intellirs.reader.domain.filtering;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.intellirs.reader.data.entity.FeedImportance;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class FilterEngineTest {
    private final FilterEngine engine = new FilterEngine();

    @Test
    public void allConditionsMustMatch() {
        FilterRule rule = new FilterRule();
        rule.name = "RBI";
        rule.matchAll = true;
        rule.conditions.add(new FilterCondition(FilterField.TITLE,
                FilterOperator.CONTAINS, "reserve bank"));
        rule.conditions.add(new FilterCondition(FilterField.RELEVANCE,
                FilterOperator.GREATER_THAN, "50"));
        rule.actions.add(new FilterAction(FilterActionType.BOOST_RELEVANCE, "20"));
        rule.actions.add(new FilterAction(FilterActionType.ADD_TAG, "Economy"));
        FilterContext context = context();
        context.relevanceScore = 60;
        FilterEvaluation result = engine.evaluate(Collections.singletonList(rule), context);
        assertEquals(1, result.appliedRuleNames.size());
        assertEquals(80, result.adjustedScore(60));
        assertTrue(result.tags.contains("Economy"));
    }

    @Test
    public void anyConditionCanMatch() {
        FilterRule rule = new FilterRule();
        rule.name = "Topic";
        rule.matchAll = false;
        rule.conditions.add(new FilterCondition(FilterField.CATEGORY,
                FilterOperator.EQUALS, "Sport"));
        rule.conditions.add(new FilterCondition(FilterField.TITLE,
                FilterOperator.CONTAINS, "Reserve Bank"));
        rule.actions.add(new FilterAction(FilterActionType.MUTE_NOTIFICATION, ""));
        FilterEvaluation result = engine.evaluate(Collections.singletonList(rule), context());
        assertTrue(result.muteNotification);
        assertEquals(1, result.appliedRuleNames.size());
    }

    @Test
    public void muteWinsOverEnableAcrossRules() {
        FilterRule enable = new FilterRule();
        enable.name = "Enable";
        enable.conditions.add(new FilterCondition(FilterField.TITLE,
                FilterOperator.CONTAINS, "bank"));
        enable.actions.add(new FilterAction(FilterActionType.ENABLE_NOTIFICATION, ""));
        FilterRule mute = new FilterRule();
        mute.name = "Mute";
        mute.priority = 1;
        mute.conditions.add(new FilterCondition(FilterField.CATEGORY,
                FilterOperator.EQUALS, "Economy"));
        mute.actions.add(new FilterAction(FilterActionType.MUTE_NOTIFICATION, ""));
        FilterEvaluation result = engine.evaluate(Arrays.asList(enable, mute), context());
        assertTrue(result.muteNotification);
        assertFalse(result.forceNotify);
        assertFalse(result.notificationEnabled());
    }

    @Test
    public void booleanAndImportanceConditionsWork() {
        FilterRule rule = new FilterRule();
        rule.name = "Important video";
        rule.matchAll = true;
        rule.conditions.add(new FilterCondition(FilterField.HAS_VIDEO,
                FilterOperator.IS_TRUE, "true"));
        rule.conditions.add(new FilterCondition(FilterField.FEED_IMPORTANCE,
                FilterOperator.GREATER_THAN, "NORMAL"));
        rule.actions.add(new FilterAction(FilterActionType.MARK_READ, ""));
        FilterContext context = context();
        context.hasVideo = true;
        context.feedImportance = FeedImportance.HIGH;
        assertTrue(engine.evaluate(Collections.singletonList(rule), context).markRead);
        context.hasVideo = false;
        assertFalse(engine.evaluate(Collections.singletonList(rule), context).markRead);
    }

    @Test
    public void codecRoundTripsConditionsAndActions() {
        FilterRule rule = new FilterRule();
        rule.matchAll = false;
        rule.conditions.add(new FilterCondition(FilterField.AUTHOR,
                FilterOperator.EQUALS, "Asha Nair"));
        rule.actions.add(new FilterAction(FilterActionType.ADD_TAG, "Policy"));
        String conditionJson = FilterRuleCodec.encodeConditions(rule);
        String actionJson = FilterRuleCodec.encodeActions(rule);
        com.intellirs.reader.data.entity.FilterRuleEntity entity =
                new com.intellirs.reader.data.entity.FilterRuleEntity();
        entity.conditionJson = conditionJson;
        entity.actionJson = actionJson;
        FilterRule decoded = FilterRuleCodec.fromEntity(entity);
        assertFalse(decoded.matchAll);
        assertEquals(1, decoded.conditions.size());
        assertEquals(FilterField.AUTHOR, decoded.conditions.get(0).field);
        assertEquals("Policy", decoded.actions.get(0).value);
    }

    private FilterContext context() {
        FilterContext context = new FilterContext();
        context.title = "Reserve Bank holds policy rate";
        context.description = "The central bank kept rates steady.";
        context.content = "Governor Shah spoke about inflation and the economy.";
        context.author = "Asha Nair";
        context.publisher = "River City Journal";
        context.category = "Economy";
        context.feedImportance = FeedImportance.HIGH;
        context.relevanceScore = 60;
        context.publishedAt = System.currentTimeMillis() - 3_600_000L;
        context.hasImages = true;
        return context;
    }
}
