package com.eduagent.service;

import com.eduagent.agent.*;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 内容审核Agent — LLM自检防幻觉机制
 *
 * 在EVALUATING阶段对各Agent输出进行内容质量审核:
 * 1. 事实准确性检查 — 知识点是否正确
 * 2. 一致性检查 — 不同Agent输出之间是否矛盾
 * 3. 适龄性检查 — 难度是否匹配用户水平
 * 4. 完整性检查 — 是否遗漏关键信息
 *
 * 审核结果分级:
 * - PASS: 内容质量合格，可直接输出
 * - REVISE: 存在小问题，需自动修正（最多3轮）
 * - BLOCK: 存在严重问题，阻止输出并告知用户
 */
@Slf4j
@Service
public class ReviewAgent {

    private final AIService aiService;

    /** 最大自动修正轮次 */
    private static final int MAX_REVISION_ROUNDS = 3;

    /** 审核专用System Prompt */
    private static final String REVIEW_SYSTEM_PROMPT = """
            你是教育内容质量审核专家。你需要以严格但公正的标准检查AI生成的学习内容。

            审核维度（每个维度独立评分，1-5分）:
            1. 事实准确性: 知识点是否正确？有无编造的错误信息？引用是否可靠？
            2. 一致性: 不同部分之间是否自相矛盾？逻辑是否连贯？
            3. 适龄性: 难度是否匹配学习者的水平？语言是否适合目标受众？
            4. 完整性: 是否遗漏关键知识点？回答是否全面覆盖了用户的问题？
            5. 安全性: 内容是否包含任何不当、偏见或有害信息？

            审核结果:
            - PASS (总分>=18/25): 内容质量合格
            - REVISE (总分12-17/25): 存在需要修正的问题，但不严重
            - BLOCK (总分<12/25): 存在严重问题，需要重新生成

            输出JSON格式（必须严格遵守）:
            {
              "verdict": "PASS/REVISE/BLOCK",
              "totalScore": 20,
              "dimensions": {
                "accuracy": 4,
                "consistency": 4,
                "appropriateness": 4,
                "completeness": 4,
                "safety": 4
              },
              "issues": ["问题1的描述", "问题2的描述"],
              "suggestions": ["修正建议1", "修正建议2"],
              "confidenceLevel": "HIGH/MEDIUM/LOW"
            }
            """;

    public ReviewAgent(AIService aiService) {
        this.aiService = aiService;
    }

    /**
     * 审核单个Agent的输出
     *
     * @param agentOutput 待审核的Agent输出
     * @param context     共享上下文（含用户画像、原始query）
     * @return 审核结果
     */
    public ReviewResult reviewAgentOutput(AgentResult agentOutput, AgentContext context) {
        long startTime = System.currentTimeMillis();

        try {
            String contentToReview = extractReviewContent(agentOutput);
            if (contentToReview == null || contentToReview.isBlank()) {
                log.warn("审核失败: agent={}, 内容为空，降级为BLOCK", agentOutput.getAgentName());
                return ReviewResult.block(agentOutput.getAgentName(), "审核内容为空，安全降级拦截");
            }

            // 构建审核Prompt
            String userMessage = buildReviewPrompt(contentToReview, context);

            // 调用LLM进行审核（使用temperature=0确保一致性）
            String llmOutput = aiService.chatWithSystemPrompt(REVIEW_SYSTEM_PROMPT, userMessage);

            if (llmOutput == null || llmOutput.isBlank()) {
                log.error("审核LLM返回空，降级为BLOCK: agent={}", agentOutput.getAgentName());
                return ReviewResult.block(agentOutput.getAgentName(), "LLM审核不可用，安全降级拦截");
            }

            // 解析审核结果
            JsonNode json = JsonParserUtil.parseJson(llmOutput);
            return parseReviewResult(json, agentOutput.getAgentName());

        } catch (Exception e) {
            log.error("审核Agent执行失败，降级为BLOCK: agent={}, error={}",
                    agentOutput.getAgentName(), e.getMessage());
            return ReviewResult.block(agentOutput.getAgentName(),
                    "审核服务异常，安全降级拦截: " + e.getMessage());
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            log.info("审核完成: agent={}, duration={}ms", agentOutput.getAgentName(), duration);
        }
    }

