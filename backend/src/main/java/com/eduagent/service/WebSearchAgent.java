package com.eduagent.service;

import com.eduagent.agent.*;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import org.springframework.beans.factory.annotation.Value;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 联网搜索/拓展阅读智能体
 * 通过LLM生成基于学习主题的拓展阅读推荐材料
 */
@Slf4j
@Service
public class WebSearchAgent {

    private final AIService aiService;

    @Value("${serpapi.key:}")
    private String serpApiKey;

    public WebSearchAgent(AIService aiService) {
        this.aiService = aiService;
    }

    // ==================== System Prompt ====================

    private static final String EXTENDED_READING_PROMPT = """
            你是资深学习资源推荐专家，擅长根据学习主题和用户画像，生成高质量的拓展阅读推荐材料。

            你的核心职责：
            1. 根据用户的学习主题和难度级别，推荐3-5篇最相关的拓展阅读材料
            2. 每篇材料需要包含：标题、摘要、类型、难度、推荐理由
            3. 推荐材料应覆盖不同角度和难度层级，形成互补
            4. 摘要应简洁准确，2-3句话概括核心内容
            5. 推荐理由应说明该材料对当前学习目标的帮助

            工作原则：
            1. 推荐内容必须与学习主题高度相关
            2. 难度标注应准确匹配用户当前水平
            3. 材料类型应多样化，避免全部为同一类型
            4. 所有推荐均为AI整理，需提醒用户交叉验证

            ---
            【输出格式要求 - 必须严格遵守】
            你必须只输出以下JSON格式，不要输出任何其他内容（不要用markdown代码块包裹，不要加解释文字）：

            {
              "resources": [
                {
                  "title": "拓展阅读标题",
                  "summary": "内容摘要(2-3句话，简洁描述该材料涵盖的核心内容)",
                  "type": "文章/教程/论文/书籍",
                  "difficulty": "入门/基础/中级/高级",
                  "recommendReason": "推荐理由(说明为什么推荐该材料，对当前学习目标有何帮助)",
                  "source": "AI整理"
                }
              ],
              "note": "以下内容由AI整理推荐，建议交叉验证，结合教材和官方资料学习效果更佳"
            }
            """;

    // ==================== 新接口：LLM驱动的拓展阅读推荐 ====================

    /**
     * 基于LLM生成拓展阅读推荐材料
     *
     * @param query              用户原始学习查询
     * @param parsedRequirements 统筹解析后的结构化需求（包含subject、level等字段）
     * @param context             Agent共享上下文（包含用户画像等）
     * @return AgentResult 包含resources列表
     */
    public AgentResult searchAndSummarize(String query, Map<String, Object> parsedRequirements, AgentContext context) {
        long startTime = System.currentTimeMillis();

        try {
            // 1. 提取搜索关键词
            String subject = parsedRequirements != null ?
                    (String) parsedRequirements.getOrDefault("subject", query) : query;

            // 2. 尝试真实搜索
            List<Map<String, Object>> searchResults = searchSerpAPI(subject);
            String searchContext = "";
            if (!searchResults.isEmpty()) {
                StringBuilder ctx = new StringBuilder();
                ctx.append("以下是从搜索引擎获取的最新结果，请据此整理推荐：\n");
                for (int i = 0; i < searchResults.size(); i++) {
                    Map<String, Object> r = searchResults.get(i);
                    ctx.append(i + 1).append(". ").append(r.get("title")).append("\n");
                    ctx.append("   摘要: ").append(r.get("snippet")).append("\n");
                    ctx.append("   链接: ").append(r.get("link")).append("\n");
                }
                searchContext = ctx.toString();
                log.info("SerpAPI搜索成功: {}条结果", searchResults.size());
            }

            // 3. 构建Prompt + 调用LLM
            String userMessage = buildSearchPrompt(query, parsedRequirements, context, searchContext);
            String llmOutput = aiService.chatWithSystemPrompt(EXTENDED_READING_PROMPT, userMessage);

            if (llmOutput == null || llmOutput.trim().isEmpty()) {
                log.warn("LLM拓展阅读生成返回空，使用降级方案");
                return buildFallbackResult(query, startTime);
            }

            // 3. 解析LLM输出的JSON
            JsonNode parsedJson = JsonParserUtil.parseJson(llmOutput);

            // 4. 提取resources列表
            List<Map<String, Object>> resources = extractResources(parsedJson);
            if (resources.isEmpty()) {
                log.warn("LLM返回的resources为空，使用降级方案");
                return buildFallbackResult(query, startTime);
            }

            // 5. 提取note
            String note = parsedJson.has("note") ? parsedJson.get("note").asText() : "";

            // 6. 构建结果数据
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("resources", resources);
            resultData.put("note", note);
            resultData.put("query", query);

            // 7. 构建Markdown内容
            String markdown = buildMarkdownContent(resources, note);

            long duration = System.currentTimeMillis() - startTime;
            log.info("拓展阅读智能体完成，LLM生成{}条推荐，耗时{}ms", resources.size(), duration);
            return AgentResult.success("拓展阅读智能体", resultData, markdown, duration);

        } catch (Exception e) {
            log.error("拓展阅读智能体LLM调用失败，使用降级方案: {}", e.getMessage());
            return buildFallbackResult(query, startTime);
        }
    }

