package com.eduagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eduagent.entity.IntentRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface IntentRuleMapper extends BaseMapper<IntentRule> {

    @Select("SELECT * FROM intent_rule WHERE status = 'ACTIVE' ORDER BY priority ASC")
    List<IntentRule> selectActiveRules();

    @Select("SELECT * FROM intent_rule WHERE target_base_id = #{baseId} AND status = 'ACTIVE'")
    List<IntentRule> selectByBaseId(@Param("baseId") Long baseId);
}