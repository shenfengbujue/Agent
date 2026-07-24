package com.eduagent.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginVO {

    private String token;

    private Long userId;

    private String username;

    private String nickname;

    private String role;

    private LocalDateTime createdAt;

    private LocalDateTime lastLogin;
    
    private Integer loginDays;
    
    private String level;
}