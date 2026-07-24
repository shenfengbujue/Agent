package com.eduagent.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 工作流状态机核心State对象
 * 贯穿整个多Agent编排过程，支持中断恢复
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowState {

    /** 本次执行唯一ID */
    private String runId;

    /** 用户ID */
    private Long userId;

    /** 用户原始query */
    private String query;

    /** Agent上下文 */
    private AgentContext context;

    /** 当前执行阶段 */
    private Phase currentPhase;

    /** 所有Agent的输出（key=agentName） — 使用ConcurrentHashMap保证线程安全 */
    private Map<String, AgentResult> agentOutputs;

    /** 阶段执行历史 — 使用线程安全列表 */
    private List<PhaseLog> phaseHistory;

    /** 是否需要重新规划（评估不通过时设为true） */
    private boolean needsReplan;

    /** 重试次数 */
    private int retryCount;

    /** 最大重试次数 */
    private static final int MAX_RETRIES = 2;

    /** 执行开始时间 */
    private LocalDateTime startedAt;

    /** 前端SSE回调（推送进度用） */
    private transient ProgressCallback progressCallback;
    private transient ResultCallback resultCallback;
    private transient StreamTokenCallback streamTokenCallback;
    public void setResultCallback(ResultCallback cb) { this.resultCallback = cb; }
    public void setStreamTokenCallback(StreamTokenCallback cb) { this.streamTokenCallback = cb; }
    public void pushStreamToken(String agentName, String token) {
        if (streamTokenCallback != null) streamTokenCallback.onToken(agentName, token);
    }

    // ---- 工厂方法 ----

    public static WorkflowState create(String query, Long userId, AgentContext context) {
        return WorkflowState.builder()
                .runId(UUID.randomUUID().toString().substring(0, 8))
                .userId(userId)
                .query(query)
                .context(context)
                .currentPhase(Phase.IDLE)
                .agentOutputs(new java.util.concurrent.ConcurrentHashMap<>())
                .phaseHistory(java.util.Collections.synchronizedList(new ArrayList<>()))
                .needsReplan(false)
                .retryCount(0)
                .startedAt(LocalDateTime.now())
                .build();
    }

    // ---- 状态操作 ----

    /**
     * 推进到下一阶段
     */
    public void advanceTo(Phase nextPhase) {
        this.currentPhase = nextPhase;
        pushProgress(nextPhase, "开始执行");
    }

    /**
     * 存储Agent执行结果
     */
    public void putAgentResult(String agentName, AgentResult result) {
        this.agentOutputs.put(agentName, result);
        phaseHistory.add(PhaseLog.success(agentName, currentPhase, result.getDurationMs()));
        pushProgress(currentPhase, agentName + " 完成 (" + result.getDurationMs() + "ms)");
        if (resultCallback != null) resultCallback.onAgentResult(agentName, result);
    }

    /**
     * 记录Agent执行失败
     */
    public void putAgentError(String agentName, String errorMessage) {
        phaseHistory.add(PhaseLog.error(agentName, currentPhase, errorMessage));
        pushProgress(currentPhase, agentName + " 失败: " + errorMessage);
    }

    /**
     * 是否需要重试
     */
    public boolean canRetry() {
        return retryCount < MAX_RETRIES;
    }

    /**
     * 标记需要重新规划并增加重试计数
     */
    public void markForReplan() {
        this.needsReplan = true;
        this.retryCount++;
        pushProgress(currentPhase, "质量评估不通过，第" + retryCount + "次重新规划");
    }

    /**
     * 获取已完成Agent的输出列表（用于构建上下文）
     */
    public List<Map<String, Object>> getCompletedAgentOutputs() {
        List<Map<String, Object>> outputs = new ArrayList<>();
        for (PhaseLog log : phaseHistory) {
            if (log.success && agentOutputs.containsKey(log.agentName)) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("agent", log.agentName);
                entry.put("phase", log.phase.name());
                entry.put("data", agentOutputs.get(log.agentName).getData());
                outputs.add(entry);
            }
        }
        return outputs;
    }

    private void pushProgress(Phase phase, String message) {
        if (progressCallback != null) {
            progressCallback.onProgress(phase, message, this);
        }
    }

    // ---- 执行阶段枚举 ----

    public enum Phase {
        IDLE("空闲"),
        PARSING("需求解析"),
        PLANNING("路径规划"),
        EXECUTING("并行执行"),
        EVALUATING("质量评估"),
        REPLANNING("重新规划"),
        EXERCISING("练习题生成"),
        SUMMARIZING("格式化总结"),
        COMPLETE("完成"),
        ERROR("错误");

        private final String displayName;

        Phase(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // ---- 阶段日志 ----

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PhaseLog {
        private String agentName;
        private Phase phase;
        private boolean success;
        private long durationMs;
        private String errorMessage;
        private LocalDateTime timestamp;

        public static PhaseLog success(String agentName, Phase phase, long durationMs) {
            return new PhaseLog(agentName, phase, true, durationMs, null, LocalDateTime.now());
        }

        public static PhaseLog error(String agentName, Phase phase, String errorMessage) {
            return new PhaseLog(agentName, phase, false, 0, errorMessage, LocalDateTime.now());
        }
    }

    // ---- 进度回调接口 ----

    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(Phase phase, String message, WorkflowState state);
    }

    @FunctionalInterface
    public interface ResultCallback {
        void onAgentResult(String agentName, AgentResult result);
    }

    @FunctionalInterface
    public interface StreamTokenCallback {
        void onToken(String agentName, String token);
    }

}
