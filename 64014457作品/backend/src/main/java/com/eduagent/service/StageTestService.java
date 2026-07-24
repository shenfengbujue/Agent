package com.eduagent.service;

import com.eduagent.entity.DailyContent;
import com.eduagent.mapper.DailyContentMapper;
import com.eduagent.agent.JsonParserUtil;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 阶段测试服务 —— 闭环验证的核心
 * 阶段测试作为解锁下一阶段的门槛，不过关不能前进
 */
@Slf4j
@Service
public class StageTestService {

    private final DailyContentMapper dailyMapper;
    private final AIService aiService;

    private static final String STAGE_TEST_PROMPT = """
你是大学课程评估专家。为以下学习阶段生成综合测试题。直接输出纯JSON。

【阶段信息】
学科：{subject}
阶段名称：{stageName}
阶段目标：{stageGoal}
已学天数：{daysLearned}
已学知识点：{learnedTopics}

【出题要求】
1. 共10道选择题，覆盖本阶段所有已学知识点
2. 难度分布：基础40%（直接应用）+ 中等40%（综合2个知识点）+ 拔高20%（跨知识点应用）
3. 每题必须有标准答案和分步解析
4. 通过标准：正确率>=70%

【输出JSON格式】
{
  "title": "阶段测试 - {stageName}",
  "passScore": 70,
  "totalQuestions": 10,
  "timeLimit": 30,
  "questions": [
    {
      "id": 1,
      "question": "题目",
      "options": ["A.", "B.", "C.", "D."],
      "answer": "A",
      "knowledgePoint": "知识点",
      "difficulty": "基础",
      "analysis": "解析",
      "scoreWeight": 10
    }
  ]
}
""";

    public StageTestService(DailyContentMapper dailyMapper, AIService aiService) {
        this.dailyMapper = dailyMapper;
        this.aiService = aiService;
    }

