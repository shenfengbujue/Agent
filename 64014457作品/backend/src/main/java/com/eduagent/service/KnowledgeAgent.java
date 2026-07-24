package com.eduagent.service;

import com.eduagent.agent.*;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识库检索智能体
 * 负责组合数据库检索 + LLM生成标准化知识模块文档
 */
@Slf4j
@Service
public class KnowledgeAgent {

    private final KnowledgeBaseService knowledgeBaseService;
    private final AIService aiService;

    public KnowledgeAgent(KnowledgeBaseService knowledgeBaseService, AIService aiService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.aiService = aiService;
    }

    // ==================== 新方法：LLM驱动的知识检索与生成 ====================

    /**
     * 组合DB搜索 + LLM知识文档生成
     * 1. 从KnowledgeBase搜索相关条目
     * 2. 将搜索结果、需求解析、路径规划、用户画像一并送入LLM
     * 3. LLM输出结构化知识模块JSON
     * 4. 失败时降级为mock知识
     *
     * @param query              用户原始查询
     * @param parsedRequirements 统筹解析Agent的结构化输出
     * @param pathResult         路径规划Agent的输出
     * @param context            共享上下文
     * @return AgentResult（data=模块列表，markdownContent=格式化知识文本）
     */
    public AgentResult searchAndGenerate(String query,
                                         Map<String, Object> parsedRequirements,
                                         AgentResult pathResult,
                                         AgentContext context) {
        long startTime = System.currentTimeMillis();

        try {
            // Step 1: 数据库搜索
            List<com.eduagent.entity.KnowledgeEntry> dbEntries =
                    knowledgeBaseService.searchAllEntries(query, 10);
            log.info("知识库DB检索到 {} 条相关记录", dbEntries != null ? dbEntries.size() : 0);

            if (dbEntries == null) {
                dbEntries = List.of();
            }

            // Step 2: 构建LLM提示词
            String prompt = buildKnowledgePrompt(query, parsedRequirements, pathResult,
                    dbEntries, context);

            // Step 3: 调用LLM（优先流式）
            String llmOutput = null;
            boolean llmFailed = false;
            try {
                if (context != null && context.getStreamTokenCallback() != null) {
                    llmOutput = aiService.chatWithSystemPromptStreaming(
                            SystemPrompts.KNOWLEDGE_RETRIEVAL, prompt,
                            token -> context.getStreamTokenCallback().accept("知识库检索智能体", token));
                } else {
                    llmOutput = aiService.chatWithSystemPrompt(
                            SystemPrompts.KNOWLEDGE_RETRIEVAL, prompt);
                }
                log.info("LLM知识生成响应长度: {} chars",
                        llmOutput != null ? llmOutput.length() : 0);
            } catch (Exception e) {
                log.warn("LLM知识生成调用失败，将使用降级方案: {}", e.getMessage());
                llmFailed = true;
            }

            // Step 4: 解析LLM输出
            if (!llmFailed && llmOutput != null && !llmOutput.isBlank()) {
                try {
                    // 先用Jackson直解析，失败再用JsonParserUtil
                    JsonNode jsonNode;
                    try {
                        jsonNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(llmOutput);
                    } catch (Exception e1) {
                        jsonNode = JsonParserUtil.parseJson(llmOutput);
                    }
                    if (jsonNode.isEmpty() || !jsonNode.has("modules")) {
                        log.warn("LLM输出解析失败或缺少modules字段，原始输出前300字: {}",
                                llmOutput.substring(0, Math.min(300, llmOutput.length())));
                    } else if (jsonNode.get("modules").size() > 0) {
                        List<Map<String, Object>> modulesList = extractModulesList(jsonNode);
                        String markdown = formatKnowledgeMarkdown(jsonNode);
                        long duration = System.currentTimeMillis() - startTime;
                        log.info("LLM知识生成成功，共 {} 个模块，耗时 {}ms",
                                modulesList.size(), duration);
                        return AgentResult.success("知识库检索智能体",
                                modulesList, markdown, duration);
                    } else {
                        log.warn("LLM输出modules为空数组，降级处理");
                    }
                } catch (Exception e) {
                    log.warn("LLM输出JSON解析异常: {}", e.getMessage());
                }
            }

            // Step 5: 降级方案 — DB有结果则格式化DB结果，否则使用mock
            long duration = System.currentTimeMillis() - startTime;
            String fallbackMarkdown;
            List<Map<String, Object>> fallbackData;

            if (!dbEntries.isEmpty()) {
                fallbackData = formatDbEntriesAsModules(dbEntries);
                fallbackMarkdown = buildFallbackMarkdownFromData(fallbackData);
            } else {
                List<Map<String, Object>> mockItems = generateMockKnowledgeFallback(query);
                fallbackData = formatMockItemsAsModules(mockItems);
                fallbackMarkdown = buildFallbackMarkdownFromData(fallbackData);
            }

            log.info("知识库检索降级完成（degraded），耗时 {}ms", duration);
            return AgentResult.degraded("知识库检索智能体",
                    fallbackData, fallbackMarkdown, duration);

        } catch (Exception e) {
            log.error("知识库检索智能体执行失败", e);
            long duration = System.currentTimeMillis() - startTime;
            return AgentResult.error("知识库检索智能体", e.getMessage(), duration);
        }
    }

