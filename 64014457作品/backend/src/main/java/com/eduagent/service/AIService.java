package com.eduagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * AI服务 — OpenAI兼容接口（支持DeepSeek/DashScope/任意兼容端点）
 */
@Slf4j
@Service
public class AIService {

    @Value("${ai.api-key}")
    private String apiKey;

    @Value("${ai.endpoint}")
    private String endpoint;

    @Value("${ai.model:deepseek-chat}")
    private String model;

    @Value("${ai.temperature:0.7}")
    private Double temperature;

    @Value("${ai.max-tokens:16384}")
    private Integer maxTokens;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    /**
     * 带System Prompt的LLM调用（OpenAI兼容格式）
     */
    public String chatWithSystemPrompt(String systemPrompt, String userMessage) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);
            body.put("temperature", temperature.floatValue());
            body.put("max_tokens", maxTokens);

            ArrayNode messages = body.putArray("messages");

            ObjectNode sysMsg = messages.addObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt);

            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);

            String json = mapper.writeValueAsString(body);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(600))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 200) {
                JsonNode root = mapper.readTree(resp.body());
                JsonNode choices = root.get("choices");
                if (choices != null && choices.isArray() && choices.size() > 0) {
                    JsonNode msg = choices.get(0).get("message");
                    if (msg != null) {
                        String content = msg.get("content").asText();
                        log.debug("AI response: {} chars", content != null ? content.length() : 0);
                        return content;
                    }
                }
                log.warn("AI返回空choices");
                return null;
            } else {
                log.error("AI API error {}: {}", resp.statusCode(), resp.body().substring(0, Math.min(300, resp.body().length())));
                throw new RuntimeException("AI API error " + resp.statusCode() + ": " + resp.body());
            }

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI调用失败: {}", e.getMessage());
            throw new RuntimeException("AI服务调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 带System Prompt的流式LLM调用
     */
    public String chatWithSystemPromptStreaming(String systemPrompt, String userMessage, java.util.function.Consumer<String> onToken) {
        return chatStream(systemPrompt, userMessage, onToken);
    }

    /**
     * 流式调用 — 每收到一个token就回调onToken，返回完整文本
     */
    public String chatStream(String systemPrompt, String userMessage, Consumer<String> onToken) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);
            body.put("temperature", temperature.floatValue());
            body.put("max_tokens", maxTokens);
            body.put("stream", true);

            ArrayNode messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", systemPrompt);
            messages.addObject().put("role", "user").put("content", userMessage);

            String json = mapper.writeValueAsString(body);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(600))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<java.io.InputStream> resp = httpClient.send(req,
                    HttpResponse.BodyHandlers.ofInputStream());

            if (resp.statusCode() != 200) {
                String err = new String(resp.body().readAllBytes());
                log.error("Stream API error {}: {}", resp.statusCode(), err);
                throw new RuntimeException("Stream API error: " + err);
            }

            StringBuilder fullText = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(resp.body()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data: ")) {
                    String data = line.substring(6);
                    if ("[DONE]".equals(data)) break;
                    try {
                        JsonNode root = mapper.readTree(data);
                        JsonNode choices = root.get("choices");
                        if (choices != null && choices.size() > 0) {
                            JsonNode delta = choices.get(0).get("delta");
                            if (delta != null) {
                                JsonNode content = delta.get("content");
                                if (content != null && !content.isNull() && !content.asText().isEmpty()) {
                                    String token = content.asText();
                                    fullText.append(token);
                                    onToken.accept(token);
                                }
                            }
                        }
                    } catch (Exception e) { /* skip parse errors */ }
                }
            }
            log.debug("Stream complete: {} chars", fullText.length());
            return fullText.toString();

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Stream failed: {}", e.getMessage());
            throw new RuntimeException("Stream failed: " + e.getMessage(), e);
        }
    }

    /**
     * 兼容旧接口
     */
    public String chat(String message, String agentType) {
        String systemPrompt = switch (agentType) {
            case "学习助手" -> "你是「智学未来」平台的学习助手，由多智能体协同系统驱动。请用友好、专业的语言回答用户的问题。你的名字叫「智学未来」，不是DeepSeek或其他AI。";
            case "编程专家" -> "你是「智学未来」平台的编程专家智能体。你精通多种编程语言，请提供详细的代码示例和解释。你的名字叫「智学未来」。";
            case "写作助手" -> "你是「智学未来」平台的写作助手智能体，擅长撰写文章、报告和文档。你的名字叫「智学未来」。";
            case "英语教练" -> "你是「智学未来」平台的英语学习教练智能体。请用英语对话，并提供语法解释和词汇建议。你的名字叫「智学未来」。";
            default -> "你是「智学未来」平台的学习助手，由多智能体协同系统驱动，帮助用户解答各种学习相关的问题。你的名字叫「智学未来」，不是DeepSeek。";
        };
        try {
            return chatWithSystemPrompt(systemPrompt, message);
        } catch (Exception e) {
            log.error("Chat failed: {}", e.getMessage());
            return "抱歉，服务暂时不可用，请稍后重试。";
        }
    }

    public String getModel() { return model; }
}
