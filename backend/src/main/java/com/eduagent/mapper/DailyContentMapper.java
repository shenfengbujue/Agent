package com.eduagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eduagent.entity.DailyContent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DailyContentMapper extends BaseMapper<DailyContent> {

    @Select("SELECT * FROM daily_learning_content WHERE goal_id = #{goalId} AND stage_index = #{stageIndex} ORDER BY day_index")
    List<DailyContent> selectByGoalAndStage(Long goalId, Integer stageIndex);

    @Select("SELECT * FROM daily_learning_content WHERE goal_id = #{goalId} AND stage_index = #{stageIndex} AND day_index = #{dayIndex}")
    DailyContent selectByGoalStageDay(Long goalId, Integer stageIndex, Integer dayIndex);
}
