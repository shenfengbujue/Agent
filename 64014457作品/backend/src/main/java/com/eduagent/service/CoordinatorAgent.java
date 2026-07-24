package com.eduagent.service;

import com.eduagent.agent.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 多智能体协同编排引擎（StateGraph状态机）
 *
 * 改造要点:
 * 1. 使用 RequirementParser (LLM) 替代硬编码关键词匹配
 * 2. 每个Agent接收 AgentContext（含用户画像+短期记忆）
 * 3. 条件路由: 简单问题→RAG直接回答; 复杂问题→完整Agent管道
 * 4. 并行Fan-out: Knowledge/WebSearch/GraphGen 三个无依赖Agent并行执行
 * 5. 质量评估: 评估不通过→重新规划
 * 6. 状态持久化: 通过WorkflowState支持中断恢复
 */
@Slf4j
@Service
public class CoordinatorAgent {

    private final RequirementParser requirementParser;
    private final KnowledgeAgent knowledgeAgent;
    private final WebSearchAgent webSearchAgent;
    private final GraphGenerationAgent graphGenerationAgent;
    private final SummaryAgent summaryAgent;
    private final PathPlanningAgent pathPlanningAgent;
    private final ExerciseGenerationAgent exerciseGenerationAgent;
    private final ImageGenerationAgent imageGenerationAgent;
    private final CodePracticeAgent codePracticeAgent;
    private final ReviewAgent reviewAgent;
    private final AgentRegistry agentRegistry;
    private final java.util.concurrent.ThreadPoolExecutor agentExecutor;
    private final RagService ragService;
    private final UserProfileService userProfileService;
    private final ContentSafetyFilter contentSafetyFilter;
    private final ChatHistoryService chatHistoryService;
    private final MemoryService memoryService;

    public CoordinatorAgent(RequirementParser requirementParser,
                           KnowledgeAgent knowledgeAgent,
                           WebSearchAgent webSearchAgent,
                           GraphGenerationAgent graphGenerationAgent,
                           SummaryAgent summaryAgent,
                           PathPlanningAgent pathPlanningAgent,
                           ExerciseGenerationAgent exerciseGenerationAgent,
                           ImageGenerationAgent imageGenerationAgent,
                           CodePracticeAgent codePracticeAgent,
                           ReviewAgent reviewAgent,
                           RagService ragService,
                           UserProfileService userProfileService,
                           ContentSafetyFilter contentSafetyFilter,
                           ChatHistoryService chatHistoryService,
                           MemoryService memoryService,
                           AgentRegistry agentRegistry,
                           @org.springframework.beans.factory.annotation.Qualifier("agentExecutor")
                           java.util.concurrent.ThreadPoolExecutor agentExecutor) {
        this.requirementParser = requirementParser;
        this.knowledgeAgent = knowledgeAgent;
        this.webSearchAgent = webSearchAgent;
        this.graphGenerationAgent = graphGenerationAgent;
        this.summaryAgent = summaryAgent;
        this.pathPlanningAgent = pathPlanningAgent;
        this.exerciseGenerationAgent = exerciseGenerationAgent;
        this.imageGenerationAgent = imageGenerationAgent;
        this.codePracticeAgent = codePracticeAgent;
        this.reviewAgent = reviewAgent;
        this.ragService = ragService;
        this.userProfileService = userProfileService;
        this.contentSafetyFilter = contentSafetyFilter;
        this.chatHistoryService = chatHistoryService;
        this.memoryService = memoryService;
        this.agentRegistry = agentRegistry;
        this.agentExecutor = agentExecutor;
    }

    /**
     * 【核心入口】处理用户查询 —— 状态机驱动多Agent协同
     *
     * @param query  用户原始输入
     * @param userId 当前用户ID（用于加载画像和记忆）
     * @return 完整的协同结果
     */
    public Map<String, Object> processQuery(String query, Long userId) {
        return processQuery(query, userId, null);
    }