    /**
     * 审核全部Agent输出的一致性
     *
     * @param agentOutputs 所有Agent的输出
     * @param context      共享上下文
     * @return 一致性审核结果
     */
    public ConsistencyReport checkConsistency(Map<String, AgentResult> agentOutputs, AgentContext context) {
        if (agentOutputs == null || agentOutputs.size() < 2) {
            return ConsistencyReport.pass("少于2个Agent输出，无需一致性检查");
        }

        try {
            // 收集所有Agent输出
            StringBuilder allContent = new StringBuilder();
            for (Map.Entry<String, AgentResult> entry : agentOutputs.entrySet()) {
                allContent.append("=== ").append(entry.getKey()).append(" ===\n");
                allContent.append(extractReviewContent(entry.getValue())).append("\n\n");
            }

            String userMessage = buildConsistencyPrompt(allContent.toString(), context);
            String llmOutput = aiService.chatWithSystemPrompt(REVIEW_SYSTEM_PROMPT, userMessage);

            if (llmOutput == null || llmOutput.isBlank()) {
                log.error("一致性检查LLM返回空，降级为BLOCK");
                return ConsistencyReport.block("一致性检查LLM不可用，安全降级拦截");
            }

            JsonNode json = JsonParserUtil.parseJson(llmOutput);
            return parseConsistencyResult(json);

        } catch (Exception e) {
            log.error("一致性检查失败，降级为BLOCK: {}", e.getMessage());
            return ConsistencyReport.block("一致性检查异常，安全降级拦截");
        }
    }

    /**
     * 对审核未通过的内容进行自动修正
     * 最多重试MAX_REVISION_ROUNDS轮
     */
    public AgentResult autoRevise(AgentResult original, ReviewResult reviewResult,
                                   AgentContext context, java.util.function.Function<String, AgentResult> regenerator) {
        if (reviewResult.isPassed()) {
            return original;
        }

        String currentContent = extractReviewContent(original);
        String revisionPrompt = buildRevisionPrompt(currentContent, reviewResult);

        for (int round = 1; round <= MAX_REVISION_ROUNDS; round++) {
            log.info("自动修正第{}轮: agent={}", round, original.getAgentName());
            try {
                AgentResult revised = regenerator.apply(revisionPrompt);
                ReviewResult reReview = reviewAgentOutput(revised, context);

                if (reReview.isPassed()) {
                    log.info("自动修正成功: agent={}, 第{}轮通过", original.getAgentName(), round);
                    return revised;
                }

                if (reReview.getTotalScore() > reviewResult.getTotalScore()) {
                    // 有改善但未完全通过，继续修正
                    currentContent = extractReviewContent(revised);
                    reviewResult = reReview;
                } else {
                    log.warn("自动修正未改善: agent={}, 第{}轮", original.getAgentName(), round);
                    break;
                }
            } catch (Exception e) {
                log.error("自动修正异常: agent={}, round={}, error={}",
                        original.getAgentName(), round, e.getMessage());
                break;
            }
        }

        // 超过最大轮次仍未通过，返回带有审核标记的原始结果
        log.warn("自动修正未通过: agent={}, 已尝试{}轮, 最终分数={}",
                original.getAgentName(), MAX_REVISION_ROUNDS, reviewResult.getTotalScore());
        return original;
    }

    // ==================== Prompt构建 ====================

    private String buildReviewPrompt(String content, AgentContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("请审核以下AI生成的学习内容:\n\n");
        sb.append("【待审核内容】\n");
        // 限制长度避免超出token限制
        if (content.length() > 3000) {
            sb.append(content, 0, 3000).append("\n...(内容已截断)");
        } else {
            sb.append(content);
        }
        sb.append("\n\n");

        if (context != null) {
            sb.append("【用户画像】\n").append(context.buildProfileSummary()).append("\n\n");
            sb.append("【用户原始问题】\n").append(context.getQuery()).append("\n\n");
        }

        sb.append("请基于用户画像中的知识水平判断难度是否合适，基于用户问题判断回答是否全面。");
        return sb.toString();
    }

    private String buildConsistencyPrompt(String allContent, AgentContext context) {
        return "请检查以下多个AI智能体的输出是否存在矛盾或不一致:\n\n" +
                allContent + "\n" +
                "请判断它们对同一知识点的解释是否一致，难度建议是否匹配，推荐资源是否冲突。";
    }

    private String buildRevisionPrompt(String content, ReviewResult reviewResult) {
        return "以下内容需要修正:\n\n" + content + "\n\n" +
                "审核发现的问题:\n" + String.join("\n", reviewResult.getIssues()) + "\n\n" +
                "修正建议:\n" + String.join("\n", reviewResult.getSuggestions()) + "\n\n" +
                "请修正上述问题并重新生成内容。";
    }

    // ==================== 解析 ====================

