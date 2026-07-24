package com.eduagent.controller;

import com.eduagent.entity.IntentRule;
import com.eduagent.entity.KnowledgeBase;
import com.eduagent.entity.KnowledgeEntry;
import com.eduagent.model.vo.Result;
import com.eduagent.service.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final TaskDispatcher taskDispatcher;
    private final KnowledgeBaseService knowledgeBaseService;
    private final IntentService intentService;
    private final DataImportService dataImportService;
    private final ResourceInteractionService resourceInteractionService;
    private final KnowledgeGraphService knowledgeGraphService;

    public KnowledgeController(TaskDispatcher taskDispatcher,
                              KnowledgeBaseService knowledgeBaseService,
                              IntentService intentService,
                              DataImportService dataImportService,
                              ResourceInteractionService resourceInteractionService,
                              KnowledgeGraphService knowledgeGraphService) {
        this.taskDispatcher = taskDispatcher;
        this.knowledgeBaseService = knowledgeBaseService;
        this.intentService = intentService;
        this.dataImportService = dataImportService;
        this.resourceInteractionService = resourceInteractionService;
        this.knowledgeGraphService = knowledgeGraphService;
    }

    // ==================== 意图&分发 ====================

    @PostMapping("/query")
    public Result<Map<String, Object>> query(@RequestBody Map<String, String> request) {
        String query = request.get("query");
        if (query == null || query.trim().isEmpty()) {
            return Result.error(400, "查询内容不能为空");
        }
        Map<String, Object> result = taskDispatcher.dispatch(query);
        return Result.success(result);
    }

    @PostMapping("/query/{baseId}")
    public Result<Map<String, Object>> queryByBase(@PathVariable Long baseId, @RequestBody Map<String, String> request) {
        String query = request.get("query");
        if (query == null || query.trim().isEmpty()) {
            return Result.error(400, "查询内容不能为空");
        }
        Map<String, Object> result = taskDispatcher.dispatchToBase(baseId, query);
        if (result.containsKey("error")) {
            return Result.error(404, (String) result.get("error"));
        }
        return Result.success(result);
    }

    // ==================== 知识库管理 ====================

    @GetMapping("/bases")
    public Result<List<KnowledgeBase>> getAllBases() {
        List<KnowledgeBase> bases = knowledgeBaseService.getAllBases();
        return Result.success(bases);
    }

    @GetMapping("/bases/{id}")
    public Result<KnowledgeBase> getBaseById(@PathVariable Long id) {
        KnowledgeBase base = knowledgeBaseService.getBaseById(id);
        if (base == null) {
            return Result.error(404, "知识库不存在");
        }
        return Result.success(base);
    }

    @PostMapping("/bases")
    public Result<KnowledgeBase> createBase(@RequestBody KnowledgeBase base) {
        if (base.getName() == null || base.getName().trim().isEmpty()) {
            return Result.error(400, "知识库名称不能为空");
        }
        if (base.getDomain() == null || base.getDomain().trim().isEmpty()) {
            return Result.error(400, "知识库领域不能为空");
        }
        KnowledgeBase created = knowledgeBaseService.createBase(base);
        return Result.success(created);
    }

    @PutMapping("/bases/{id}")
    public Result<Boolean> updateBase(@PathVariable Long id, @RequestBody KnowledgeBase base) {
        boolean success = knowledgeBaseService.updateBase(id, base);
        if (!success) {
            return Result.error(404, "知识库不存在");
        }
        return Result.success(true);
    }

    @DeleteMapping("/bases/{id}")
    public Result<Boolean> deleteBase(@PathVariable Long id) {
        boolean success = knowledgeBaseService.deleteBase(id);
        if (!success) {
            return Result.error(404, "知识库不存在");
        }
        return Result.success(true);
    }

    // ==================== 知识条目管理 ====================

    @GetMapping("/entries/{baseId}")
    public Result<List<KnowledgeEntry>> getEntriesByBaseId(@PathVariable Long baseId) {
        List<KnowledgeEntry> entries = knowledgeBaseService.getEntriesByBaseId(baseId);
        return Result.success(entries);
    }

    @GetMapping("/entries/{baseId}/category/{category}")
    public Result<List<KnowledgeEntry>> getEntriesByCategory(@PathVariable Long baseId, @PathVariable String category) {
        List<KnowledgeEntry> entries = knowledgeBaseService.getEntriesByBaseIdAndCategory(baseId, category);
        return Result.success(entries);
    }

    @GetMapping("/entry/{id}")
    public Result<KnowledgeEntry> getEntryById(@PathVariable Long id) {
        KnowledgeEntry entry = knowledgeBaseService.getEntryById(id);
        if (entry == null) {
            return Result.error(404, "知识条目不存在");
        }
        return Result.success(entry);
    }

    @PostMapping("/entries")
    public Result<KnowledgeEntry> createEntry(@RequestBody KnowledgeEntry entry) {
        if (entry.getBaseId() == null) {
            return Result.error(400, "所属知识库ID不能为空");
        }
        if (entry.getTitle() == null || entry.getTitle().trim().isEmpty()) {
            return Result.error(400, "标题不能为空");
        }
        if (entry.getContent() == null || entry.getContent().trim().isEmpty()) {
            return Result.error(400, "内容不能为空");
        }
        KnowledgeEntry created = knowledgeBaseService.createEntry(entry);
        return Result.success(created);
    }

    @PutMapping("/entries/{id}")
    public Result<Boolean> updateEntry(@PathVariable Long id, @RequestBody KnowledgeEntry entry) {
        boolean success = knowledgeBaseService.updateEntry(id, entry);
        if (!success) {
            return Result.error(404, "知识条目不存在");
        }
        return Result.success(true);
    }

    @DeleteMapping("/entries/{id}")
    public Result<Boolean> deleteEntry(@PathVariable Long id, HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        KnowledgeEntry entry = knowledgeBaseService.getEntryById(id);
        if (entry == null) {
            return Result.error(404, "知识条目不存在");
        }
        // 仅允许删除本人创建的AI方案
        if ("LEARNING_PLAN".equals(entry.getEntryType()) && entry.getOwnerId() != null) {
            try {
                if (!userId.equals(String.valueOf(entry.getOwnerId()))) {
                    return Result.error(403, "无权删除他人的学习方案");
                }
            } catch (NumberFormatException e) {
                return Result.error(403, "无权删除");
            }
        }
        boolean success = knowledgeBaseService.deleteEntry(id);
        if (!success) {
            return Result.error(404, "删除失败");
        }
        return Result.success(true);
    }

    // ==================== 意图规则管理 ====================

    @GetMapping("/intents")
    public Result<List<IntentRule>> getAllIntentRules() {
        List<IntentRule> rules = intentService.getAllRules();
        return Result.success(rules);
    }

    @PostMapping("/intents")
    public Result<IntentRule> createIntentRule(@RequestBody IntentRule rule) {
        if (rule.getPattern() == null || rule.getPattern().trim().isEmpty()) {
            return Result.error(400, "匹配模式不能为空");
        }
        if (rule.getIntentType() == null || rule.getIntentType().trim().isEmpty()) {
            return Result.error(400, "意图类型不能为空");
        }
        IntentRule created = intentService.createRule(rule);
        return Result.success(created);
    }

    @PutMapping("/intents/{id}")
    public Result<Boolean> updateIntentRule(@PathVariable Long id, @RequestBody IntentRule rule) {
        boolean success = intentService.updateRule(id, rule);
        if (!success) {
            return Result.error(404, "意图规则不存在");
        }
        return Result.success(true);
    }

    @DeleteMapping("/intents/{id}")
    public Result<Boolean> deleteIntentRule(@PathVariable Long id) {
        boolean success = intentService.deleteRule(id);
        if (!success) {
            return Result.error(404, "意图规则不存在");
        }
        return Result.success(true);
    }

    @PostMapping("/intent/recognize")
    public Result<Map<String, Object>> recognizeIntent(@RequestBody Map<String, String> request) {
        String query = request.get("query");
        if (query == null || query.trim().isEmpty()) {
            return Result.error(400, "查询内容不能为空");
        }
        String intentType = intentService.recognizeIntent(query);
        IntentRule rule = intentService.getMatchingRule(query);
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("query", query);
        resultMap.put("intentType", intentType);
        resultMap.put("matchingRule", rule);
        return Result.success(resultMap);
    }

    // ==================== 数据导入 ====================

    @PostMapping("/import/all")
    public Result<Map<String, Object>> importAll() {
        Map<String, Object> result = dataImportService.importAll();
        return Result.success(result);
    }

    // ==================== AI生成内容列表 ====================

    @GetMapping("/ai-generated")
    public Result<List<KnowledgeEntry>> getAIGenerated(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        List<KnowledgeEntry> all = knowledgeBaseService.searchAllEntries("", 500);
        // 只看AI生成的，且只看自己的
        List<KnowledgeEntry> filtered = all.stream().filter(e ->
            "LEARNING_PLAN".equals(e.getEntryType()) &&
            userId != null && e.getOwnerId() != null && userId.equals(String.valueOf(e.getOwnerId()))
        ).toList();
        return Result.success(filtered);
    }

    // ==================== 搜索（兼容前端） ====================

    @GetMapping("/search")
    public Result<List<KnowledgeEntry>> searchEntries(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) Long baseId,
            HttpServletRequest request) {
        String keyword = (query != null) ? query : (category != null ? category : "");
        List<KnowledgeEntry> entries;
        if (baseId != null && baseId > 0) {
            entries = knowledgeBaseService.searchEntries(baseId, keyword, limit);
        } else {
            entries = knowledgeBaseService.searchAllEntries(keyword, limit);
        }
        // 过滤：AI方案只能看自己的
        String userId = (String) request.getAttribute("userId");
        if (userId != null) {
            entries = entries.stream().filter(e ->
                !"LEARNING_PLAN".equals(e.getEntryType()) ||
                (e.getOwnerId() != null && userId.equals(String.valueOf(e.getOwnerId())))
            ).toList();
        }
        return Result.success(entries);
    }

    // ==================== 兼容旧前端接口 ====================

    @GetMapping("/item/{id}")
    public Result<KnowledgeEntry> getItem(@PathVariable Long id, HttpServletRequest request) {
        KnowledgeEntry entry = knowledgeBaseService.getEntryById(id);
        if (entry == null) {
            return Result.error(404, "知识项不存在");
        }
        // AI方案仅允许所有者查看
        if ("LEARNING_PLAN".equals(entry.getEntryType())) {
            String userId = (String) request.getAttribute("userId");
            if (userId == null || entry.getOwnerId() == null ||
                !userId.equals(String.valueOf(entry.getOwnerId()))) {
                return Result.error(403, "无权查看此方案");
            }
        }
        return Result.success(entry);
    }

    @GetMapping("/item")
    public Result<KnowledgeEntry> getItemByTitle(@RequestParam String title) {
        List<KnowledgeEntry> entries = knowledgeBaseService.searchAllEntries(title, 1);
        if (entries.isEmpty()) {
            return Result.error(404, "知识项不存在");
        }
        return Result.success(entries.get(0));
    }

    @PostMapping("/generate")
    public Result<Map<String, Object>> generate(@RequestBody Map<String, String> request) {
        String goalTitle = request.get("goalTitle");
        String prompt = request.get("prompt");
        String keyword = (prompt != null) ? prompt : (goalTitle != null ? goalTitle : "");
        List<KnowledgeEntry> entries = knowledgeBaseService.searchAllEntries(keyword, 10);
        return Result.success(Map.of(
            "resources", entries,
            "suggestion", "根据您的学习目标，为您推荐以下学习资源。"
        ));
    }

    @PostMapping("/item/{id}/like")
    public Result<Map<String, Object>> toggleLike(@PathVariable Long id, HttpServletRequest request) {
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

    @PostMapping("/item/{id}/view")
    public Result<Map<String, Object>> recordView(@PathVariable Long id, HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "未登录或Token已过期");
        }
        try {
            boolean counted = resourceInteractionService.recordView(id, userId);
            Map<String, Object> stats = resourceInteractionService.getStats(id);
            Map<String, Object> result = new HashMap<>();
            result.put("counted", counted);
            result.put("likeCount", stats.get("likeCount"));
            result.put("viewCount", stats.get("viewCount"));
            return Result.success(result);
        } catch (Exception e) {
            log.error("记录浏览失败", e);
            return Result.error(500, "记录浏览失败");
        }
    }

    // ==================== 保存AI生成内容 ====================

    @PostMapping("/save-generated")
    public Result<Map<String, Object>> saveGeneratedContent(
            HttpServletRequest request,
            @RequestBody Map<String, Object> body) {

        String userId = (String) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "未登录或Token已过期");
        }

        try {
            String title = (String) body.getOrDefault("title", "AI生成内容");
            String content = (String) body.getOrDefault("content", "");
            String category = (String) body.getOrDefault("category", "AI生成");
            String type = (String) body.getOrDefault("type", "article");

            KnowledgeBase aiBase = knowledgeBaseService.getBaseByDomain("AI_GENERATED");
            if (aiBase == null) {
                aiBase = new KnowledgeBase();
                aiBase.setName("AI生成内容");
                aiBase.setDomain("AI_GENERATED");
                aiBase.setDescription("多智能体协同生成的个性化学习内容");
                aiBase.setStatus("ACTIVE");
                knowledgeBaseService.createBase(aiBase);
            }

            // 支持追加模式：如果传了appendToId，则追加到已有条目
            Object appendToId = body.get("appendToId");
            if (appendToId != null) {
                Long existingId = Long.valueOf(appendToId.toString());
                KnowledgeEntry existing = knowledgeBaseService.getEntryById(existingId);
                if (existing != null) {
                    String newContent = (existing.getContent() != null ? existing.getContent() : "")
                            + "\n\n---\n\n" + content;
                    existing.setContent(newContent);
                    knowledgeBaseService.updateEntry(existingId, existing);
                    Map<String, Object> result = new HashMap<>();
                    result.put("id", existingId);
                    result.put("title", existing.getTitle());
                    result.put("message", "已追加到资源");
                    log.info("AI内容已追加: userId={}, id={}", userId, existingId);
                    return Result.success(result);
                }
            }

            KnowledgeEntry entry = new KnowledgeEntry();
            entry.setBaseId(aiBase.getId());
            entry.setTitle(title);
            entry.setContent(content);
            entry.setCategory(category);
            entry.setSubModule(type);
            // AI方案标记 + 结构化数据
            entry.setEntryType("LEARNING_PLAN");
            String planData = body.get("planData") != null ? body.get("planData").toString() : null;
            if (planData != null && !planData.isEmpty()) {
                entry.setPlanData(planData);
            }
            // 方案归属
            try { entry.setOwnerId(Long.parseLong(userId)); } catch (NumberFormatException e) {}
            knowledgeBaseService.createEntry(entry);

            Map<String, Object> result = new HashMap<>();
            result.put("id", entry.getId());
            result.put("title", entry.getTitle());
            result.put("message", "已保存到资源中心");
            log.info("AI生成内容已保存: userId={}, title={}", userId, title);
            return Result.success(result);

        } catch (Exception e) {
            log.error("保存AI生成内容失败", e);
            return Result.error(500, "保存失败");
        }
    }

    // ==================== 知识图谱 ====================

    @GetMapping("/graph/{topic}")
    public Result<Map<String, Object>> getKnowledgeGraph(@PathVariable String topic) {
        Map<String, Object> graph = knowledgeGraphService.getKnowledgeGraph(topic);
        graph.put("available", knowledgeGraphService.isAvailable());
        return Result.success(graph);
    }

    @GetMapping("/graph-topics")
    public Result<List<String>> getUserGraphTopics(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        if (userId == null) return Result.error(401, "未登录");
        return Result.success(knowledgeGraphService.getUserTopics(userId));
    }
}