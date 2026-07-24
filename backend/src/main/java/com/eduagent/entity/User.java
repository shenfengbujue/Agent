package com.eduagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("users")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String password;

    private String nickname;

    private String email;

    private String role;

    private String status;

    private String profileData;

    private String learningGoal;

    private String timeAvailability;

    private String learningStyle;

    private String workPainPoints;

    private String skillLevel;

    private String examTime;

    private String knowledgeLevel;

    private String weakPoints;

    private String motivation;

    private String achievementStyle;

    private String socialWillingness;

    private String frustrationHandling;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDateTime createdAt;

    private LocalDateTime profileUpdatedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime lastLogin;

    private Integer loginDays;

    private String lastLoginDate;

    private String level;
}