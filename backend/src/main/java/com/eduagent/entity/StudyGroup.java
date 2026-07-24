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
@TableName("study_group")
public class StudyGroup {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String groupName;

    private String description;

    private String course;

    private String creatorId;

    private Integer memberCount;

    private Integer maxMembers;

    private Integer postCount;

    private String status;

    private LocalDateTime createdAt;
}