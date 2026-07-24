package com.eduagent.service;

import com.eduagent.agent.AgentContext;
import com.eduagent.agent.AgentResult;
import com.eduagent.agent.JsonParserUtil;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 代码实操案例生成Agent
 *
 * 根据学科和难度生成结构化代码实操案例：
 * - 完整可运行代码
 * - 逐行注释讲解
 * - 预期输出
 * - 常见错误与调试技巧
 * - 变式练习
 *
 * 不依赖外部API，完全由LLM生成代码案例内容。
 */
@Slf4j
@Service
public class CodePracticeAgent {

    private final AIService aiService;

    private static final String CODE_PRACTICE_PROMPT = """
            你是编程教学专家。根据学生的学科和难度需求，生成结构化代码实操案例。

            要求生成以下结构:
            1. title: 案例标题
            2. difficulty: 入门/进阶/实战
            3. description: 案例描述（要解决的问题）
            4. language: 编程语言
            5. code: 完整可运行的代码
            6. lineByLineExplanation: 逐行讲解 [{ "lineNum": 1, "code": "...", "explanation": "..." }]
            7. expectedOutput: 预期运行结果
            8. commonMistakes: 常见错误 [{ "mistake": "错误描述", "fix": "修正方法" }]
            9. variationExercises: 变式练习 [{ "title": "...", "description": "...", "hint": "..." }]
            10.keyConcepts: 涉及的核心知识点列表

            输出JSON格式（必须严格遵守，不要用```json包裹）:
            {
              "title": "案例标题",
              "difficulty": "入门",
              "description": "解决什么问题",
              "language": "Python",
              "code": "完整代码文本",
              "lineByLineExplanation": [
                {"lineNum": 1, "code": "def add(a, b):", "explanation": "定义函数add，接收两个参数"}
              ],
              "expectedOutput": "预期输出文本",
              "commonMistakes": [
                {"mistake": "忘记返回值", "fix": "确保函数末尾使用return语句"}
              ],
              "variationExercises": [
                {"title": "变式1", "description": "修改要求", "hint": "提示"}
              ],
              "keyConcepts": ["概念1", "概念2"],
              "learningTips": "学习建议"
            }
            """;

    public CodePracticeAgent(AIService aiService) {
        this.aiService = aiService;
    }

    /**
     * 生成代码实操案例
     *
     * @param topic      学科/主题（如"Python"、"Java"、"数据结构"）
     * @param difficulty 难度: 入门/进阶/实战
     * @param context    共享上下文（含用户画像）
     * @return AgentResult，data中存放结构化案例
     */
    public AgentResult generate(String topic, String difficulty, AgentContext context) {
        long startTime = System.currentTimeMillis();

        try {
            String userMessage = buildCodePracticePrompt(topic, difficulty, context);
            String llmOutput = aiService.chatWithSystemPrompt(CODE_PRACTICE_PROMPT, userMessage);

            if (llmOutput != null && !llmOutput.isBlank()) {
                JsonNode json = JsonParserUtil.parseJson(llmOutput);
                Map<String, Object> data = parseCodePracticeResult(json);
                String markdown = buildMarkdownPresentation(data);

                long duration = System.currentTimeMillis() - startTime;
                return AgentResult.success("代码实操智能体", data, markdown, duration);
            }

            return fallbackResult(topic, difficulty, startTime);

        } catch (Exception e) {
            log.error("代码实操Agent失败: topic={}, error={}", topic, e.getMessage());
            return fallbackResult(topic, difficulty, startTime);
        }
    }

    /**
     * 生成指定难度的多个案例
     */
    public List<AgentResult> generateMultiple(String topic, int count, AgentContext context) {
        String[] difficulties = {"入门", "进阶", "实战"};
        List<AgentResult> results = new ArrayList<>();

        for (int i = 0; i < Math.min(count, difficulties.length); i++) {
            try {
                results.add(generate(topic, difficulties[i], context));
            } catch (Exception e) {
                log.warn("生成第{}个案例失败: {}", i + 1, e.getMessage());
            }
        }

        return results;
    }

    // ==================== 私有方法 ====================

    private String buildCodePracticePrompt(String topic, String difficulty, AgentContext context) {
        String profileInfo = "";
        if (context != null && context.getUserProfile() != null && !context.getUserProfile().isEmpty()) {
            profileInfo = "\n学生水平: " + context.getUserProfile().getOrDefault("knowledgeLevel", "未知");
        }

        return String.format("""
                请为以下学生生成代码实操案例:
                学科主题: %s
                难度: %s%s

                请确保代码可以实际运行，注释清晰，适合该水平的学生学习。
                """, topic, difficulty, profileInfo);
    }

