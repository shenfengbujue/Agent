package com.eduagent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("study_goals")
public class StudyGoal {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    private String title;

    @TableField("goal_icon")
    private String icon;

    private String category;

    private Integer progress = 0;

    private String color;

    @TableField("resources")
    private String resources;

    @TableField("current_resource_index")
    private Integer currentResourceIndex = 0;

    @TableField("completed_resources")
    private String completedResources;

    /** 学习路径规划结果（PathPlanningAgent生成的stages JSON） */
    @TableField("learning_path")
    private String learningPath;

    /** 当前学习阶段索引 */
    @TableField("current_stage_index")
    private Integer currentStageIndex = 0;

    @TableField("last_study_time")
    private LocalDateTime lastStudyTime;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}