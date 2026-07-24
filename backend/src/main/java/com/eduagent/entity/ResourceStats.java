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
@TableName("resource_stats")
public class ResourceStats {

    @TableId(type = IdType.INPUT)
    private Long resourceId;

    private Integer likeCount;

    private Integer viewCount;

    private LocalDateTime updatedAt;
}