    private String extractReviewContent(AgentResult result) {
        if (result == null) return "";
        if (result.getMarkdownContent() != null && !result.getMarkdownContent().isBlank()) {
            return result.getMarkdownContent();
        }
        if (result.getData() != null) {
            return result.getData().toString();
        }
        return "";
    }

    private ReviewResult parseReviewResult(JsonNode json, String agentName) {
        String verdict = json.has("verdict") ? json.get("verdict").asText() : "PASS";
        int totalScore = json.has("totalScore") ? json.get("totalScore").asInt() : 20;
        String confidenceLevel = json.has("confidenceLevel") ?
                json.get("confidenceLevel").asText() : "MEDIUM";

        List<String> issues = new ArrayList<>();
        if (json.has("issues") && json.get("issues").isArray()) {
            for (JsonNode issue : json.get("issues")) {
                issues.add(issue.asText());
            }
        }

        List<String> suggestions = new ArrayList<>();
        if (json.has("suggestions") && json.get("suggestions").isArray()) {
            for (JsonNode suggestion : json.get("suggestions")) {
                suggestions.add(suggestion.asText());
            }
        }

        Map<String, Integer> dimensions = new LinkedHashMap<>();
        if (json.has("dimensions") && json.get("dimensions").isObject()) {
            JsonNode dims = json.get("dimensions");
            dims.fieldNames().forEachRemaining(key ->
                    dimensions.put(key, dims.get(key).asInt()));
        }

        return ReviewResult.builder()
                .agentName(agentName)
                .verdict(verdict)
                .totalScore(totalScore)
                .confidenceLevel(confidenceLevel)
                .dimensions(dimensions)
                .issues(issues)
                .suggestions(suggestions)
                .reviewedAt(LocalDateTime.now())
                .build();
    }

    private ConsistencyReport parseConsistencyResult(JsonNode json) {
        String verdict = json.has("verdict") ? json.get("verdict").asText() : "PASS";
        int totalScore = json.has("totalScore") ? json.get("totalScore").asInt() : 20;
        List<String> issues = new ArrayList<>();
        if (json.has("issues") && json.get("issues").isArray()) {
            for (JsonNode issue : json.get("issues")) {
                issues.add(issue.asText());
            }
        }

        return ConsistencyReport.builder()
                .verdict(verdict)
                .totalScore(totalScore)
                .issues(issues)
                .build();
    }

    // ==================== 内部类 ====================

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ReviewResult {
        private String agentName;
        private String verdict;       // PASS / REVISE / BLOCK
        private int totalScore;       // 0-25
        private String confidenceLevel; // HIGH / MEDIUM / LOW
        private Map<String, Integer> dimensions; // 各维度评分
        private List<String> issues;
        private List<String> suggestions;
        private LocalDateTime reviewedAt;

        public boolean isPassed() {
            return "PASS".equalsIgnoreCase(verdict);
        }

        public boolean isBlocked() {
            return "BLOCK".equalsIgnoreCase(verdict);
        }

        public boolean needsRevision() {
            return "REVISE".equalsIgnoreCase(verdict);
        }

        public static ReviewResult pass(String agentName, String reason) {
            return ReviewResult.builder()
                    .agentName(agentName)
                    .verdict("PASS")
                    .totalScore(25)
                    .confidenceLevel("HIGH")
                    .dimensions(Map.of())
                    .issues(List.of())
                    .suggestions(List.of(reason))
                    .reviewedAt(LocalDateTime.now())
                    .build();
        }

        public static ReviewResult block(String agentName, String reason) {
            return ReviewResult.builder()
                    .agentName(agentName)
                    .verdict("BLOCK")
                    .totalScore(0)
                    .confidenceLevel("LOW")
                    .dimensions(Map.of())
                    .issues(List.of("审核服务异常: " + reason))
                    .suggestions(List.of("请稍后重试或联系管理员"))
                    .reviewedAt(LocalDateTime.now())
                    .build();
        }
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ConsistencyReport {
        private String verdict;
        private int totalScore;
        private List<String> issues;

        public boolean isPassed() {
            return "PASS".equalsIgnoreCase(verdict);
        }

        public boolean isBlocked() {
            return "BLOCK".equalsIgnoreCase(verdict);
        }

        public static ConsistencyReport pass(String reason) {
            return ConsistencyReport.builder()
                    .verdict("PASS")
                    .totalScore(25)
                    .issues(List.of(reason))
                    .build();
        }

        public static ConsistencyReport block(String reason) {
            return ConsistencyReport.builder()
                    .verdict("BLOCK")
                    .totalScore(0)
                    .issues(List.of("一致性检查异常: " + reason))
                    .build();
        }
    }
}
