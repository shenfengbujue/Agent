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
@TableName("intent_rule")
public class IntentRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String pattern;

    private String patternType;

    private String intentType;

    private Long targetBaseId;

    private Integer priority;

    private String description;

    private String status;

    private LocalDateTime createdAt;
}