    /** 生成阶段综合测试 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> generateStageTest(Long goalId, int stageIndex,
                                                   String subject, String stageName,
                                                   String stageGoal, int daysLearned) {
        // 收集已学知识点
        StringBuilder topics = new StringBuilder();
        List<DailyContent> days = dailyMapper.selectByGoalAndStage(goalId, stageIndex);
        Set<String> topicSet = new LinkedHashSet<>();
        for (DailyContent d : days) {
            if ("COMPLETED".equals(d.getStatus()) || "GENERATED".equals(d.getStatus())) {
                try {
                    JsonNode knowledge = new com.fasterxml.jackson.databind.ObjectMapper().readTree(d.getKnowledge());
                    if (knowledge.isArray()) {
                        for (JsonNode k : knowledge) {
                            if (k.has("title")) topicSet.add(k.get("title").asText());
                        }
                    }
                } catch (Exception e) { /* ignore */ }
            }
        }
        for (String t : topicSet) topics.append(t).append("、");

        String prompt = STAGE_TEST_PROMPT
                .replace("{subject}", subject)
                .replace("{stageName}", stageName)
                .replace("{stageGoal}", stageGoal)
                .replace("{daysLearned}", String.valueOf(daysLearned))
                .replace("{learnedTopics}", topics.toString());

        try {
            String llmOutput = aiService.chatWithSystemPrompt(
                    "你是大学教材评估专家。直接输出纯JSON。", prompt);

            if (llmOutput == null || llmOutput.isBlank()) {
                return createFallbackTest(stageName);
            }

            JsonNode result = JsonParserUtil.parseJson(llmOutput);
            Map<String, Object> test = new LinkedHashMap<>();
            test.put("title", result.has("title") ? result.get("title").asText() : "阶段测试");
            test.put("passScore", result.has("passScore") ? result.get("passScore").asInt() : 70);
            test.put("totalQuestions", result.has("totalQuestions") ? result.get("totalQuestions").asInt() : 10);
            test.put("timeLimit", result.has("timeLimit") ? result.get("timeLimit").asInt() : 30);

            List<Map<String, Object>> questions = new ArrayList<>();
            if (result.has("questions")) {
                for (JsonNode q : result.get("questions")) {
                    Map<String, Object> qm = new LinkedHashMap<>();
                    qm.put("id", q.get("id").asInt());
                    qm.put("question", q.get("question").asText());
                    qm.put("answer", q.get("answer").asText());
                    qm.put("knowledgePoint", q.has("knowledgePoint") ? q.get("knowledgePoint").asText() : "");
                    qm.put("difficulty", q.has("difficulty") ? q.get("difficulty").asText() : "基础");
                    qm.put("analysis", q.has("analysis") ? q.get("analysis").asText() : "");
                    qm.put("scoreWeight", q.has("scoreWeight") ? q.get("scoreWeight").asInt() : 10);
                    List<String> options = new ArrayList<>();
                    if (q.has("options")) {
                        for (JsonNode o : q.get("options")) options.add(o.asText());
                    }
                    qm.put("options", options);
                    questions.add(qm);
                }
            }
            test.put("questions", questions);
            return test;

        } catch (Exception e) {
            log.error("生成阶段测试失败", e);
            return createFallbackTest(stageName);
        }
    }

    /** 评估测试结果 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> evaluateTest(Map<String, Object> test, Map<Integer, String> userAnswers) {
        List<Map<String, Object>> questions = (List<Map<String, Object>>) test.getOrDefault("questions", List.of());
        int passScore = (int) test.getOrDefault("passScore", 70);
        int totalScore = 0;
        int earnedScore = 0;
        List<String> wrongTopics = new ArrayList<>();

        for (Map<String, Object> q : questions) {
            int id = (int) q.getOrDefault("id", 0);
            int weight = (int) q.getOrDefault("scoreWeight", 10);
            totalScore += weight;
            String correctAnswer = (String) q.getOrDefault("answer", "");
            String userAnswer = userAnswers.getOrDefault(id, "");
            if (correctAnswer.equalsIgnoreCase(userAnswer)) {
                earnedScore += weight;
            } else {
                wrongTopics.add((String) q.getOrDefault("knowledgePoint", "未知"));
            }
        }

        int percentage = totalScore > 0 ? earnedScore * 100 / totalScore : 0;
        boolean passed = percentage >= passScore;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalScore", totalScore);
        result.put("earnedScore", earnedScore);
        result.put("percentage", percentage);
        result.put("passScore", passScore);
        result.put("passed", passed);
        result.put("wrongTopics", wrongTopics);

        if (!passed) {
            result.put("message", "未通过！正确率" + percentage + "%，需要达到" + passScore +
                    "%。薄弱知识点：" + String.join("、", wrongTopics) + "。建议重新复习后再测。");
            result.put("extraDays", Math.max(1, wrongTopics.size())); // 每个薄弱点追加1天练习
        } else {
            result.put("message", "通过！正确率" + percentage + "%。可以进入下一阶段了。");
            result.put("extraDays", 0);
        }

        return result;
    }

    private Map<String, Object> createFallbackTest(String stageName) {
        Map<String, Object> test = new LinkedHashMap<>();
        test.put("title", "阶段测试 - " + stageName);
        test.put("passScore", 70);
        test.put("totalQuestions", 5);
        test.put("timeLimit", 15);
        List<Map<String, Object>> questions = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            questions.add(Map.of(
                    "id", i, "question", "请自我评估你对「" + stageName + "」第" + i + "个核心知识点的掌握程度",
                    "options", List.of("A.完全掌握", "B.基本掌握", "C.部分了解", "D.完全不会"),
                    "answer", "A", "knowledgePoint", "知识点" + i,
                    "difficulty", "基础", "analysis", "自我评估", "scoreWeight", 10
            ));
        }
        test.put("questions", questions);
        return test;
    }
}
