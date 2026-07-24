package com.eduagent.service;

import com.eduagent.entity.DailyContent;
import com.eduagent.entity.StudyGoal;
import com.eduagent.mapper.DailyContentMapper;
import com.eduagent.repository.StudyGoalRepository;
import com.eduagent.agent.JsonParserUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class DailyContentService {

    private final DailyContentMapper dailyMapper;
    private final StudyGoalRepository goalRepo;
    private final AIService aiService;
    private final WebSearchAgent webSearchAgent;
    private final java.util.concurrent.ThreadPoolExecutor agentExecutor;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String DAY_GENERATE_PROMPT = """
你是大学教材教学专家。你需要为学生生成高质量、可验证的教材级学习内容。

【你的知识边界】
- 你必须严格基于大学标准教材的知识体系生成内容
- 每个知识点必须能在主流教材中找到对应章节
- 定义、定理、公式必须与教材一致，不得自行编造
- 如果涉及数学公式，使用$$或$包裹的LaTeX格式

【内容质量标准】
1. 知识点讲解(800-1200字)：概念定义 → 核心定理/性质 → 典型例题(含详细步骤) → 常见错误提示
2. 每道练习题必须：标注对应知识点、有标准答案、有分步解析、有常见错误提醒
3. 综合测试题必须：覆盖当天所有知识点、至少1道跨知识点综合题

【联网搜索结果（教材参考）】
{searchResults}

【阶段信息】
学科：{subject}
阶段：{stageName}（第{stageIndex}阶段）
阶段目标：{stageGoal}
难度：{difficulty}
计划天数：{totalDays}天，当前第{dayIndex}天
模块：{modules}
弱项突破：{weakPointFocus}

【前序学习情况】
{previousSummary}

【输出JSON格式，严格遵守，直接输出纯JSON，不要用```json包裹】
{
  "knowledge": [
    {
      "title": "知识点名称（如：函数极限的ε-δ定义）",
      "basic": "知识点讲解(Markdown格式，含概念定义、定理性质、1道典型例题的详细步骤、1条常见错误)",
      "keyPoints": ["核心重点1", "核心重点2"],
      "pitfalls": ["常见错误1", "常见错误2"],
      "reference": "教材出处（如：《高等数学》同济七版 §1.3 P32）"
    }
  ],
  "exercises": [
    {
      "question": "题目文本",
      "options": ["A. 选项", "B. 选项", "C. 选项", "D. 选项"],
      "answer": "正确选项字母或答案",
      "type": "选择题",
      "difficulty": "基础",
      "analysis": "分步解析，说明为什么选这个，其他选项错在哪",
      "knowledgePoint": "对应知识点名称",
      "commonMistake": "学生做这道题最常见的错误"
    }
  ],
  "comprehensiveTest": [
    {
      "question": "综合题（覆盖多个知识点）",
      "options": ["A.", "B.", "C.", "D."],
      "answer": "答案",
      "analysis": "解析",
      "coversKnowledge": ["知识点1", "知识点2"]
    }
  ],
  "dailySummary": "用2-3句话总结今天学习的核心收获",
  "nextPreview": "预告下一天将要学习的内容（一句话）"
}

【数量要求】
- knowledge: 2个知识点
- exercises: 4道（每个知识点2道，题型至少包含选择和计算/填空）
- comprehensiveTest: 3道（覆盖当天所有知识点）
""";

    public DailyContentService(DailyContentMapper dailyMapper, StudyGoalRepository goalRepo,
                                AIService aiService, WebSearchAgent webSearchAgent,
                                @org.springframework.beans.factory.annotation.Qualifier("agentExecutor")
                                java.util.concurrent.ThreadPoolExecutor agentExecutor) {
        this.dailyMapper = dailyMapper;
        this.goalRepo = goalRepo;
        this.aiService = aiService;
        this.webSearchAgent = webSearchAgent;
        this.agentExecutor = agentExecutor;
    }

    public DailyContent getDayContent(Long goalId, int stageIndex, int dayIndex) {
        return dailyMapper.selectByGoalStageDay(goalId, stageIndex, dayIndex);
    }

    public List<DailyContent> getStageDays(Long goalId, int stageIndex) {
        return dailyMapper.selectByGoalAndStage(goalId, stageIndex);
    }

    @SuppressWarnings("unchecked")
    public DailyContent generateDay(Long goalId, int stageIndex, int dayIndex) {
        DailyContent existing = dailyMapper.selectByGoalStageDay(goalId, stageIndex, dayIndex);
        if (existing != null && "GENERATED".equals(existing.getStatus())) {
            return existing;
        }

        if (existing == null) {
            existing = DailyContent.builder()
                    .goalId(goalId).stageIndex(stageIndex).dayIndex(dayIndex)
                    .status("GENERATING").createdAt(LocalDateTime.now()).build();
            dailyMapper.insert(existing);
        } else {
            existing.setStatus("GENERATING");
            dailyMapper.updateById(existing);
        }

        try {
            StudyGoal goal = goalRepo.selectById(goalId);
            if (goal == null) throw new RuntimeException("目标不存在");

            Map<String, Object> stage = extractStageInfo(goal, stageIndex);

            // 联网搜索教材相关内容（失败不影响主流程）
            String searchResults;
            try {
                searchResults = searchTextbookContent(goal, stage, dayIndex);
            } catch (Exception e) {
                log.warn("联网搜索失败，继续使用纯LLM生成: {}", e.getMessage());
                searchResults = "（联网搜索暂时不可用，请严格基于你的教材知识生成内容）";
            }

            // 前序内容
            String prevSummary = buildPreviousSummary(goalId, stageIndex, dayIndex);

            // 构建上下文
            String contextPrompt = buildContextPrompt(goal, stage, dayIndex, stageIndex, searchResults, prevSummary);

            log.info("开始生成每日内容(3步): goalId={}, stage={}, day={}", goalId, stageIndex, dayIndex);

            // Step 1: 生成knowledge
            String knowledgeJson = generateKnowledge(contextPrompt);
            if (knowledgeJson == null) throw new RuntimeException("Step1知识生成失败");

            // Step 2: 基于knowledge生成exercises
            String exercisesJson = generateExercises(contextPrompt, knowledgeJson);
            if (exercisesJson == null) exercisesJson = "[]";
            log.info("Step2练习生成完成, exercisesSize={}", exercisesJson.length());

            // Step 3: 基于knowledge+exercises生成comprehensiveTest
            String testJson = generateComprehensiveTest(contextPrompt, knowledgeJson, exercisesJson);
            if (testJson == null) testJson = "[]";
            log.info("Step3综合测试生成完成, testSize={}", testJson.length());

            existing.setKnowledge(knowledgeJson);
            existing.setExercises(exercisesJson);
            existing.setComprehensiveTest(testJson);
            existing.setStatus("GENERATED");
            existing.setGeneratedAt(LocalDateTime.now());
            dailyMapper.updateById(existing);
            log.info("每日内容生成成功: goalId={}, stage={}, day={}", goalId, stageIndex, dayIndex);

            return existing;

        } catch (Exception e) {
            log.error("生成每日内容失败: goalId={}, stage={}, day={}, error={}",
                    goalId, stageIndex, dayIndex, e.getMessage());
            existing.setStatus("PENDING");
            dailyMapper.updateById(existing);
            throw new RuntimeException("生成失败: " + e.getMessage());
        }
    }

    /**
     * 后台异步生成每日内容（用户离开页面也不会中断）
     * 先插入GENERATING记录立即返回，再后台执行LLM调用
     */
    public void generateDayAsync(Long goalId, int stageIndex, int dayIndex) {
        // 先创建/更新为GENERATING状态
        DailyContent existing = dailyMapper.selectByGoalStageDay(goalId, stageIndex, dayIndex);
        if (existing != null && "GENERATED".equals(existing.getStatus())) {
            return; // 已生成，无需重复
        }
        if (existing == null) {
            existing = DailyContent.builder()
                    .goalId(goalId).stageIndex(stageIndex).dayIndex(dayIndex)
                    .status("GENERATING").createdAt(LocalDateTime.now()).build();
            dailyMapper.insert(existing);
        } else {
            existing.setStatus("GENERATING");
            dailyMapper.updateById(existing);
        }

        final Long existingId = existing.getId();
        // 后台线程执行，不受HTTP请求取消影响
        agentExecutor.execute(() -> {
            try {
                StudyGoal goal = goalRepo.selectById(goalId);
                if (goal == null) throw new RuntimeException("目标不存在");

                Map<String, Object> stage = extractStageInfo(goal, stageIndex);

                String searchResults;
                try {
                    searchResults = searchTextbookContent(goal, stage, dayIndex);
                } catch (Exception e) {
                    log.warn("联网搜索失败，继续使用纯LLM生成: {}", e.getMessage());
                    searchResults = "（联网搜索暂时不可用，请严格基于你的教材知识生成内容）";
                }

                String prevSummary = buildPreviousSummary(goalId, stageIndex, dayIndex);
                String contextPrompt = buildContextPrompt(goal, stage, dayIndex, stageIndex, searchResults, prevSummary);

                log.info("后台开始生成每日内容: goalId={}, stage={}, day={}", goalId, stageIndex, dayIndex);

                // Step 1: 生成知识点
                updateStatus(existingId, "GENERATING_KNOWLEDGE");
                String knowledgeJson = generateKnowledge(contextPrompt);
                if (knowledgeJson == null) throw new RuntimeException("Step1知识生成失败");

                // Step 2: 生成练习题
                updateStatus(existingId, "GENERATING_EXERCISES");
                String exercisesJson = generateExercises(contextPrompt, knowledgeJson);
                if (exercisesJson == null) exercisesJson = "[]";

                // Step 3: 生成综合测试
                updateStatus(existingId, "GENERATING_TEST");
                String testJson = generateComprehensiveTest(contextPrompt, knowledgeJson, exercisesJson);
                if (testJson == null) testJson = "[]";

                DailyContent record = dailyMapper.selectById(existingId);
                if (record != null) {
                    record.setKnowledge(knowledgeJson);
                    record.setExercises(exercisesJson);
                    record.setComprehensiveTest(testJson);
                    record.setStatus("GENERATED");
                    record.setGeneratedAt(LocalDateTime.now());
                    dailyMapper.updateById(record);
                    log.info("后台每日内容生成成功: goalId={}, stage={}, day={}", goalId, stageIndex, dayIndex);
                }
            } catch (Exception e) {
                log.error("后台生成每日内容失败: goalId={}, stage={}, day={}, error={}",
                        goalId, stageIndex, dayIndex, e.getMessage());
                DailyContent record = dailyMapper.selectById(existingId);
                if (record != null) {
                    record.setStatus("PENDING");
                    dailyMapper.updateById(record);
                }
            }
        });
    }

    private void updateStatus(Long id, String status) {
        try {
            DailyContent record = dailyMapper.selectById(id);
            if (record != null) {
                record.setStatus(status);
                dailyMapper.updateById(record);
            }
        } catch (Exception e) {
            log.warn("更新状态失败: {}", e.getMessage());
        }
    }

    /** 提取阶段信息 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractStageInfo(StudyGoal goal, int stageIndex) {
        Map<String, Object> stage = new LinkedHashMap<>();
        stage.put("name", "未知阶段");
        stage.put("goal", "");
        stage.put("difficulty", "基础");
        stage.put("days", 15);
        stage.put("modules", "[]");
        stage.put("weakPointFocus", "无");
        try {
            if (goal.getLearningPath() != null) {
                JsonNode lp = mapper.readTree(goal.getLearningPath());
                JsonNode stages = lp.has("learningPath") ? lp.get("learningPath").get("stages") :
                                  lp.has("stages") ? lp.get("stages") : null;
                if (stages != null && stages.isArray() && stages.size() > stageIndex) {
                    JsonNode s = stages.get(stageIndex);
                    if (s.has("name")) stage.put("name", s.get("name").asText());
                    if (s.has("goal")) stage.put("goal", s.get("goal").asText());
                    if (s.has("difficulty")) stage.put("difficulty", s.get("difficulty").asText());
                    if (s.has("days")) stage.put("days", s.get("days").asInt());
                    if (s.has("modules")) stage.put("modules", s.get("modules").toString());
                    if (s.has("weakPointFocus")) stage.put("weakPointFocus", s.get("weakPointFocus").asText());
                }
            }
        } catch (Exception e) { log.warn("解析阶段信息失败", e); }
        return stage;
    }

    /** 联网搜索教材内容 */
    private String searchTextbookContent(StudyGoal goal, Map<String, Object> stage, int dayIndex) {
        try {
            String subject = goal.getTitle();
            String stageName = str(stage.get("name"));
            String modules = str(stage.get("modules"));

            // 构建搜索词：教材名 + 学科 + 阶段 + 知识点
            String searchQuery = subject + " " + stageName + " 教材知识点 第" + dayIndex + "天";
            if (!modules.isEmpty() && !"[]".equals(modules)) {
                searchQuery = subject + " " + stageName + " " + modules + " 教材 知识点 例题";
            }

            Map<String, Object> searchResult = webSearchAgent.searchWeb(subject + " " + stageName + " 大学教材");
            if (searchResult != null && searchResult.get("data") instanceof List) {
                List<?> results = (List<?>) searchResult.get("data");
                if (!results.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("以下是从网络搜索到的相关教材参考资料：\n\n");
                    int count = 0;
                    for (Object r : results) {
                        if (count++ >= 3) break;
                        if (r instanceof Map) {
                            Map<?, ?> rm = (Map<?, ?>) r;
                            Object t = rm.get("title");
                            String title = t != null ? t.toString() : "";
                            Object sn = rm.get("snippet");
                            if (sn == null) sn = rm.get("summary");
                            sb.append("- ").append(title).append("\n");
                            sb.append("  ").append(String.valueOf(sn != null ? sn : "")).append("\n\n");
                        }
                    }
                    if (count > 0) return sb.toString();
                }
            }
        } catch (Exception e) {
            log.warn("联网搜索失败，使用纯LLM生成: {}", e.getMessage());
        }
        return "（联网搜索未获取到额外参考内容，请严格基于你的教材知识生成）";
    }

    /** 构建前序学习总结 */
    private String buildPreviousSummary(Long goalId, int stageIndex, int dayIndex) {
        if (dayIndex <= 1) return "这是本阶段第1天，无前序学习记录。";
        try {
            DailyContent prev = dailyMapper.selectByGoalStageDay(goalId, stageIndex, dayIndex - 1);
            if (prev != null) {
                String wp = prev.getWeakPoints() != null && !prev.getWeakPoints().equals("[]")
                        ? prev.getWeakPoints() : "无";
                return String.format("前一天已完成。薄弱知识点：%s。请针对这些薄弱点加强练习。得分：%s",
                        wp, prev.getScore() != null ? prev.getScore() : "未记录");
            }
        } catch (Exception e) { /* ignore */ }
        return "前一天无完成记录。";
    }

    public DailyContent completeDay(Long goalId, int stageIndex, int dayIndex, Integer score, Integer timeSpent, List<String> weakPoints) {
        DailyContent content = dailyMapper.selectByGoalStageDay(goalId, stageIndex, dayIndex);
        if (content == null) throw new RuntimeException("内容不存在");
        content.setStatus("COMPLETED");
        content.setScore(score);
        content.setTimeSpent(timeSpent);
        content.setCompletedAt(LocalDateTime.now());
        // 存储薄弱点
        if (weakPoints != null && !weakPoints.isEmpty()) {
            try {
                content.setWeakPoints(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(weakPoints));
            } catch (Exception e) { log.warn("存储薄弱点失败", e); }
        }
        dailyMapper.updateById(content);

        try {
            StudyGoal goal = goalRepo.selectById(goalId);
            if (goal != null) {
                // 遍历全部阶段计算总进度
                int totalDays = 0, completedDays = 0;
                for (int si = 0; si < 10; si++) {
                    List<DailyContent> stageDays = dailyMapper.selectByGoalAndStage(goalId, si);
                    if (stageDays.isEmpty()) break;
                    totalDays += stageDays.size();
                    completedDays += stageDays.stream().filter(d -> "COMPLETED".equals(d.getStatus())).count();
                }
                goal.setProgress(totalDays > 0 ? (int)(completedDays * 100L / totalDays) : 0);
                goal.setCurrentStageIndex(stageIndex);
                goalRepo.updateById(goal);
            }
        } catch (Exception e) { log.warn("更新进度失败", e); }
        return content;
    }

    private String buildContextPrompt(StudyGoal goal, Map<String, Object> stage, int dayIndex,
                                       int stageIndex, String searchResults, String prevSummary) {
        return String.format("学科：%s | 阶段：%s | 第%d天 | 难度：%s | 模块：%s | 弱项：%s\n联网参考：%s\n前序：%s",
                goal.getTitle(), str(stage.get("name")), dayIndex,
                str(stage.get("difficulty")), str(stage.get("modules")),
                str(stage.get("weakPointFocus")), searchResults, prevSummary);
    }
    private String generateKnowledge(String context) {
        String prompt = "生成2个知识点。每个400-600字含概念定义、核心定理、1道例题(含步骤)。公式用LaTeX $...$。\n" +
            "直接输出纯JSON数组(用英文双引号)：\n" +
            "[{\"title\":\"知识点\",\"basic\":\"Markdown讲解\",\"keyPoints\":[\"重点\"],\"pitfalls\":[\"错误\"]}]\n" +
            "输出2个知识点。" + context;
        log.info("开始生成知识点，prompt长度={}", prompt.length());
        String lastError = "";
        for (int i = 0; i < 2; i++) {
            long t0 = System.currentTimeMillis();
            String output = aiService.chatWithSystemPrompt("你是大学教材教学专家。直接输出纯JSON数组，使用英文引号。", prompt);
            log.info("知识点LLM调用完成，耗时{}ms，输出长度={}", System.currentTimeMillis() - t0, output != null ? output.length() : 0);
            if (output == null || output.isBlank()) { lastError = "AI返回空"; continue; }
            try {
                JsonNode result = JsonParserUtil.parseJson(output);
                if (result == null || result.isEmpty()) {
                    lastError = "parseJson返回空对象，原始:" + output.substring(0, Math.min(200, output.length()));
                    continue;
                }
                if (result.isArray() && result.size() >= 1) return result.toString();
                if (result.has("title") && result.has("basic")) return "[" + result.toString() + "]";
                if (result.has("knowledge") && result.get("knowledge").isArray()) return result.get("knowledge").toString();
                lastError = "JSON格式不对:" + result.toString().substring(0, Math.min(150, result.toString().length()));
            } catch (Exception e) { lastError = "解析异常:" + e.getMessage(); }
        }
        throw new RuntimeException("知识生成失败：" + lastError);
    }
    private String generateExercises(String context, String knowledgeJson) {
        String prompt = "基于知识点生成4道练习题，每题有答案和解析。\n知识点：" +
                (knowledgeJson != null ? knowledgeJson.substring(0, Math.min(1500, knowledgeJson.length())) : "") +
                "\n直接输出纯JSON数组：[{\"question\":\"...\",\"options\":[\"A.\"],\"answer\":\"A\",\"type\":\"选择\",\"difficulty\":\"基础\",\"analysis\":\"...\"}]\n输出4道题。" + context;
        long t0 = System.currentTimeMillis();
        String output = aiService.chatWithSystemPrompt("直接输出纯JSON数组。", prompt);
        log.info("练习题LLM完成，耗时{}ms", System.currentTimeMillis() - t0);
        if (output == null || output.isBlank()) return "[]";
        try {
            JsonNode result = JsonParserUtil.parseJson(output);
            if (result.isArray()) return result.toString();
            if (result.has("exercises")) return result.get("exercises").toString();
        } catch (Exception e) {}
        return "[]";
    }
    private String generateComprehensiveTest(String context, String knowledgeJson, String exercisesJson) {
        String prompt = "基于知识点生成2道综合测试题。\n知识点：" +
                (knowledgeJson != null ? knowledgeJson.substring(0, Math.min(800, knowledgeJson.length())) : "") +
                "\n直接输出纯JSON数组：[{\"question\":\"...\",\"options\":[\"A.\"],\"answer\":\"A\",\"analysis\":\"...\"}]\n输出2道题。" + context;
        long t0 = System.currentTimeMillis();
        String output = aiService.chatWithSystemPrompt("直接输出纯JSON数组。", prompt);
        log.info("测试题LLM完成，耗时{}ms", System.currentTimeMillis() - t0);
        if (output == null || output.isBlank()) return "[]";
        try {
            JsonNode result = JsonParserUtil.parseJson(output);
            if (result.isArray()) return result.toString();
            if (result.has("comprehensiveTest")) return result.get("comprehensiveTest").toString();
        } catch (Exception e) {}
        return "[]";
    }

    /** 阶段测试未通过 → 追加强化练习天 */
    public int addExtraPracticeDays(Long goalId, int stageIndex, List<String> weakTopics, int extraDays) {
        int added = 0;
        int startDay = getMaxDayIndex(goalId, stageIndex) + 1;
        for (int d = 0; d < extraDays; d++) {
            DailyContent dc = DailyContent.builder()
                    .goalId(goalId).stageIndex(stageIndex).dayIndex(startDay + d)
                    .status("GENERATING").createdAt(LocalDateTime.now()).build();
            dailyMapper.insert(dc);
            // 生成针对薄弱点的强化练习
            try {
                String prompt = String.format(
                    "你是大学教材教学专家。请针对以下薄弱知识点生成2道强化练习题。\n薄弱点：%s\n直接输出纯JSON数组：[{\"question\":\"...\",\"options\":[\"A.\"],\"answer\":\"A\",\"type\":\"选择题\",\"difficulty\":\"基础\",\"analysis\":\"...\"}]",
                    String.join("、", weakTopics));
                String output = aiService.chatWithSystemPrompt("你是教材专家。直接输出纯JSON数组。", prompt);
                if (output != null && !output.isBlank()) {
                    JsonNode result = JsonParserUtil.parseJson(output);
                    dc.setKnowledge("[{\"title\":\"薄弱点强化练习\",\"basic\":\"针对" +
                            String.join("、", weakTopics) + "的专项强化训练\"}]");
                    dc.setExercises(result.isArray() ? result.toString() : "[]");
                }
            } catch (Exception e) { log.warn("追加练习生成失败: {}", e.getMessage()); }
            dc.setStatus("GENERATED");
            dc.setGeneratedAt(LocalDateTime.now());
            dc.setComprehensiveTest("[]");
            dailyMapper.updateById(dc);
            added++;
        }
        log.info("追加练习完成: goalId={}, stage={}, days={}", goalId, stageIndex, added);
        return added;
    }

    /** 生成综合期末考试 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> generateFinalExam(Long goalId, String subject) {
        Map<String, Object> exam = new LinkedHashMap<>();
        exam.put("title", subject + " 综合期末考试");
        exam.put("passScore", 75);
        exam.put("timeLimit", 60);

        // 收集所有已学知识点
        Set<String> allTopics = new LinkedHashSet<>();
        for (int si = 0; si < 10; si++) {
            List<DailyContent> days = dailyMapper.selectByGoalAndStage(goalId, si);
            if (days.isEmpty()) break;
            for (DailyContent d : days) {
                if (!"COMPLETED".equals(d.getStatus()) && !"GENERATED".equals(d.getStatus())) continue;
                try {
                    JsonNode knowledge = new com.fasterxml.jackson.databind.ObjectMapper().readTree(d.getKnowledge());
                    if (knowledge.isArray()) {
                        for (JsonNode k : knowledge) {
                            if (k.has("title")) allTopics.add(k.get("title").asText());
                        }
                    }
                } catch (Exception e) { /* ignore */ }
            }
        }

        if (allTopics.isEmpty()) {
            exam.put("totalQuestions", 0);
            exam.put("questions", List.of());
            exam.put("message", "暂无足够的学习数据生成考试");
            return exam;
        }

        String topicsStr = String.join("、", allTopics);
        String prompt = String.format("""
                你是大学教材评估专家。请为以下学科生成综合期末考试题。
                学科：%s
                覆盖知识点：%s

                要求：15道选择题，覆盖所列知识点的80%%以上。
                难度分布：基础40%%、中等40%%、拔高20%%。
                每道题有标准答案和分步解析。通过标准：75分(正确率75%%)。

                直接输出纯JSON数组：
                [{"id":1,"question":"...","options":["A.","B.","C.","D."],"answer":"A","difficulty":"基础","analysis":"...","knowledgePoint":"..."}]
                """, subject, topicsStr);

        try {
            String output = aiService.chatWithSystemPrompt("你是大学教材评估专家。直接输出纯JSON数组。", prompt);
            if (output != null && !output.isBlank()) {
                JsonNode result = JsonParserUtil.parseJson(output);
                List<Map<String, Object>> questions = new ArrayList<>();
                if (result.isArray()) {
                    for (JsonNode q : result) {
                        Map<String, Object> qm = new LinkedHashMap<>();
                        qm.put("id", q.has("id") ? q.get("id").asInt() : 0);
                        qm.put("question", q.has("question") ? q.get("question").asText() : "");
                        qm.put("answer", q.has("answer") ? q.get("answer").asText() : "");
                        qm.put("difficulty", q.has("difficulty") ? q.get("difficulty").asText() : "基础");
                        qm.put("analysis", q.has("analysis") ? q.get("analysis").asText() : "");
                        qm.put("knowledgePoint", q.has("knowledgePoint") ? q.get("knowledgePoint").asText() : "");
                        List<String> options = new ArrayList<>();
                        if (q.has("options")) for (JsonNode o : q.get("options")) options.add(o.asText());
                        qm.put("options", options);
                        questions.add(qm);
                    }
                }
                exam.put("totalQuestions", questions.size());
                exam.put("questions", questions);
                return exam;
            }
        } catch (Exception e) { log.error("期末考生成失败", e); }

        exam.put("totalQuestions", 0);
        exam.put("questions", List.of());
        exam.put("message", "生成失败，请重试");
        return exam;
    }

    private int getMaxDayIndex(Long goalId, int stageIndex) {
        List<DailyContent> days = dailyMapper.selectByGoalAndStage(goalId, stageIndex);
        return days.stream().mapToInt(DailyContent::getDayIndex).max().orElse(0);
    }

    private String str(Object obj) {
        return obj != null ? obj.toString() : "";
    }
}
