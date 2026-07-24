package com.eduagent.controller;

import com.eduagent.entity.Conversation;
import com.eduagent.model.vo.Result;
import com.eduagent.service.ConversationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {
    private final ConversationService service;

    public ConversationController(ConversationService service) { this.service = service; }

    private Long getUserId(HttpServletRequest req) {
        Object uid = req.getAttribute("userId");
        if (uid == null) throw new RuntimeException("未登录");
        return Long.valueOf(uid.toString());
    }

    @GetMapping
    public Result<List<Map<String,Object>>> list(HttpServletRequest req) {
        Long uid = getUserId(req);
        List<Map<String,Object>> r = new ArrayList<>();
        for (Conversation c : service.getUserConversations(uid)) {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id", c.getId()); m.put("title", c.getTitle()); m.put("createdAt", c.getCreatedAt()); m.put("updatedAt", c.getUpdatedAt());
            r.add(m);
        }
        return Result.success(r);
    }

    @PostMapping
    public Result<Map<String,Object>> create(HttpServletRequest req, @RequestBody Map<String,String> body) {
        Long uid = getUserId(req);
        String title = body.getOrDefault("title", "新对话");
        Conversation c = service.create(uid, title);
        Map<String,Object> r = new LinkedHashMap<>();
        r.put("id", c.getId()); r.put("title", c.getTitle());
        return Result.success(r);
    }

    @PutMapping("/{id}/title")
    public Result<Void> updateTitle(@PathVariable Long id, @RequestBody Map<String,String> body, HttpServletRequest req) {
        service.updateTitle(id, body.getOrDefault("title", "未命名"));
        return Result.success(null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest req) {
        Long uid = getUserId(req);
        if (service.delete(id, uid)) return Result.success(null);
        return Result.error(403, "无权删除");
    }
}
