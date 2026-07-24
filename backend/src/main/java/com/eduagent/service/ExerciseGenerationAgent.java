package com.eduagent.service;

import com.eduagent.agent.*;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 练习题生成智能体
 * 负责基于学习模块和用户薄弱点，通过LLM动态生成练习题与综合测评
 */
@Slf4j
@Service
public class ExerciseGenerationAgent {

    private final AIService aiService;

    public ExerciseGenerationAgent(AIService aiService) {
        this.aiService = aiService;
    }

    // ==================== 新方法：LLM驱动的练习题生成 ====================

    /**
     * 使用LLM动态生成练习题
     * 1. 构建包含学习模块、薄弱点、知识内容的Prompt
     * 2. 调用LLM生成结构化练习题JSON
     * 3. 解析JSON并组装AgentResult
     * 4. 失败时降级为硬编码练习题
     *
     * @param query   用户原始查询
     * @param modules 学习模块名称列表
     * @param context 共享上下文（含需求解析、前序Agent输出）
     * @return AgentResult（data=exercises+quizzes，markdownContent=格式化习题文本）
     */
    public AgentResult generate(String query, List<String> modules, AgentContext context) {
        long startTime = System.currentTimeMillis();

        try {
            // Step 1: 构建LLM提示词
            String prompt = buildExercisePrompt(query, modules, context);

            // Step 2: 调用LLM
            String llmOutput = null;
            boolean llmFailed = false;
            try {
                llmOutput = aiService.chatWithSystemPrompt(
                        SystemPrompts.EXERCISE_GENERATION, prompt);
                log.info("LLM练习题生成响应长度: {} chars",
                        llmOutput != null ? llmOutput.length() : 0);
            } catch (Exception e) {
                log.warn("LLM练习题生成调用失败，将使用降级方案: {}", e.getMessage());
                llmFailed = true;
            }

            // Step 3: 解析LLM输出
            if (!llmFailed && llmOutput != null && !llmOutput.isBlank()) {
                try {
                    JsonNode jsonNode = JsonParserUtil.parseJson(llmOutput);
                    List<Map<String, Object>> exercises = extractExercisesList(jsonNode);
                    List<Map<String, Object>> quizzes = extractQuizzesList(jsonNode);
                    Map<String, Object> difficultyRatio = extractDifficultyRatio(jsonNode);

                    if (!exercises.isEmpty()) {
                        // 数量不够？追加生成
                        if (exercises.size() < 15) {
                            log.info("练习题仅{}道，不足15道，追加生成...", exercises.size());
                            List<Map<String, Object>> moreExercises = generateMoreExercises(
                                    query, modules, exercises, context);
                            exercises.addAll(moreExercises);
                            log.info("追加后共 {} 道题", exercises.size());
                        }

                        String markdown = formatExercisesMarkdown(exercises, quizzes, difficultyRatio);
                        long duration = System.currentTimeMillis() - startTime;

                        Map<String, Object> resultData = new LinkedHashMap<>();
                        resultData.put("exercises", exercises);
                        resultData.put("quizzes", quizzes);
                        resultData.put("difficultyRatio", difficultyRatio);

                        log.info("LLM练习题生成成功，共 {} 道题，{} 套测评，耗时 {}ms",
                                exercises.size(), quizzes.size(), duration);
                        return AgentResult.success("练习题生成智能体",
                                resultData, markdown, duration);
                    } else {
                        log.warn("LLM输出的练习题列表为空，降级处理");
                    }
                } catch (Exception e) {
                    log.warn("LLM练习题JSON解析失败，降级处理: {}", e.getMessage());
                }
            }

            // Step 4: 降级方案 — 使用原有硬编码逻辑
            String subject = extractSubjectFallback(query);
            List<Map<String, Object>> fallbackExercises = generateExercisesFallback(subject, modules);
            List<Map<String, Object>> fallbackQuizzes = generateQuizzesFallback(subject);
            Map<String, Object> fallbackRatio = Map.of("基础", 0.6, "拔高", 0.3, "拓展", 0.1);

            long duration = System.currentTimeMillis() - startTime;

            Map<String, Object> fallbackData = new LinkedHashMap<>();
            fallbackData.put("exercises", fallbackExercises);
            fallbackData.put("quizzes", fallbackQuizzes);
            fallbackData.put("difficultyRatio", fallbackRatio);

            String fallbackMarkdown = formatExercisesMarkdown(
                    fallbackExercises, fallbackQuizzes, fallbackRatio);

            log.info("练习题生成降级完成，共 {} 道题，{} 套测评，耗时 {}ms",
                    fallbackExercises.size(), fallbackQuizzes.size(), duration);
            return AgentResult.degraded("练习题生成智能体",
                    fallbackData, fallbackMarkdown, duration);

        } catch (Exception e) {
            log.error("练习题生成智能体执行失败", e);
            long duration = System.currentTimeMillis() - startTime;
            return AgentResult.error("练习题生成智能体", e.getMessage(), duration);
        }
    }

