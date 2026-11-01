package com.intellirs.reader.worker;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.intellirs.reader.IntelliRssApp;
import com.intellirs.reader.data.dao.ArticleDao;
import com.intellirs.reader.data.dao.CategoryDao;
import com.intellirs.reader.data.dao.FeedDao;
import com.intellirs.reader.data.dao.FilterRuleDao;
import com.intellirs.reader.data.dao.KeywordRuleDao;
import com.intellirs.reader.data.database.AppDatabase;
import com.intellirs.reader.data.entity.ArticleEntity;
import com.intellirs.reader.data.entity.CategoryEntity;
import com.intellirs.reader.data.entity.FeedEntity;
import com.intellirs.reader.data.entity.FeedImportance;
import com.intellirs.reader.data.entity.KeywordRuleEntity;
import com.intellirs.reader.data.entity.KeywordType;
import com.intellirs.reader.data.model.FeedStats;
import com.intellirs.reader.data.repository.SettingsRepository;
import com.intellirs.reader.di.AppContainer;
import com.intellirs.reader.domain.filtering.FilterContext;
import com.intellirs.reader.domain.filtering.FilterEngine;
import com.intellirs.reader.domain.filtering.FilterEvaluation;
import com.intellirs.reader.domain.filtering.FilterRule;
import com.intellirs.reader.domain.filtering.FilterRuleCodec;
import com.intellirs.reader.domain.prioritization.ArticleScoringEngine;
import com.intellirs.reader.domain.prioritization.ScoringConfig;
import com.intellirs.reader.domain.prioritization.ScoringInput;
import com.intellirs.reader.domain.prioritization.ScoringResult;
import com.intellirs.reader.util.TextUtils;

import org.jsoup.Jsoup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Re-scores stored unread articles after the user changes keyword/filter preferences. */
public class RescoreWorker extends Worker {
    private static final String TAG = "RescoreWorker";
    private static final int PAGE = 250;

    public RescoreWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        AppContainer container = ((IntelliRssApp) getApplicationContext()).getContainer();
        try {
            AppDatabase db = container.database();
            SettingsRepository settings = container.settingsRepository();
            ScoringConfig config = new ScoringConfig();
            config.criticalThreshold = settings.getCriticalThreshold();
            config.highThreshold = settings.getHighThreshold();
            config.normalThreshold = settings.getNormalThreshold();
            config.lowThreshold = settings.getLowThreshold();
            addKeywords(config.priorityKeywords,
                    db.keywordRuleDao().getByType(KeywordType.PRIORITY));
            addKeywords(config.mutedKeywords,
                    db.keywordRuleDao().getByType(KeywordType.MUTED));
            addKeywords(config.topics,
                    db.keywordRuleDao().getByType(KeywordType.TOPIC));
            for (String author : settings.getPreferredAuthors().split(",")) {
                if (!author.trim().isEmpty()) {
                    config.preferredAuthors.add(author.trim());
                }
            }
            List<FilterRule> filters = FilterRuleCodec.decodeList(
                    db.filterRuleDao().getEnabled());
            ArticleScoringEngine scoring = new ArticleScoringEngine();
            FilterEngine filterEngine = new FilterEngine();
            ArticleDao articleDao = db.articleDao();
            FeedDao feedDao = db.feedDao();
            CategoryDao categoryDao = db.categoryDao();
            Map<Long, FeedStats> statsCache = new HashMap<>();
            Map<Long, String> categoryCache = new HashMap<>();
            int offset = 0;
            int updated = 0;
            while (true) {
                List<ArticleEntity> page = articleDao.getUnreadPage(PAGE, offset);
                if (page.isEmpty()) {
                    break;
                }
                for (ArticleEntity article : page) {
                    FeedEntity feed = article.primaryFeedId == null ? null
                            : feedDao.getById(article.primaryFeedId);
                    FeedStats stats = null;
                    if (feed != null) {
                        stats = statsCache.get(feed.id);
                        if (stats == null) {
                            stats = articleDao.getFeedStats(feed.id);
                            if (stats == null) {
                                stats = new FeedStats();
                            }
                            stats.readRate = stats.totalArticles == 0 ? 0f
                                    : Math.min(1f, stats.readArticles
                                    / (float) stats.totalArticles);
                            statsCache.put(feed.id, stats);
                        }
                    }
                    String categoryName = "";
                    if (feed != null && feed.categoryId != null) {
                        categoryName = categoryCache.get(feed.categoryId);
                        if (categoryName == null) {
                            CategoryEntity category = categoryDao.getById(feed.categoryId);
                            categoryName = category == null ? "" : category.name;
                            categoryCache.put(feed.categoryId, categoryName);
                        }
                    }
                    String description = Jsoup.parse(article.description == null
                            ? "" : article.description).text();
                    String content = article.plainText == null ? "" : article.plainText;
                    ScoringInput input = new ScoringInput();
                    input.title = article.title;
                    input.description = description;
                    input.content = content;
                    input.author = article.author;
                    input.publisher = article.publisher;
                    input.category = categoryName;
                    input.publishedAt = article.publishedAt;
                    input.now = System.currentTimeMillis();
                    input.feedReadRate = stats == null ? 0f : stats.readRate;
                    input.hasMedia = article.hasImages || article.hasVideo || article.hasAudio;
                    ScoringResult result = scoring.score(input, config);
                    FilterContext context = new FilterContext();
                    context.title = article.title;
                    context.description = description;
                    context.content = content;
                    context.author = article.author;
                    context.publisher = article.publisher;
                    context.category = categoryName;
                    context.feedImportance = feed == null
                            ? FeedImportance.NORMAL : feed.feedPriority;
                    context.relevanceScore = result.score;
                    context.publishedAt = article.publishedAt;
                    context.hasImages = article.hasImages;
                    context.hasVideo = article.hasVideo;
                    context.hasAudio = article.hasAudio;
                    FilterEvaluation evaluation = filterEngine.evaluate(filters, context);
                    int finalScore = evaluation.adjustedScore(result.score);
                    List<String> reasons = new ArrayList<>(result.reasons);
                    reasons.addAll(evaluation.reasons);
                    reasons.add("feed importance is not an input to article relevance");
                    articleDao.updateRelevance(article.id, finalScore,
                            ScoringResult.levelFor(finalScore, config),
                            TextUtils.truncate(String.join("; ", reasons), 2000));
                    updated++;
                }
                offset += page.size();
                if (page.size() < PAGE) {
                    break;
                }
            }
            Log.i(TAG, "Re-scored " + updated + " unread articles");
            return Result.success();
        } catch (RuntimeException e) {
            Log.e(TAG, "Re-scoring failed", e);
            return Result.retry();
        }
    }

    private static void addKeywords(List<ScoringConfig.ScoringKeyword> target,
                                    List<KeywordRuleEntity> rules) {
        for (KeywordRuleEntity rule : rules) {
            target.add(new ScoringConfig.ScoringKeyword(rule.keyword, rule.weight));
        }
    }
}