    public Map<String, Object> processQuery(String query, Long userId, java.util.function.Consumer<String> onResult) {
        AgentContext context = buildContext(query, userId);
        WorkflowState state = WorkflowState.create(query, userId, context);
        if (onResult != null) {
            state.setResultCallback((agentName, ar) -> { try { var m = new com.fasterxml.jackson.databind.ObjectMapper(); var line = new java.util.LinkedHashMap<String, Object>(); line.put("agent", agentName); line.put("status", ar.getStatus()); line.put("durationMs", ar.getDurationMs()); line.put("data", ar.getData()); line.put("message", ar.getMarkdownContent() != null ? ar.getMarkdownContent().substring(0, Math.min(200, ar.getMarkdownContent().length())) : ""); onResult.accept(m.writeValueAsString(line)); } catch (Exception e) {} });
            state.setStreamTokenCallback((agentName, token) -> { try { var m = new com.fasterxml.jackson.databind.ObjectMapper(); var line = new java.util.LinkedHashMap<String, Object>(); line.put("type", "stream"); line.put("agent", agentName); line.put("token", token); onResult.accept(m.writeValueAsString(line)); } catch (Exception e) {} });
            context.setStreamTokenCallback((agentName, token) -> { try { var m2 = new com.fasterxml.jackson.databind.ObjectMapper(); var line2 = new java.util.LinkedHashMap<String, Object>(); line2.put("type", "stream"); line2.put("agent", agentName); line2.put("token", token); onResult.accept(m2.writeValueAsString(line2)); } catch (Exception e2) {} });
        }

        log.info("=== 多智能体协同开始 === runId={}, userId={}, query={}", state.getRunId(), userId, query);

        // 入口安全检查
        ContentSafetyFilter.SafetyCheckResult safetyCheck = contentSafetyFilter.check(query);
        if (safetyCheck.isBlocked()) {
            log.warn("用户输入被安全拦截: runId={}, matched={}", state.getRunId(), safetyCheck.getMatchedItems());
            Map<String, Object> blockedResult = new LinkedHashMap<>();
            blockedResult.put("query", query);
            blockedResult.put("runId", state.getRunId());
            blockedResult.put("mode", "blocked");
            blockedResult.put("response", "您的问题包含不适当内容，已被安全系统拦截。如需学习帮助，请重新表述您的问题。");
            blockedResult.put("reason", safetyCheck.getMessage());
            return blockedResult;
        }

        try {
            // ===== Phase 1: PARSING - 需求解析 =====
            state.advanceTo(WorkflowState.Phase.PARSING);
            long parseStart = System.currentTimeMillis();
            Map<String, Object> parsedReqs = executeParsing(query, context);
            long parseDuration = System.currentTimeMillis() - parseStart;
            context.setParsedRequirements(parsedReqs);
            state.putAgentResult("统筹解析智能体",
                    AgentResult.success("统筹解析智能体", parsedReqs, buildParsingSummary(parsedReqs), parseDuration));

            // 条件路由: 简单闲聊 → 直接RAG回答，跳过完整Agent管道
            if (isSimpleQuery(parsedReqs)) {
                log.info("检测到简单查询，使用RAG直接回答");
                return handleSimpleQuery(query, state);
            }

            // ===== Phase 2: PLANNING - 路径规划 =====
            state.advanceTo(WorkflowState.Phase.PLANNING);
            AgentResult pathResult = pathPlanningAgent.plan(query, parsedReqs, context);
            state.putAgentResult("路径规划智能体", pathResult);

            // ===== Phase 3: EXECUTING - 并行执行 =====
            state.advanceTo(WorkflowState.Phase.EXECUTING);
            executeParallelAgents(query, parsedReqs, pathResult, context, state);

            // ===== Phase 4: EVALUATING - 审核关键内容（仅知识+练习） =====
            state.advanceTo(WorkflowState.Phase.EVALUATING);
            for (String keyAgent : new String[]{"知识库检索智能体", "练习题生成智能体"}) {
                AgentResult ar = state.getAgentOutputs().get(keyAgent);
                if (ar != null && !"error".equals(ar.getStatus())) {
                    try {
                        ReviewAgent.ReviewResult review = reviewAgent.reviewAgentOutput(ar, context);
                        if (review.isBlocked()) {
                            log.warn("ReviewAgent拦截: agent={}", keyAgent);
                        }
                    } catch (Exception e) { log.warn("审核失败: agent={}", keyAgent, e); }
                }
            }
            if (shouldReplan(state) && state.canRetry()) {
                state.markForReplan();
                state.advanceTo(WorkflowState.Phase.REPLANNING);
                pathResult = pathPlanningAgent.plan(query, parsedReqs, context);
                state.putAgentResult("路径规划智能体", pathResult);
            }

            // ===== Phase 5: EXERCISING（已在Phase 3中并行完成）=====
            // 练习题跟随知识Agent完成后立即启动，无需单独等待

            // ===== Phase 6: SUMMARIZING - 格式化总结 =====
            state.advanceTo(WorkflowState.Phase.SUMMARIZING);
            AgentResult summaryResult = summaryAgent.summarize(query, state.getAgentOutputs(), context, state);
            state.putAgentResult("格式化总结智能体", summaryResult);

            // ===== Phase 7: COMPLETE =====
            state.advanceTo(WorkflowState.Phase.COMPLETE);
            log.info("=== 多智能体协同完成 === runId={}, phases={}", state.getRunId(), state.getPhaseHistory().size());

            return buildFinalResult(query, state, summaryResult);

        } catch (Exception e) {
            log.error("多智能体协同异常: runId={}, error={}", state.getRunId(), e.getMessage(), e);
            return buildErrorResult(query, state, e.getMessage());
        }
    }

