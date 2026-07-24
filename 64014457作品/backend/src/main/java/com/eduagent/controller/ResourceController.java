package com.eduagent.controller;

import com.eduagent.entity.KnowledgeEntry;
import com.eduagent.entity.ResourceStats;
import com.eduagent.mapper.KnowledgeEntryMapper;
import com.eduagent.mapper.ResourceStatsMapper;
import com.eduagent.model.vo.Result;
import com.eduagent.service.ResourceInteractionService;
import com.eduagent.service.UserProfileService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.AbstractMap;

@Slf4j
@RestController
@RequestMapping("/api/resources")
public class ResourceController {

    private final KnowledgeEntryMapper entryMapper;
    private final ResourceStatsMapper resourceStatsMapper;
    private final ResourceInteractionService resourceInteractionService;
    private final UserProfileService userProfileService;

    public ResourceController(KnowledgeEntryMapper entryMapper,
                              ResourceStatsMapper resourceStatsMapper,
                              ResourceInteractionService resourceInteractionService,
                              UserProfileService userProfileService) {
        this.entryMapper = entryMapper;
        this.resourceStatsMapper = resourceStatsMapper;
        this.resourceInteractionService = resourceInteractionService;
        this.userProfileService = userProfileService;
    }

    // 过滤AI方案：只显示自己的（未登录用户看不到任何LEARNING_PLAN）
    private List<KnowledgeEntry> filterPlanOwnership(List<KnowledgeEntry> entries, HttpServletRequest req) {
        String userId = (String) req.getAttribute("userId");
        return entries.stream().filter(e ->
            !"LEARNING_PLAN".equals(e.getEntryType()) ||
            (userId != null && e.getOwnerId() != null && userId.equals(String.valueOf(e.getOwnerId())))
        ).toList();
    }

    @GetMapping
    public Result<List<Map<String, Object>>> getAllResources(HttpServletRequest req) {
        List<KnowledgeEntry> entries = entryMapper.selectList(
                new LambdaQueryWrapper<KnowledgeEntry>().orderByDesc(KnowledgeEntry::getCreatedAt));
        return Result.success(enrichWithStats(filterPlanOwnership(entries, req)));
    }

    @GetMapping("/featured")
    public Result<List<Map<String, Object>>> getFeaturedResources(HttpServletRequest req) {
        // 取最近资源（候选池，最多50条）
        List<KnowledgeEntry> entries = entryMapper.selectList(
                new LambdaQueryWrapper<KnowledgeEntry>()
                        .orderByDesc(KnowledgeEntry::getCreatedAt)
                        .last("LIMIT 50"));
        List<KnowledgeEntry> filtered = filterPlanOwnership(entries, req);

        // 尝试基于用户画像个性化推荐
        String userId = (String) req.getAttribute("userId");
        if (userId != null && !filtered.isEmpty()) {
            try {
                Map<String, Object> profile = userProfileService.analyzeProfile(Long.valueOf(userId));
                List<String> userKeywords = new ArrayList<>();
                List<String> userInterests = new ArrayList<>();

                @SuppressWarnings("unchecked")
                List<String> kw = (List<String>) profile.getOrDefault("keywords", List.of());
                @SuppressWarnings("unchecked")
                List<String> interests = (List<String>) profile.getOrDefault("interests", List.of());
                userKeywords.addAll(kw);
                userInterests.addAll(interests);

                // 如果有画像数据，按相关性排序
                if (!userKeywords.isEmpty() || !userInterests.isEmpty()) {
                    List<String> allTerms = new ArrayList<>();
                    allTerms.addAll(userKeywords);
                    allTerms.addAll(userInterests);

                    // 为每条资源打分
                    List<Map.Entry<KnowledgeEntry, Integer>> scored = new ArrayList<>();
                    for (KnowledgeEntry entry : filtered) {
                        int score = scoreResource(entry, allTerms);
                        if (score > 0) {
                            scored.add(new AbstractMap.SimpleEntry<>(entry, score));
                        }
                    }
                    // 按分数降序，取前6条
                    scored.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
                    List<KnowledgeEntry> recommended = scored.stream()
                            .limit(6)
                            .map(Map.Entry::getKey)
                            .collect(java.util.stream.Collectors.toList());

                    // 如果推荐不够6条，用最新资源补齐
                    if (recommended.size() < 6) {
                        for (KnowledgeEntry e : filtered) {
                            if (recommended.size() >= 6) break;
                            if (!recommended.contains(e)) {
                                recommended.add(e);
                            }
                        }
                    }
                    return Result.success(enrichWithStats(recommended));
                }
            } catch (Exception e) {
                log.warn("个性化推荐失败，降级为最新资源: {}", e.getMessage());
            }
        }

        // 降级：无用户画像时返回最新6条
        List<KnowledgeEntry> latest = filtered.size() > 6
                ? filtered.subList(0, 6) : filtered;
        return Result.success(enrichWithStats(latest));
    }

