package com.eduagent.service;

import com.eduagent.entity.ChatHistory;
import com.eduagent.mapper.ChatHistoryMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ChatHistoryService {

    private final ChatHistoryMapper chatHistoryMapper;

    public ChatHistoryService(ChatHistoryMapper chatHistoryMapper) {
        this.chatHistoryMapper = chatHistoryMapper;
    }

    public void saveMessage(Long userId, Long agentId, String agentName, String role, String content) {
        saveMessage(userId, agentId, agentName, role, content, null);
    }
    public void saveMessage(Long userId, Long agentId, String agentName, String role, String content, Long conversationId) {
        ChatHistory history = new ChatHistory();
        history.setUserId(userId);
        history.setAgentId(agentId);
        history.setAgentName(agentName);
        history.setRole(role);
        history.setContent(content);
        history.setConversationId(conversationId);
        history.setCreatedAt(LocalDateTime.now());
        try {
            chatHistoryMapper.insert(history);
            log.info("Saved chat message - userId:{}, role:{}, convId:{}", userId, role, conversationId);
        } catch (Exception e) { log.error("Failed to save: {}", e.getMessage()); }
    }

    public List<ChatHistory> getHistory(Long userId, Long agentId) {
        try { return chatHistoryMapper.selectByUserAndAgent(userId, agentId); }
        catch (Exception e) { log.error("Failed: {}", e.getMessage()); return List.of(); }
    }
    public List<ChatHistory> getHistoryByConversation(Long userId, Long convId) {
        try { return chatHistoryMapper.selectByConversation(userId, convId); }
        catch (Exception e) { log.error("Failed: {}", e.getMessage()); return List.of(); }
    }

    public List<Map<String, Object>> getAgentConversations(Long userId) {
        try {
            List<ChatHistory> agents = chatHistoryMapper.selectDistinctAgents(userId);
            return agents.stream()
                    .map(agent -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("agentId", agent.getAgentId());
                        map.put("agentName", agent.getAgentName());
                        return map;
                    })
                    .toList();
        } catch (Exception e) {
            log.error("Failed to get agent conversations: {}", e.getMessage());
            return List.of();
        }
    }

    public void deleteHistory(Long userId, Long agentId) {
        try {
            chatHistoryMapper.deleteByUserAndAgent(userId, agentId);
            log.info("Deleted chat history - userId: {}, agentId: {}", userId, agentId);
        } catch (Exception e) {
            log.error("Failed to delete chat history: {}", e.getMessage());
        }
    }

    public void clearAllHistory(Long userId) {
        try {
            chatHistoryMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatHistory>()
                    .eq(ChatHistory::getUserId, userId));
            log.info("Cleared all chat history for userId: {}", userId);
        } catch (Exception e) {
            log.error("Failed to clear chat history: {}", e.getMessage());
        }
    }
}