    // ==================== 旧方法（向后兼容，已废弃） ====================

    /**
     * @deprecated 请使用 {@link #generate(String, List, AgentContext)}
     *             获得LLM动态生成的练习题
     */
    @Deprecated
    public Map<String, Object> generate(String query, List<String> modules) {
        Map<String, Object> result = new HashMap<>();

        try {
            String subject = extractSubjectFallback(query);

            List<Map<String, Object>> exercises = generateExercisesFallback(subject, modules);
            List<Map<String, Object>> quizzes = generateQuizzesFallback(subject);

            result.put("agent", "练习题生成智能体");
            result.put("status", "success");
            result.put("exercises", exercises);
            result.put("quizzes", quizzes);
            result.put("description", "已为您生成" + exercises.size() + "道练习题和" + quizzes.size() + "套综合测评");

            log.info("练习题生成智能体完成，生成{}道练习题，{}套测评", exercises.size(), quizzes.size());

        } catch (Exception e) {
            log.error("练习题生成智能体执行失败", e);
            result.put("agent", "练习题生成智能体");
            result.put("status", "error");
            result.put("exercises", new ArrayList<>());
            result.put("quizzes", new ArrayList<>());
        }

        return result;
    }

    // ==================== Prompt构建 ====================

    /**
     * 构建发送给LLM的完整用户消息
     */
    @SuppressWarnings("unchecked")
    /**
     * 题目数量不足时追加生成
     */
    private List<Map<String, Object>> generateMoreExercises(String query, List<String> modules,
                                                            List<Map<String, Object>> existing,
                                                            AgentContext context) {
        try {
            StringBuilder prompt = new StringBuilder();
            prompt.append("你之前已经生成了以下").append(existing.size()).append("道题，但数量不够：\n");
            for (int i = 0; i < existing.size(); i++) {
                prompt.append(i + 1).append(". ").append(existing.get(i).get("question")).append("\n");
            }
            prompt.append("\n请再生成至少").append(15 - existing.size()).append("道不同知识点的练习题，");
            prompt.append("不要与上面已生成的题目重复。题型要多样化。\n");

            // 也包含模块信息
            prompt.append("学习模块: ").append(String.join(", ", modules)).append("\n");

            String llmOutput = aiService.chatWithSystemPrompt(
                    SystemPrompts.EXERCISE_GENERATION, prompt.toString());
            if (llmOutput != null && !llmOutput.isBlank()) {
                JsonNode jsonNode = JsonParserUtil.parseJson(llmOutput);
                List<Map<String, Object>> more = extractExercisesList(jsonNode);
                log.info("追加生成 {} 道题", more.size());
                return more;
            }
        } catch (Exception e) {
            log.warn("追加生成练习题失败: {}", e.getMessage());
        }
        return List.of();
    }

