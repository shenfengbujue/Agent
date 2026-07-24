package com.eduagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户画像维度实体（结构化存储）
 *
 * 替代原有 UserProfile.preferences 字符串解析模式，
 * 每个维度独立存储为一行记录，支持:
 * - 按维度查询（如"所有视觉型学习者"）
 * - 置信度标记（LLM推断 vs 用户手动填写）
 * - 来源追踪（对话推断/问卷填写/进度行为）
 * - 动态新增维度（无需改表结构）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("profile_dimensions")
public class ProfileDimension {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    @TableField("user_id")
    private Long userId;

    /** 维度键（如 "knowledgeLevel", "cognitiveStyle", "errorPatterns"） */
    @TableField("dimension_key")
    private String dimensionKey;

    /** 维度值（如 "中级", "视觉型", "概念混淆"） */
    @TableField("dimension_value")
    private String dimensionValue;

    /** 维度中文标签（如 "知识基础", "认知风格"） */
    @TableField("dimension_label")
    private String dimensionLabel;

    /** 置信度 (0.0 ~ 1.0)，LLM推断的值通常 < 1.0 */
    @TableField("confidence")
    private Double confidence;

    /** 数据来源: QUESTIONNAIRE(问卷) / DIALOGUE_INFERRED(对话推断) / PROGRESS(进度行为) / MANUAL(手动) / MIGRATED(迁移) */
    @TableField("source")
    private String source;

    /** 创建时间 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    // ==================== 预定义维度常量 ====================

    /** 知识基础 */
    public static final String DIM_KNOWLEDGE_LEVEL = "knowledgeLevel";
    /** 认知风格 */
    public static final String DIM_COGNITIVE_STYLE = "cognitiveStyle";
    /** 易错模式 */
    public static final String DIM_ERROR_PATTERNS = "errorPatterns";
    /** 学习动机 */
    public static final String DIM_MOTIVATION = "motivation";
    /** 时间偏好 */
    public static final String DIM_TIME_PREFERENCE = "timePreference";
    /** 社交倾向 */
    public static final String DIM_SOCIAL_TENDENCY = "socialTendency";
    /** 学习节奏 */
    public static final String DIM_LEARNING_PACE = "learningPace";
    /** 完成率 */
    public static final String DIM_COMPLETION_RATE = "completionRate";

    // ==================== 预定义维度标签 ====================

    public static final java.util.Map<String, String> DIMENSION_LABELS = java.util.Map.ofEntries(
            java.util.Map.entry(DIM_KNOWLEDGE_LEVEL, "知识基础"),
            java.util.Map.entry(DIM_COGNITIVE_STYLE, "认知风格"),
            java.util.Map.entry(DIM_ERROR_PATTERNS, "易错模式"),
            java.util.Map.entry(DIM_MOTIVATION, "学习动机"),
            java.util.Map.entry(DIM_TIME_PREFERENCE, "时间偏好"),
            java.util.Map.entry(DIM_SOCIAL_TENDENCY, "社交倾向"),
            java.util.Map.entry(DIM_LEARNING_PACE, "学习节奏"),
            java.util.Map.entry(DIM_COMPLETION_RATE, "完成率")
    );

    // ==================== 数据来源常量 ====================

    public static final String SOURCE_QUESTIONNAIRE = "QUESTIONNAIRE";
    public static final String SOURCE_DIALOGUE_INFERRED = "DIALOGUE_INFERRED";
    public static final String SOURCE_PROGRESS = "PROGRESS";
    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String SOURCE_MIGRATED = "MIGRATED";

    /**
     * 便捷工厂方法
     */
    public static ProfileDimension of(Long userId, String key, String value, String source, Double confidence) {
        return ProfileDimension.builder()
                .userId(userId)
                .dimensionKey(key)
                .dimensionValue(value)
                .dimensionLabel(DIMENSION_LABELS.getOrDefault(key, key))
                .source(source)
                .confidence(confidence != null ? confidence : 1.0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
