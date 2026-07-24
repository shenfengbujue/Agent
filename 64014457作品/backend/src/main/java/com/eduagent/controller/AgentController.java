package com.eduagent.controller;

import com.eduagent.service.AIService;
import com.eduagent.service.ChatHistoryService;
import com.eduagent.service.CoordinatorAgent;
import com.eduagent.entity.KnowledgeEntry;
import com.eduagent.service.KnowledgeBaseService;
import com.eduagent.service.RagService;
import com.eduagent.service.TaskDispatcher;
import com.eduagent.service.UserProfileService;
import com.eduagent.service.MemoryService;
import com.eduagent.service.ConversationService;
import com.eduagent.agent.AgentRegistry;
import com.eduagent.agent.AgentResult;
import com.eduagent.agent.BaseAgent;
import com.eduagent.agent.WorkflowState;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping(value = "/api/agents", produces = "application/json;charset=UTF-8")
public class AgentController {

    private final AIService aiService;
    private final TaskDispatcher taskDispatcher;
    private final KnowledgeBaseService knowledgeBaseService;
    private final RagService ragService;
    private final UserProfileService profileService;
    private final ChatHistoryService chatHistoryService;
    private final CoordinatorAgent coordinatorAgent;
    private final MemoryService memoryService;
    private final AgentRegistry agentRegistry;
    private final ConversationService conversationService;

    public AgentController(AIService aiService, TaskDispatcher taskDispatcher,
                          KnowledgeBaseService knowledgeBaseService, RagService ragService,
                          UserProfileService profileService, ChatHistoryService chatHistoryService,
                          CoordinatorAgent coordinatorAgent,
                          MemoryService memoryService,
                          AgentRegistry agentRegistry,
                          ConversationService conversationService) {
        this.aiService = aiService;
        this.taskDispatcher = taskDispatcher;
        this.knowledgeBaseService = knowledgeBaseService;
        this.ragService = ragService;
        this.profileService = profileService;
        this.chatHistoryService = chatHistoryService;
        this.coordinatorAgent = coordinatorAgent;
        this.memoryService = memoryService;
        this.agentRegistry = agentRegistry;
        this.conversationService = conversationService;
    }

    @PostMapping("/{agentId}/chat")
    public ResponseEntity<Map<String, Object>> chat(
            @PathVariable Long agentId,
            HttpServletRequest request,
            @RequestBody Map<String, String> body) {

        String message = body.get("message");
        String agentType = getAgentType(agentId);

        Long userId = getUserIdFromToken(request);

        log.info("Agent chat - agentId: {}, userId: {}, message: {}", agentId, userId, message);

        String agentName = getAgentName(agentId);

        chatHistoryService.saveMessage(userId, agentId, agentName, "user", message);

        // 加载近期对话作为上下文，注入当前消息中
        String contextualMessage = buildContextualMessage(userId, agentId, message);

        Map<String, Object> result = ragService.answer(contextualMessage, agentType);

        String response = (String) result.get("response");
        chatHistoryService.saveMessage(userId, agentId, agentName, "assistant", response);

        try {
            profileService.updateKeywords(userId, message);
            log.info("User profile updated for userId: {}", userId);
        } catch (Exception e) {
            log.warn("Failed to update user profile: {}", e.getMessage());
        }

        result.put("agentId", agentId);
        result.put("agentType", agentType);

        return ResponseEntity.ok(result);
    }

