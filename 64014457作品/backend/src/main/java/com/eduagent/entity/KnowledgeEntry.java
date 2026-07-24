package com.eduagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowledge_entry")
public class KnowledgeEntry {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long baseId;

    private String title;

    private String content;

    private String category;

    private String subModule;

    private String metadata;

    private String embedding;

    /** 条目类型: KNOWLEDGE(普通) / LEARNING_PLAN(AI方案) */
    @com.baomidou.mybatisplus.annotation.TableField("entry_type")
    private String entryType;

    /** AI学习方案结构化数据JSON */
    @com.baomidou.mybatisplus.annotation.TableField("plan_data")
    private String planData;

    /** 方案归属用户 */
    @com.baomidou.mybatisplus.annotation.TableField("owner_id")
    private Long ownerId;

    private String tags;

    @com.baomidou.mybatisplus.annotation.TableField("difficulty_level")
    private String difficultyLevel;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}