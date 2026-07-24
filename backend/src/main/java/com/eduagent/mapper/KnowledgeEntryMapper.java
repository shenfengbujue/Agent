package com.eduagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eduagent.entity.KnowledgeEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KnowledgeEntryMapper extends BaseMapper<KnowledgeEntry> {

    @Select("SELECT * FROM knowledge_entry WHERE base_id = #{baseId} ORDER BY created_at DESC")
    List<KnowledgeEntry> selectByBaseId(@Param("baseId") Long baseId);

    @Select("SELECT * FROM knowledge_entry WHERE base_id = #{baseId} AND category = #{category} ORDER BY created_at DESC")
    List<KnowledgeEntry> selectByBaseIdAndCategory(@Param("baseId") Long baseId, @Param("category") String category);
}