    /**
     * 构建发送给LLM的用户消息
     */
    private String buildSearchPrompt(String query, Map<String, Object> parsedRequirements, AgentContext context, String searchContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户查询: ").append(query).append("\n");
        if (parsedRequirements != null) {
            sb.append("主题: ").append(parsedRequirements.getOrDefault("subject", query)).append("\n");
            sb.append("难度: ").append(parsedRequirements.getOrDefault("level", "基础")).append("\n");
        }
        if (searchContext != null && !searchContext.isEmpty()) {
            sb.append("\n").append(searchContext);
        }
        return sb.toString();
    }

    private List<Map<String, Object>> searchSerpAPI(String query) {
        if (serpApiKey == null || serpApiKey.isEmpty()) return List.of();
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://serpapi.com/search?q=" + encoded + "&api_key=" + serpApiKey + "&hl=zh-CN&num=5";
            var req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build();
            var resp = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                var root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(resp.body());
                List<Map<String, Object>> results = new ArrayList<>();
                var organic = root.get("organic_results");
                if (organic != null) for (var r : organic) {
                    results.add(Map.of("title", r.get("title").asText(),
                            "snippet", r.has("snippet") ? r.get("snippet").asText() : "",
                            "link", r.has("link") ? r.get("link").asText() : ""));
                    if (results.size() >= 5) break;
                }
                log.info("SerpAPI搜索: {}条结果", results.size());
                return results;
            }
        } catch (Exception e) { log.warn("SerpAPI搜索失败: {}", e.getMessage()); }
        return List.of();
    }

    private String buildSearchPromptOld(String query, Map<String, Object> parsedRequirements, AgentContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 用户学习查询\n");
        sb.append(query).append("\n\n");

        // 解析后的需求
        if (parsedRequirements != null && !parsedRequirements.isEmpty()) {
            sb.append("## 学习需求分析\n");
            Object subject = parsedRequirements.get("subject");
            if (subject != null && !subject.toString().isEmpty()) {
                sb.append("- 科目/主题: ").append(subject).append("\n");
            }
            Object level = parsedRequirements.get("level");
            if (level != null && !level.toString().isEmpty()) {
                sb.append("- 当前水平: ").append(level).append("\n");
            }
            Object goal = parsedRequirements.get("goal");
            if (goal != null && !goal.toString().isEmpty()) {
                sb.append("- 学习目标: ").append(goal).append("\n");
            }
            Object availableTime = parsedRequirements.get("availableTime");
            if (availableTime != null && !availableTime.toString().isEmpty()) {
                sb.append("- 可用时间: ").append(availableTime).append("\n");
            }
            Object weaknesses = parsedRequirements.get("weaknesses");
            if (weaknesses != null && !weaknesses.toString().isEmpty()) {
                sb.append("- 薄弱环节: ").append(weaknesses).append("\n");
            }
            Object preferences = parsedRequirements.get("preferences");
            if (preferences != null && !preferences.toString().isEmpty()) {
                sb.append("- 资源偏好: ").append(preferences).append("\n");
            }
            sb.append("\n");
        }

        // 用户画像
        if (context != null) {
            String profileSummary = context.buildProfileSummary();
            if (profileSummary != null && !profileSummary.isEmpty()) {
                sb.append("## ").append(profileSummary).append("\n");
            }
            String memorySummary = context.buildMemorySummary();
            if (memorySummary != null && !memorySummary.isEmpty()) {
                sb.append("## ").append(memorySummary).append("\n");
            }
        }

        sb.append("\n请基于以上信息，为该学习主题推荐3-5篇高质量的拓展阅读材料。");
        return sb.toString();
    }

    /**
     * 从解析后的JSON中提取resources列表
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractResources(JsonNode parsedJson) {
        List<Map<String, Object>> resources = new ArrayList<>();

        if (parsedJson.has("resources") && parsedJson.get("resources").isArray()) {
            for (JsonNode item : parsedJson.get("resources")) {
                Map<String, Object> resource = new HashMap<>();
                resource.put("title", item.has("title") ? item.get("title").asText() : "");
                resource.put("summary", item.has("summary") ? item.get("summary").asText() : "");
                resource.put("type", item.has("type") ? item.get("type").asText() : "文章");
                resource.put("difficulty", item.has("difficulty") ? item.get("difficulty").asText() : "基础");
                resource.put("recommendReason", item.has("recommendReason") ? item.get("recommendReason").asText() : "");
                resource.put("source", item.has("source") ? item.get("source").asText() : "AI整理");
                resources.add(resource);
            }
        }

        return resources;
    }

    /**
     * 构建Markdown展示内容
     */
    private String buildMarkdownContent(List<Map<String, Object>> resources, String note) {
        StringBuilder md = new StringBuilder();
        md.append("## 拓展阅读推荐\n\n");

        if (note != null && !note.isEmpty()) {
            md.append("> ").append(note).append("\n\n");
        }

        for (int i = 0; i < resources.size(); i++) {
            Map<String, Object> r = resources.get(i);
            md.append("### ").append(i + 1).append(". ").append(r.get("title")).append("\n\n");
            md.append("- **类型**: ").append(r.get("type")).append("\n");
            md.append("- **难度**: ").append(r.get("difficulty")).append("\n");
            md.append("- **摘要**: ").append(r.get("summary")).append("\n");
            md.append("- **推荐理由**: ").append(r.get("recommendReason")).append("\n");
            md.append("- **来源**: ").append(r.get("source")).append("\n\n");
        }

        return md.toString();
    }

    /**
     * 构建降级方案结果（使用旧的硬编码逻辑）
     */
    private AgentResult buildFallbackResult(String query, long startTime) {
        List<Map<String, Object>> fallbackResults = generateSearchResultsFallback(query);

        // 转换为拓展阅读格式
        List<Map<String, Object>> resources = new ArrayList<>();
        for (Map<String, Object> item : fallbackResults) {
            Map<String, Object> resource = new HashMap<>();
            resource.put("title", item.getOrDefault("title", ""));
            resource.put("summary", item.getOrDefault("summary", ""));
            resource.put("type", "文章");
            resource.put("difficulty", "基础");
            resource.put("recommendReason", "基于Mock数据的推荐阅读");
            resource.put("source", item.getOrDefault("source", "AI整理"));
            resources.add(resource);
        }

        Map<String, Object> resultData = new HashMap<>();
        resultData.put("resources", resources);
        resultData.put("note", "以下内容为本地推荐（LLM服务暂时不可用），建议稍后刷新获取AI定制推荐。");
        resultData.put("query", query);

        String markdown = buildMarkdownContent(resources,
                "以下内容为本地推荐（LLM服务暂时不可用），建议稍后刷新获取AI定制推荐。");

        long duration = System.currentTimeMillis() - startTime;
        log.info("拓展阅读智能体使用降级方案，{}条结果，耗时{}ms", resources.size(), duration);
        return AgentResult.degraded("拓展阅读智能体", resultData, markdown, duration);
    }

    // ==================== 旧接口（向后兼容） ====================

    /**
     * 旧版联网搜索接口（向后兼容）
     * 对调用方无感知，内部仍使用Mock数据
     */
    public Map<String, Object> searchWeb(String query) {
        Map<String, Object> result = new HashMap<>();

        try {
            List<Map<String, Object>> searchResults = generateSearchResultsFallback(query);

            result.put("agent", "联网智能体");
            result.put("status", "success");
            result.put("count", searchResults.size());
            result.put("data", searchResults);

            log.info("联网智能体完成搜索，找到{}条结果", searchResults.size());

        } catch (Exception e) {
            log.error("联网智能体执行失败", e);
            result.put("agent", "联网智能体");
            result.put("status", "error");
            result.put("count", 0);
            result.put("data", new ArrayList<>());
        }

        return result;
    }

    // ==================== 降级/硬编码数据（保留为Fallback） ====================

    /**
     * 硬编码搜索数据（LLM降级时使用）
     * 原 generateSearchResults 方法重命名
     */
    private List<Map<String, Object>> generateSearchResultsFallback(String query) {
        List<Map<String, Object>> results = new ArrayList<>();

        if (query.contains("英语") || query.contains("四级") || query.contains("六级")) {
            Map<String, Object> result1 = new HashMap<>();
            result1.put("title", "2024年英语四级考试时间及备考攻略");
            result1.put("summary", "2024年下半年英语四级考试将于12月14日举行。备考攻略包括：词汇积累、听力训练、阅读技巧、写作模板等方面的详细指导。");
            result1.put("source", "学习方法论");
            results.add(result1);

            Map<String, Object> result2 = new HashMap<>();
            result2.put("title", "英语四级高频词汇表");
            result2.put("summary", "整理了英语四级考试中出现频率最高的500个词汇，包含音标、词性和例句，适合考前突击复习。");
            result2.put("source", "英语学习网");
            results.add(result2);

            Map<String, Object> result3 = new HashMap<>();
            result3.put("title", "四级听力技巧分享");
            result3.put("summary", "听力是四级考试的重要部分，掌握预读技巧、注意关键词、学会速记等方法可以有效提高听力成绩。");
            result3.put("source", "考试技巧网");
            results.add(result3);
        } else if (query.contains("Python") || query.contains("编程")) {
            Map<String, Object> result1 = new HashMap<>();
            result1.put("title", "Python学习路线图2024");
            result1.put("summary", "从零基础到高级开发的完整学习路线，包括基础语法、数据结构、算法、框架学习等阶段。");
            result1.put("source", "Python教程网");
            results.add(result1);

            Map<String, Object> result2 = new HashMap<>();
            result2.put("title", "Python面试题汇总");
            result2.put("summary", "收集了各大公司Python面试中常见的问题，包括基础语法、数据结构、算法、设计模式等方面。");
            result2.put("source", "技术面试网");
            results.add(result2);
        } else {
            Map<String, Object> result1 = new HashMap<>();
            result1.put("title", "高效学习方法研究");
            result1.put("summary", "最新研究表明，间隔重复、主动回忆、深度学习等方法能显著提高学习效率。");
            result1.put("source", "学习科学杂志");
            results.add(result1);

            Map<String, Object> result2 = new HashMap<>();
            result2.put("title", "时间管理技巧");
            result2.put("summary", "介绍了番茄工作法、GTD时间管理、四象限法则等时间管理方法，帮助提高学习和工作效率。");
            result2.put("source", "效率提升博客");
            results.add(result2);
        }

        return results;
    }
}
