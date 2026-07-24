package com.eduagent.service;

import com.eduagent.agent.AgentContext;
import com.eduagent.agent.AgentResult;
import com.eduagent.agent.JsonParserUtil;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 入学诊断测试Agent
 * 在生成任何学习内容之前，先用10-15道题评估学生的实际水平
 * 诊断结果用于自适应调整学习路径（跳过已掌握、重点补弱）
 */
@Slf4j
@Service
public class DiagnosisAgent {

    private final AIService aiService;

    private static final String DIAGNOSIS_PROMPT = """
你是大学课程评估专家。你需要为学生的学习目标生成一套入学诊断测试题。

【测试目标】
评估学生在以下学科的真实水平，找出已掌握的知识点和薄弱环节。

【出题要求】
1. 覆盖该学科的核心知识点，从基础到进阶
2. 共10-15道选择题，难度分布：基础40% + 中等40% + 拔高20%
3. 每题标注对应的知识点名称和难度
4. 题目应该能区分"真正会"和"只是听说过"

【输出JSON格式】
{
  "title": "入学诊断测试 - {学科名}",
  "totalQuestions": 12,
  "timeLimit": 20,
  "questions": [
    {
      "id": 1,
      "question": "题目文本",
      "options": ["A.选项", "B.选项", "C.选项", "D.选项"],
      "answer": "A",
      "knowledgePoint": "对应知识点",
      "difficulty": "基础",
      "analysis": "解析"
    }
  ],
  "scoringRules": {
    "mastery": "正确率>=80%视为已掌握该知识点",
    "partial": "正确率60-79%视为部分掌握",
    "weak": "正确率<60%视为薄弱环节"
  }
}
""";

    public DiagnosisAgent(AIService aiService) {
        this.aiService = aiService;
    }

    /** 生成诊断测试 */
    public AgentResult generateDiagnosis(String subject, AgentContext context) {
        long startTime = System.currentTimeMillis();
        try {
            String prompt = "请为以下学习目标生成入学诊断测试题：\n" +
                    "学科主题：" + subject + "\n" +
                    (context != null && context.buildProfileSummary() != null
                            ? "用户画像：" + context.buildProfileSummary() : "");

            String llmOutput = aiService.chatWithSystemPrompt(DIAGNOSIS_PROMPT, prompt);
            if (llmOutput == null || llmOutput.isBlank()) {
                return fallbackDiagnosis(subject, startTime);
            }

            JsonNode result = JsonParserUtil.parseJson(llmOutput);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("title", result.has("title") ? result.get("title").asText() : "入学诊断");
            data.put("totalQuestions", result.has("totalQuestions") ? result.get("totalQuestions").asInt() : 12);
            data.put("timeLimit", result.has("timeLimit") ? result.get("timeLimit").asInt() : 20);

            List<Map<String, Object>> questions = new ArrayList<>();
            if (result.has("questions")) {
                for (JsonNode q : result.get("questions")) {
                    Map<String, Object> qm = new LinkedHashMap<>();
                    qm.put("id", q.has("id") ? q.get("id").asInt() : 0);
                    qm.put("question", q.has("question") ? q.get("question").asText() : "");
                    qm.put("answer", q.has("answer") ? q.get("answer").asText() : "");
                    qm.put("knowledgePoint", q.has("knowledgePoint") ? q.get("knowledgePoint").asText() : "");
                    qm.put("difficulty", q.has("difficulty") ? q.get("difficulty").asText() : "基础");
                    qm.put("analysis", q.has("analysis") ? q.get("analysis").asText() : "");
                    List<String> options = new ArrayList<>();
                    if (q.has("options")) {
                        for (JsonNode o : q.get("options")) options.add(o.asText());
                    }
                    qm.put("options", options);
                    questions.add(qm);
                }
            }
            data.put("questions", questions);
            data.put("scoringRules", parseScoringRules(result));

            long duration = System.currentTimeMillis() - startTime;
            return AgentResult.success("诊断测试智能体", data, formatDiagnosisMarkdown(data), duration);

        } catch (Exception e) {
            log.error("生成诊断测试失败", e);
            return fallbackDiagnosis(subject, startTime);
        }
    }