    private String buildExercisePrompt(String query, List<String> modules, AgentContext context) {
        StringBuilder sb = new StringBuilder();

        // 1. 学习模块列表
        sb.append("## 学习模块列表\n");
        if (modules != null && !modules.isEmpty()) {
            for (int i = 0; i < modules.size(); i++) {
                sb.append(i + 1).append(". ").append(modules.get(i)).append("\n");
            }
        } else {
            sb.append("暂无明确的学习模块\n");
        }
        sb.append("\n");

        // 2. 用户薄弱点
        sb.append("## 用户薄弱点\n");
        boolean hasWeakPoints = false;
        if (context != null && context.getParsedRequirements() != null) {
            Map<String, Object> reqs = context.getParsedRequirements();
            Object weakPoints = reqs.get("weakPoints");
            if (weakPoints == null) {
                weakPoints = reqs.get("薄弱点");
            }
            if (weakPoints == null) {
                weakPoints = reqs.get("weaknesses");
            }
            if (weakPoints instanceof List) {
                List<?> wpList = (List<?>) weakPoints;
                if (!wpList.isEmpty()) {
                    for (Object wp : wpList) {
                        sb.append("- ").append(wp).append("\n");
                    }
                    hasWeakPoints = true;
                }
            } else if (weakPoints instanceof String && !((String) weakPoints).isBlank()) {
                sb.append("- ").append(weakPoints).append("\n");
                hasWeakPoints = true;
            }
        }
        if (!hasWeakPoints) {
            sb.append("用户未提供明确的薄弱点信息\n");
        }
        sb.append("\n");

        // 3. 知识内容（从上下文中获取知识库智能体的输出）
        sb.append("## 知识内容参考\n");
        boolean hasKnowledge = false;
        if (context != null && context.getPreviousOutputs() != null) {
            AgentResult knowledgeResult = context.getPreviousOutputs().get("知识库检索智能体");
            if (knowledgeResult != null && knowledgeResult.getData() != null) {
                Object knowledgeData = knowledgeResult.getData();
                if (knowledgeData instanceof List) {
                    List<?> kList = (List<?>) knowledgeData;
                    for (int i = 0; i < Math.min(kList.size(), 5); i++) {
                        Object item = kList.get(i);
                        if (item instanceof Map) {
                            Map<String, Object> kMap = (Map<String, Object>) item;
                            sb.append("### ").append(kMap.getOrDefault("name", "模块")).append("\n");
                            Object bk = kMap.get("basicKnowledge");
                            if (bk != null && !bk.toString().isBlank()) {
                                sb.append(bk.toString()).append("\n");
                            }
                            sb.append("\n");
                            hasKnowledge = true;
                        }
                    }
                }
            }
        }
        if (!hasKnowledge) {
            sb.append("暂无知识库内容，请基于你的知识出题\n");
        }
        sb.append("\n");

        // 4. 用户画像
        sb.append("## 用户画像\n");
        if (context != null) {
            sb.append(context.buildProfileSummary()).append("\n");
        } else {
            sb.append("暂无用户画像数据\n");
        }
        sb.append("\n");

        // 5. 出题数量要求（非常重要！）
        sb.append("## ⚠️ 出题数量硬性要求 — 必须严格遵守！\n");
        sb.append("你必须生成 **至少15道** 练习题！绝对不能少于15道！\n");
        sb.append("- 基础题: 至少8道（选择题+填空题混合，覆盖基础概念）\n");
        sb.append("- 拔高题: 至少5道（简答题/编程题/综合应用题）\n");
        sb.append("- 拓展题: 至少2道（多模块交叉题/实际场景题）\n");
        sb.append("- 每个学习模块至少覆盖2道题\n");
        sb.append("- 薄弱模块额外增加至少3道专项题\n");
        sb.append("- 题型必须多样化：至少包含3种不同题型（选择题、填空题、简答题、编程题、翻译题）\n");
        sb.append("- 如果你生成的题目少于15道，你的回答将被视为不完整！\n\n");

        // 6. 输出格式指令
        sb.append(buildFormatInstruction());

        return sb.toString();
    }

    /**
     * 构建JSON输出格式指令
     */
    private String buildFormatInstruction() {
        return """

                ---
                【输出格式要求 - 必须严格遵守】
                你必须只输出以下JSON格式，不要输出任何其他内容（不要用markdown代码块包裹，不要加解释文字）：

                {
                  "exercises": [
                    {
                      "module": "所属模块",
                      "type": "选择题/填空题/简答题/编程题",
                      "difficulty": "基础/中级/高级",
                      "question": "题目内容",
                      "options": ["A选项", "B选项", "C选项", "D选项"],
                      "answer": "正确答案",
                      "analysis": "详细解析",
                      "commonMistake": "常见错误",
                      "knowledgePoint": "对应知识点"
                    }
                  ],
                  "quizzes": [
                    {
                      "name": "测评名",
                      "difficulty": "难度",
                      "duration": 30,
                      "questionCount": 10,
                      "type": "综合测评"
                    }
                  ],
                  "difficultyRatio": {
                    "基础": 0.6,
                    "拔高": 0.3,
                    "拓展": 0.1
                  }
                }

                注意:
                1. 直接输出纯JSON，不要用 ```json 包裹
                2. 确保所有字符串用双引号
                3. 数组和对象的最后一个元素后面不要加逗号
                4. duration为整数（分钟），questionCount为整数
                5. 每个exercise必须包含所有字段：module, type, difficulty, question, options, answer, analysis, commonMistake, knowledgePoint
                6. 选择题的options必须是一个字符串数组，简答题/编程题的options为空数组[]
                7. difficultyRatio的三个值加起来必须等于1.0
                """;
    }

