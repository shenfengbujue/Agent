package com.eduagent.service;

import com.eduagent.agent.AgentContext;
import com.eduagent.agent.AgentResult;
import com.eduagent.agent.JsonParserUtil;
import com.eduagent.agent.SystemPrompts;
import com.eduagent.agent.WorkflowState;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class SummaryAgent {

    private final AIService aiService;

    public SummaryAgent(AIService aiService) {
        this.aiService = aiService;
    }

    /**
     * LLM驱动的格式化总结
     * 汇总全部智能体产出，使用 AI 生成结构化 Markdown 学习总结，
     * 解析失败时降级到 StringBuilder 硬编码逻辑
     *
     * @param query        用户原始输入
     * @param agentOutputs 所有上游Agent的输出（key=agentName）
     * @param context      共享上下文（含用户画像、历史记录）
     * @return AgentResult，markdownContent 中存放格式化的总结内容
     */
    public AgentResult summarize(String query, Map<String, AgentResult> agentOutputs, AgentContext context, WorkflowState state) {
        long startTime = System.currentTimeMillis();

        try {
            // 1. 构建包含所有Agent输出的 Prompt（含画像+对话历史）
            String profileSummary = context != null
                    ? context.buildProfileSummary()
                    : "暂无用户画像数据";
            String memoryContext = context != null
                    ? context.buildFullMemoryContext()
                    : "";

            String userMessage = buildSummaryPrompt(query, agentOutputs, profileSummary, memoryContext);

            // 2. 调用 LLM
            StringBuilder sb = new StringBuilder();
            String llmOutput = aiService.chatStream(SystemPrompts.SUMMARY + buildFormatInstruction(),
                    userMessage, token -> {
                        if (state != null) state.pushStreamToken("格式化总结智能体", token);
                        sb.append(token);
                    });
            if (llmOutput == null || llmOutput.isBlank()) llmOutput = sb.toString();

            if (llmOutput == null || llmOutput.trim().isEmpty()) {
                log.warn("LLM返回空结果，使用降级方案");
                return summarizeFallback(query, agentOutputs, context, startTime);
            }

            // 3. 解析 JSON
            JsonNode rootNode = JsonParserUtil.parseJson(llmOutput);
            if (rootNode == null || rootNode.isNull() || (rootNode.isObject() && rootNode.isEmpty())) {
                log.warn("JSON解析结果为空，使用降级方案");
                return summarizeFallback(query, agentOutputs, context, startTime);
            }

            // 4. 提取 markdown summary
            String markdownSummary = rootNode.has("summary")
                    ? rootNode.get("summary").asText()
                    : rootNode.has("markdownContent")
                            ? rootNode.get("markdownContent").asText()
                            : rootNode.toString();

            // 如果 LLM 返回的 summary 字段直接就是 markdown 字符串（非 JSON 嵌套），直接用
            if (markdownSummary == null || markdownSummary.trim().isEmpty()) {
                log.warn("LLM未返回有效的summary字段，使用降级方案");
                return summarizeFallback(query, agentOutputs, context, startTime);
            }

            // 5. 构建返回数据
            Map<String, Object> data = new HashMap<>();
            data.put("query", query);
            if (rootNode.has("sections")) {
                data.put("sections", rootNode.get("sections"));
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("总结智能体(LLM)完成汇总，耗时{}ms", duration);
            return AgentResult.success("格式化总结智能体", data, markdownSummary, duration);

        } catch (Exception e) {
            log.error("总结智能体LLM调用失败，使用降级方案", e);
            return summarizeFallbackDirect(query, agentOutputs, startTime);
        }
    }

    /**
     * 【已弃用】旧版硬编码总结方法，保留向后兼容
     * @deprecated 请使用 summarize(String, Map<String, AgentResult>, AgentContext)
     */
    @Deprecated
    public Map<String, Object> summarize(String query, Map<String, Object> knowledgeResult,
                                          Map<String, Object> webResult, Map<String, Object> graphResult,
                                          Map<String, Object> pathResult, Map<String, Object> exerciseResult) {
        // 转换旧格式 Map 到 AgentResult
        Map<String, AgentResult> agentOutputs = new HashMap<>();
        if (knowledgeResult != null) {
            agentOutputs.put("知识库检索智能体", AgentResult.builder()
                    .agentName("知识库检索智能体")
                    .status((String) knowledgeResult.getOrDefault("status", "success"))
                    .data(knowledgeResult)
                    .build());
        }
        if (webResult != null) {
            agentOutputs.put("网络搜索智能体", AgentResult.builder()
                    .agentName("网络搜索智能体")
                    .status((String) webResult.getOrDefault("status", "success"))
                    .data(webResult)
                    .build());
        }
        if (graphResult != null) {
            agentOutputs.put("图生成智能体", AgentResult.builder()
                    .agentName("图生成智能体")
                    .status((String) graphResult.getOrDefault("status", "success"))
                    .data(graphResult)
                    .build());
        }
        if (pathResult != null) {
            agentOutputs.put("路径规划智能体", AgentResult.builder()
                    .agentName("路径规划智能体")
                    .status((String) pathResult.getOrDefault("status", "success"))
                    .data(pathResult)
                    .build());
        }
        if (exerciseResult != null) {
            agentOutputs.put("练习题生成智能体", AgentResult.builder()
                    .agentName("练习题生成智能体")
                    .status((String) exerciseResult.getOrDefault("status", "success"))
                    .data(exerciseResult)
                    .build());
        }

        AgentContext context = AgentContext.builder().query(query).build();
        AgentResult result = summarize(query, agentOutputs, context, null);

        // 转换回旧格式
        Map<String, Object> legacyResult = new HashMap<>();
        legacyResult.put("agent", "格式化总结智能体");
        legacyResult.put("status", result.getStatus());
        legacyResult.put("summary", result.getMarkdownContent());
        return legacyResult;
    }

    // ==================== Prompt 构建 ====================

    private String buildSummaryPrompt(String query, Map<String, AgentResult> agentOutputs,
                                       String profileSummary, String memoryContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("请根据以下用户查询和各个智能体的输出结果，生成一份全面的学习总结报告。\n\n");

        sb.append("【用户查询】\n").append(query).append("\n\n");

        sb.append("【用户画像】\n").append(profileSummary).append("\n\n");

        if (memoryContext != null && !memoryContext.isEmpty()) {
            sb.append("【对话历史上下文】\n").append(memoryContext).append("\n\n");
        }

        sb.append("【各智能体输出】\n");
        if (agentOutputs != null) {
            for (Map.Entry<String, AgentResult> entry : agentOutputs.entrySet()) {
                String agentName = entry.getKey();
                AgentResult ar = entry.getValue();
                sb.append("--- ").append(agentName).append(" ---\n");
                sb.append("状态: ").append(ar.getStatus()).append("\n");
                if (ar.getMarkdownContent() != null && !ar.getMarkdownContent().isEmpty()) {
                    sb.append("内容:\n").append(ar.getMarkdownContent()).append("\n");
                } else if (ar.getData() != null) {
                    sb.append("数据:\n").append(ar.getData().toString()).append("\n");
                }
                if (ar.getErrorMessage() != null) {
                    sb.append("错误: ").append(ar.getErrorMessage()).append("\n");
                }
                sb.append("\n");
            }
        }

        sb.append("请综合以上所有信息，生成一份结构完整的学习总结。\n");
        return sb.toString();
    }

    private String buildFormatInstruction() {
        return """

                ---
                【输出格式要求 - 必须严格遵守】
                你必须只输出以下JSON格式，不要输出任何其他内容（不要用markdown代码块包裹，不要加解释文字）：

                {
                  "summary": "完整的Markdown格式的学习总结，包含以下章节：\\n\\n## 学习路径建议\\n...\\n\\n## 分模块知识\\n...\\n\\n## 拓展阅读推荐\\n...\\n\\n## 思维导图描述\\n...\\n\\n## 练习题概览\\n...\\n\\n## 整体学习建议\\n...",
                  "sections": {
                    "learningPath": "学习路径建议内容摘要",
                    "knowledgeModules": "分模块知识内容摘要",
                    "readingRecommendations": "拓展阅读推荐内容",
                    "mindMapDescription": "思维导图描述",
                    "exerciseOverview": "练习题概览",
                    "overallAdvice": "整体学习建议"
                  }
                }

                注意：
                1. 直接输出纯JSON，不要用 ```json 包裹
                2. summary 字段中是完整的Markdown格式文本，用 \\n 表示换行
                3. 如果某个智能体没有产出，在对应章节说明"该部分内容暂未生成"
                4. 确保JSON中字符串的双引号正确转义
                5. 总结应专业、结构化、易于阅读
                """;
    }

    // ==================== 降级方案 ====================

    private AgentResult summarizeFallback(String query, Map<String, AgentResult> agentOutputs,
                                           AgentContext context, long startTime) {
        // 将 AgentResult map 转换为旧格式 map 以复用 buildSummaryFallback
        Map<String, Object> knowledgeResult = extractLegacyData(agentOutputs, "知识库检索智能体");
        Map<String, Object> webResult = extractLegacyData(agentOutputs, "网络搜索智能体");
        Map<String, Object> graphResult = extractLegacyData(agentOutputs, "图生成智能体");
        Map<String, Object> pathResult = extractLegacyData(agentOutputs, "路径规划智能体");
        Map<String, Object> exerciseResult = extractLegacyData(agentOutputs, "练习题生成智能体");

        String summary = buildSummaryFallback(query, knowledgeResult, webResult, graphResult, pathResult, exerciseResult);

        Map<String, Object> data = new HashMap<>();
        data.put("query", query);

        long duration = System.currentTimeMillis() - startTime;
        return AgentResult.degraded("格式化总结智能体", data, summary, duration);
    }

    private AgentResult summarizeFallbackDirect(String query, Map<String, AgentResult> agentOutputs, long startTime) {
        Map<String, Object> knowledgeResult = extractLegacyData(agentOutputs, "知识库检索智能体");
        Map<String, Object> webResult = extractLegacyData(agentOutputs, "网络搜索智能体");
        Map<String, Object> graphResult = extractLegacyData(agentOutputs, "图生成智能体");
        Map<String, Object> pathResult = extractLegacyData(agentOutputs, "路径规划智能体");
        Map<String, Object> exerciseResult = extractLegacyData(agentOutputs, "练习题生成智能体");

        String summary = buildSummaryFallback(query, knowledgeResult, webResult, graphResult, pathResult, exerciseResult);

        long duration = System.currentTimeMillis() - startTime;
        return AgentResult.degraded("格式化总结智能体", new HashMap<>(), summary, duration);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractLegacyData(Map<String, AgentResult> agentOutputs, String agentName) {
        if (agentOutputs == null) return null;
        AgentResult ar = agentOutputs.get(agentName);
        if (ar == null) return null;
        if (ar.getData() instanceof Map) {
            return (Map<String, Object>) ar.getData();
        }
        // 如果 data 不是 Map，构造一个包装 Map
        Map<String, Object> wrapper = new HashMap<>();
        wrapper.put("status", ar.getStatus());
        wrapper.put("data", ar.getData());
        wrapper.put("markdownContent", ar.getMarkdownContent());
        return wrapper;
    }

    // ==================== 旧版硬编码逻辑（保留作为降级方案） ====================

    private String buildSummaryFallback(String query, Map<String, Object> knowledgeResult,
                                         Map<String, Object> webResult, Map<String, Object> graphResult,
                                         Map<String, Object> pathResult, Map<String, Object> exerciseResult) {
        StringBuilder summaryBuilder = new StringBuilder();

        summaryBuilder.append("## 学习计划总结\n\n");
        summaryBuilder.append("### 学习目标\n");
        summaryBuilder.append(query).append("\n\n");

        // 学习路径规划
        if (pathResult != null && "success".equals(pathResult.get("status"))) {
            summaryBuilder.append("### 学习路径规划\n");
            if (pathResult.containsKey("markdownContent") && pathResult.get("markdownContent") != null) {
                summaryBuilder.append(pathResult.get("markdownContent")).append("\n\n");
            } else if (pathResult.containsKey("description")) {
                summaryBuilder.append((String) pathResult.get("description")).append("\n\n");
            }

            Object stagesObj = pathResult.get("stages");
            if (stagesObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> stages = (List<Map<String, Object>>) stagesObj;
                if (!stages.isEmpty() && !pathResult.containsKey("markdownContent")) {
                    summaryBuilder.append("**详细阶段安排：**\n");
                    for (int i = 0; i < stages.size(); i++) {
                        Map<String, Object> stage = stages.get(i);
                        summaryBuilder.append(i + 1).append(". **").append(stage.get("name")).append("**\n");
                        summaryBuilder.append("   - 目标：").append(stage.get("goal")).append("\n");
                        summaryBuilder.append("   - 时长：").append(stage.get("days")).append("天\n");
                        summaryBuilder.append("   - 难度：").append(stage.get("difficulty")).append("\n");
                        summaryBuilder.append("   - 模块：").append(stage.get("modules")).append("\n\n");
                    }
                }
            }
        }

        int knowledgeCount = knowledgeResult != null ? (int) knowledgeResult.getOrDefault("count", 0) : 0;
        int webCount = webResult != null ? (int) webResult.getOrDefault("count", 0) : 0;

        // 知识库内容
        if (knowledgeCount > 0) {
            summaryBuilder.append("### 分模块知识\n");
            Object dataObj = knowledgeResult.get("data");
            if (dataObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> knowledgeItems = (List<Map<String, Object>>) dataObj;
                int index = 1;
                for (Map<String, Object> item : knowledgeItems) {
                    if (index > 3) break;
                    summaryBuilder.append(index).append(". **").append(item.get("title")).append("**\n");
                    summaryBuilder.append("   ").append(item.get("content")).append("\n\n");
                    index++;
                }
            }
        } else if (knowledgeResult != null && knowledgeResult.containsKey("markdownContent")) {
            summaryBuilder.append("### 分模块知识\n");
            summaryBuilder.append(knowledgeResult.get("markdownContent")).append("\n\n");
        }

        // 拓展阅读推荐（网络搜索结果）
        if (webCount > 0) {
            summaryBuilder.append("### 拓展阅读推荐\n");
            Object dataObj = webResult.get("data");
            if (dataObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> webItems = (List<Map<String, Object>>) dataObj;
                int index = 1;
                for (Map<String, Object> item : webItems) {
                    if (index > 3) break;
                    summaryBuilder.append(index).append(". **").append(item.get("title")).append("**\n");
                    summaryBuilder.append("   ").append(item.get("summary")).append("\n");
                    summaryBuilder.append("   来源: ").append(item.get("source")).append("\n\n");
                    index++;
                }
            }
        } else if (webResult != null && webResult.containsKey("markdownContent")) {
            summaryBuilder.append("### 拓展阅读推荐\n");
            summaryBuilder.append(webResult.get("markdownContent")).append("\n\n");
        }

        // 思维导图描述
        if (graphResult != null && "success".equals(graphResult.get("status"))) {
            summaryBuilder.append("### 思维导图描述\n");
            if (graphResult.containsKey("markdownContent") && graphResult.get("markdownContent") != null) {
                summaryBuilder.append(graphResult.get("markdownContent")).append("\n\n");
            } else {
                Object graphDataObj = graphResult.get("data");
                if (graphDataObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> graphData = (Map<String, Object>) graphDataObj;
                    Object nodesObj = graphData.get("nodes");
                    String graphDescription = (String) graphData.getOrDefault("description", "");

                    summaryBuilder.append(graphDescription).append("\n\n");

                    if (nodesObj instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> nodes = (List<Map<String, Object>>) nodesObj;
                        for (Map<String, Object> node : nodes) {
                            int level = (int) node.getOrDefault("level", 0);
                            String label = (String) node.get("label");
                            String description = (String) node.get("description");

                            if (level == 0) {
                                summaryBuilder.append("**").append(label).append("**\n");
                                summaryBuilder.append("   ").append(description).append("\n\n");
                            } else if (level == 1) {
                                summaryBuilder.append("**").append(label).append("**\n");
                                summaryBuilder.append("   ").append(description).append("\n");
                                summaryBuilder.append("   具体内容:\n");
                            } else if (level == 2) {
                                summaryBuilder.append("   - **").append(label).append("**: ").append(description).append("\n");
                            }
                        }
                        summaryBuilder.append("\n");
                    }
                }
            }
        }

        // 练习题概览
        if (exerciseResult != null && "success".equals(exerciseResult.get("status"))) {
            summaryBuilder.append("### 练习题概览\n");
            if (exerciseResult.containsKey("markdownContent") && exerciseResult.get("markdownContent") != null) {
                summaryBuilder.append(exerciseResult.get("markdownContent")).append("\n\n");
            } else {
                Object exercisesObj = exerciseResult.get("exercises");
                if (exercisesObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> exercises = (List<Map<String, Object>>) exercisesObj;
                    summaryBuilder.append("已为您生成 ").append(exercises.size()).append(" 道练习题\n\n");

                    for (int i = 0; i < Math.min(2, exercises.size()); i++) {
                        Map<String, Object> exercise = exercises.get(i);
                        summaryBuilder.append(i + 1).append(". **").append(exercise.get("module")).append("** (")
                                .append(exercise.get("type")).append(")\n");
                        summaryBuilder.append("   难度: ").append(exercise.get("difficulty")).append("\n");
                        summaryBuilder.append("   题目: ").append(exercise.get("question")).append("\n\n");
                    }
                }

                Object quizzesObj = exerciseResult.get("quizzes");
                if (quizzesObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> quizzes = (List<Map<String, Object>>) quizzesObj;
                    if (!quizzes.isEmpty()) {
                        summaryBuilder.append("### 综合测评\n");
                        for (int i = 0; i < quizzes.size(); i++) {
                            Map<String, Object> quiz = quizzes.get(i);
                            summaryBuilder.append(i + 1).append(". **").append(quiz.get("name")).append("**\n");
                            summaryBuilder.append("   难度: ").append(quiz.get("difficulty"))
                                    .append(" | 时长: ").append(quiz.get("duration"))
                                    .append("分钟 | 题数: ").append(quiz.get("questionCount")).append("题\n");
                        }
                        summaryBuilder.append("\n");
                    }
                }
            }
        }

        // 整体学习建议
        summaryBuilder.append("### 整体学习建议\n");
        if (query.contains("英语")) {
            summaryBuilder.append("1. **词汇积累**: 每天背诵20-30个单词，使用APP辅助记忆\n");
            summaryBuilder.append("2. **听力训练**: 每天听30分钟英语新闻或播客\n");
            summaryBuilder.append("3. **口语练习**: 尝试跟读模仿，找语伴练习\n");
            summaryBuilder.append("4. **阅读提升**: 从简单的英文文章开始，逐步提高难度\n");
            summaryBuilder.append("5. **写作练习**: 每周写一篇英文短文并寻求反馈\n");
        } else if (query.contains("编程") || query.contains("Python")) {
            summaryBuilder.append("1. **基础语法**: 掌握变量、控制流程、函数等基本概念\n");
            summaryBuilder.append("2. **数据结构**: 学习数组、链表、树等常用数据结构\n");
            summaryBuilder.append("3. **算法练习**: 每周做3-5道算法题\n");
            summaryBuilder.append("4. **框架学习**: 根据方向选择合适的框架深入学习\n");
            summaryBuilder.append("5. **项目实战**: 动手做一个完整的小项目\n");
        } else {
            summaryBuilder.append("1. **制定计划**: 明确学习目标和时间表\n");
            summaryBuilder.append("2. **选择方法**: 使用适合自己的学习方法\n");
            summaryBuilder.append("3. **收集资源**: 寻找优质的学习资源\n");
            summaryBuilder.append("4. **坚持练习**: 保持每天学习的习惯\n");
            summaryBuilder.append("5. **定期复习**: 巩固已学知识\n");
        }

        summaryBuilder.append("\n---\n");
        summaryBuilder.append("*以上内容由多智能体协作系统生成*");

        return summaryBuilder.toString();
    }
}
