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
@TableName("study_post")
public class StudyPost {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long groupId;

    private String userId;

    private String title;

    private String content;

    private String postType;

    private Integer likeCount;

    private Integer commentCount;

    private Integer viewCount;

    private Boolean isPinned;

    private Boolean isSolved;

    private LocalDateTime createdAt;
}