    // ==================== JSON解析与Markdown格式化 ====================

    /**
     * 从JsonNode中提取练习题列表
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractExercisesList(JsonNode jsonNode) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            JsonNode exercisesNode = jsonNode.get("exercises");
            if (exercisesNode != null && exercisesNode.isArray()) {
                for (JsonNode exNode : exercisesNode) {
                    Map<String, Object> exercise = new LinkedHashMap<>();
                    exercise.put("module", getTextValue(exNode, "module"));
                    exercise.put("type", getTextValue(exNode, "type"));
                    exercise.put("difficulty", getTextValue(exNode, "difficulty"));
                    exercise.put("question", getTextValue(exNode, "question"));
                    exercise.put("options", getStringList(exNode, "options"));
                    exercise.put("answer", getTextValue(exNode, "answer"));
                    exercise.put("analysis", getTextValue(exNode, "analysis"));
                    exercise.put("commonMistake", getTextValue(exNode, "commonMistake"));
                    exercise.put("knowledgePoint", getTextValue(exNode, "knowledgePoint"));
                    result.add(exercise);
                }
            }
        } catch (Exception e) {
            log.warn("提取练习题列表失败: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 从JsonNode中提取测评列表
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractQuizzesList(JsonNode jsonNode) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            JsonNode quizzesNode = jsonNode.get("quizzes");
            if (quizzesNode != null && quizzesNode.isArray()) {
                for (JsonNode qzNode : quizzesNode) {
                    Map<String, Object> quiz = new LinkedHashMap<>();
                    quiz.put("name", getTextValue(qzNode, "name"));
                    quiz.put("difficulty", getTextValue(qzNode, "difficulty"));
                    quiz.put("duration", qzNode.has("duration") ? qzNode.get("duration").asInt() : 30);
                    quiz.put("questionCount", qzNode.has("questionCount") ? qzNode.get("questionCount").asInt() : 10);
                    quiz.put("type", getTextValue(qzNode, "type"));
                    result.add(quiz);
                }
            }
        } catch (Exception e) {
            log.warn("提取测评列表失败: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 从JsonNode中提取难度比例
     */
    private Map<String, Object> extractDifficultyRatio(JsonNode jsonNode) {
        Map<String, Object> ratio = new LinkedHashMap<>();
        ratio.put("基础", 0.6);
        ratio.put("拔高", 0.3);
        ratio.put("拓展", 0.1);

        try {
            JsonNode ratioNode = jsonNode.get("difficultyRatio");
            if (ratioNode != null && ratioNode.isObject()) {
                if (ratioNode.has("基础")) {
                    ratio.put("基础", ratioNode.get("基础").asDouble());
                }
                if (ratioNode.has("拔高")) {
                    ratio.put("拔高", ratioNode.get("拔高").asDouble());
                }
                if (ratioNode.has("拓展")) {
                    ratio.put("拓展", ratioNode.get("拓展").asDouble());
                }
            }
        } catch (Exception e) {
            log.warn("提取难度比例失败，使用默认值: {}", e.getMessage());
        }
        return ratio;
    }

