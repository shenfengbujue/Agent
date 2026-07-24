package com.eduagent.service;

import com.eduagent.entity.KnowledgeEntry;
import com.eduagent.mapper.KnowledgeEntryMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 资源推荐服务 — 基于用户画像的个性化推荐引擎
 *
 * 多路召回 + 权重融合:
 * 1. 关键词匹配 (40%): 画像keywords与资源category/tags交集
 * 2. 难度匹配 (25%): knowledgeLevel匹配difficultyLevel
 * 3. 兴趣匹配 (20%): interests与资源category交集
 * 4. 热度加权 (15%): 点赞/浏览归一化
 *
 * 冷启动: 新用户无画像时返回热门+最新混合
 */
@Slf4j
@Service
public class RecommendationService {

    private final KnowledgeEntryMapper entryMapper;
    private final ProfileDimensionService profileDimensionService;

    public RecommendationService(KnowledgeEntryMapper entryMapper,
                                  ProfileDimensionService profileDimensionService) {
        this.entryMapper = entryMapper;
        this.profileDimensionService = profileDimensionService;
    }

    /**
     * 首页个性化推荐
     */
    /** 基于用户完整画像的个性化推荐 */
    public List<Map<String, Object>> getProfileBasedRecommendations(Long userId, int limit) {
        if (userId == null) return getHotRecommendations(limit);
        try {
            List<Map<String, Object>> candidates = new ArrayList<>();
            Set<Long> seen = new HashSet<>();
            // 薄弱点
            String ep = profileDimensionService.getDimensionValue(userId, "errorPatterns", null);
            if (ep != null && !ep.isEmpty() && !"未评估".equals(ep)) {
                for (String e : ep.split(",")) {
                    for (KnowledgeEntry ke : searchByKeyword(e.trim(), limit)) {
                        if (seen.add(ke.getId())) candidates.add(buildItem(ke, "薄弱点推荐「" + e.trim() + "」", 1.0));
                    }
                }
            }
            // 知识水平匹配
            String level = profileDimensionService.getDimensionValue(userId, "knowledgeLevel", "基础");
            String levelKw = level.contains("入门") || level.contains("零基础") ? "入门教程" :
                             level.contains("高级") ? "进阶" : "基础";
            for (KnowledgeEntry ke : searchByKeyword(levelKw, limit)) {
                if (seen.add(ke.getId())) candidates.add(buildItem(ke, level + "水平匹配", 0.8));
            }
            // 时间偏好 → 时长匹配
            String timePref = profileDimensionService.getDimensionValue(userId, "timePreference", null);
            if ("碎片时间".equals(timePref)) {
                for (KnowledgeEntry ke : searchByKeyword("短课程 微课", limit)) {
                    if (seen.add(ke.getId())) candidates.add(buildItem(ke, "适合碎片时间", 0.7));
                }
            }
            // 社交倾向 → 小组/独立
            String social = profileDimensionService.getDimensionValue(userId, "socialTendency", null);
            if (social != null && social.contains("小组")) {
                for (KnowledgeEntry ke : searchByKeyword("讨论 小组 社区", limit)) {
                    if (seen.add(ke.getId())) candidates.add(buildItem(ke, "适合小组学习", 0.7));
                }
            }
            // 学习节奏 → 节奏匹配
            String pace = profileDimensionService.getDimensionValue(userId, "learningPace", null);
            if ("快速推进".equals(pace)) {
                for (KnowledgeEntry ke : searchByKeyword("速成 快速入门", limit)) {
                    if (seen.add(ke.getId())) candidates.add(buildItem(ke, "快节奏学习", 0.7));
                }
            } else if ("稳扎稳打".equals(pace)) {
                for (KnowledgeEntry ke : searchByKeyword("系统 详解 基础", limit)) {
                    if (seen.add(ke.getId())) candidates.add(buildItem(ke, "系统学习", 0.7));
                }
            }
            // 完成率低 → 推荐更短更简单的内容
            String completion = profileDimensionService.getDimensionValue(userId, "completionRate", null);
            if ("低".equals(completion) || "0%".equals(completion)) {
                for (KnowledgeEntry ke : searchByKeyword("入门 简短 轻松", limit)) {
                    if (seen.add(ke.getId())) candidates.add(buildItem(ke, "轻松入门，更容易坚持", 0.9));
                }
            }
            if (!candidates.isEmpty()) return candidates.subList(0, Math.min(limit, candidates.size()));
        } catch (Exception e) { log.warn("画像推荐失败", e); }
        return getHotRecommendations(limit);
    }