    private Map<String, Object> parseCodePracticeResult(JsonNode json) {
        Map<String, Object> data = new LinkedHashMap<>();

        data.put("title", getString(json, "title", "代码实操案例"));
        data.put("difficulty", getString(json, "difficulty", "入门"));
        data.put("description", getString(json, "description", ""));
        data.put("language", getString(json, "language", "Java"));
        data.put("code", getString(json, "code", "// 代码生成失败"));

        // 逐行讲解
        List<Map<String, Object>> lineExplanations = new ArrayList<>();
        if (json.has("lineByLineExplanation") && json.get("lineByLineExplanation").isArray()) {
            for (JsonNode line : json.get("lineByLineExplanation")) {
                Map<String, Object> exp = new LinkedHashMap<>();
                exp.put("lineNum", line.has("lineNum") ? line.get("lineNum").asInt() : 0);
                exp.put("code", getString(line, "code", ""));
                exp.put("explanation", getString(line, "explanation", ""));
                lineExplanations.add(exp);
            }
        }
        data.put("lineByLineExplanation", lineExplanations);

        data.put("expectedOutput", getString(json, "expectedOutput", ""));
        data.put("learningTips", getString(json, "learningTips", ""));

        // 常见错误
        List<Map<String, String>> mistakes = new ArrayList<>();
        if (json.has("commonMistakes") && json.get("commonMistakes").isArray()) {
            for (JsonNode m : json.get("commonMistakes")) {
                mistakes.add(Map.of(
                    "mistake", getString(m, "mistake", ""),
                    "fix", getString(m, "fix", "")
                ));
            }
        }
        data.put("commonMistakes", mistakes);

        // 变式练习
        List<Map<String, String>> variations = new ArrayList<>();
        if (json.has("variationExercises") && json.get("variationExercises").isArray()) {
            for (JsonNode v : json.get("variationExercises")) {
                variations.add(Map.of(
                    "title", getString(v, "title", ""),
                    "description", getString(v, "description", ""),
                    "hint", getString(v, "hint", "")
                ));
            }
        }
        data.put("variationExercises", variations);

        // 核心概念
        List<String> concepts = new ArrayList<>();
        if (json.has("keyConcepts") && json.get("keyConcepts").isArray()) {
            for (JsonNode c : json.get("keyConcepts")) {
                concepts.add(c.asText());
            }
        }
        data.put("keyConcepts", concepts);

        return data;
    }

    @SuppressWarnings("unchecked")
    private String buildMarkdownPresentation(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder();

        sb.append("# ").append(data.get("title")).append("\n\n");
        sb.append("**难度**: ").append(data.get("difficulty"))
          .append(" | **语言**: ").append(data.get("language")).append("\n\n");
        sb.append(data.get("description")).append("\n\n");

        // 代码块
        sb.append("## 代码实现\n\n");
        sb.append("```").append(((String) data.getOrDefault("language", "java")).toLowerCase()).append("\n");
        sb.append(data.get("code")).append("\n");
        sb.append("```\n\n");

        // 逐行讲解
        List<Map<String, Object>> explanations = (List<Map<String, Object>>) data.get("lineByLineExplanation");
        if (explanations != null && !explanations.isEmpty()) {
            sb.append("## 逐行讲解\n\n");
            for (Map<String, Object> exp : explanations) {
                sb.append("- **第").append(exp.get("lineNum")).append("行** `")
                  .append(exp.get("code")).append("` — ").append(exp.get("explanation")).append("\n");
            }
            sb.append("\n");
        }

        // 预期输出
        String output = (String) data.get("expectedOutput");
        if (output != null && !output.isEmpty()) {
            sb.append("## 预期输出\n\n```\n").append(output).append("\n```\n\n");
        }

        // 常见错误
        List<Map<String, String>> mistakes = (List<Map<String, String>>) data.get("commonMistakes");
        if (mistakes != null && !mistakes.isEmpty()) {
            sb.append("## 常见错误与调试\n\n");
            for (Map<String, String> m : mistakes) {
                sb.append("- ❌ **").append(m.get("mistake")).append("** → ✅ ").append(m.get("fix")).append("\n");
            }
            sb.append("\n");
        }

        // 变式练习
        List<Map<String, String>> variations = (List<Map<String, String>>) data.get("variationExercises");
        if (variations != null && !variations.isEmpty()) {
            sb.append("## 变式练习\n\n");
            for (int i = 0; i < variations.size(); i++) {
                Map<String, String> v = variations.get(i);
                sb.append(i + 1).append(". **").append(v.get("title")).append("** — ")
                  .append(v.get("description")).append("\n");
                String hint = v.get("hint");
                if (hint != null && !hint.isEmpty()) {
                    sb.append("   💡 提示: ").append(hint).append("\n");
                }
            }
            sb.append("\n");
        }

        // 学习建议
        String tips = (String) data.get("learningTips");
        if (tips != null && !tips.isEmpty()) {
            sb.append("## 学习建议\n\n").append(tips).append("\n");
        }

        return sb.toString();
    }

    private AgentResult fallbackResult(String topic, String difficulty, long startTime) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("title", topic + "代码实操（" + difficulty + "）");
        data.put("difficulty", difficulty);
        data.put("language", "java");
        data.put("code", "// 代码生成暂时不可用，请稍后重试\nSystem.out.println(\"Hello, EduAgent!\");");
        data.put("expectedOutput", "Hello, EduAgent!");
        data.put("lineByLineExplanation", List.of(
            Map.of("lineNum", 1, "code", "// 占位代码", "explanation", "代码生成服务暂时不可用")
        ));
        data.put("commonMistakes", List.of());
        data.put("variationExercises", List.of());
        data.put("keyConcepts", List.of(topic));

        String markdown = buildMarkdownPresentation(data);
        long duration = System.currentTimeMillis() - startTime;
        return AgentResult.degraded("代码实操智能体", data, markdown, duration);
    }

    private String getString(JsonNode node, String field, String defaultValue) {
        if (node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asText();
        }
        return defaultValue;
    }
}
