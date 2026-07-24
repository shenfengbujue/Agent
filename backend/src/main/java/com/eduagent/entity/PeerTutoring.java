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
@TableName("peer_tutoring")
public class PeerTutoring {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String requesterId;

    private String tutorId;

    private String topic;

    private String description;

    private String status;

    private Integer rating;

    private String feedback;

    private LocalDateTime createdAt;
}