    // ==================== 旧方法（向后兼容） ====================

    /**
     * @deprecated 请使用 {@link #searchAndGenerate(String, Map, AgentResult, AgentContext)}
     *             获得LLM增强的知识内容
     */
    @Deprecated
    public Map<String, Object> searchKnowledge(String query) {
        Map<String, Object> result = new HashMap<>();

        try {
            List<Map<String, Object>> knowledgeItems = new ArrayList<>();

            List<com.eduagent.entity.KnowledgeEntry> entries =
                    knowledgeBaseService.searchAllEntries(query, 5);

            if (entries != null && !entries.isEmpty()) {
                for (com.eduagent.entity.KnowledgeEntry entry : entries) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", entry.getId());
                    item.put("title", entry.getTitle());
                    item.put("content", truncateContent(entry.getContent(), 200));
                    item.put("source", "知识库");
                    item.put("category", entry.getCategory());
                    knowledgeItems.add(item);
                }
            } else {
                knowledgeItems = generateMockKnowledgeFallback(query);
            }

            result.put("agent", "知识库检索智能体");
            result.put("status", "success");
            result.put("count", knowledgeItems.size());
            result.put("data", knowledgeItems);

            log.info("知识库检索智能体完成，找到{}条相关内容", knowledgeItems.size());

        } catch (Exception e) {
            log.error("知识库检索智能体执行失败", e);
            result.put("agent", "知识库检索智能体");
            result.put("status", "error");
            result.put("count", 0);
            result.put("data", generateMockKnowledgeFallback(query));
        }