    /** 为资源打分：标题匹配+3，分类匹配+5，内容匹配+1 */
    private int scoreResource(KnowledgeEntry entry, List<String> userTerms) {
        if (userTerms == null || userTerms.isEmpty()) return 0;
        int score = 0;
        String title = entry.getTitle() != null ? entry.getTitle().toLowerCase() : "";
        String category = entry.getCategory() != null ? entry.getCategory().toLowerCase() : "";
        String content = entry.getContent() != null ? entry.getContent().toLowerCase() : "";

        for (String term : userTerms) {
            if (term == null || term.isBlank()) continue;
            String t = term.toLowerCase().trim();
            if (category.contains(t)) score += 5;       // 分类匹配权重最高
            else if (title.contains(t)) score += 3;      // 标题匹配次之
            else if (content.contains(t)) score += 1;    // 内容匹配最低
        }
        return score;
    }

    @GetMapping("/top")
    public Result<List<Map<String, Object>>> getTopResources(HttpServletRequest req) {
        List<ResourceStats> stats = resourceStatsMapper.selectList(
                new LambdaQueryWrapper<ResourceStats>()
                        .orderByDesc(ResourceStats::getLikeCount)
                        .last("LIMIT 10"));
        List<Map<String, Object>> result = new ArrayList<>();
        for (ResourceStats s : stats) {
            KnowledgeEntry entry = entryMapper.selectById(s.getResourceId());
            if (entry != null) {
                // 过滤AI方案所有权
                String uid = (String) req.getAttribute("userId");
                if ("LEARNING_PLAN".equals(entry.getEntryType()) && uid != null &&
                    (entry.getOwnerId() == null || !uid.equals(String.valueOf(entry.getOwnerId())))) {
                    continue;
                }
                Map<String, Object> map = entryToMap(entry);
                map.put("likeCount", s.getLikeCount());
                map.put("viewCount", s.getViewCount());
                result.add(map);
            }
        }
        return Result.success(result);
    }

    @GetMapping("/latest")
    public Result<List<Map<String, Object>>> getLatestResources(HttpServletRequest req) {
        List<KnowledgeEntry> entries = entryMapper.selectList(
                new LambdaQueryWrapper<KnowledgeEntry>()
                        .orderByDesc(KnowledgeEntry::getCreatedAt)
                        .last("LIMIT 10"));
        return Result.success(enrichWithStats(filterPlanOwnership(entries, req)));
    }