    /**
     * 将练习题列表格式化为前端可展示的Markdown
     */
    private String formatExercisesMarkdown(List<Map<String, Object>> exercises,
                                           List<Map<String, Object>> quizzes,
                                           Map<String, Object> difficultyRatio) {
        StringBuilder md = new StringBuilder();
        md.append("# 📝 练习题与测评\n\n");

        // 难度分布概览
        if (difficultyRatio != null && !difficultyRatio.isEmpty()) {
            md.append("## 📊 难度分布\n\n");
            md.append("| 难度 | 占比 |\n");
            md.append("|------|------|\n");
            difficultyRatio.forEach((k, v) -> {
                double pct = v instanceof Number ? ((Number) v).doubleValue() * 100 : 0;
                md.append("| ").append(k).append(" | ")
                        .append(String.format("%.0f%%", pct)).append(" |\n");
            });
            md.append("\n");
        }

        // 按模块分组显示练习题
        Map<String, List<Map<String, Object>>> grouped =
                exercises.stream().collect(Collectors.groupingBy(
                        e -> String.valueOf(e.getOrDefault("module", "未分类")),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        int totalCount = 0;
        for (Map.Entry<String, List<Map<String, Object>>> entry : grouped.entrySet()) {
            md.append("## 📚 ").append(entry.getKey()).append("\n\n");

            List<Map<String, Object>> moduleExercises = entry.getValue();
            for (int i = 0; i < moduleExercises.size(); i++) {
                totalCount++;
                Map<String, Object> ex = moduleExercises.get(i);
                String type = String.valueOf(ex.getOrDefault("type", "未知"));
                String difficulty = String.valueOf(ex.getOrDefault("difficulty", "基础"));
                String question = String.valueOf(ex.getOrDefault("question", ""));
                String answer = String.valueOf(ex.getOrDefault("answer", ""));
                String analysis = String.valueOf(ex.getOrDefault("analysis", ""));
                String commonMistake = String.valueOf(ex.getOrDefault("commonMistake", ""));
                String knowledgePoint = String.valueOf(ex.getOrDefault("knowledgePoint", ""));

                md.append("### 题").append(totalCount).append(" / ")
                        .append(getTypeEmoji(type)).append(" ").append(type)
                        .append(" / ").append(getDifficultyTag(difficulty)).append("\n\n");

                md.append("**题目：** ").append(question).append("\n\n");

                // 选择题显示选项
                @SuppressWarnings("unchecked")
                Object optionsObj = ex.get("options");
                if (optionsObj instanceof List) {
                    List<?> options = (List<?>) optionsObj;
                    if (!options.isEmpty()) {
                        md.append("**选项：**\n");
                        char label = 'A';
                        for (Object opt : options) {
                            md.append("- ").append(label).append(". ").append(opt).append("\n");
                            label++;
                        }
                        md.append("\n");
                    }
                }

                md.append("**正确答案：** ").append(answer).append("\n\n");

                if (!analysis.isEmpty()) {
                    md.append("<details>\n<summary>📖 解析</summary>\n\n");
                    md.append(analysis).append("\n\n");
                    md.append("</details>\n\n");
                }

                if (!commonMistake.isEmpty() && !"null".equals(commonMistake)) {
                    md.append("> ⚠️ **常见错误：** ").append(commonMistake).append("\n\n");
                }

                md.append("---\n\n");
            }
        }

        // 综合测评
        if (quizzes != null && !quizzes.isEmpty()) {
            md.append("## 📋 综合测评\n\n");
            md.append("| 测评名称 | 难度 | 时长(分钟) | 题目数 | 类型 |\n");
            md.append("|---------|------|-----------|--------|------|\n");
            for (Map<String, Object> quiz : quizzes) {
                md.append("| ").append(quiz.getOrDefault("name", "未命名")).append(" | ")
                        .append(quiz.getOrDefault("difficulty", "基础")).append(" | ")
                        .append(quiz.getOrDefault("duration", 30)).append(" | ")
                        .append(quiz.getOrDefault("questionCount", 10)).append(" | ")
                        .append(quiz.getOrDefault("type", "综合测评")).append(" |\n");
            }
            md.append("\n");
        }

        md.append("> 共生成 **").append(totalCount).append("** 道练习题");

        if (quizzes != null) {
            md.append("，**").append(quizzes.size()).append("** 套综合测评");
        }
        md.append("\n");

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

    private String getTypeEmoji(String type) {
        return switch (type) {
            case "选择题" -> "🔤";
            case "填空题" -> "✏️";
            case "简答题" -> "📝";
            case "编程题" -> "💻";
            default -> "❓";
        };
    }

    private String getDifficultyTag(String difficulty) {
        return switch (difficulty) {
            case "入门" -> "🟢 入门";
            case "基础" -> "🟢 基础";
            case "中级" -> "🟡 中级";
            case "高级" -> "🔴 高级";
            default -> "⚪ " + difficulty;
        };
    }

    // ==================== 降级方案：硬编码练习题（从原generateExercises/generateQuizzes重命名） ====================

    private String extractSubjectFallback(String query) {
        if (query.contains("六级") || query.contains("四级") || query.contains("英语")) return "英语";
        if (query.contains("Python") || query.contains("编程")) return "编程";
        return "综合";
    }

    private List<Map<String, Object>> generateExercisesFallback(String subject, List<String> modules) {
        List<Map<String, Object>> exercises = new ArrayList<>();

        if ("英语".equals(subject)) {
            exercises.add(createExerciseFallback("词汇题", "选择题", "基础",
                    "The professor's lecture was so ______ that many students fell asleep.",
                    List.of("boring", "bored", "interesting", "interested"),
                    "boring", "boring表示令人厌烦的，用来形容事物；bored表示感到厌烦的。",
                    "boring与bored的辨析", "词汇辨析"));
            exercises.add(createExerciseFallback("词汇题", "填空题", "基础",
                    "She is very ______ (interest) in learning foreign languages.",
                    List.of(), "interested",
                    "be interested in 是固定搭配，表示对...感兴趣。",
                    "interesting与interested混淆", "形容词辨析"));
            exercises.add(createExerciseFallback("语法题", "选择题", "基础",
                    "By the time you arrive, we ______ the meeting.",
                    List.of("will finish", "will have finished", "have finished", "finished"),
                    "will have finished", "by the time引导时间状语从句，主句用将来完成时。",
                    "时态选择错误", "将来完成时"));
            exercises.add(createExerciseFallback("语法题", "填空题", "基础",
                    "If I ______ (be) you, I would study harder.",
                    List.of(), "were",
                    "虚拟语气中，与现在事实相反，be动词用were。",
                    "虚拟语气形式错误", "虚拟语气"));
            exercises.add(createExerciseFallback("阅读理解", "简答题", "中级",
                    "根据文章内容，作者认为学习英语最重要的是什么？",
                    List.of(), "坚持练习和日常积累",
                    "文章强调了持续学习的重要性。",
                    "片面理解，忽略主旨", "阅读理解"));
            exercises.add(createExerciseFallback("翻译题", "翻译题", "中级",
                    "将下列句子翻译成英文：学习英语需要耐心和坚持。",
                    List.of(), "Learning English requires patience and persistence.",
                    "需要可以翻译为require，耐心是patience，坚持是persistence。",
                    "中式英语表达", "英汉互译"));
            exercises.add(createExerciseFallback("翻译题", "翻译题", "中级",
                    "将下列句子翻译成中文：Practice makes perfect.",
                    List.of(), "熟能生巧。",
                    "英语谚语的中文对应表达。",
                    "过度直译", "英汉互译"));
            exercises.add(createExerciseFallback("写作", "简答题", "高级",
                    "请用英语写一段话（50词以上），描述你的学习计划。",
                    List.of(), "I plan to study English for one hour every day...",
                    "写作需要包含学习目标、时间安排和具体方法。",
                    "内容空洞，缺乏细节", "英语写作"));
        } else if ("编程".equals(subject)) {
            exercises.add(createExerciseFallback("基础语法", "编程题", "基础",
                    "编写一个Python函数，计算两个数的和",
                    List.of(), "def add(a, b):\n    return a + b",
                    "定义函数add，接受两个参数a和b，返回它们的和。",
                    "忘记return语句", "函数定义"));
            exercises.add(createExerciseFallback("基础语法", "选择题", "基础",
                    "Python中用于定义函数的关键字是？",
                    List.of("function", "def", "func", "define"),
                    "def", "Python使用def关键字定义函数。",
                    "与其他语言混淆", "Python语法"));
            exercises.add(createExerciseFallback("数据结构", "选择题", "中级",
                    "以下哪种数据结构适合实现先进先出？",
                    List.of("栈(Stack)", "队列(Queue)", "链表(LinkedList)", "树(Tree)"),
                    "队列(Queue)", "队列是一种先进先出(FIFO)的数据结构。",
                    "栈和队列混淆", "数据结构"));
            exercises.add(createExerciseFallback("数据结构", "填空题", "基础",
                    "Python中，list的append()方法将元素添加到列表的______。",
                    List.of(), "末尾",
                    "append()在列表末尾添加元素，insert()在指定位置添加。",
                    "append与insert混淆", "列表操作"));
            exercises.add(createExerciseFallback("算法", "编程题", "高级",
                    "实现快速排序算法",
                    List.of(), "def quicksort(arr):\n    if len(arr) <= 1:\n        return arr\n    pivot = arr[len(arr) // 2]\n    left = [x for x in arr if x < pivot]\n    middle = [x for x in arr if x == pivot]\n    right = [x for x in arr if x > pivot]\n    return quicksort(left) + middle + quicksort(right)",
                    "快速排序采用分治策略，选择基准元素，递归排序。",
                    "递归终止条件遗漏", "排序算法"));
            exercises.add(createExerciseFallback("算法", "编程题", "中级",
                    "编写一个Python函数，判断一个字符串是否是回文。",
                    List.of(), "def is_palindrome(s):\n    s = s.lower()\n    return s == s[::-1]",
                    "回文字符串正读反读相同，使用切片反转进行比较。",
                    "忽略大小写", "字符串处理"));
            exercises.add(createExerciseFallback("综合", "简答题", "高级",
                    "解释面向对象编程中的封装、继承和多态，并各举一个例子。",
                    List.of(), "封装：将数据和操作封装在类中...",
                    "封装修饰访问权限，继承复用代码，多态实现接口统一。",
                    "概念混淆", "面向对象"));
        } else {
            exercises.add(createExerciseFallback("基础概念", "选择题", "入门",
                    "以下哪个是有效的学习方法？",
                    List.of("死记硬背", "理解记忆", "不复习", "拖延"),
                    "理解记忆", "理解记忆通过理解知识的内在联系来记忆，更持久有效。",
                    "误认为死记硬背有效", "学习方法论"));
            exercises.add(createExerciseFallback("基础概念", "选择题", "入门",
                    "艾宾浩斯遗忘曲线说明复习的最佳时机是？",
                    List.of("学习后立即复习", "学习后24小时内复习", "学习后一周复习", "考试前复习"),
                    "学习后24小时内复习", "遗忘曲线显示在学习后24小时内遗忘最快，应及时复习。",
                    "拖延复习", "记忆规律"));
            exercises.add(createExerciseFallback("时间管理", "简答题", "基础",
                    "简述番茄工作法的基本原理",
                    List.of(), "番茄工作法将工作时间划分为25分钟的工作块，每完成一个休息5分钟，每4个后休息20分钟。",
                    "番茄工作法通过将任务分解为小的时间块，提高专注力。",
                    "时间块划分不合理", "时间管理"));
            exercises.add(createExerciseFallback("时间管理", "填空题", "基础",
                    "GTD时间管理法的GTD全称是______。",
                    List.of(), "Getting Things Done",
                    "GTD由David Allen提出，核心理念是将任务从大脑中清空到外部系统。",
                    "理解偏差", "时间管理"));
            exercises.add(createExerciseFallback("学习方法", "选择题", "中级",
                    "费曼学习法的核心是什么？",
                    List.of("反复阅读", "以教代学", "大量刷题", "死记硬背"),
                    "以教代学", "费曼学习法要求用简单语言解释复杂概念，通过教学来检验理解。",
                    "误认为等同于复述", "学习方法"));
        }

        return exercises;
    }

    private List<Map<String, Object>> generateQuizzesFallback(String subject) {
        List<Map<String, Object>> quizzes = new ArrayList<>();

        Map<String, Object> quiz1 = new HashMap<>();
        quiz1.put("name", subject + "基础测试");
        quiz1.put("difficulty", "基础");
        quiz1.put("duration", 30);
        quiz1.put("questionCount", 10);
        quiz1.put("type", "综合测评");
        quizzes.add(quiz1);

        Map<String, Object> quiz2 = new HashMap<>();
        quiz2.put("name", subject + "进阶测试");
        quiz2.put("difficulty", "中级");
        quiz2.put("duration", 45);
        quiz2.put("questionCount", 15);
        quiz2.put("type", "综合测评");
        quizzes.add(quiz2);

        return quizzes;
    }

    private Map<String, Object> createExerciseFallback(String module, String type, String difficulty,
                                                       String question, List<String> options,
                                                       String answer, String analysis,
                                                       String commonMistake, String knowledgePoint) {
        Map<String, Object> exercise = new LinkedHashMap<>();
        exercise.put("module", module);
        exercise.put("type", type);
        exercise.put("difficulty", difficulty);
        exercise.put("question", question);
        exercise.put("options", options);
        exercise.put("answer", answer);
        exercise.put("analysis", analysis);
        exercise.put("commonMistake", commonMistake);
        exercise.put("knowledgePoint", knowledgePoint);
        return exercise;
    }
}
