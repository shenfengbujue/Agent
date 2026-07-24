package com.eduagent.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileUpdateRequest {

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

    private String profileData;
}