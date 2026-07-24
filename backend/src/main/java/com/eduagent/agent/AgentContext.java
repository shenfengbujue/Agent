package com.eduagent.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Agent共享上下文对象
 * 贯穿整个编排过程，每个Agent从此对象读取所需信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentContext {

    /** 当前用户ID */
    private Long userId;

    /** 用户原始输入query */
    private String query;

    /** 用户画像（从UserProfileService加载） */
    private Map<String, Object> userProfile;

    /** 活跃学习目标 */
    private List<Map<String, Object>> activeGoals;

    /** 最近学习的主题（短期记忆，最多10条） */
    private List<String> recentTopics;

    /** 格式化后的近期对话记录（user+assistant完整轮次） */
    private String conversationHistory;

    /** 压缩后的远期对话摘要（由MemoryService生成） */
    private String memorySummary;

    /** 上游解析结果（统筹解析Agent的结构化输出） */
    private Map<String, Object> parsedRequirements;

    /** 前序Agent的输出（全局数据总线，key=agentName） */
    private Map<String, AgentResult> previousOutputs;

    /** 当前请求的会话ID */
    private String sessionId;

    /** 流式token回调（供Agent逐token推送） */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private java.util.function.BiConsumer<String, String> streamTokenCallback;

    // ---- 便捷方法 ----

    /**
     * 获取画像中的某个字段
     */
    public String getProfileField(String key) {
        if (userProfile == null) return null;
        Object val = userProfile.get(key);
        return val != null ? val.toString() : null;
    }

    /**
     * 获取画像关键词列表
     */
    @SuppressWarnings("unchecked")
    public List<String> getProfileKeywords() {
        if (userProfile == null) return List.of();
        Object kw = userProfile.get("keywords");
        if (kw instanceof List) return (List<String>) kw;
        return List.of();
    }

    /**
     * 获取画像兴趣列表
     */
    @SuppressWarnings("unchecked")
    public List<String> getProfileInterests() {
        if (userProfile == null) return List.of();
        Object interests = userProfile.get("interests");
        if (interests instanceof List) return (List<String>) interests;
        return List.of();
    }

    /**
     * 构建用户画像文本摘要（注入Prompt用）
     */
    public String buildProfileSummary() {
        if (userProfile == null || userProfile.isEmpty()) {
            return "暂无用户画像数据";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("用户画像:\n");
        userProfile.forEach((k, v) -> {
            if (v != null && !v.toString().isEmpty()) {
                sb.append("- ").append(k).append(": ").append(v).append("\n");
            }
        });
        return sb.toString();
    }

    /**
     * 构建短期记忆摘要（注入Prompt用）
     */
    public String buildMemorySummary() {
        if (recentTopics == null || recentTopics.isEmpty()) {
            return "暂无近期学习记录";
        }
        return "近期学习主题: " + String.join(", ", recentTopics);
    }

    /**
     * 构建近期对话记录文本（注入Prompt用）
     * 包含完整的 user+assistant 对话轮次
     */
    public String buildConversationHistory() {
        if (conversationHistory == null || conversationHistory.isEmpty()) {
            return "暂无对话历史";
        }
        return conversationHistory;
    }

    /**
     * 构建完整记忆上下文（压缩摘要 + 近期对话）
     * 取代原 buildMemorySummary() 供各 Agent prompt 调用
     */
    public String buildFullMemoryContext() {
        StringBuilder sb = new StringBuilder();
        if (memorySummary != null && !memorySummary.isEmpty()) {
            sb.append("【历史学习摘要】\n").append(memorySummary).append("\n\n");
        }
        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            sb.append("【近期对话记录】\n").append(conversationHistory);
        } else if ((memorySummary == null || memorySummary.isEmpty())) {
            sb.append("暂无对话记录");
        }
        return sb.toString();
    }
}
