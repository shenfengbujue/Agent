package com.eduagent.service;

import com.eduagent.entity.KnowledgeBase;
import com.eduagent.entity.KnowledgeEntry;
import com.eduagent.entity.IntentRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class RagService {

    private final KnowledgeBaseService knowledgeBaseService;
    private final IntentService intentService;
    private final AIService aiService;

    public RagService(KnowledgeBaseService knowledgeBaseService, IntentService intentService, AIService aiService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.intentService = intentService;
        this.aiService = aiService;
    }

    public Map<String, Object> answer(String question, String agentType) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String intentType = intentService.recognizeIntent(question);
            IntentRule matchingRule = intentService.getMatchingRule(question);
            
            Long targetBaseId = null;
            if (matchingRule != null) {
                targetBaseId = matchingRule.getTargetBaseId();
            }
            
            List<KnowledgeEntry> retrievedEntries = new ArrayList<>();
            
            if (targetBaseId != null && targetBaseId > 0) {
                retrievedEntries = knowledgeBaseService.searchEntries(targetBaseId, question, 5);
            } else {
                retrievedEntries = knowledgeBaseService.searchAllEntries(question, 5);
            }
            
            String response;
            boolean usedKnowledge = !retrievedEntries.isEmpty();
            
            if (usedKnowledge) {
                String knowledgeContext = buildKnowledgeContext(retrievedEntries);
                String prompt = buildRagPrompt(question, knowledgeContext);
                response = aiService.chat(prompt, agentType);
                log.info("RAG回答 - 使用知识库内容");
            } else {
                response = aiService.chat(question, agentType);
                log.info("直接回答 - 未找到相关知识库内容");
            }
            
            result.put("response", response);
            result.put("usedKnowledge", usedKnowledge);
            result.put("knowledgeCount", retrievedEntries.size());
            result.put("intentType", intentType);
            result.put("sourceReferences", buildReferences(retrievedEntries));
            
            return result;
            
        } catch (Exception e) {
            log.error("RAG processing failed", e);
            result.put("response", aiService.chat(question, agentType));
            result.put("usedKnowledge", false);
            result.put("knowledgeCount", 0);
            return result;
        }
    }

    private String buildKnowledgeContext(List<KnowledgeEntry> entries) {
        StringBuilder context = new StringBuilder();
        context.append("参考知识库内容：\n");
        context.append("================================\n");
        
        int index = 1;
        for (KnowledgeEntry entry : entries) {
            context.append("[知识").append(index).append("] ");
            context.append("标题: ").append(entry.getTitle()).append("\n");
            context.append("内容: ").append(truncateContent(entry.getContent(), 500)).append("\n");
            context.append("分类: ").append(entry.getCategory()).append("\n");
            context.append("--------------------------------\n");
            index++;
        }
        
        return context.toString();
    }

    private String buildRagPrompt(String question, String knowledgeContext) {
        return """
            你是「智学未来」平台的学习助手，由多智能体协同系统驱动。请根据提供的知识库内容来回答用户的问题。

            如果知识库中有相关内容，请优先使用知识库的信息进行回答，并在回答末尾注明参考来源。
            如果知识库中的内容与你的知识有冲突，请以知识库为准。
            如果知识库中没有相关内容，请直接回答，不需要强行引用知识库。

            知识库内容：
            %s
            
            用户问题：%s
            
            请用简洁、专业的语言回答用户的问题。
            """.formatted(knowledgeContext, question);
    }

    private List<Map<String, String>> buildReferences(List<KnowledgeEntry> entries) {
        List<Map<String, String>> references = new ArrayList<>();
        int index = 1;
        for (KnowledgeEntry entry : entries) {
            Map<String, String> ref = new HashMap<>();
            ref.put("id", String.valueOf(entry.getId()));
            ref.put("title", entry.getTitle());
            ref.put("category", entry.getCategory());
            ref.put("reference", "[知识" + index + "]");
            references.add(ref);
            index++;
        }
        return references;
    }

    private String truncateContent(String content, int maxLength) {
        if (content == null) return "";
        if (content.length() <= maxLength) return content;
        return content.substring(0, maxLength) + "...";
    }
}