package com.eduagent.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent统一返回对象
 * 所有Agent的输出都通过此类标准化
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentResult {

    /** Agent名称（与前端展示一致） */
    private String agentName;

    /** 执行状态: success / error / degraded */
    private String status;

    /** 结构化数据（给下游Agent使用） */
    private Object data;

    /** Markdown内容（给前端直接展示） */
    private String markdownContent;

    /** 执行耗时(毫秒) */
    private long durationMs;

    /** 错误信息（status=error时） */
    private String errorMessage;

    /** 是否使用了降级方案 */
    private boolean degraded;

    // ---- 工厂方法 ----

    public static AgentResult success(String agentName, Object data, String markdownContent, long durationMs) {
        return AgentResult.builder()
                .agentName(agentName)
                .status("success")
                .data(data)
                .markdownContent(markdownContent)
                .durationMs(durationMs)
                .degraded(false)
                .build();
    }

    public static AgentResult degraded(String agentName, Object data, String markdownContent, long durationMs) {
        return AgentResult.builder()
                .agentName(agentName)
                .status("success")
                .data(data)
                .markdownContent(markdownContent)
                .durationMs(durationMs)
                .degraded(true)
                .build();
    }

    public static AgentResult error(String agentName, String errorMessage, long durationMs) {
        return AgentResult.builder()
                .agentName(agentName)
                .status("error")
                .errorMessage(errorMessage)
                .durationMs(durationMs)
                .degraded(false)
                .build();
    }
}