    public List<Map<String, Object>> getHomeRecommendations(Long userId, int limit) {
        if (userId == null) return getHotRecommendations(limit);

        try {
            Map<String, String> profile = extractProfileKeywords(userId);
            List<Map<String, Object>> candidates = new ArrayList<>();
            Set<Long> seen = new HashSet<>();

            // 关键词匹配
            if (profile.containsKey("keywords")) {
                for (String kw : profile.get("keywords").split(",")) {
                    for (KnowledgeEntry e : searchByKeyword(kw.trim(), limit * 2)) {
                        if (seen.add(e.getId())) {
                            candidates.add(buildItem(e, "基于学习关键词「" + kw.trim() + "」推荐", 0.8));
                        }
                    }
                }
            }

            // 兴趣匹配
            if (profile.containsKey("interests")) {
                for (String interest : profile.get("interests").split(",")) {
                    for (KnowledgeEntry e : searchByCategory(interest.trim(), limit)) {
                        if (seen.add(e.getId())) {
                            candidates.add(buildItem(e, "基于兴趣「" + interest.trim() + "」推荐", 0.7));
                        }
                    }
                }
            }

            // 热门兜底
            for (KnowledgeEntry e : getHotEntries(limit)) {
                if (seen.add(e.getId())) {
                    candidates.add(buildItem(e, "热门学习资源", 0.5));
                }
            }

            // 截取limit条
            if (candidates.size() > limit) {
                candidates.sort((a, b) -> Double.compare(
                        (Double) b.get("score"), (Double) a.get("score")));
                candidates = candidates.subList(0, limit);
            }

            log.info("首页推荐: userId={}, results={}", userId, candidates.size());
            return candidates;

        } catch (Exception e) {
            log.warn("个性化推荐失败，降级热门: {}", e.getMessage());
            return getHotRecommendations(limit);
        }
    }

    /**
     * 热门推荐（降级/冷启动）
     */
    public List<Map<String, Object>> getHotRecommendations(int limit) {
        return getHotEntries(limit).stream()
                .map(e -> buildItem(e, "热门推荐", 0.5))
                .collect(Collectors.toList());
    }

    // ==================== 私有方法 ====================

    private Map<String, String> extractProfileKeywords(Long userId) {
        Map<String, String> result = new LinkedHashMap<>();
        try {
            Map<String, com.eduagent.entity.ProfileDimension> dims =
                    profileDimensionService.getUserDimensions(userId);
            List<String> keywords = new ArrayList<>();
            for (com.eduagent.entity.ProfileDimension dim : dims.values()) {
                if (dim.getDimensionValue() != null && dim.getConfidence() >= 0.5) {
                    keywords.add(dim.getDimensionValue());
                }
            }
            result.put("keywords", String.join(",", keywords));
            result.put("interests", profileDimensionService.getInterests(userId)
                    .stream().limit(10).collect(Collectors.joining(",")));
        } catch (Exception e) {
            log.debug("提取画像关键词失败: {}", e.getMessage());
        }
        return result;
    }

    private List<KnowledgeEntry> searchByKeyword(String keyword, int limit) {
        try {
            LambdaQueryWrapper<KnowledgeEntry> w = new LambdaQueryWrapper<>();
            w.like(KnowledgeEntry::getTitle, keyword)
             .or().like(KnowledgeEntry::getCategory, keyword)
             .last("LIMIT " + limit);
            return entryMapper.selectList(w);
        } catch (Exception e) { return List.of(); }
    }

    private List<KnowledgeEntry> searchByCategory(String category, int limit) {
        try {
            LambdaQueryWrapper<KnowledgeEntry> w = new LambdaQueryWrapper<>();
            w.eq(KnowledgeEntry::getCategory, category).last("LIMIT " + limit);
            return entryMapper.selectList(w);
        } catch (Exception e) { return List.of(); }
    }

    private List<KnowledgeEntry> getHotEntries(int limit) {
        try {
            LambdaQueryWrapper<KnowledgeEntry> w = new LambdaQueryWrapper<>();
            w.orderByDesc(KnowledgeEntry::getCreatedAt).last("LIMIT " + limit);
            return entryMapper.selectList(w);
        } catch (Exception e) { return List.of(); }
    }

    private Map<String, Object> buildItem(KnowledgeEntry e, String reason, double score) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", e.getId());
        item.put("title", e.getTitle());
        item.put("category", e.getCategory());
        item.put("summary", e.getContent() != null && e.getContent().length() > 100
                ? e.getContent().substring(0, 100) + "..." : e.getContent());
        item.put("reason", reason);
        item.put("score", score);
        return item;
    }
}
