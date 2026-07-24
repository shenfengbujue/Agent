package com.eduagent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("daily_learning_content")
public class DailyContent {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("goal_id")
    private Long goalId;

    @TableField("stage_index")
    private Integer stageIndex;

    @TableField("day_index")
    private Integer dayIndex;

    /** 知识点 JSON: [{title, basic, keyPoints, pitfalls}] */
    private String knowledge;

    /** 随堂练习 JSON */
    private String exercises;

    /** 综合测试 JSON */
    private String comprehensiveTest;

    /** PENDING / GENERATING / GENERATED / COMPLETED */
    private String status;

    private Integer score;

    @TableField("time_spent")
    private Integer timeSpent;

    /** 薄弱点 JSON */
    @TableField("weak_points")
    private String weakPoints;

    @TableField("generated_at")
    private LocalDateTime generatedAt;

    @TableField("completed_at")
    private LocalDateTime completedAt;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
