package com.eduagent.service;

import com.eduagent.agent.AgentContext;
import com.eduagent.agent.AgentResult;
import com.eduagent.agent.JsonParserUtil;
import com.eduagent.agent.SystemPrompts;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class PathPlanningAgent {

    private final AIService aiService;

    public PathPlanningAgent(AIService aiService) {
        this.aiService = aiService;
    }

    /**
     * LLM驱动的学习路径规划
     * 使用 AI 生成个性化分阶段学习路线，解析失败时降级到硬编码逻辑
     *
     * @param query              用户原始输入
     * @param parsedRequirements 统筹解析Agent的结构化需求
     * @param context            共享上下文（含用户画像、历史记录）
     * @return AgentResult，data 中存放 stages 列表
     */
    public AgentResult plan(String query, Map<String, Object> parsedRequirements, AgentContext context) {
        long startTime = System.currentTimeMillis();

        try {
            // 1. 提取需求字段
            String subject = parsedRequirements != null
                    ? (String) parsedRequirements.getOrDefault("subject", extractSubjectFallback(query))
                    : extractSubjectFallback(query);
            String level = parsedRequirements != null
                    ? (String) parsedRequirements.getOrDefault("level", determineLevelFallback(parsedRequirements))
                    : determineLevelFallback(parsedRequirements);
            String goal = parsedRequirements != null
                    ? (String) parsedRequirements.getOrDefault("goal", "掌握" + subject + "的核心知识与技能")
                    : "掌握" + subject + "的核心知识与技能";
            Object dailyTime = parsedRequirements != null
                    ? parsedRequirements.getOrDefault("dailyTime", 60)
                    : 60;
            Object totalDays = parsedRequirements != null
                    ? parsedRequirements.getOrDefault("totalDays", 30)
                    : 30;
            Object weakPoints = parsedRequirements != null
                    ? parsedRequirements.getOrDefault("weakPoints", new ArrayList<>())
                    : new ArrayList<>();

            // 2. 构建用户画像文本
            String profileSummary = context != null
                    ? context.buildProfileSummary()
                    : "暂无用户画像数据";

            // 3. 构建 LLM Prompt
            String userMessage = buildPathPlanningPrompt(subject, level, goal, dailyTime, totalDays, weakPoints, profileSummary);

            // 4. 调用 LLM
            String llmOutput = aiService.chatWithSystemPrompt(
                    SystemPrompts.PATH_PLANNING + buildFormatInstruction(),
                    userMessage
            );

            if (llmOutput == null || llmOutput.trim().isEmpty()) {
                log.warn("LLM返回空结果，使用降级方案");
                return planFallbackResult(query, parsedRequirements, subject, level, startTime);
            }

            // 5. 解析 JSON
            JsonNode rootNode = JsonParserUtil.parseJson(llmOutput);
            if (rootNode == null || rootNode.isNull() || (rootNode.isObject() && rootNode.isEmpty())) {
                log.warn("JSON解析结果为空，使用降级方案");
                return planFallbackResult(query, parsedRequirements, subject, level, startTime);
            }

            // 6. 提取 stages
            List<Map<String, Object>> stages = new ArrayList<>();
            if (rootNode.has("stages") && rootNode.get("stages").isArray()) {
                JsonNode stagesNode = rootNode.get("stages");
                for (JsonNode stageNode : stagesNode) {
                    Map<String, Object> stage = new HashMap<>();
                    stage.put("name", getJsonString(stageNode, "name", "未命名阶段"));
                    stage.put("goal", getJsonString(stageNode, "goal", ""));
                    stage.put("days", stageNode.has("days") ? stageNode.get("days").asInt() : 7);
                    stage.put("dailyMinutes", stageNode.has("dailyMinutes") ? stageNode.get("dailyMinutes").asInt() : 60);
                    stage.put("difficulty", getJsonString(stageNode, "difficulty", "基础"));
                    stage.put("modules", getJsonStringList(stageNode, "modules"));
                    stage.put("weakPointFocus", getJsonString(stageNode, "weakPointFocus", "无"));
                    stages.add(stage);
                }
            }

            if (stages.isEmpty()) {
                log.warn("LLM返回的stages为空，使用降级方案");
                return planFallbackResult(query, parsedRequirements, subject, level, startTime);
            }

            // 7. 提取辅助字段
            int totalDaysValue = rootNode.has("totalDays") ? rootNode.get("totalDays").asInt() : totalDaysAsInt(totalDays);
            String riskWarning = getJsonString(rootNode, "riskWarning", null);
            String alternativePlan = getJsonString(rootNode, "alternativePlan", null);

            // 8. 构建返回结果
            Map<String, Object> data = new HashMap<>();
            data.put("subject", subject);
            data.put("level", level);
            data.put("stages", stages);
            data.put("totalDays", totalDaysValue);
            data.put("riskWarning", riskWarning);
            data.put("alternativePlan", alternativePlan);

            String markdown = buildMarkdownDescription(subject, level, stages, totalDaysValue, riskWarning, alternativePlan);

            long duration = System.currentTimeMillis() - startTime;
            log.info("路径规划智能体(LLM)完成规划，生成{}个学习阶段，耗时{}ms", stages.size(), duration);
            return AgentResult.success("路径规划智能体", data, markdown, duration);

        } catch (Exception e) {
            log.error("路径规划智能体LLM调用失败，使用降级方案", e);
            return planFallbackDirect(query, parsedRequirements, startTime);
        }
    }

    /**
     * 【已弃用】旧版硬编码规划方法，保留向后兼容
     * @deprecated 请使用 plan(String, Map, AgentContext)
     */
    @Deprecated
    public Map<String, Object> plan(String query, Map<String, Object> parsedRequirements) {
        // 构建一个最小的 AgentContext 以适配新方法
        AgentContext context = AgentContext.builder()
                .query(query)
                .parsedRequirements(parsedRequirements)
                .build();
        AgentResult result = plan(query, parsedRequirements, context);

        // 转换回旧格式
        Map<String, Object> legacyResult = new HashMap<>();
        legacyResult.put("agent", "路径规划智能体");
        legacyResult.put("status", result.getStatus());
        if (result.getData() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.getData();
            legacyResult.putAll(data);
        }
        legacyResult.put("description", result.getMarkdownContent());
        return legacyResult;
    }

    // ==================== Prompt 构建 ====================

    private String buildPathPlanningPrompt(String subject, String level, String goal,
                                           Object dailyTime, Object totalDays, Object weakPoints,
                                           String profileSummary) {
        return String.format("""
                请根据以下信息，为该用户生成一个分阶段的学习路径规划：

                【学习科目】%s
                【用户水平】%s
                【学习目标】%s
                【每日学习时间】%s 分钟
                【总学习天数】%s 天
                【薄弱知识点】%s

                【用户画像】
                %s

                请严格按照JSON格式输出学习路径规划，包含合理的阶段划分、难度递进和薄弱环节重点突破。
                """,
                subject, level, goal,
                String.valueOf(dailyTime), String.valueOf(totalDays),
                weakPoints != null ? weakPoints.toString() : "无",
                profileSummary
        );
    }

    private String buildFormatInstruction() {
        return """

                ---
                【输出格式要求 - 必须严格遵守】
                你必须只输出以下JSON格式，不要输出任何其他内容（不要用markdown代码块包裹，不要加解释文字）：

                {
                  "stages": [
                    {
                      "name": "阶段名称",
                      "goal": "阶段目标",
                      "days": 天数整数,
                      "dailyMinutes": 每日分钟数整数,
                      "difficulty": "入门/基础/中级/高级",
                      "modules": ["模块1", "模块2"],
                      "weakPointFocus": "重点补弱的环节或null"
                    }
                  ],
                  "totalDays": 总天数整数,
                  "riskWarning": "风险提示或null",
                  "alternativePlan": "备选方案或null"
                }

                注意：
                1. 直接输出纯JSON，不要用 ```json 包裹
                2. 确保所有字符串用双引号
                3. stages至少包含2-4个阶段，难度由低到高递进
                4. 有薄弱知识点时，对应阶段必须标注 weakPointFocus
                5. totalDays 应等于所有 stages.days 之和
                """;
    }

    // ==================== 降级方案 ====================

    private AgentResult planFallbackResult(String query, Map<String, Object> parsedRequirements,
                                           String subject, String level, long startTime) {
        List<Map<String, Object>> stages = generateStagesFallback(subject, level);
        String description = generateDescriptionFallback(subject, level, stages);

        Map<String, Object> data = new HashMap<>();
        data.put("subject", subject);
        data.put("level", level);
        data.put("stages", stages);
        data.put("totalDays", calculateTotalDays(stages));

        long duration = System.currentTimeMillis() - startTime;
        return AgentResult.degraded("路径规划智能体", data, description, duration);
    }

    private AgentResult planFallbackDirect(String query, Map<String, Object> parsedRequirements, long startTime) {
        String subject = extractSubjectFallback(query);
        String level = determineLevelFallback(parsedRequirements);
        List<Map<String, Object>> stages = generateStagesFallback(subject, level);
        String description = generateDescriptionFallback(subject, level, stages);

        Map<String, Object> data = new HashMap<>();
        data.put("subject", subject);
        data.put("level", level);
        data.put("stages", stages);
        data.put("totalDays", calculateTotalDays(stages));

        long duration = System.currentTimeMillis() - startTime;
        return AgentResult.degraded("路径规划智能体", data, description, duration);
    }

    // ==================== 旧版硬编码逻辑（保留作为降级方案） ====================

    private String extractSubjectFallback(String query) {
        if (query.contains("六级") || query.contains("CET-6")) return "英语六级";
        if (query.contains("四级") || query.contains("CET-4")) return "英语四级";
        if (query.contains("雅思") || query.contains("IELTS")) return "雅思";
        if (query.contains("英语")) return "英语";
        if (query.contains("Python") || query.contains("python")) return "Python编程";
        if (query.contains("Java") || query.contains("java")) return "Java编程";
        if (query.contains("数学") || query.contains("概率论") || query.contains("高数")) return "数学";
        return "综合学习";
    }

    private String determineLevelFallback(Map<String, Object> requirements) {
        if (requirements == null) return "基础";

        String level = (String) requirements.getOrDefault("level", "基础");
        String experience = (String) requirements.getOrDefault("experience", "");

        if (experience.contains("零基础") || experience.contains("入门")) {
            return "入门";
        } else if (experience.contains("冲刺") || experience.contains("拔高")) {
            return "高级";
        }
        return level;
    }

    private List<Map<String, Object>> generateStagesFallback(String subject, String level) {
        List<Map<String, Object>> stages = new ArrayList<>();

        if (subject.contains("英语")) {
            if ("入门".equals(level)) {
                stages.add(createStageFallback("入门阶段", "掌握基础词汇和简单语法", 7, "入门", 30,
                        List.of("基础词汇", "简单语法", "日常对话"), "基础词汇积累"));
                stages.add(createStageFallback("基础阶段", "扩展词汇量，提升听说能力", 14, "基础", 45,
                        List.of("核心词汇", "听力入门", "简单阅读"), "听力理解"));
                stages.add(createStageFallback("进阶阶段", "强化各模块能力", 21, "中级", 60,
                        List.of("高频词汇", "听力进阶", "阅读理解"), "写作表达"));
            } else if ("高级".equals(level)) {
                stages.add(createStageFallback("冲刺阶段", "专项突破难点", 14, "中级", 90,
                        List.of("高频考点", "真题训练", "模拟测试"), "易错题型"));
                stages.add(createStageFallback("拔高阶段", "综合能力提升", 7, "高级", 120,
                        List.of("难点突破", "错题复盘", "全真模拟"), "时间管理"));
            } else {
                stages.add(createStageFallback("基础入门阶段", "掌握核心概念和基础词汇", 10, "入门", 45,
                        List.of("基础词汇", "基础语法", "简单听力"), "语法基础"));
                stages.add(createStageFallback("能力提升阶段", "系统提升各模块能力", 20, "基础", 60,
                        List.of("核心词汇", "听力训练", "阅读理解"), "长难句理解"));
                stages.add(createStageFallback("强化冲刺阶段", "综合训练和模拟考试", 15, "中级", 90,
                        List.of("真题练习", "模拟测试", "错题整理"), "考试技巧"));
            }
        } else if (subject.contains("编程")) {
            if ("入门".equals(level)) {
                stages.add(createStageFallback("环境搭建", "配置开发环境", 3, "入门", 60,
                        List.of("环境安装", "IDE配置", "基础操作"), "开发环境配置"));
                stages.add(createStageFallback("语法基础", "掌握基础语法", 10, "入门", 60,
                        List.of("变量类型", "控制流程", "函数定义"), "逻辑思维"));
                stages.add(createStageFallback("实战入门", "简单项目实践", 7, "基础", 90,
                        List.of("简单项目", "代码调试", "版本控制"), "调试能力"));
            } else {
                stages.add(createStageFallback("基础语法", "掌握核心语法和概念", 10, "基础", 60,
                        List.of("变量类型", "控制流程", "函数定义"), "面向对象"));
                stages.add(createStageFallback("进阶开发", "数据结构和框架", 20, "中级", 90,
                        List.of("数据结构", "常用框架", "数据库操作"), "算法思维"));
                stages.add(createStageFallback("项目实战", "完整项目开发", 15, "高级", 120,
                        List.of("项目设计", "代码优化", "部署上线"), "系统设计"));
            }
        } else {
            stages.add(createStageFallback("基础阶段", "掌握核心概念", 10, "入门", 45,
                    List.of("基础概念", "核心原理", "基础练习"), "概念理解"));
            stages.add(createStageFallback("进阶阶段", "深化理解和应用", 15, "基础", 60,
                    List.of("进阶知识", "综合应用", "专项练习"), "综合运用"));
            stages.add(createStageFallback("巩固阶段", "综合训练和测评", 10, "中级", 60,
                    List.of("综合练习", "模拟测试", "知识总结"), "查漏补缺"));
        }

        return stages;
    }

    private Map<String, Object> createStageFallback(String name, String goal, int days, String difficulty,
                                                     int dailyMinutes, List<String> modules, String weakPointFocus) {
        Map<String, Object> stage = new HashMap<>();
        stage.put("name", name);
        stage.put("goal", goal);
        stage.put("days", days);
        stage.put("dailyMinutes", dailyMinutes);
        stage.put("difficulty", difficulty);
        stage.put("modules", modules);
        stage.put("weakPointFocus", weakPointFocus);
        return stage;
    }

    private String generateDescriptionFallback(String subject, String level, List<Map<String, Object>> stages) {
        StringBuilder sb = new StringBuilder();
        sb.append("根据您的学习目标「").append(subject).append("」和水平「").append(level).append("」，为您规划了");
        sb.append(stages.size()).append("个学习阶段：\n\n");

        int totalDays = 0;
        for (int i = 0; i < stages.size(); i++) {
            Map<String, Object> stage = stages.get(i);
            totalDays += (int) stage.getOrDefault("days", 0);
            sb.append(i + 1).append(". ").append(stage.get("name"));
            sb.append("（").append(stage.get("days")).append("天，").append(stage.get("difficulty")).append("）\n");
            sb.append("   目标：").append(stage.get("goal")).append("\n");
        }

        sb.append("\n总计学习周期：").append(totalDays).append("天");
        return sb.toString();
    }

    // ==================== 辅助方法 ====================

    private int calculateTotalDays(List<Map<String, Object>> stages) {
        int total = 0;
        for (Map<String, Object> stage : stages) {
            total += (int) stage.getOrDefault("days", 0);
        }
        return total;
    }

    private String buildMarkdownDescription(String subject, String level, List<Map<String, Object>> stages,
                                            int totalDays, String riskWarning, String alternativePlan) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 学习路径规划\n\n");
        sb.append("**学科：**").append(subject).append(" | **水平：**").append(level).append("\n\n");

        sb.append("### 学习阶段\n\n");
        for (int i = 0; i < stages.size(); i++) {
            Map<String, Object> stage = stages.get(i);
            sb.append("**").append(i + 1).append(". ").append(stage.get("name")).append("**\n");
            sb.append("- 目标：").append(stage.get("goal")).append("\n");
            sb.append("- 时长：").append(stage.get("days")).append("天，每日").append(stage.get("dailyMinutes")).append("分钟\n");
            sb.append("- 难度：").append(stage.get("difficulty")).append("\n");
            sb.append("- 模块：").append(stage.get("modules")).append("\n");
            Object wpf = stage.get("weakPointFocus");
            if (wpf != null && !"null".equals(wpf) && !"无".equals(wpf)) {
                sb.append("- 弱项突破：").append(wpf).append("\n");
            }
            sb.append("\n");
        }

        sb.append("**总学习周期：").append(totalDays).append("天**\n\n");

        if (riskWarning != null && !riskWarning.isEmpty() && !"null".equals(riskWarning)) {
            sb.append("### 风险提示\n").append(riskWarning).append("\n\n");
        }
        if (alternativePlan != null && !alternativePlan.isEmpty() && !"null".equals(alternativePlan)) {
            sb.append("### 备选方案\n").append(alternativePlan).append("\n\n");
        }

        return sb.toString();
    }

    private String getJsonString(JsonNode node, String field, String defaultValue) {
        if (node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asText();
        }
        return defaultValue;
    }

    private List<String> getJsonStringList(JsonNode node, String field) {
        List<String> result = new ArrayList<>();
        if (node.has(field) && node.get(field).isArray()) {
            for (JsonNode item : node.get(field)) {
                if (!item.isNull()) {
                    result.add(item.asText());
                }
            }
        }
        return result;
    }

    private int totalDaysAsInt(Object totalDays) {
        if (totalDays instanceof Integer) return (Integer) totalDays;
        if (totalDays instanceof Number) return ((Number) totalDays).intValue();
        if (totalDays instanceof String) {
            try {
                return Integer.parseInt((String) totalDays);
            } catch (NumberFormatException e) {
                return 30;
            }
        }
        return 30;
    }
}