    @GetMapping("/category/{category}")
    public Result<List<Map<String, Object>>> getResourcesByCategory(@PathVariable String category, HttpServletRequest req) {
        List<KnowledgeEntry> entries = entryMapper.selectList(
                new LambdaQueryWrapper<KnowledgeEntry>()
                        .eq(KnowledgeEntry::getCategory, category)
                        .orderByDesc(KnowledgeEntry::getCreatedAt));
        return Result.success(enrichWithStats(filterPlanOwnership(entries, req)));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> getResourceById(@PathVariable Long id, HttpServletRequest req) {
        KnowledgeEntry entry = entryMapper.selectById(id);
        if (entry == null) {
            return Result.error(404, "资源不存在");
        }
        Map<String, Object> map = entryToMap(entry);
        ResourceStats stats = resourceStatsMapper.selectById(id);
        if (stats != null) {
            map.put("likeCount", stats.getLikeCount());
            map.put("viewCount", stats.getViewCount());
        } else {
            map.put("likeCount", 0);
            map.put("viewCount", 0);
        }
        return Result.success(map);
    }

    @PostMapping
    public Result<KnowledgeEntry> createResource(@RequestBody KnowledgeEntry entry, HttpServletRequest req) {
        if (entry.getTitle() == null || entry.getTitle().trim().isEmpty()) {
            return Result.error(400, "标题不能为空");
        }
        if (entry.getContent() == null || entry.getContent().trim().isEmpty()) {
            return Result.error(400, "内容不能为空");
        }
        String userId = (String) req.getAttribute("userId");
        if (userId != null) try { entry.setOwnerId(Long.valueOf(userId)); } catch(Exception e) {}
        entry.setCreatedAt(LocalDateTime.now());
        entry.setUpdatedAt(LocalDateTime.now());
        entryMapper.insert(entry);
        return Result.success(entry);
    }

    @PutMapping("/{id}")
    public Result<Boolean> updateResource(@PathVariable Long id, @RequestBody KnowledgeEntry entry, HttpServletRequest req) {
        KnowledgeEntry existing = entryMapper.selectById(id);
        if (existing == null) { return Result.error(404, "资源不存在"); }
        String userId = (String) req.getAttribute("userId");
        if (existing.getOwnerId() != null && userId != null && !userId.equals(String.valueOf(existing.getOwnerId()))) {
            return Result.error(403, "无权修改");
        }
        if (entry.getTitle() != null) existing.setTitle(entry.getTitle());
        if (entry.getContent() != null) existing.setContent(entry.getContent());
        if (entry.getCategory() != null) existing.setCategory(entry.getCategory());
        existing.setUpdatedAt(LocalDateTime.now());
        entryMapper.updateById(existing);
        return Result.success(true);
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> deleteResource(@PathVariable Long id, HttpServletRequest req) {
        KnowledgeEntry existing = entryMapper.selectById(id);
        if (existing == null) { return Result.error(404, "资源不存在"); }
        String userId = (String) req.getAttribute("userId");
        if (existing.getOwnerId() != null && userId != null && !userId.equals(String.valueOf(existing.getOwnerId()))) {
            return Result.error(403, "无权删除");
        }
        entryMapper.deleteById(id);
        return Result.success(true);
    }

    @PostMapping("/{id}/like")
    public Result<Map<String, Object>> likeResource(@PathVariable Long id, HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "未登录或Token已过期");
        }
        try {
            Map<String, Object> result = resourceInteractionService.toggleLike(id, userId);
            return Result.success(result);
        } catch (Exception e) {
            log.error("点赞失败", e);
            return Result.error(500, "点赞失败");
        }
    }

    private List<Map<String, Object>> enrichWithStats(List<KnowledgeEntry> entries) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (KnowledgeEntry entry : entries) {
            Map<String, Object> map = entryToMap(entry);
            ResourceStats stats = resourceStatsMapper.selectById(entry.getId());
            if (stats != null) {
                map.put("likeCount", stats.getLikeCount());
                map.put("viewCount", stats.getViewCount());
            } else {
                map.put("likeCount", 0);
                map.put("viewCount", 0);
            }
            result.add(map);
        }
        return result;
    }

    private Map<String, Object> entryToMap(KnowledgeEntry entry) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entry.getId());
        map.put("title", entry.getTitle());
        map.put("content", entry.getContent());
        map.put("category", entry.getCategory());
        map.put("subModule", entry.getSubModule());
        map.put("baseId", entry.getBaseId());
        map.put("entryType", entry.getEntryType());
        map.put("createdAt", entry.getCreatedAt());
        map.put("updatedAt", entry.getUpdatedAt());
        return map;
    }
}