package com.eduagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eduagent.entity.ProfileDimension;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 画像维度Mapper
 */
@Mapper
public interface ProfileDimensionMapper extends BaseMapper<ProfileDimension> {

    /**
     * 查询用户所有画像维度
     */
    @Select("SELECT * FROM profile_dimensions WHERE user_id = #{userId} ORDER BY dimension_key")
    List<ProfileDimension> selectByUserId(@Param("userId") Long userId);

    /**
     * 查询用户指定维度的值
     */
    @Select("SELECT * FROM profile_dimensions WHERE user_id = #{userId} AND dimension_key = #{dimensionKey}")
    ProfileDimension selectByUserIdAndKey(@Param("userId") Long userId, @Param("dimensionKey") String dimensionKey);

    /**
     * Upsert: 同维度存在则更新，不存在则插入
     */
    @Insert("INSERT INTO profile_dimensions (user_id, dimension_key, dimension_value, dimension_label, confidence, source, created_at, updated_at) " +
            "VALUES (#{userId}, #{dimensionKey}, #{dimensionValue}, #{dimensionLabel}, #{confidence}, #{source}, NOW(), NOW()) " +
            "ON DUPLICATE KEY UPDATE dimension_value = VALUES(dimension_value), dimension_label = VALUES(dimension_label), " +
            "confidence = VALUES(confidence), source = VALUES(source), updated_at = NOW()")
    int upsert(ProfileDimension dimension);

    /**
     * 批量查询用户画像维度
     */
    @Select("<script>" +
            "SELECT * FROM profile_dimensions WHERE user_id = #{userId} AND dimension_key IN " +
            "<foreach collection='keys' item='key' open='(' separator=',' close=')'>#{key}</foreach>" +
            "</script>")
    List<ProfileDimension> selectByUserIdAndKeys(@Param("userId") Long userId, @Param("keys") List<String> keys);

    /**
     * 按维度键和值查询用户列表（如"所有视觉型学习者"）
     */
    @Select("SELECT DISTINCT user_id FROM profile_dimensions WHERE dimension_key = #{dimensionKey} AND dimension_value = #{dimensionValue}")
    List<Long> findUserIdsByDimension(@Param("dimensionKey") String dimensionKey, @Param("dimensionValue") String dimensionValue);

    /**
     * 删除用户所有画像维度（迁移前清理用）
     */
    @Delete("DELETE FROM profile_dimensions WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);
}
