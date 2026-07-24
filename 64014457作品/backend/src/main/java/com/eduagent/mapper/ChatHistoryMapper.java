package com.eduagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eduagent.entity.ChatHistory;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChatHistoryMapper extends BaseMapper<ChatHistory> {

    @Select("SELECT * FROM chat_history WHERE user_id = #{userId} AND agent_id = #{agentId} ORDER BY created_at ASC LIMIT 100")
    List<ChatHistory> selectByUserAndAgent(Long userId, Long agentId);

    @Select("SELECT agent_id, agent_name, MAX(created_at) AS created_at FROM chat_history WHERE user_id = #{userId} GROUP BY agent_id, agent_name ORDER BY MAX(created_at) DESC")
    List<ChatHistory> selectDistinctAgents(Long userId);

    @Select("SELECT * FROM chat_history WHERE user_id=#{userId} AND conversation_id=#{convId} ORDER BY created_at ASC")
    List<ChatHistory> selectByConversation(Long userId, Long convId);

    @Delete("DELETE FROM chat_history WHERE user_id = #{userId} AND agent_id = #{agentId}")
    void deleteByUserAndAgent(Long userId, Long agentId);

    /** 取最早的N条普通对话（用于压缩），user_id隔离 */
    @Select("SELECT * FROM chat_history WHERE user_id = #{userId} AND agent_id = #{agentId} AND role IN ('user','assistant') ORDER BY created_at ASC LIMIT #{limit}")
    List<ChatHistory> selectOldestMessages(Long userId, Long agentId, int limit);

    /** 删除指定ID列表的消息，双重校验 userId + id IN (...) */
    @Delete("<script>DELETE FROM chat_history WHERE user_id = #{userId} AND id IN <foreach item='id' collection='ids' open='(' separator=',' close=')'>#{id}</foreach></script>")
    int deleteByIds(@Param("userId") Long userId, @Param("ids") List<Long> ids);

    /** 加载最新一条记忆摘要，user_id隔离 */
    @Select("SELECT * FROM chat_history WHERE user_id = #{userId} AND role = 'memory_summary' ORDER BY created_at DESC LIMIT 1")
    ChatHistory selectLatestMemorySummary(Long userId);

    /** 统计用户普通消息总数（用于阈值判断），user_id隔离 */
    @Select("SELECT COUNT(*) FROM chat_history WHERE user_id = #{userId} AND role IN ('user','assistant')")
    long countByUserId(Long userId);
}