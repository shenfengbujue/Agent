package com.eduagent.controller;

import com.eduagent.entity.ChatHistory;
import com.eduagent.model.vo.Result;
import com.eduagent.service.ChatHistoryService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatHistoryController {

    private final ChatHistoryService chatHistoryService;

    public ChatHistoryController(ChatHistoryService chatHistoryService) {
        this.chatHistoryService = chatHistoryService;
    }

    /**
     * 从 JWT token 中提取当前登录用户 ID
     */
    private Long getCurrentUserId(HttpServletRequest request) {
        Object userIdAttr = request.getAttribute("userId");
        if (userIdAttr == null) {
            throw new RuntimeException("未登录或token已过期");
        }
        return Long.valueOf(userIdAttr.toString());
    }

    @GetMapping("/history/{agentId}")
    public Result<List<Map<String, Object>>> getHistory(
            HttpServletRequest request,
            @PathVariable Long agentId,
            @RequestParam(required = false) Long convId) {
        Long userId = getCurrentUserId(request);
        List<ChatHistory> history;
        if (convId != null) {
            history = chatHistoryService.getHistoryByConversation(userId, convId);
        } else {
            history = chatHistoryService.getHistory(userId, agentId);
        }
        
        List<Map<String, Object>> messages = history.stream()
                .map(h -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("role", h.getRole());
                    map.put("content", h.getContent());
                    map.put("agent", h.getAgentName());
                    map.put("createdAt", h.getCreatedAt());
                    return map;
                })
                .toList();
        
        return Result.success(messages);
    }

    @GetMapping("/conversations")
    public Result<List<Map<String, Object>>> getConversations(HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        List<Map<String, Object>> conversations = chatHistoryService.getAgentConversations(userId);
        return Result.success(conversations);
    }

    @DeleteMapping("/history/{agentId}")
    public Result<Void> deleteHistory(
            HttpServletRequest request,
            @PathVariable Long agentId) {
        Long userId = getCurrentUserId(request);
        chatHistoryService.deleteHistory(userId, agentId);
        return Result.success(null);
    }

    @DeleteMapping("/history")
    public Result<Void> clearAllHistory(HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        chatHistoryService.clearAllHistory(userId);
        return Result.success(null);
    }
}