    /** 分析诊断结果，输出自适应建议 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> analyzeResults(Map<String, Object> diagnosis, Map<Integer, String> userAnswers) {
        Map<String, Object> analysis = new LinkedHashMap<>();
        List<Map<String, Object>> questions = (List<Map<String, Object>>) diagnosis.getOrDefault("questions", List.of());

        Map<String, List<Boolean>> knowledgeResults = new LinkedHashMap<>();
        int correct = 0;
        for (Map<String, Object> q : questions) {
            int id = (int) q.getOrDefault("id", 0);
            String correctAnswer = (String) q.getOrDefault("answer", "");
            String userAnswer = userAnswers.getOrDefault(id, "");
            boolean isCorrect = correctAnswer.equalsIgnoreCase(userAnswer);
            if (isCorrect) correct++;

            String kp = (String) q.getOrDefault("knowledgePoint", "未知");
            knowledgeResults.computeIfAbsent(kp, k -> new ArrayList<>()).add(isCorrect);
        }

        double totalRate = (double) correct / Math.max(1, questions.size());
        analysis.put("totalCorrect", correct);
        analysis.put("totalQuestions", questions.size());
        analysis.put("overallRate", Math.round(totalRate * 100));

        // 分类：已掌握/部分掌握/薄弱
        List<String> mastered = new ArrayList<>();
        List<String> partial = new ArrayList<>();
        List<String> weak = new ArrayList<>();
        for (Map.Entry<String, List<Boolean>> entry : knowledgeResults.entrySet()) {
            double rate = entry.getValue().stream().filter(b -> b).count() / (double) entry.getValue().size();
            if (rate >= 0.8) mastered.add(entry.getKey());
            else if (rate >= 0.6) partial.add(entry.getKey());
            else weak.add(entry.getKey());
        }
        analysis.put("masteredTopics", mastered);
        analysis.put("partialTopics", partial);
        analysis.put("weakTopics", weak);

        // 自适应建议
        StringBuilder advice = new StringBuilder();
        if (!weak.isEmpty()) {
            advice.append("重点补弱：").append(String.join("、", weak)).append("。");
        }
        if (!mastered.isEmpty()) {
            advice.append("可快进：").append(String.join("、", mastered)).append("（已掌握）。");
        }
        if (!partial.isEmpty()) {
            advice.append("需巩固：").append(String.join("、", partial)).append("。");
        }
        analysis.put("adaptiveAdvice", advice.toString());
        analysis.put("recommendedLevel", totalRate >= 0.8 ? "高级" : totalRate >= 0.5 ? "中级" : "入门");

        return analysis;
    }

    private Map<String, Object> parseScoringRules(JsonNode result) {
        Map<String, Object> rules = new LinkedHashMap<>();
        if (result.has("scoringRules")) {
            JsonNode sr = result.get("scoringRules");
            rules.put("mastery", sr.has("mastery") ? sr.get("mastery").asText() : "正确率>=80%");
            rules.put("partial", sr.has("partial") ? sr.get("partial").asText() : "正确率60-79%");
            rules.put("weak", sr.has("weak") ? sr.get("weak").asText() : "正确率<60%");
        } else {
            rules.put("mastery", ">=80%");
            rules.put("partial", "60-79%");
            rules.put("weak", "<60%");
        }
        return rules;
    }

    @SuppressWarnings("unchecked")
    private String formatDiagnosisMarkdown(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(data.get("title")).append("\n\n");
        sb.append("**共").append(data.get("totalQuestions")).append("题**\n\n");
        List<Map<String, Object>> questions = (List<Map<String, Object>>) data.getOrDefault("questions", List.of());
        for (Map<String, Object> q : questions) {
            sb.append("**第").append(q.get("id")).append("题**");
            sb.append("（").append(q.get("difficulty")).append(" · ").append(q.get("knowledgePoint")).append("）\n");
            sb.append(q.get("question")).append("\n");
            List<String> options = (List<String>) q.getOrDefault("options", List.of());
            for (String o : options) sb.append("- ").append(o).append("\n");
            sb.append("\n");
        }
        return sb.toString();
    }

    private AgentResult fallbackDiagnosis(String subject, long startTime) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("title", subject + " 入学诊断");
        data.put("totalQuestions", 5);
        data.put("timeLimit", 10);
        List<Map<String, Object>> questions = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Map<String, Object> q = new LinkedHashMap<>();
            q.put("id", i);
            q.put("question", "请评估你对「" + subject + "」第" + i + "个核心知识点的掌握程度？");
            q.put("options", List.of("A.完全掌握", "B.基本掌握", "C.部分了解", "D.完全不会"));
            q.put("answer", "A");
            q.put("knowledgePoint", "知识点" + i);
            q.put("difficulty", "基础");
            q.put("analysis", "自我评估");
            questions.add(q);
        }
        data.put("questions", questions);
        long duration = System.currentTimeMillis() - startTime;
        return AgentResult.degraded("诊断测试智能体", data, "", duration);
    }
}