    /** 简单模式流式对话 — SSE格式逐token输出 */
    @PostMapping("/{agentId}/chat-stream")
    public void chatStream(
            @PathVariable Long agentId,
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestBody Map<String, String> body) throws Exception {

        String message = body.get("message");
        String agentType = getAgentType(agentId);
        Long userId = getUserIdFromToken(request);
        String agentName = getAgentName(agentId);

        chatHistoryService.saveMessage(userId, agentId, agentName, "user", message);
        String contextualMessage = buildContextualMessage(userId, agentId, message);

        response.setContentType("text/event-stream;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        PrintWriter writer = response.getWriter();

        try {
            // RAG知识上下文
            String systemPrompt = "你是「智学未来」平台的学习助手。请用友好、专业的语言回答用户的问题。";
            List<com.eduagent.entity.KnowledgeEntry> entries = knowledgeBaseService.searchAllEntries(contextualMessage, 3);
            if (entries != null && !entries.isEmpty()) {
                StringBuilder kb = new StringBuilder();
                for (com.eduagent.entity.KnowledgeEntry e : entries) {
                    if (e.getContent() != null) kb.append(e.getContent()).append("\n");
                }
                systemPrompt = "你是「智学未来」平台的学习助手。请根据知识库内容回答。\n知识库：\n" + kb;
            }

            // 流式调用LLM，chatStream返回完整文本
            String fullResponse = aiService.chatStream(systemPrompt, contextualMessage, token -> {
                try {
                    writer.print("data: " + new ObjectMapper().writeValueAsString(
                        Map.of("token", token)) + "\n\n");
                    writer.flush();
                } catch (Exception e) { /* ignore */ }
            });

            writer.print("data: [DONE]\n\n");
            writer.flush();

            // 保存完整响应
            chatHistoryService.saveMessage(userId, agentId, agentName, "assistant", fullResponse);

        } catch (Exception e) {
            writer.print("data: " + new ObjectMapper().writeValueAsString(
                Map.of("error", e.getMessage())) + "\n\n");
            writer.flush();
        } finally {
            writer.close();
        }
    }
    
    private String getAgentName(Long agentId) {
        return switch (agentId.intValue()) {
            case 1 -> "学习助手";
            case 2 -> "编程专家";
            case 3 -> "写作助手";
            case 4 -> "英语教练";
            default -> "学习助手";
        };
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllAgents() {
        Map<String, Object> result = new HashMap<>();
        result.put("data", getMockAgents());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/enabled")
    public ResponseEntity<Map<String, Object>> getEnabledAgents() {
        return getAllAgents();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getAgentById(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        result.put("data", getMockAgent(id));
        return ResponseEntity.ok(result);
    }

    private String getAgentType(Long agentId) {
        return switch (agentId.intValue()) {
            case 1 -> "学习助手";
            case 2 -> "编程专家";
            case 3 -> "写作助手";
            case 4 -> "英语教练";
            default -> "学习助手";
        };
    }

    /**
     * 从 JWT token 中提取当前登录用户 ID
     */
    private Long getUserIdFromToken(HttpServletRequest request) {
        Object userIdAttr = request.getAttribute("userId");
        if (userIdAttr == null) {
            throw new RuntimeException("未登录或token已过期");
        }
        return Long.valueOf(userIdAttr.toString());
    }

    /** 构建带对话上下文的消息（简单模式用） */
    private String buildContextualMessage(Long userId, Long agentId, String currentMsg) {
        try {
            List<com.eduagent.entity.ChatHistory> history = chatHistoryService.getHistory(userId, agentId);
            if (history == null || history.size() <= 1) {
                return currentMsg; // 只有刚保存的当前消息，无需上下文
            }
            // 去掉刚保存的当前消息，只取之前的记录
            List<com.eduagent.entity.ChatHistory> previous = history.subList(0, history.size() - 1);
            // 取最近6轮对话（12条）
            int start = Math.max(0, previous.size() - 12);
            List<com.eduagent.entity.ChatHistory> recent = previous.subList(start, previous.size());

            StringBuilder ctx = new StringBuilder();
            ctx.append("【对话上下文-请结合以下历史对话理解用户意图】\n");
            for (com.eduagent.entity.ChatHistory msg : recent) {
                String role = "user".equals(msg.getRole()) ? "用户" : "助手";
                String content = msg.getContent();
                if (content != null) {
                    // 去掉结构化数据标记
                    int marker = content.indexOf("<!--STRUCTURED_DATA-->");
                    if (marker >= 0) content = content.substring(0, marker).trim();
                    if (content.length() > 200) content = content.substring(0, 200) + "...";
                }
                ctx.append(role).append(": ").append(content).append("\n");
            }
            ctx.append("\n【当前问题】\n").append(currentMsg);
            return ctx.toString();
        } catch (Exception e) {
            log.warn("构建对话上下文失败: {}", e.getMessage());
            return currentMsg;
        }
    }

    private Object getMockAgents() {
        // 从AgentRegistry动态获取真实Agent列表
        java.util.List<BaseAgent> agents = new java.util.ArrayList<>(agentRegistry.getAllAgents());
        if (agents.isEmpty()) {
            // 降级：AgentRegistry为空时返回默认列表
            return new Object[]{
                Map.of("id", 1, "name", "学习助手", "role", "学习辅导",
                        "description", "帮助您制定学习计划，解答学习问题", "icon", ""),
                Map.of("id", 2, "name", "编程专家", "role", "代码帮助",
                        "description", "提供编程问题解答和代码审查", "icon", ""),
                Map.of("id", 3, "name", "写作助手", "role", "内容创作",
                        "description", "帮助您撰写文章、报告和文档", "icon", ""),
                Map.of("id", 4, "name", "英语教练", "role", "语言学习",
                        "description", "提供英语学习指导和练习", "icon", "")
            };
        }
        return agents.stream().map(agent -> {
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("name", agent.getName());
            map.put("type", agent.getType() != null ? agent.getType().name() : "通用");
            map.put("description", agent.getDescription());
            return map;
        }).toArray();
    }

    private Object getMockAgent(Long id) {
        java.util.List<BaseAgent> agents = new java.util.ArrayList<>(agentRegistry.getAllAgents());
        if (id < agents.size()) {
            BaseAgent agent = agents.get(id.intValue());
            return Map.of("name", agent.getName(),
                    "type", agent.getType() != null ? agent.getType().name() : "通用",
                    "description", agent.getDescription() != null ? agent.getDescription() : "");
        }
        return Map.of("name", "未知助手", "type", "未知", "description", "未知");
    }

    @PostMapping("/process-query")
    public ResponseEntity<Map<String, Object>> processQuery(
            HttpServletRequest request,
            @RequestBody Map<String, String> body) {

        String query = body.get("query");
        Long userId = getUserIdFromToken(request);
        // 获取或创建会话
        Long convId = body.get("conversationId") != null ? Long.valueOf(body.get("conversationId")) : null;
        if (convId == null) {
            try { convId = conversationService.create(userId, query.length() > 30 ? query.substring(0, 30) : query).getId(); } catch(Exception e) {}
        } else {
            try { conversationService.touch(convId); } catch(Exception e) {}
        }

        log.info("Processing multi-agent query - userId: {}, query: {}, convId: {}", userId, query, convId);

        // 保存用户消息到聊天历史
        chatHistoryService.saveMessage(userId, 0L, "多智能体协同", "user", query, convId);

        // 使用新的多智能体协同接口（带userId上下文）
        Map<String, Object> result = coordinatorAgent.processQuery(query, userId);

        // 保存AI回复到聊天历史（含结构化数据JSON以便恢复Tab）
        String summary = result.get("response") != null ? result.get("response").toString() : "";
        if (summary.length() > 500) summary = summary.substring(0, 500) + "...";
        // 包装：前面是可读摘要，后面是结构化JSON（前端解析恢复Tab）
        Map<String, Object> structured = new HashMap<>();
        structured.put("requirements", result.get("requirements"));
        structured.put("learningPath", result.get("learningPath"));
        structured.put("knowledge", result.get("knowledge"));
        structured.put("webSearch", result.get("webSearch"));
        structured.put("graph", result.get("graph"));
        structured.put("exercises", result.get("exercises"));
        String jsonData;
        try {
            jsonData = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(structured);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("Failed to serialize structured data: {}", e.getMessage());
            jsonData = "{}";
        }
        String savedContent = summary + "\n<!--STRUCTURED_DATA-->" + jsonData;
        chatHistoryService.saveMessage(userId, 0L, "多智能体协同", "assistant", savedContent, convId);

        // 更新用户画像 — 使用LLM解析后的结构化需求，而非原始query碎片
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> requirements = (Map<String, Object>) result.get("requirements");
            if (requirements != null && !requirements.isEmpty()) {
                // 用LLM解析出的准确主题作为关键词
                String subject = (String) requirements.getOrDefault("subject", query);
                if (subject != null && !subject.isEmpty() && !"综合学习".equals(subject)) {
                    profileService.updateKeywords(userId, subject);
                }
                // 学习目标作为兴趣
                String goal = (String) requirements.getOrDefault("goal", "");
                if (goal != null && !goal.isEmpty()) {
                    profileService.updateUserInterests(userId, goal);
                }
                // 薄弱点作为额外关键词
                Object weakPoints = requirements.get("weakPoints");
                if (weakPoints instanceof List) {
                    for (Object wp : (List<?>) weakPoints) {
                        if (wp != null && !wp.toString().isEmpty()) {
                            profileService.updateUserInterests(userId, wp.toString());
                        }
                    }
                }
                log.info("User profile synced from LLM parsing: subject={}, goal={}", subject, goal);
            } else {
                // 降级：用query直接提取
                profileService.updateKeywords(userId, query);
            }
        } catch (Exception e) {
            log.warn("Failed to update user profile: {}", e.getMessage());
        }

        // 更新记忆系统
        try {
            memoryService.updateShortTermMemory(userId, query, summary);
        } catch (Exception e) {
            log.warn("Failed to update memory: {}", e.getMessage());
        }

        if (convId != null) result.put("conversationId", convId);
        return ResponseEntity.ok(result);
    }

    /** 生成学习资源 */
    @PostMapping("/{agentId}/generate")
    public ResponseEntity<Map<String, Object>> generateResource(
            @PathVariable Long agentId, HttpServletRequest request,
            @RequestBody Map<String, String> body) {
        String prompt = body.get("prompt");
        Long userId = getUserIdFromToken(request);
        Map<String, Object> result = coordinatorAgent.processQuery(prompt, userId);
        return ResponseEntity.ok(result);
    }

    /** NDJSON流式：每完成一个Agent立即推送其结果 */
    @PostMapping("/process-query-stream")
    public void processQueryStream(HttpServletRequest request, @RequestBody Map<String, String> body,
                                    HttpServletResponse response) throws Exception {
        String query = body.get("query");
        Long userId = getUserIdFromToken(request);
        Long convId = body.get("conversationId") != null ? Long.valueOf(body.get("conversationId")) : null;
        if (convId == null) {
            try { convId = conversationService.create(userId, query.length() > 30 ? query.substring(0, 30) : query).getId(); } catch(Exception e) {}
        }

        response.setContentType("application/x-ndjson;charset=UTF-8");
        response.setHeader("X-Content-Type-Options", "nosniff");
        PrintWriter writer = response.getWriter();
        ObjectMapper mapper = new ObjectMapper();

        chatHistoryService.saveMessage(userId, 0L, "多智能体协同", "user", query, convId);

        try {
            // 流式：每个Agent完成时通过callback立即推送NDJSON行
            Map<String, Object> result = coordinatorAgent.processQuery(query, userId, jsonLine -> {
                writer.println(jsonLine);
                writer.flush();
            });

            // 最后一行：轻量完成信号（仅含摘要，完整数据已通过各Agent回调逐行推送）
            Map<String, Object> finalLine = new LinkedHashMap<>();
            finalLine.put("type", "complete");
            finalLine.put("runId", result.get("runId"));
            finalLine.put("mode", result.get("mode"));
            finalLine.put("response", result.get("response"));
            finalLine.put("requirements", result.get("requirements"));
            writer.println(mapper.writeValueAsString(finalLine));
            writer.flush();

            // 保存历史（含结构化数据，与processQuery一致）
            String summary = result.get("response") != null ? result.get("response").toString() : "";
            if (summary.length() > 500) summary = summary.substring(0, 500) + "...";
            Map<String, Object> structured = new LinkedHashMap<>();
            structured.put("requirements", result.get("requirements"));
            structured.put("learningPath", result.get("learningPath"));
            structured.put("knowledge", result.get("knowledge"));
            structured.put("webSearch", result.get("webSearch"));
            structured.put("graph", result.get("graph"));
            structured.put("exercises", result.get("exercises"));
            String jsonData = "{}";
            try { jsonData = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(structured); } catch(Exception e) {}
            String savedContent = summary + "\n<!--STRUCTURED_DATA-->" + jsonData;
            chatHistoryService.saveMessage(userId, 0L, "多智能体协同", "assistant", savedContent, convId);

            try {
                memoryService.updateShortTermMemory(userId, query, summary);
            } catch (Exception e) {
                log.warn("Failed to update memory: {}", e.getMessage());
            }

        } catch (Exception e) {
            writer.println("{\"type\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
            writer.flush();
        } finally {
            writer.close();
        }
    }

}