    /**
     * 兼容旧接口（无userId）
     */
    public Map<String, Object> processQuery(String query) {
        return processQuery(query, null);
    }

    // ==================== Phase Implementations ====================

    private Map<String, Object> executeParsing(String query, AgentContext context) {
        return requirementParser.parse(query, context);
    }

    /**
     * 并行执行 Phase 3 Agent:
     * Knowledge + WebSearch + Graph + Image + Code 并行启动
     * 练习题在知识Agent完成后立即启动（与搜索/图/图片并行）
     */
    private void executeParallelAgents(String query, Map<String, Object> parsedReqs,
                                        AgentResult pathResult, AgentContext context, WorkflowState state) {
        List<Runnable> tasks = new ArrayList<>();
        // 练习题Future引用，在知识Agent完成后提交，最后统一等待
        final java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<?>> exerciseFuture =
                new java.util.concurrent.atomic.AtomicReference<>();

        tasks.add(() -> {
            long start = System.currentTimeMillis();
            try {
                AgentResult result = knowledgeAgent.searchAndGenerate(query, parsedReqs, pathResult, context);
                state.putAgentResult("知识库检索智能体", result);
                // 将知识结果注入context，供练习题生成使用
                context.getPreviousOutputs().put("知识库检索智能体", result);
            } catch (Exception e) {
                log.error("知识库检索Agent失败: {}", e.getMessage());
                state.putAgentError("知识库检索智能体", e.getMessage());
            }
            // 知识Agent完成后，立即提交练习题生成（与搜索/图/图片并行）
            try {
                List<String> modules = extractModules(pathResult);
                java.util.concurrent.Future<?> exFuture = agentExecutor.submit(() -> {
                    try {
                        AgentResult exResult = exerciseGenerationAgent.generate(query, modules, context);
                        state.putAgentResult("练习题生成智能体", exResult);
                    } catch (Exception e) {
                        log.error("练习题生成Agent失败: {}", e.getMessage());
                        state.putAgentError("练习题生成智能体", e.getMessage());
                    }
                });
                exerciseFuture.set(exFuture);
            } catch (Exception e) {
                log.error("提交练习题生成任务失败: {}", e.getMessage());
            }
        });

        tasks.add(() -> {
            long start = System.currentTimeMillis();
            try {
                AgentResult result = webSearchAgent.searchAndSummarize(query, parsedReqs, context);
                state.putAgentResult("联网搜索智能体", result);
            } catch (Exception e) {
                log.error("联网搜索Agent失败: {}", e.getMessage());
                state.putAgentError("联网搜索智能体", e.getMessage());
            }
        });

        tasks.add(() -> {
            long start = System.currentTimeMillis();
            try {
                AgentResult result = graphGenerationAgent.generateGraph(query, state.getAgentOutputs(), context);
                state.putAgentResult("图生成智能体", result);
            } catch (Exception e) {
                log.error("图生成Agent失败: {}", e.getMessage());
                state.putAgentError("图生成智能体", e.getMessage());
            }
        });

        // 图片生成任务（第4个并行Agent）
        tasks.add(() -> {
            long start = System.currentTimeMillis();
            try {
                AgentResult result = imageGenerationAgent.generate(query,
                        context.getParsedRequirements(), context);
                state.putAgentResult("图片生成智能体", result);
            } catch (Exception e) {
                log.error("图片生成Agent失败: {}", e.getMessage());
                state.putAgentError("图片生成智能体", e.getMessage());
            }
        });

        // 代码实操案例生成（有编程相关内容时生成）
        tasks.add(() -> {
            try {
                String subject = parsedReqs != null ? (String) parsedReqs.getOrDefault("subject", "") : "";
                if (subject.contains("编程") || subject.contains("Python") || subject.contains("Java") ||
                    subject.contains("代码") || subject.contains("算法") || subject.contains("数据结构")) {
                    AgentResult result = codePracticeAgent.generate(subject, "进阶", context);
                    state.putAgentResult("代码实操智能体", result);
                }
            } catch (Exception e) {
                log.error("代码实操Agent失败: {}", e.getMessage());
            }
        });

        // 并行执行（使用线程池）
        try {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (Runnable task : tasks) {
                futures.add(agentExecutor.submit(task));
            }
            // 等待所有Agent完成，每个最多等待30秒
            for (java.util.concurrent.Future<?> future : futures) {
                try {
                    future.get(30, java.util.concurrent.TimeUnit.SECONDS);
                } catch (java.util.concurrent.TimeoutException e) {
                    log.warn("Agent执行超时(30s)，返回降级结果");
                    future.cancel(true);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Agent执行被中断");
                    try {
                        future.get(15, java.util.concurrent.TimeUnit.SECONDS);
                    } catch (Exception retryEx) {
                        log.error("Agent重试也失败: {}", retryEx.getMessage());
                    }
                }
            }
            // 等待练习题生成（在知识Agent完成后才提交，可能还在跑）
            java.util.concurrent.Future<?> exF = exerciseFuture.get();
            if (exF != null) {
                try {
                    exF.get(30, java.util.concurrent.TimeUnit.SECONDS);
                } catch (java.util.concurrent.TimeoutException e) {
                    log.warn("练习题生成超时(30s)");
                    exF.cancel(true);
                } catch (Exception e) {
                    log.warn("等待练习题生成异常: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("并行执行部分失败，降级为串行: {}", e.getMessage());
            tasks.forEach(Runnable::run);
        }
    }

    // ==================== Conditional Routing ====================

    /**
     * 判断是否为简单查询（可以跳过完整Agent管道）
     */
    private boolean isSimpleQuery(Map<String, Object> parsedReqs) {
        String subject = (String) parsedReqs.getOrDefault("subject", "");
        String goal = (String) parsedReqs.getOrDefault("goal", "");
        // "综合学习" + 无明确学习目标 → 可能是闲聊或简单问答
        return "综合学习".equals(subject) && "综合提升".equals(goal);
    }

    /**
     * 简单查询: 直接RAG回答
     */
    private Map<String, Object> handleSimpleQuery(String query, WorkflowState state) {
        Map<String, Object> ragResult = ragService.answer(query, "学习助手");
        state.advanceTo(WorkflowState.Phase.COMPLETE);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", query);
        result.put("response", ragResult.get("response"));
        result.put("mode", "simple_rag");
        result.put("agentResults", state.getPhaseHistory());
        result.put("executionSteps", buildExecutionSteps(state));
        return result;
    }

    // ==================== Quality Evaluation ====================

    private boolean shouldReplan(WorkflowState state) {
        AgentResult pathResult = state.getAgentOutputs().get("路径规划智能体");
        AgentResult knowledgeResult = state.getAgentOutputs().get("知识库检索智能体");

        // 如果路径规划或知识检索完全失败，需要重新规划
        if (pathResult != null && "error".equals(pathResult.getStatus())) return true;
        if (knowledgeResult != null && "error".equals(knowledgeResult.getStatus())) return true;

        return false;
    }

    // ==================== Helper Methods ====================

    private AgentContext buildContext(String query, Long userId) {
        AgentContext.AgentContextBuilder builder = AgentContext.builder()
                .query(query)
                .userId(userId)
                .previousOutputs(new LinkedHashMap<>());

        if (userId != null) {
            try {
                Map<String, Object> profile = userProfileService.analyzeProfile(userId);
                builder.userProfile(profile);
            } catch (Exception e) {
                log.warn("加载用户画像失败: {}", e.getMessage());
                builder.userProfile(Map.of());
            }
        } else {
            builder.userProfile(Map.of());
        }

        // 加载对话上下文（完整轮次 + 压缩摘要）
        try {
            if (userId != null) {
                // 1. 加载压缩后的远期摘要
                String memorySummary = memoryService.loadMemorySummary(userId);
                builder.memorySummary(memorySummary);

                // 2. 加载最近N条完整对话（user + assistant）
                List<com.eduagent.entity.ChatHistory> recent = chatHistoryService.getHistory(userId, 0L);
                if (recent != null && !recent.isEmpty()) {
                    // 取最后20条（约10轮对话）构建完整对话记录
                    int contextSize = Math.min(recent.size(), 20);
                    List<com.eduagent.entity.ChatHistory> contextMessages =
                            recent.subList(recent.size() - contextSize, recent.size());

                    StringBuilder history = new StringBuilder();
                    List<String> topics = new java.util.ArrayList<>();
                    for (com.eduagent.entity.ChatHistory msg : contextMessages) {
                        if ("user".equals(msg.getRole())) {
                            String content = msg.getContent() != null ? msg.getContent() : "";
                            if (content.length() > 200) content = content.substring(0, 200) + "...";
                            history.append("用户: ").append(content).append("\n");
                            // 收集话题词
                            if (content.length() > 2) topics.add(content.length() > 30
                                    ? content.substring(0, 30) : content);
                        } else if ("assistant".equals(msg.getRole())) {
                            String content = cleanAssistantContent(msg.getContent());
                            history.append("助手: ").append(content).append("\n");
                        }
                    }
                    builder.conversationHistory(history.toString());

                    // 话题词：取后6条user消息
                    List<String> userTopics = topics.stream()
                            .filter(t -> t.length() < 200)
                            .collect(java.util.stream.Collectors.toList());
                    if (userTopics.size() > 6)
                        userTopics = userTopics.subList(userTopics.size() - 6, userTopics.size());
                    builder.recentTopics(userTopics);
                } else {
                    builder.conversationHistory("");
                    builder.recentTopics(List.of());
                }
            } else {
                builder.conversationHistory("");
                builder.recentTopics(List.of());
            }
        } catch (Exception e) {
            log.warn("加载对话上下文失败: {}", e.getMessage());
            builder.conversationHistory("");
            builder.recentTopics(List.of());
        }

        return builder.build();
    }

    /** 清洗assistant消息：去掉STRUCTURED_DATA标记 */
    private String cleanAssistantContent(String content) {
        if (content == null) return "";
        int markerIdx = content.indexOf("<!--STRUCTURED_DATA-->");
        if (markerIdx >= 0) {
            content = content.substring(0, markerIdx).trim();
        }
        if (content.length() > 300) {
            content = content.substring(0, 300) + "...";
        }
        return content;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractModules(AgentResult pathResult) {
        List<String> modules = new ArrayList<>();
        try {
            if (pathResult != null && pathResult.getData() instanceof Map) {
                Map<String, Object> data = (Map<String, Object>) pathResult.getData();
                Object stages = data.get("stages");
                if (stages instanceof List) {
                    for (Object stage : (List<Object>) stages) {
                        if (stage instanceof Map) {
                            Object mods = ((Map<String, Object>) stage).get("modules");
                            if (mods instanceof List) {
                                for (Object m : (List<Object>) mods) {
                                    modules.add(m.toString());
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("提取模块列表失败: {}", e.getMessage());
        }
        return modules.isEmpty() ? List.of("基础概念", "核心原理", "综合练习") : modules;
    }

    private String buildParsingSummary(Map<String, Object> parsedReqs) {
        return String.format("**学习科目**: %s | **水平**: %s | **目标**: %s",
                parsedReqs.getOrDefault("subject", "未识别"),
                parsedReqs.getOrDefault("level", "未识别"),
                parsedReqs.getOrDefault("goal", "未识别"));
    }

    private Map<String, Object> buildFinalResult(String query, WorkflowState state, AgentResult summaryResult) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", query);
        result.put("runId", state.getRunId());
        result.put("mode", "full_agent_pipeline");
        result.put("response", summaryResult != null ? summaryResult.getMarkdownContent() : "");
        result.put("requirements", state.getContext().getParsedRequirements());

        // 各Agent的原始输出
        state.getAgentOutputs().forEach((name, agentResult) -> {
            result.put(toCamelCase(name), agentResult.getData());
        });

        result.put("agentResults", state.getAgentOutputs());
        result.put("executionSteps", buildExecutionSteps(state));
        result.put("phaseHistory", state.getPhaseHistory());

        return result;
    }

    private Map<String, Object> buildErrorResult(String query, WorkflowState state, String errorMessage) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", query);
        result.put("runId", state.getRunId());
        result.put("mode", "error");
        result.put("response", "抱歉，处理您的问题时遇到了问题: " + errorMessage);
        result.put("error", errorMessage);
        result.put("executionSteps", buildExecutionSteps(state));
        return result;
    }

    private List<Map<String, Object>> buildExecutionSteps(WorkflowState state) {
        List<Map<String, Object>> steps = new ArrayList<>();
        for (WorkflowState.PhaseLog log : state.getPhaseHistory()) {
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("agent", log.getAgentName());
            step.put("phase", log.getPhase().getDisplayName());
            step.put("status", log.isSuccess() ? "success" : "error");
            step.put("durationMs", log.getDurationMs());
            steps.add(step);
        }
        return steps;
    }

    private String toCamelCase(String chineseName) {
        return switch (chineseName) {
            case "统筹解析智能体" -> "requirements";
            case "路径规划智能体" -> "learningPath";
            case "知识库检索智能体" -> "knowledge";
            case "联网搜索智能体" -> "webSearch";
            case "图生成智能体" -> "graph";
            case "图片生成智能体" -> "illustrations";
            case "代码实操智能体" -> "codePractice";
            case "练习题生成智能体" -> "exercises";
            case "格式化总结智能体" -> "summary";
            default -> chineseName;
        };
    }
}
