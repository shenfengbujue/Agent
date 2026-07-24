package com.eduagent.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 内容安全过滤器 —— 独立于LLM的第一道防线
 *
 * 功能:
 * 1. 敏感词库匹配（从sensitive-words.txt加载）
 * 2. 正则规则过滤（隐私信息、违规模式）
 * 3. 在ReviewAgent审核之前和用户输入入口处进行安全检查
 *
 * 安全分级:
 * - BLOCK: 命中高危敏感词或正则，直接拦截
 * - WARN: 命中低危词，记录日志但放行
 * - PASS: 未命中任何规则
 */
@Slf4j
@Service
public class ContentSafetyFilter {

    /** 高危敏感词集合（命中即BLOCK） */
    private final Set<String> blockedWords = new HashSet<>();

    /** 低危敏感词集合（命中即WARN） */
    private final Set<String> warnWords = new HashSet<>();

    /** 高危正则模式 */
    private final List<Pattern> blockedPatterns = new ArrayList<>();

    /** 隐私信息模式 */
    private final List<Pattern> privacyPatterns = new ArrayList<>();

    @PostConstruct
    public void init() {
        loadSensitiveWords();
        compilePatterns();
        log.info("ContentSafetyFilter 初始化完成: blockedWords={}, warnWords={}, blockedPatterns={}",
                blockedWords.size(), warnWords.size(), blockedPatterns.size());
    }

    /**
     * 从classpath:sensitive-words.txt加载敏感词库
     * 格式: 每行一个词，#开头为注释，!前缀表示BLOCK，~前缀表示WARN，无前缀默认BLOCK
     */
    private void loadSensitiveWords() {
        try {
            ClassPathResource resource = new ClassPathResource("sensitive-words.txt");
            if (!resource.exists()) {
                log.warn("敏感词库文件不存在: sensitive-words.txt，使用默认词库");
                loadDefaultWords();
                return;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    if (line.startsWith("!")) {
                        blockedWords.add(line.substring(1).trim().toLowerCase());
                    } else if (line.startsWith("~")) {
                        warnWords.add(line.substring(1).trim().toLowerCase());
                    } else {
                        blockedWords.add(line.toLowerCase());
                    }
                }
            }
        } catch (Exception e) {
            log.error("加载敏感词库失败: {}", e.getMessage());
            loadDefaultWords();
        }
    }

    /** 默认敏感词库（文件加载失败时兜底） */
    private void loadDefaultWords() {
        // 政治敏感
        blockedWords.addAll(List.of(
            "falun", "法轮功", "六四", "天安门事件"
        ));
        // 色情相关
        blockedWords.addAll(List.of(
            "色情", "淫秽", "成人影片"
        ));
        // 暴力恐怖
        blockedWords.addAll(List.of(
            "恐怖主义", "炸弹制作"
        ));
        // 违法内容
        blockedWords.addAll(List.of(
            "黑客攻击", "翻墙", "盗版"
        ));
        // 警告级
        warnWords.addAll(List.of(
            "免费领取", "加微信", "扫码"
        ));
    }

    /** 编译正则模式 */
    private void compilePatterns() {
        // 隐私信息模式
        privacyPatterns.add(Pattern.compile("1[3-9]\\d{9}"));              // 手机号
        privacyPatterns.add(Pattern.compile("\\d{17}[0-9Xx]"));           // 身份证号
        privacyPatterns.add(Pattern.compile("\\d{16,19}"));               // 银行卡号（长数字）
        privacyPatterns.add(Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")); // 邮箱

        // 违规模式
        blockedPatterns.add(Pattern.compile("(?i)(hack|破解|crack)\\s*(网站|系统|服务器|账号)"));
        blockedPatterns.add(Pattern.compile("(代写|代考|替考|作弊).*(论文|作业|考试)"));
        blockedPatterns.add(Pattern.compile("(出售|买卖).*(枪支|毒品|迷药)"));
    }

    /**
     * 对内容进行安全检查
     *
     * @param content 待检测文本
     * @return SafetyCheckResult 检测结果
     */
    public SafetyCheckResult check(String content) {
        if (content == null || content.isBlank()) {
            return new SafetyCheckResult(Action.PASS, Collections.emptyList(), "空内容");
        }

        String lowerContent = content.toLowerCase();
        List<String> matchedItems = new ArrayList<>();
        Action finalAction = Action.PASS;

        // 第一层：高危敏感词匹配
        for (String word : blockedWords) {
            if (lowerContent.contains(word)) {
                matchedItems.add("高危词: " + word);
                finalAction = Action.BLOCK;
            }
        }

        // 第二层：高危正则匹配
        for (Pattern pattern : blockedPatterns) {
            if (pattern.matcher(content).find()) {
                matchedItems.add("违规模式: " + pattern.pattern());
                finalAction = Action.BLOCK;
            }
        }

        if (finalAction == Action.BLOCK) {
            log.warn("内容安全拦截(BLOCK): matched={}", matchedItems);
            return new SafetyCheckResult(Action.BLOCK, matchedItems, "内容命中高危安全规则，已被拦截");
        }

        // 第三层：低危词匹配
        for (String word : warnWords) {
            if (lowerContent.contains(word)) {
                matchedItems.add("低危词: " + word);
                finalAction = Action.WARN;
            }
        }

        // 第四层：隐私信息检测
        for (Pattern pattern : privacyPatterns) {
            if (pattern.matcher(content).find()) {
                matchedItems.add("隐私信息: " + pattern.pattern());
                finalAction = Action.WARN;
            }
        }

        if (finalAction == Action.WARN) {
            log.info("内容安全提示(WARN): matched={}", matchedItems);
            return new SafetyCheckResult(Action.WARN, matchedItems,
                    "内容可能包含敏感信息，已标记但未拦截");
        }

        return new SafetyCheckResult(Action.PASS, Collections.emptyList(), "内容安全检测通过");
    }

    /**
     * 对AI生成的内容进行安全检查（生成后检测）
     * 与check()相同逻辑，但在AI输出场景使用
     */
    public SafetyCheckResult checkGenerated(String content) {
        return check(content);
    }

    // ==================== 内部类 ====================

    public enum Action {
        /** 通过，无需处理 */
        PASS,
        /** 警告，记录日志但放行 */
        WARN,
        /** 拦截，阻止内容输出 */
        BLOCK
    }

    public static class SafetyCheckResult {
        private final Action action;
        private final List<String> matchedItems;
        private final String message;

        public SafetyCheckResult(Action action, List<String> matchedItems, String message) {
            this.action = action;
            this.matchedItems = matchedItems;
            this.message = message;
        }

        public boolean isBlocked() { return action == Action.BLOCK; }
        public boolean isWarning() { return action == Action.WARN; }
        public boolean isPassed() { return action == Action.PASS; }
        public Action getAction() { return action; }
        public List<String> getMatchedItems() { return matchedItems; }
        public String getMessage() { return message; }
    }
}
