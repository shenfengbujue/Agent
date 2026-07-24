package com.eduagent.controller;

import com.eduagent.entity.DailyContent;
import com.eduagent.model.vo.Result;
import com.eduagent.service.DailyContentService;
import com.eduagent.service.DiagnosisAgent;
import com.eduagent.service.StageTestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/daily")
public class DailyContentController {

    private final DailyContentService dailyService;
    private final DiagnosisAgent diagnosisAgent;
    private final StageTestService stageTestService;

    public DailyContentController(DailyContentService dailyService,
                                   DiagnosisAgent diagnosisAgent,
                                   StageTestService stageTestService) {
        this.dailyService = dailyService;
        this.diagnosisAgent = diagnosisAgent;
        this.stageTestService = stageTestService;
    }

    // ==================== 每日内容 ====================

    @PostMapping("/goals/{goalId}/stage/{stageIndex}/day/{dayIndex}/generate")
    public Result<Map<String, Object>> generateDay(
            @PathVariable Long goalId, @PathVariable int stageIndex, @PathVariable int dayIndex) {
        // 检查是否已生成
        DailyContent existing = dailyService.getDayContent(goalId, stageIndex, dayIndex);
        if (existing != null && "GENERATED".equals(existing.getStatus())) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", existing.getId()); result.put("status", existing.getStatus());
            result.put("knowledge", parseJson(existing.getKnowledge()));
            result.put("exercises", parseJson(existing.getExercises()));
            result.put("comprehensiveTest", parseJson(existing.getComprehensiveTest()));
            return Result.success(result);
        }
        // 异步后台生成，立即返回（用户离开页面也不中断）
        dailyService.generateDayAsync(goalId, stageIndex, dayIndex);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "GENERATING");
        result.put("message", "内容正在后台生成，请稍后刷新页面查看");
        return Result.success(result);
    }

    @GetMapping("/goals/{goalId}/stage/{stageIndex}/day/{dayIndex}")
    public Result<Map<String, Object>> getDay(@PathVariable Long goalId, @PathVariable int stageIndex, @PathVariable int dayIndex) {
        DailyContent content = dailyService.getDayContent(goalId, stageIndex, dayIndex);
        if (content == null) return Result.error(404, "内容不存在");
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", content.getId()); r.put("status", content.getStatus());
        r.put("knowledge", parseJson(content.getKnowledge()));
        r.put("exercises", parseJson(content.getExercises()));
        r.put("comprehensiveTest", parseJson(content.getComprehensiveTest()));
        r.put("score", content.getScore()); r.put("timeSpent", content.getTimeSpent());
        r.put("generatedAt", content.getGeneratedAt()); r.put("completedAt", content.getCompletedAt());
        return Result.success(r);
    }

    @GetMapping("/goals/{goalId}/stage/{stageIndex}")
    public Result<List<Map<String, Object>>> getStageDays(@PathVariable Long goalId, @PathVariable int stageIndex) {
        List<DailyContent> days = dailyService.getStageDays(goalId, stageIndex);
        List<Map<String, Object>> r = new ArrayList<>();
        for (DailyContent d : days) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("dayIndex", d.getDayIndex()); item.put("status", d.getStatus()); item.put("score", d.getScore());
            r.add(item);
        }
        return Result.success(r);
    }

    @PostMapping("/goals/{goalId}/stage/{stageIndex}/day/{dayIndex}/complete")
    public Result<Map<String, Object>> completeDay(@PathVariable Long goalId, @PathVariable int stageIndex,
            @PathVariable int dayIndex, @RequestBody Map<String, Object> body) {
        Integer score = body.get("score") != null ? ((Number) body.get("score")).intValue() : null;
        Integer timeSpent = body.get("timeSpent") != null ? ((Number) body.get("timeSpent")).intValue() : 0;
        @SuppressWarnings("unchecked")
        List<String> weakPoints = (List<String>) body.getOrDefault("weakPoints", List.of());
        DailyContent content = dailyService.completeDay(goalId, stageIndex, dayIndex, score, timeSpent, weakPoints);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status", content.getStatus()); r.put("progress", content.getScore());
        return Result.success(r);
    }

    // ==================== 诊断测试 ====================

    /** 生成入学诊断测试 */
    @PostMapping("/diagnosis/generate")
    public Result<Map<String, Object>> generateDiagnosis(@RequestBody Map<String, String> body) {
        String subject = body.get("subject");
        if (subject == null || subject.isBlank()) return Result.error(400, "学科不能为空");
        var agentResult = diagnosisAgent.generateDiagnosis(subject, null);
        return Result.success((Map<String, Object>) agentResult.getData());
    }

    /** 提交诊断答案，获得自适应建议 */
    @PostMapping("/diagnosis/analyze")
    public Result<Map<String, Object>> analyzeDiagnosis(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> diagnosis = (Map<String, Object>) body.get("diagnosis");
        @SuppressWarnings("unchecked")
        Map<String, String> answersRaw = (Map<String, String>) body.getOrDefault("answers", Map.of());
        Map<Integer, String> answers = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : answersRaw.entrySet()) {
            answers.put(Integer.parseInt(e.getKey()), e.getValue());
        }
        var analysis = diagnosisAgent.analyzeResults(diagnosis, answers);
        return Result.success(analysis);
    }

    // ==================== 阶段测试（闭环门槛） ====================

    /** 生成阶段综合测试 */
    @PostMapping("/goals/{goalId}/stage/{stageIndex}/test/generate")
    public Result<Map<String, Object>> generateStageTest(
            @PathVariable Long goalId, @PathVariable int stageIndex,
            @RequestBody Map<String, Object> body) {
        String subject = (String) body.getOrDefault("subject", "学习");
        String stageName = (String) body.getOrDefault("stageName", "未知阶段");
        String stageGoal = (String) body.getOrDefault("stageGoal", "");
        int daysLearned = body.get("daysLearned") != null ? ((Number) body.get("daysLearned")).intValue() : 0;
        var test = stageTestService.generateStageTest(goalId, stageIndex, subject, stageName, stageGoal, daysLearned);
        return Result.success(test);
    }

    /** 提交阶段测试答案 */
    @PostMapping("/goals/{goalId}/stage/{stageIndex}/test/submit")
    public Result<Map<String, Object>> submitStageTest(
            @PathVariable Long goalId, @PathVariable int stageIndex,
            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> test = (Map<String, Object>) body.get("test");
        @SuppressWarnings("unchecked")
        Map<String, String> answersRaw = (Map<String, String>) body.getOrDefault("answers", Map.of());
        Map<Integer, String> answers = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : answersRaw.entrySet()) {
            answers.put(Integer.parseInt(e.getKey()), e.getValue());
        }
        var evaluation = stageTestService.evaluateTest(test, answers);
        return Result.success(evaluation);
    }

    // ==================== 追加练习（阶段测试未通过） ====================

    @PostMapping("/goals/{goalId}/stage/{stageIndex}/extra")
    public Result<Map<String, Object>> addExtraDays(
            @PathVariable Long goalId, @PathVariable int stageIndex,
            @RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            List<String> weakTopics = (List<String>) body.getOrDefault("weakTopics", List.of());
            int extraDays = body.get("extraDays") != null ? ((Number) body.get("extraDays")).intValue() : 1;
            int daysAdded = dailyService.addExtraPracticeDays(goalId, stageIndex, weakTopics, extraDays);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("daysAdded", daysAdded);
            r.put("message", "已追加" + daysAdded + "天强化练习，针对薄弱点：" + String.join("、", weakTopics));
            return Result.success(r);
        } catch (Exception e) {
            return Result.error(500, "追加失败: " + e.getMessage());
        }
    }

    // ==================== 综合期末考试 ====================

    @PostMapping("/goals/{goalId}/final-exam")
    public Result<Map<String, Object>> generateFinalExam(
            @PathVariable Long goalId, @RequestBody Map<String, Object> body) {
        try {
            String subject = (String) body.getOrDefault("subject", "综合");
            var exam = dailyService.generateFinalExam(goalId, subject);
            return Result.success(exam);
        } catch (Exception e) {
            return Result.error(500, "生成失败: " + e.getMessage());
        }
    }

    private Object parseJson(String json) {
        if (json == null) return null;
        try { return new com.fasterxml.jackson.databind.ObjectMapper().readTree(json); } catch (Exception e) { return json; }
    }
}
