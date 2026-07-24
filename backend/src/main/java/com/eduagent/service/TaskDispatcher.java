package com.eduagent.service;

import com.eduagent.entity.IntentRule;
import com.eduagent.entity.KnowledgeBase;
import com.eduagent.entity.KnowledgeEntry;
import com.eduagent.mapper.KnowledgeBaseMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class TaskDispatcher {

    private final IntentService intentService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeBaseMapper baseMapper;

    public TaskDispatcher(IntentService intentService,
                          KnowledgeBaseService knowledgeBaseService,
                          KnowledgeBaseMapper baseMapper) {
        this.intentService = intentService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.baseMapper = baseMapper;
    }

    public Map<String, Object> dispatch(String query) {
        Map<String, Object> result = new HashMap<>();

        IntentRule rule = intentService.getMatchingRule(query);

        if (rule != null && rule.getTargetBaseId() != null) {
            KnowledgeBase targetBase = baseMapper.selectById(rule.getTargetBaseId());
            if (targetBase != null) {
                result.put("intentType", rule.getIntentType());
                result.put("targetBaseId", rule.getTargetBaseId());
                result.put("targetBaseName", targetBase.getName());
                result.put("targetBaseDomain", targetBase.getDomain());
                result.put("query", query);

                List<KnowledgeEntry> entries = knowledgeBaseService.getEntriesByBaseId(rule.getTargetBaseId());
                result.put("entries", entries);

                log.info("分发查询到知识库: {} (ID: {})", targetBase.getName(), targetBase.getId());
            } else {
                result = handleGeneralQuery(query);
            }
        } else {
            result = handleGeneralQuery(query);
        }

        return result;
    }

    public Map<String, Object> dispatchToBase(Long baseId, String query) {
        Map<String, Object> result = new HashMap<>();

        KnowledgeBase targetBase = baseMapper.selectById(baseId);
        if (targetBase == null) {
            result.put("error", "知识库不存在");
            return result;
        }

        result.put("targetBaseId", baseId);
        result.put("targetBaseName", targetBase.getName());
        result.put("targetBaseDomain", targetBase.getDomain());
        result.put("query", query);

        List<KnowledgeEntry> entries = knowledgeBaseService.getEntriesByBaseId(baseId);
        result.put("entries", entries);

        log.info("定向查询知识库: {} (ID: {})", targetBase.getName(), targetBase.getId());
        return result;
    }

    private Map<String, Object> handleGeneralQuery(String query) {
        Map<String, Object> result = new HashMap<>();

        KnowledgeBase generalBase = baseMapper.selectById(1L);
        if (generalBase != null) {
            result.put("intentType", "GENERAL");
            result.put("targetBaseId", 1L);
            result.put("targetBaseName", generalBase.getName());
            result.put("targetBaseDomain", generalBase.getDomain());
            result.put("query", query);

            List<KnowledgeEntry> entries = knowledgeBaseService.getEntriesByBaseId(1L);
            result.put("entries", entries);
        }

        result.put("directAnswer", generateDirectAnswer(query));

        return result;
    }

    private String generateDirectAnswer(String query) {
        query = query.toLowerCase();

        if (query.contains("你好") || query.contains("您好") || query.contains("hello") || query.contains("hi")) {
            return "你好！我是你的智能学习助手，有什么可以帮到你的？";
        }
        if (query.contains("谢谢") || query.contains("感谢")) {
            return "不客气！很高兴能帮到你。";
        }
        if (query.contains("再见") || query.contains("拜拜")) {
            return "再见！祝你学习愉快！";
        }
        if (query.contains("你是谁") || query.contains("你叫什么")) {
            return "我是智能学习助手，专注于帮助你更好地学习和获取知识。";
        }
        return null;
    }
}