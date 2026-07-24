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
@TableName("group_resources")
public class GroupResource {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long groupId;

    private String uploaderId;

    private String title;

    private String description;

    private String resourceType;

    private String fileUrl;

    private String fileName;

    private Long fileSize;

    private LocalDateTime createdAt;
}
