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
@TableName("study_comment")
public class StudyComment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long postId;

    private String userId;

    private String content;

    private Long parentId;

    private Boolean isAccepted;

    private LocalDateTime createdAt;
}