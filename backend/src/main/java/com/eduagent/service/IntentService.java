package com.eduagent.service;

import com.eduagent.entity.IntentRule;
import com.eduagent.mapper.IntentRuleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
public class IntentService {

    private final IntentRuleMapper intentRuleMapper;

    public IntentService(IntentRuleMapper intentRuleMapper) {
        this.intentRuleMapper = intentRuleMapper;
    }

    public String recognizeIntent(String query) {
        List<IntentRule> rules = intentRuleMapper.selectActiveRules();

        for (IntentRule rule : rules) {
            if (matchesRule(query, rule)) {
                log.info("识别意图: {} -> {}", query, rule.getIntentType());
                return rule.getIntentType();
            }
        }

        log.info("未识别到特定意图，使用通用意图: {}", query);
        return "GENERAL";
    }

    public IntentRule getMatchingRule(String query) {
        List<IntentRule> rules = intentRuleMapper.selectActiveRules();

        for (IntentRule rule : rules) {
            if (matchesRule(query, rule)) {
                return rule;
            }
        }
        return null;
    }

    private boolean matchesRule(String query, IntentRule rule) {
        if (query == null || rule.getPattern() == null) {
            return false;
        }

        String patternType = rule.getPatternType();
        String pattern = rule.getPattern();

        if ("KEYWORD".equalsIgnoreCase(patternType)) {
            String[] keywords = pattern.split("\\|");
            for (String keyword : keywords) {
                if (query.contains(keyword.trim())) {
                    return true;
                }
            }
        } else if ("REGEX".equalsIgnoreCase(patternType)) {
            try {
                Pattern regex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
                return regex.matcher(query).find();
            } catch (Exception e) {
                log.warn("正则表达式匹配失败: {}", pattern);
            }
        }

        return false;
    }

    public List<IntentRule> getAllRules() {
        return intentRuleMapper.selectActiveRules();
    }

    public IntentRule createRule(IntentRule rule) {
        rule.setCreatedAt(LocalDateTime.now());
        if (rule.getPriority() == null) {
            rule.setPriority(10);
        }
        if (rule.getPatternType() == null) {
            rule.setPatternType("KEYWORD");
        }
        if (rule.getStatus() == null) {
            rule.setStatus("ACTIVE");
        }
        intentRuleMapper.insert(rule);
        return rule;
    }

    public boolean updateRule(Long id, IntentRule rule) {
        IntentRule existing = intentRuleMapper.selectById(id);
        if (existing == null) {
            return false;
        }
        if (rule.getPattern() != null) existing.setPattern(rule.getPattern());
        if (rule.getPatternType() != null) existing.setPatternType(rule.getPatternType());
        if (rule.getIntentType() != null) existing.setIntentType(rule.getIntentType());
        if (rule.getTargetBaseId() != null) existing.setTargetBaseId(rule.getTargetBaseId());
        if (rule.getPriority() != null) existing.setPriority(rule.getPriority());
        if (rule.getDescription() != null) existing.setDescription(rule.getDescription());
        if (rule.getStatus() != null) existing.setStatus(rule.getStatus());
        return intentRuleMapper.updateById(existing) > 0;
    }

    public boolean deleteRule(Long id) {
        return intentRuleMapper.deleteById(id) > 0;
    }
}