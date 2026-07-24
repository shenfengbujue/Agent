package com.eduagent.agent;

import com.eduagent.service.AIService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 统筹解析Agent —— 用LLM解析用户输入 → 输出结构化需求标签
 * 替代原来的 CoordinatorAgent.parseRequirements() 硬编码关键词匹配
 */
@Slf4j
@Component
public class RequirementParser {

    private final AIService aiService;

    public RequirementParser(AIService aiService) {
        this.aiService = aiService;
    }

    /**
     * 解析用户需求
     *
     * @param query   用户原始输入
     * @param context Agent上下文（含用户画像、短期记忆）
     * @return 结构化需求标签
     */
    public Map<String, Object> parse(String query, AgentContext context) {
        long startTime = System.currentTimeMillis();

        try {
            String userMessage = buildParsePrompt(query, context);
            String systemPrompt = SystemPrompts.COORDINATOR + buildOutputFormatInstruction();

            String llmOutput = aiService.chatWithSystemPrompt(systemPrompt, userMessage);

            if (llmOutput == null || llmOutput.trim().isEmpty()) {
                log.warn("LLM返回空，使用降级方案");
                return fallbackParse(query);
            }

            JsonNode json = JsonParserUtil.parseJson(llmOutput);
            Map<String, Object> result = jsonToMap(json);

            // 确保必要字段存在
            result.putIfAbsent("subject", extractSubjectFallback(query));
            result.putIfAbsent("level", extractLevelFallback(query));
            result.putIfAbsent("goal", "综合提升");
            result.putIfAbsent("dailyTime", "未指定");
            result.putIfAbsent("totalDays", 30);
            result.putIfAbsent("weakPoints", new ArrayList<>());
            result.putIfAbsent("preferences", new ArrayList<>());
            result.putIfAbsent("missingInfo", new ArrayList<>());

            long duration = System.currentTimeMillis() - startTime;
            log.info("需求解析完成 ({}ms): subject={}, level={}", duration, result.get("subject"), result.get("level"));
            return result;

        } catch (Exception e) {
            log.error("需求解析失败，使用降级方案: {}", e.getMessage());
            return fallbackParse(query);
        }
    }

    /**
     * 构建给LLM的解析Prompt
     */
    private String buildParsePrompt(String query, AgentContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("请分析以下用户的学习需求，提取结构化信息：\n\n");
        sb.append("【用户输入】\n").append(query).append("\n\n");

        if (context != null) {
            sb.append("【用户画像】\n").append(context.buildProfileSummary()).append("\n\n");
            sb.append("【对话上下文】\n").append(context.buildFullMemoryContext()).append("\n\n");
        }

        sb.append("请提取：学习科目、当前水平、学习目标、每日可用时间、总备考天数、薄弱环节、资源偏好（视频/文章/练习/思维导图）、缺失信息列表");
        return sb.toString();
    }

    /**
     * 构建输出格式指令（JSON Schema）
     */
    private String buildOutputFormatInstruction() {
        String schema = """
                {
                  "subject": "学习科目（如：英语四级、Python编程、概率论）",
                  "level": "当前水平（入门/基础/中级/高级）",
                  "goal": "学习目标（备考复习/学习入门/练习提升/综合提升）",
                  "dailyTime": "每日可用学习时间",
                  "totalDays": 总备考天数(整数),
                  "weakPoints": ["薄弱环节1", "薄弱环节2"],
                  "preferences": ["视频", "文章", "练习题", "思维导图"],
                  "missingInfo": ["缺失的信息1", "缺失的信息2"],
                  "suggestedQuestions": ["建议向用户追问的问题"]
                }
                """;

        String example = """
                {
                  "subject": "英语四级",
                  "level": "基础",
                  "goal": "备考复习",
                  "dailyTime": "1小时",
                  "totalDays": 60,
                  "weakPoints": ["听力", "词汇量不足"],
                  "preferences": ["练习题", "文章"],
                  "missingInfo": ["目标分数未指定"],
                  "suggestedQuestions": ["您的目标分数是多少？"]
                }
                """;

        return JsonParserUtil.buildFormatInstruction(schema, example);
    }

    // ==================== 降级方案（保留旧硬编码逻辑） ====================

    private Map<String, Object> fallbackParse(String query) {
        Map<String, Object> requirements = new LinkedHashMap<>();
        requirements.put("subject", extractSubjectFallback(query));
        requirements.put("level", extractLevelFallback(query));
        requirements.put("goal", extractGoalFallback(query));
        requirements.put("dailyTime", "未指定");
        requirements.put("totalDays", 30);
        requirements.put("weakPoints", new ArrayList<>());
        requirements.put("preferences", extractPreferencesFallback(query));
        requirements.put("missingInfo", List.of("未使用AI解析，信息可能不完整"));
        requirements.put("degraded", true);
        return requirements;
    }

    private String extractSubjectFallback(String query) {
        if (query.contains("六级") || query.contains("CET-6")) return "英语六级";
        if (query.contains("四级") || query.contains("CET-4")) return "英语四级";
        if (query.contains("雅思") || query.contains("IELTS")) return "雅思";
        if (query.contains("英语")) return "英语";
        if (query.contains("Python") || query.contains("python")) return "Python编程";
        if (query.contains("Java") || query.contains("java")) return "Java编程";
        if (query.contains("数学") || query.contains("概率论")) return "数学";
        if (query.contains("前端") || query.contains("Vue") || query.contains("React")) return "前端开发";
        if (query.contains("AI") || query.contains("人工智能") || query.contains("机器学习")) return "人工智能";
        return "综合学习";
    }

    private String extractLevelFallback(String query) {
        if (query.contains("零基础") || query.contains("入门") || query.contains("新手")) return "入门";
        if (query.contains("冲刺") || query.contains("拔高") || query.contains("高分") || query.contains("高级")) return "高级";
        if (query.contains("中级") || query.contains("进阶")) return "中级";
        return "基础";
    }

    private String extractGoalFallback(String query) {
        if (query.contains("复习") || query.contains("备考") || query.contains("考试")) return "备考复习";
        if (query.contains("入门") || query.contains("初学") || query.contains("新手")) return "学习入门";
        if (query.contains("练习") || query.contains("刷题") || query.contains("面试")) return "练习提升";
        return "综合提升";
    }

    private List<String> extractPreferencesFallback(String query) {
        List<String> preferences = new ArrayList<>();
        if (query.contains("视频")) preferences.add("视频");
        if (query.contains("文章") || query.contains("文档") || query.contains("阅读")) preferences.add("文章");
        if (query.contains("练习") || query.contains("刷题") || query.contains("题")) preferences.add("练习题");
        if (query.contains("思维导图") || query.contains("图表") || query.contains("图")) preferences.add("思维导图");
        return preferences;
    }

    // ==================== 工具方法 ====================

    @SuppressWarnings("unchecked")
    private Map<String, Object> jsonToMap(JsonNode node) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (node == null || !node.isObject()) return map;

        var fields = node.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            String key = field.getKey();
            JsonNode value = field.getValue();

            if (value.isTextual()) {
                map.put(key, value.asText());
            } else if (value.isInt() || value.isLong()) {
                map.put(key, value.asInt());
            } else if (value.isDouble() || value.isFloat()) {
                map.put(key, value.asDouble());
            } else if (value.isBoolean()) {
                map.put(key, value.asBoolean());
            } else if (value.isArray()) {
                List<String> list = new ArrayList<>();
                value.forEach(item -> list.add(item.asText()));
                map.put(key, list);
            } else if (value.isObject()) {
                map.put(key, jsonToMap(value));
            }
        }
        return map;
    }
}
