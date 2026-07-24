package com.eduagent.repository;

import com.eduagent.entity.StudyGoal;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface StudyGoalRepository extends BaseMapper<StudyGoal> {
    
    @Select("SELECT * FROM study_goals WHERE user_id = #{userId}")
    List<StudyGoal> findByUserId(Long userId);
}