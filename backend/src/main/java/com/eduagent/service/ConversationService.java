package com.eduagent.service;

import com.eduagent.entity.Conversation;
import com.eduagent.mapper.ConversationMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class ConversationService {
    private final ConversationMapper mapper;

    public ConversationService(ConversationMapper mapper) { this.mapper = mapper; }

    public List<Conversation> getUserConversations(Long userId) {
        return mapper.selectByUserId(userId);
    }

    public Conversation create(Long userId, String title) {
        Conversation c = Conversation.builder().userId(userId)
            .title(title != null ? title : "新对话").agentId(0L)
            .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        mapper.insert(c);
        return c;
    }

    public void updateTitle(Long id, String title) {
        Conversation c = mapper.selectById(id);
        if (c != null) { c.setTitle(title); c.setUpdatedAt(LocalDateTime.now()); mapper.updateById(c); }
    }

    public void touch(Long id) {
        Conversation c = mapper.selectById(id);
        if (c != null) { c.setUpdatedAt(LocalDateTime.now()); mapper.updateById(c); }
    }

    public boolean delete(Long id, Long userId) {
        Conversation c = mapper.selectById(id);
        if (c != null && c.getUserId().equals(userId)) { mapper.deleteById(id); return true; }
        return false;
    }
}
