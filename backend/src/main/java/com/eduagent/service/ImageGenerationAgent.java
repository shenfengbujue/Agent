package com.eduagent.service;

import com.eduagent.agent.*;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 图片生成Agent — 基于教学内容生成教育插图
 *
 * 通过LLM将教学内容转化为图片生成Prompt，
 * 支持异步+SSE通知模式、流式输出进度、超时降级。
 */
@Slf4j
@Service
public class ImageGenerationAgent {

    private final AIService aiService;
    private final ThreadPoolExecutor imageGenExecutor;

    public ImageGenerationAgent(AIService aiService,
                                @Qualifier("imageGenExecutor") ThreadPoolExecutor imageGenExecutor) {
        this.aiService = aiService;
        this.imageGenExecutor = imageGenExecutor;
    }

    private static final long IMAGE_GEN_TIMEOUT_SECONDS = 60;

    /**
     * 根据教学内容生成配图描述并异步提交图片生成
     */
    public AgentResult generate(String query, Map<String, Object> parsedRequirements,
                                 AgentContext context) {
        long startTime = System.currentTimeMillis();

        try {
            String subject = parsedRequirements != null ?
                    (String) parsedRequirements.getOrDefault("subject", query) : query;

            String prompt = buildImageGenPrompt(query, parsedRequirements, context);
            String llmOutput = aiService.chatWithSystemPrompt(
                    SystemPrompts.IMAGE_GENERATION, prompt);

            List<Map<String, Object>> illustrations = new ArrayList<>();

            if (llmOutput != null && !llmOutput.isBlank()) {
                try {
                    JsonNode json = JsonParserUtil.parseJson(llmOutput);
                    if (json.has("illustrations") && json.get("illustrations").isArray()) {
                        for (JsonNode ill : json.get("illustrations")) {
                            Map<String, Object> item = new LinkedHashMap<>();
                            item.put("caption", getText(ill, "caption"));
                            item.put("prompt", getText(ill, "prompt"));
                            item.put("type", getText(ill, "type"));
                            item.put("relevance", getText(ill, "relevance"));
                            item.put("status", "pending");
                            illustrations.add(item);
                        }
                    }
                } catch (Exception e) {
                    log.warn("LLM图片描述解析失败: {}", e.getMessage());
                }
            }

            if (illustrations.isEmpty()) {
                illustrations.add(createDefaultIllustration(subject));
            }

            // 异步提交图片生成
            final List<Map<String, Object>> finalIllustrations = illustrations;
            imageGenExecutor.submit(() -> {
                for (Map<String, Object> ill : finalIllustrations) {
                    try {
                        String imagePrompt = (String) ill.get("prompt");
                        if (imagePrompt == null || imagePrompt.isBlank()) continue;
                        String imageUrl = generateImageWithTimeout(imagePrompt);
                        ill.put("imageUrl", imageUrl);
                        ill.put("status", imageUrl != null ? "completed" : "fallback");
                    } catch (Exception e) {
                        log.warn("图片生成失败: caption={}", ill.get("caption"));
                        ill.put("status", "fallback");
                        ill.put("imageUrl", getPlaceholderImage((String) ill.get("type")));
                    }
                }
            });

            Map<String, Object> resultData = new LinkedHashMap<>();
            resultData.put("illustrations", illustrations);
            resultData.put("subject", subject);
            resultData.put("totalCount", illustrations.size());
            resultData.put("status", "generating");

            String markdown = buildIllustrationsMarkdown(illustrations);
            long duration = System.currentTimeMillis() - startTime;
            log.info("图片生成Agent: {}个描述已提交, {}ms", illustrations.size(), duration);
            return AgentResult.success("图片生成智能体", resultData, markdown, duration);

        } catch (Exception e) {
            log.error("图片生成Agent失败: {}", e.getMessage());
            long duration = System.currentTimeMillis() - startTime;
            return AgentResult.error("图片生成智能体", e.getMessage(), duration);
        }
    }

    private String generateImageWithTimeout(String prompt) throws Exception {
        java.util.concurrent.Future<String> future = imageGenExecutor.submit(() ->
                aiService.chatWithSystemPrompt(
                        "Generate educational illustration: " + prompt,
                        "Create a clear educational diagram in vector style."));

        try {
            return future.get(IMAGE_GEN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            future.cancel(true);
            log.warn("图片生成超时({}s)", IMAGE_GEN_TIMEOUT_SECONDS);
            return null;
        }
    }

    private Map<String, Object> createDefaultIllustration(String subject) {
        Map<String, Object> ill = new LinkedHashMap<>();
        ill.put("caption", subject + " - 概念示意图");
        ill.put("prompt", "Educational diagram of " + subject + ", clean vector style");
        ill.put("type", "concept_diagram");
        ill.put("relevance", "核心概念可视化");
        ill.put("status", "pending");
        return ill;
    }

    private String getPlaceholderImage(String type) {
        return "/assets/placeholders/" + (type != null ? type : "generic") + ".svg";
    }

    private String buildIllustrationsMarkdown(List<Map<String, Object>> illustrations) {
        StringBuilder md = new StringBuilder();
        md.append("# 🎨 教学配图\n\n> 图片正在生成中，完成后自动更新...\n\n");
        for (int i = 0; i < illustrations.size(); i++) {
            Map<String, Object> ill = illustrations.get(i);
            md.append("## 图").append(i + 1).append(": ")
                    .append(ill.getOrDefault("caption", "未命名")).append("\n\n");
            md.append("- **类型**: ").append(ill.getOrDefault("type", "示意图")).append("\n");
            md.append("- **状态**: ")
                    .append("completed".equals(ill.get("status")) ? "✅ 已生成" :
                            "fallback".equals(ill.get("status")) ? "⚠️ 降级占位图" : "⏳ 生成中...")
                    .append("\n\n");
        }
        return md.toString();
    }

    private String buildImageGenPrompt(String query, Map<String, Object> parsedRequirements,
                                        AgentContext context) {
        StringBuilder sb = new StringBuilder();
        String subject = parsedRequirements != null ?
                (String) parsedRequirements.getOrDefault("subject", query) : query;
        sb.append("学习主题: ").append(subject).append("\n");
        if (context != null && context.getPreviousOutputs() != null) {
            AgentResult kr = context.getPreviousOutputs().get("知识库检索智能体");
            if (kr != null && kr.getMarkdownContent() != null) {
                String kc = kr.getMarkdownContent();
                sb.append("教学内容: ").append(kc, 0, Math.min(1500, kc.length())).append("\n");
            }
        }
        sb.append("\n请为以上教学内容设计2-3张最合适的教育配图。");
        return sb.toString();
    }

    private String getText(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return "";
        return node.get(field).asText();
    }
}