        return result;
    }

    // ==================== Prompt构建 ====================

    /**
     * 构建发送给LLM的完整用户消息
     */
    private String buildKnowledgePrompt(String query,
                                        Map<String, Object> parsedRequirements,
                                        AgentResult pathResult,
                                        List<com.eduagent.entity.KnowledgeEntry> dbEntries,
                                        AgentContext context) {
        StringBuilder sb = new StringBuilder();

        // 1. 核心任务
        String subject = parsedRequirements != null ? (String) parsedRequirements.getOrDefault("subject", query) : query;
        String level = parsedRequirements != null ? (String) parsedRequirements.getOrDefault("level", "基础") : "基础";
        sb.append("【核心任务】你是[" + subject + "]领域的教学专家。请围绕这个主题，生成难度为[" + level + "]的专业知识模块。你需要完全依靠你的专业知识来生成内容，不要等待外部数据。\n\n");

        // 2. 需求解析
        sb.append("## 用户需求\n");
        sb.append("原始输入: ").append(query).append("\n");
        if (parsedRequirements != null && !parsedRequirements.isEmpty()) {
            parsedRequirements.forEach((k, v) ->
                    sb.append("- ").append(k).append(": ").append(v).append("\n"));
        }
        sb.append("\n");

        // 3. 学习路径（据此安排模块顺序和阶段）
        sb.append("## 学习路径\n");
        if (pathResult != null && pathResult.getData() != null) {
            appendPathStages(sb, pathResult.getData());
        }
        sb.append("\n");

        // 4. 知识库参考（仅当找到与主题关键词匹配的条目时才加入，否则完全跳过）
        List<com.eduagent.entity.KnowledgeEntry> relevantEntries = filterRelevantEntries(dbEntries, subject);
        if (!relevantEntries.isEmpty()) {
            sb.append("## 知识库中找到的相关参考（可用可忽略，最多3条）\n");
            for (int i = 0; i < relevantEntries.size(); i++) {
                com.eduagent.entity.KnowledgeEntry entry = relevantEntries.get(i);
                sb.append("- ").append(entry.getTitle()).append(": ");
                sb.append(truncateContent(entry.getContent(), 300)).append("\n");
            }
        } else {
            sb.append("## 知识库状态\n知识库暂无关于[" + subject + "]的相关记录，请完全基于你的专业知识生成内容。\n");
        }
        sb.append("\n");

        // 5. 用户画像 + 对话上下文
        sb.append("## 用户画像与对话上下文\n");
        if (context != null) {
            sb.append(context.buildProfileSummary()).append("\n");
            sb.append(context.buildFullMemoryContext()).append("\n");
        } else {
            sb.append("暂无用户画像数据\n");
        }
        sb.append("\n");

        // 6. 输出格式指令
        sb.append(buildFormatInstruction());

        return sb.toString();
    }

    /**
     * 追加路径规划的阶段信息
     */
    @SuppressWarnings("unchecked")
    private void appendPathStages(StringBuilder sb, Object pathData) {
        if (pathData instanceof List) {
            List<?> stages = (List<?>) pathData;
            for (int i = 0; i < stages.size(); i++) {
                Object stage = stages.get(i);
                if (stage instanceof Map) {
                    Map<String, Object> stageMap = (Map<String, Object>) stage;
                    sb.append("阶段").append(i + 1).append(": ");
                    sb.append(stageMap.getOrDefault("name",
                            stageMap.getOrDefault("stageName", "未命名"))).append("\n");
                    if (stageMap.containsKey("modules")) {
                        Object mods = stageMap.get("modules");
                        if (mods instanceof List) {
                            for (Object m : (List<?>) mods) {
                                sb.append("  - 模块: ").append(m).append("\n");
                            }
                        }
                    }
                    if (stageMap.containsKey("duration")) {
                        sb.append("  建议时长: ").append(stageMap.get("duration")).append("\n");
                    }
                }
            }
        } else if (pathData instanceof Map) {
            Map<String, Object> dataMap = (Map<String, Object>) pathData;
            if (dataMap.containsKey("stages")) {
                appendPathStages(sb, dataMap.get("stages"));
            } else {
                sb.append(pathData.toString()).append("\n");
            }
        } else {
            sb.append(pathData != null ? pathData.toString() : "无").append("\n");
        }
    }

    /**
     * 构建JSON输出格式指令
     */
    private String buildFormatInstruction() {
        return """

                ---
                【输出格式要求 - 致命规则，违反则输出被丢弃】

                你必须输出一个JSON对象，顶层必须有 "modules" 键，值为数组。
                以下是唯一合法的JSON Schema:

                {"modules":[{"name":"模块名","basicKnowledge":"基础知识(Markdown)","corePoints":["重点"],"commonMistakes":["易错点"],"resources":[{"title":"资源名","type":"文章/练习/视频","difficulty":"入门/基础/中级/高级","selected":true}]}]}

                致命规则:
                1. 直接输出纯JSON，不要用 ```json 包裹，不要加任何解释文字
                2. 顶层必须是 {"modules": [...]} 对象，不能是数组，不能缺少modules键
                3. modules必须是数组，至少包含1个模块
                4. 所有字符串必须用英文双引号 "，严禁使用中文引号 ""
                5. basicKnowledge使用Markdown格式，含公式用$...$包裹
                6. resources至少包含1个资源推荐
                """;
    }

    // ==================== JSON解析与Markdown格式化 ====================

    /**
     * 从JsonNode中提取模块列表
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractModulesList(JsonNode jsonNode) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            JsonNode modulesNode = jsonNode.get("modules");
            if (modulesNode != null && modulesNode.isArray()) {
                for (JsonNode moduleNode : modulesNode) {
                    Map<String, Object> module = new java.util.LinkedHashMap<>();
                    module.put("name", getTextValue(moduleNode, "name"));
                    module.put("basicKnowledge", getTextValue(moduleNode, "basicKnowledge"));
                    module.put("corePoints", getStringList(moduleNode, "corePoints"));
                    module.put("commonMistakes", getStringList(moduleNode, "commonMistakes"));
                    module.put("resources", getResourcesList(moduleNode));
                    result.add(module);
                }
            }
        } catch (Exception e) {
            log.warn("提取模块列表失败: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 将LLM输出的JSON格式化为前端可展示的Markdown
     */
    private String formatKnowledgeMarkdown(JsonNode jsonNode) {
        StringBuilder md = new StringBuilder();
        md.append("# 📚 知识库检索结果\n\n");

        JsonNode modulesNode = jsonNode.get("modules");
        if (modulesNode == null || !modulesNode.isArray() || modulesNode.size() == 0) {
            md.append("> 暂无相关知识模块\n");
            return md.toString();
        }

        for (int i = 0; i < modulesNode.size(); i++) {
            JsonNode module = modulesNode.get(i);
            String name = getTextValue(module, "name");
            String basicKnowledge = getTextValue(module, "basicKnowledge");

            md.append("## ").append(i + 1).append(". ").append(name).append("\n\n");

            // 基础知识
            md.append("### 📖 基础知识\n\n");
            md.append(basicKnowledge).append("\n\n");

            // 核心重点
            JsonNode corePoints = module.get("corePoints");
            if (corePoints != null && corePoints.isArray() && corePoints.size() > 0) {
                md.append("### 🎯 核心重点\n\n");
                for (JsonNode cp : corePoints) {
                    md.append("- ").append(cp.asText()).append("\n");
                }
                md.append("\n");
            }

            // 易错点
            JsonNode mistakes = module.get("commonMistakes");
            if (mistakes != null && mistakes.isArray() && mistakes.size() > 0) {
                md.append("### ⚠️ 常见易错点\n\n");
                for (JsonNode cm : mistakes) {
                    md.append("- ").append(cm.asText()).append("\n");
                }
                md.append("\n");
            }

            // 推荐资源
            JsonNode resources = module.get("resources");
            if (resources != null && resources.isArray() && resources.size() > 0) {
                md.append("### 📂 推荐资源\n\n");
                md.append("| 资源名称 | 类型 | 难度 |\n");
                md.append("|---------|------|------|\n");
                for (JsonNode res : resources) {
                    String title = getTextValue(res, "title");
                    String type = getTextValue(res, "type");
                    String difficulty = getTextValue(res, "difficulty");
                    md.append("| ").append(title).append(" | ")
                            .append(type).append(" | ")
                            .append(difficulty).append(" |\n");
                }
                md.append("\n");
            }

            md.append("---\n\n");
        }

        return md.toString();
    }

    // ==================== 降级方案 ====================

    /**
     * 将DB搜索结果格式化为模块列表（降级输出）
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> formatDbEntriesAsModules(
            List<com.eduagent.entity.KnowledgeEntry> entries) {
        List<Map<String, Object>> modules = new ArrayList<>();
        for (com.eduagent.entity.KnowledgeEntry entry : entries) {
            Map<String, Object> module = new LinkedHashMap<>();
            module.put("name", entry.getTitle() != null ? entry.getTitle() : "未命名");
            module.put("basicKnowledge", entry.getContent() != null ? entry.getContent() : "暂无内容");
            module.put("corePoints", List.of("请参考基础知识内容"));
            module.put("commonMistakes", List.of("暂无易错点数据"));
            module.put("resources", List.of(Map.of(
                    "title", "知识库原文",
                    "type", "文章",
                    "difficulty", "基础",
                    "selected", true
            )));
            modules.add(module);
        }
        return modules;
    }

    /**
     * 将mock结果转换为模块列表（降级输出）
     */
    private List<Map<String, Object>> formatMockItemsAsModules(
            List<Map<String, Object>> mockItems) {
        List<Map<String, Object>> modules = new ArrayList<>();
        for (Map<String, Object> item : mockItems) {
            Map<String, Object> module = new LinkedHashMap<>();
            module.put("name", item.getOrDefault("title", "未命名"));
            module.put("basicKnowledge", item.getOrDefault("content", "暂无内容"));
            module.put("corePoints", List.of("请参考基础知识内容"));
            module.put("commonMistakes", List.of("暂无易错点数据"));
            module.put("resources", List.of(Map.of(
                    "title", item.getOrDefault("title", "参考资源"),
                    "type", "文章",
                    "difficulty", "基础",
                    "selected", true
            )));
            modules.add(module);
        }
        return modules;
    }

    /**
     * 从数据列表构建降级Markdown
     */
    private String buildFallbackMarkdownFromData(List<Map<String, Object>> modules) {
        StringBuilder md = new StringBuilder();
        md.append("# 📚 知识库检索结果（降级方案）\n\n");
        md.append("> 以下内容来自知识库直接检索结果，未经AI深度加工。\n\n");

        for (int i = 0; i < modules.size(); i++) {
            Map<String, Object> module = modules.get(i);
            md.append("## ").append(i + 1).append(". ")
                    .append(module.getOrDefault("name", "未命名")).append("\n\n");

            Object knowledge = module.get("basicKnowledge");
            if (knowledge != null) {
                md.append("### 📖 基础知识\n\n");
                md.append(knowledge.toString()).append("\n\n");
            }

            @SuppressWarnings("unchecked")
            List<String> corePoints = (List<String>) module.get("corePoints");
            if (corePoints != null && !corePoints.isEmpty()) {
                md.append("### 🎯 核心重点\n\n");
                for (String cp : corePoints) {
                    md.append("- ").append(cp).append("\n");
                }
                md.append("\n");
            }

            md.append("---\n\n");
        }

        return md.toString();
    }

    // ==================== 辅助方法 ====================

    private String getTextValue(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return "";
        }
        return node.get(field).asText();
    }

    private List<String> getStringList(JsonNode node, String field) {
        List<String> result = new ArrayList<>();
        if (node != null && node.has(field) && node.get(field).isArray()) {
            for (JsonNode item : node.get(field)) {
                result.add(item.asText());
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getResourcesList(JsonNode node) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (node != null && node.has("resources") && node.get("resources").isArray()) {
            for (JsonNode resNode : node.get("resources")) {
                Map<String, Object> res = new LinkedHashMap<>();
                res.put("title", getTextValue(resNode, "title"));
                res.put("type", getTextValue(resNode, "type"));
                res.put("difficulty", getTextValue(resNode, "difficulty"));
                res.put("selected", resNode.has("selected") && resNode.get("selected").asBoolean());
                result.add(res);
            }
        }
        return result;
    }

    private List<com.eduagent.entity.KnowledgeEntry> filterRelevantEntries(
            List<com.eduagent.entity.KnowledgeEntry> entries, String subject) {
        if (entries == null || entries.isEmpty() || subject == null) return List.of();
        String lower = subject.toLowerCase();
        return entries.stream()
                .filter(e -> {
                    String t = (e.getTitle() != null ? e.getTitle() : "").toLowerCase();
                    String c = (e.getContent() != null ? e.getContent() : "").toLowerCase();
                    return t.contains(lower) || c.contains(lower) || containsWord(t, lower) || containsWord(c, lower);
                })
                .limit(3).toList();
    }

    private boolean containsWord(String text, String subject) {
        for (int i = 0; i < subject.length() - 1; i++)
            for (int j = i + 2; j <= Math.min(i + 4, subject.length()); j++)
                if (text.contains(subject.substring(i, j))) return true;
        return false;
    }

    private String truncateContent(String content, int maxLength) {
        if (content == null) return "";
        if (content.length() <= maxLength) return content;
        return content.substring(0, maxLength) + "...";
    }

    /**
     * 降级mock知识生成（从原generateMockKnowledge重命名）
     */
    private List<Map<String, Object>> generateMockKnowledgeFallback(String query) {
        List<Map<String, Object>> items = new ArrayList<>();

        if (query.contains("英语") || query.contains("四级") || query.contains("六级")) {
            Map<String, Object> item1 = new HashMap<>();
            item1.put("title", "英语四级考试备考指南");
            item1.put("content", "英语四级考试是大学英语四级考试（CET-4）的简称，是由教育部高等教育司组织的全国统一的单科性标准化教学考试。备考建议：1. 词汇积累是基础；2. 听力每天坚持练习30分钟；3. 阅读理解注重技巧训练；4. 写作背诵模板，多加练习。");
            item1.put("source", "知识库");
            item1.put("category", "英语学习");
            items.add(item1);

            Map<String, Object> item2 = new HashMap<>();
            item2.put("title", "英语学习方法");
            item2.put("content", "有效的英语学习方法包括：制定学习计划、坚持每天学习、多听多说、阅读英文材料、写作练习、使用学习APP等。");
            item2.put("source", "知识库");
            item2.put("category", "学习方法");
            items.add(item2);
        } else if (query.contains("Python") || query.contains("编程")) {
            Map<String, Object> item1 = new HashMap<>();
            item1.put("title", "Python入门指南");
            item1.put("content", "Python是一种高级通用编程语言，以简洁的语法和强大的功能著称。学习路径：1. 安装Python环境；2. 学习基础语法；3. 学习面向对象编程；4. 实践小项目；5. 深入学习常用库。");
            item1.put("source", "知识库");
            item1.put("category", "编程学习");
            items.add(item1);
        } else {
            Map<String, Object> item1 = new HashMap<>();
            item1.put("title", "高效学习方法");
            item1.put("content", "高效学习的关键在于：设定明确目标、制定学习计划、专注学习、定期复习、主动回忆、间隔重复、实践应用。");
            item1.put("source", "知识库");
            item1.put("category", "学习方法");
            items.add(item1);
        }

        return items;
    }
}
