package com.eduagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eduagent.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Update("UPDATE users SET " +
            "profile_data = #{profileData}, " +
            "learning_goal = #{learningGoal}, " +
            "time_availability = #{timeAvailability}, " +
            "learning_style = #{learningStyle}, " +
            "work_pain_points = #{workPainPoints}, " +
            "skill_level = #{skillLevel}, " +
            "exam_time = #{examTime}, " +
            "knowledge_level = #{knowledgeLevel}, " +
            "weak_points = #{weakPoints}, " +
            "motivation = #{motivation}, " +
            "achievement_style = #{achievementStyle}, " +
            "social_willingness = #{socialWillingness}, " +
            "frustration_handling = #{frustrationHandling}, " +
            "profile_updated_at = NOW() " +
            "WHERE id = #{userId}")
    int updateProfile(@Param("userId") Long userId,
                      @Param("profileData") String profileData,
                      @Param("learningGoal") String learningGoal,
                      @Param("timeAvailability") String timeAvailability,
                      @Param("learningStyle") String learningStyle,
                      @Param("workPainPoints") String workPainPoints,
                      @Param("skillLevel") String skillLevel,
                      @Param("examTime") String examTime,
                      @Param("knowledgeLevel") String knowledgeLevel,
                      @Param("weakPoints") String weakPoints,
                      @Param("motivation") String motivation,
                      @Param("achievementStyle") String achievementStyle,
                      @Param("socialWillingness") String socialWillingness,
                      @Param("frustrationHandling") String frustrationHandling);
}